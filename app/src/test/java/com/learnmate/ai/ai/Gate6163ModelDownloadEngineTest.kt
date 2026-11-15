package com.learnmate.ai.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * Gate 6.16.3: Automated Qualification Suite for On-Device AI Model Download,
 * Hardware Compatibility Verification, and Atomic Installation Engine.
 */
class Gate6163ModelDownloadEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testModelsDir: File
    private lateinit var customManifest: ModelManifestRegistry.ModelDistributionManifest
    private lateinit var engine: ModelDownloadEngine

    @Before
    fun setUp() {
        testModelsDir = tempFolder.newFolder("models")
        customManifest = ModelManifestRegistry.PRIMARY_1B_MANIFEST.copy(
            modelFileName = "test_model.pte",
            modelSizeBytes = 1024L,
            modelSha256 = "dummy_sha256_for_test",
            tokenizerFileName = "test_tokenizer.model",
            tokenizerSizeBytes = 256L,
            tokenizerSha256 = "dummy_tok_sha256"
        )
        engine = ModelDownloadEngine(testModelsDir, customManifest)
    }

    @Test
    fun testDeviceCompatibility_api27_rejectedAsIncompatible() {
        // Vivo 1820: Android 8.1.0, API 27
        val result = ModelManifestRegistry.evaluateDeviceCompatibility(
            apiLevel = 27,
            totalRamBytes = 1_842_304L * 1024L, // 1.84 GB
            availableStorageBytes = 20L * 1024L * 1024L * 1024L // 20 GB
        )

        assertFalse("API 27 must be rejected as incompatible", result.isCompatible)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("API level 27"))
        assertEquals(27, result.evaluatedApiLevel)
    }

    @Test
    fun testDeviceCompatibility_lowRam_rejectedAsIncompatible() {
        // Device with modern OS (API 34) but only 2 GB RAM
        val result = ModelManifestRegistry.evaluateDeviceCompatibility(
            apiLevel = 34,
            totalRamBytes = 2L * 1024L * 1024L * 1024L, // 2.0 GB
            availableStorageBytes = 30L * 1024L * 1024L * 1024L
        )

        assertFalse("Sub-3.5GB RAM device must be rejected for on-device 1B Llama", result.isCompatible)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("Insufficient Device RAM"))
    }

    @Test
    fun testDeviceCompatibility_insufficientStorage_rejectedAsIncompatible() {
        // Device with good OS and RAM, but only 500 MB free storage
        val result = ModelManifestRegistry.evaluateDeviceCompatibility(
            apiLevel = 35,
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            availableStorageBytes = 500L * 1024L * 1024L // 500 MB
        )

        assertFalse("Device with < 2.5 GB free storage must be rejected", result.isCompatible)
        assertNotNull(result.failureReason)
        assertTrue(result.failureReason!!.contains("Insufficient Storage"))
    }

    @Test
    fun testDeviceCompatibility_motoG54Specs_acceptedAsCompatible() {
        // Motorola Moto G54 5G: Android 15 (API 35), 8 GB RAM, 64 GB free storage
        val result = ModelManifestRegistry.evaluateDeviceCompatibility(
            apiLevel = 35,
            totalRamBytes = 8L * 1024L * 1024L * 1024L,
            availableStorageBytes = 64L * 1024L * 1024L * 1024L
        )

        assertTrue("Moto G54 5G hardware specifications must pass qualification", result.isCompatible)
        assertNull(result.failureReason)
        assertEquals(35, result.evaluatedApiLevel)
        assertEquals(8192L, result.evaluatedTotalRamMb)
    }

    @Test
    fun testPreFlightCompatibility_engineStateTransition() {
        val compatible = engine.evaluatePreFlightCompatibility(
            apiLevel = 27,
            totalRamBytes = 1_842_304L * 1024L,
            availableStorageBytes = 20L * 1024L * 1024L * 1024L
        )

        assertFalse(compatible)
        val snapshot = engine.downloadFlow.value
        assertEquals(ModelDownloadEngine.ModelDownloadState.INCOMPATIBLE, snapshot.state)
        assertEquals("Unsupported device hardware", snapshot.currentStage)
        assertNotNull(snapshot.errorMessage)
    }

    @Test
    fun testDigestCalculation_sha256Accurate() {
        val testContent = "LearnMate-AI-ExecuTorch-Gate-6-16-3-Verification".toByteArray(Charsets.UTF_8)
        val file = File(testModelsDir, "sample_test_blob.bin").apply {
            writeBytes(testContent)
        }

        val calculated = ModelManifestRegistry.calculateFileSha256(file)

        // Calculate expected SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val expected = digest.digest(testContent).joinToString("") { "%02x".format(it) }

        assertEquals(expected, calculated)
    }

    @Test
    fun testAtomicPromotion_partFilesPromotedCleanly() {
        // Simulate downloaded .part files
        val dummyData = ByteArray(1024) { 0x42 }
        engine.partialModelFile.writeBytes(dummyData)
        engine.partialTokenizerFile.writeBytes(ByteArray(256) { 0x24 })

        assertTrue(engine.partialModelFile.exists())
        assertTrue(engine.partialTokenizerFile.exists())
        assertFalse(engine.finalModelFile.exists())
        assertFalse(engine.finalTokenizerFile.exists())

        // Calculate actual SHA256 of dummyData
        val actualSha256 = ModelManifestRegistry.calculateFileSha256(engine.partialModelFile)
        val verifiedManifest = customManifest.copy(modelSha256 = actualSha256)
        val testEngine = ModelDownloadEngine(testModelsDir, verifiedManifest)

        // Manual atomic promotion verification
        testEngine.partialModelFile.renameTo(testEngine.finalModelFile)
        testEngine.partialTokenizerFile.renameTo(testEngine.finalTokenizerFile)

        assertTrue(testEngine.finalModelFile.exists())
        assertEquals(1024L, testEngine.finalModelFile.length())
        assertTrue(testEngine.finalTokenizerFile.exists())
        assertEquals(256L, testEngine.finalTokenizerFile.length())
        assertFalse(testEngine.partialModelFile.exists())
        assertFalse(testEngine.partialTokenizerFile.exists())
    }

    @Test
    fun testPartialFileCleanup_onCancel() {
        engine.partialModelFile.writeBytes(ByteArray(500) { 0x01 })
        engine.partialTokenizerFile.writeBytes(ByteArray(100) { 0x02 })

        assertTrue(engine.partialModelFile.exists())
        assertTrue(engine.partialTokenizerFile.exists())

        engine.cancel()

        assertFalse("Partial model file must be deleted upon cancellation", engine.partialModelFile.exists())
        assertFalse("Partial tokenizer file must be deleted upon cancellation", engine.partialTokenizerFile.exists())
        val snapshot = engine.downloadFlow.value
        assertEquals(ModelDownloadEngine.ModelDownloadState.DOWNLOAD_REQUIRED, snapshot.state)
        assertEquals(0L, snapshot.downloadedBytes)
    }

    @Test
    fun testMobileDataApprovalGate() = runBlocking {
        val snapshotBefore = engine.downloadFlow.value
        assertFalse(snapshotBefore.mobileDataApproved)

        // Attempt download on mobile data without approval
        val result = engine.downloadAndInstall(
            modelSourceUrl = "https://invalid.domain.internal/model.pte",
            tokenizerSourceUrl = "https://invalid.domain.internal/tok.model",
            isMobileNetwork = true
        )

        assertFalse("Download must fail or halt if mobile data is unapproved", result)
        val snapshotAfter = engine.downloadFlow.value
        assertTrue(snapshotAfter.isMobileData)
        assertFalse(snapshotAfter.mobileDataApproved)
        assertTrue(snapshotAfter.currentStage.contains("Approval required"))

        // Now approve
        engine.approveMobileDataUsage()
        assertTrue(engine.downloadFlow.value.mobileDataApproved)
    }

    @Test
    fun testRefreshInstalledStatus_detectsExistingFiles() {
        // Put valid completed files
        engine.finalModelFile.writeBytes(ByteArray(1024) { 0x11 })
        engine.finalTokenizerFile.writeBytes(ByteArray(256) { 0x22 })

        val status = engine.refreshInstalledStatus()
        assertEquals(ModelDownloadEngine.ModelDownloadState.DOWNLOADED, status)
        val snapshot = engine.downloadFlow.value
        assertEquals(ModelDownloadEngine.ModelDownloadState.DOWNLOADED, snapshot.state)
        assertEquals(100, snapshot.percent)
    }
}

