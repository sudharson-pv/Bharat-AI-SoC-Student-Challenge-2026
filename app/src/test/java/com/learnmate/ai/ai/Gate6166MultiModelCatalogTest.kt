package com.learnmate.ai.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

/**
 * Gate 6.16.6: Multi-Llama Model Support, Catalog Metadata, Dynamic Model Switching,
 * Hardware Compatibility, and Format Rejection Automated Unit Test Suite.
 */
class Gate6166MultiModelCatalogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testAppDir: File
    private lateinit var testModelsDir: File
    private lateinit var assetManager: ModelAssetManager
    private lateinit var engine: ModelDownloadEngine

    @Before
    fun setUp() {
        testAppDir = tempFolder.newFolder("app_files")
        testModelsDir = File(testAppDir, "models").apply { mkdirs() }
        assetManager = ModelAssetManager(testAppDir)
        engine = ModelDownloadEngine(testModelsDir)
    }

    // =========================================================================
    // SECTION 1: Catalog Registry & Metadata Integrity
    // =========================================================================

    @Test
    fun testModelCatalog_allEntriesPresent_andValidMetadata() {
        val entries = ModelCatalog.ALL_ENTRIES
        assertEquals("Catalog must contain exactly 3 models (1B, 3B, 8B)", 3, entries.size)

        val entry1B = ModelCatalog.ENTRY_LLAMA_3_2_1B
        val entry3B = ModelCatalog.ENTRY_LLAMA_3_2_3B
        val entry8B = ModelCatalog.ENTRY_LLAMA_3_1_8B

        // 1B Verification
        assertEquals(ModelCatalog.ENTRY_LLAMA_3_2_1B.modelId, entry1B.modelId)
        assertEquals(803033472L, entry1B.modelSizeBytes)
        assertEquals(ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES, entry1B.tokenizerSizeBytes)
        assertEquals("XNNPACK CPU", entry1B.targetBackend)
        assertEquals("arm64-v8a", entry1B.supportedAbi)
        assertTrue(entry1B.isDefaultRecommended)

        // 3B Verification
        assertEquals(ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId, entry3B.modelId)
        assertEquals(1984524288L, entry3B.modelSizeBytes)
        assertEquals(ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES, entry3B.tokenizerSizeBytes)
        assertEquals("XNNPACK CPU", entry3B.targetBackend)
        assertEquals("arm64-v8a", entry3B.supportedAbi)
        assertFalse(entry3B.isDefaultRecommended)

        // 8B Verification
        assertEquals(ModelCatalog.ENTRY_LLAMA_3_1_8B.modelId, entry8B.modelId)
        assertEquals(4850000000L, entry8B.modelSizeBytes)
        assertEquals(ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES, entry8B.tokenizerSizeBytes)
        assertEquals("XNNPACK CPU", entry8B.targetBackend)
        assertEquals("arm64-v8a", entry8B.supportedAbi)
        assertFalse(entry8B.isDefaultRecommended)
    }

    // =========================================================================
    // SECTION 2: Signed Manifest Verification for All Catalog Models
    // =========================================================================

    @Test
    fun testSignedManifest_1B_validatesWithPublisherKey() {
        val manifest = ModelCatalog.ENTRY_LLAMA_3_2_1B.signedManifest
        val result = ModelSignatureVerifier.verifyManifest(manifest)
        assertTrue("1B manifest must validate with publisher key", result is ModelSignatureVerifier.VerificationResult.Valid)
    }

    @Test
    fun testSignedManifest_3B_validatesWithPublisherKey() {
        val manifest = ModelCatalog.ENTRY_LLAMA_3_2_3B.signedManifest
        val result = ModelSignatureVerifier.verifyManifest(manifest)
        assertTrue("3B manifest must validate with publisher key", result is ModelSignatureVerifier.VerificationResult.Valid)
    }

    @Test
    fun testSignedManifest_8B_validatesWithPublisherKey() {
        val manifest = ModelCatalog.ENTRY_LLAMA_3_1_8B.signedManifest
        val result = ModelSignatureVerifier.verifyManifest(manifest)
        assertTrue("8B manifest must validate with publisher key", result is ModelSignatureVerifier.VerificationResult.Valid)
    }

    // =========================================================================
    // SECTION 3: Hardware Compatibility Evaluation
    // =========================================================================

    @Test
    fun testHardwareCompatibility_motoG54Reference_8GBRam() {
        // Motorola Moto G54 5G Specs: Android 15 (API 35), 8 GB RAM (8,000,000,000 B), 60 GB Storage
        val apiLevel = 35
        val totalRamBytes = 8_000_000_000L
        val freeStorageBytes = 60_000_000_000L

        val result1B = ModelCatalog.ENTRY_LLAMA_3_2_1B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertTrue("Moto G54 5G must support Llama 3.2 1B", result1B.isCompatible)

        val result3B = ModelCatalog.ENTRY_LLAMA_3_2_3B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertTrue("Moto G54 5G (8 GB RAM) must support Llama 3.2 3B (requires 6 GB RAM)", result3B.isCompatible)

        val result8B = ModelCatalog.ENTRY_LLAMA_3_1_8B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertFalse("Moto G54 5G (8 GB RAM) must REJECT Llama 3.1 8B (requires 12 GB RAM)", result8B.isCompatible)
        assertTrue("Failure reason must mention RAM insufficiency", result8B.failureReason?.contains("RAM") == true)

        val recommended = ModelCatalog.getRecommendedEntry(totalRamBytes)
        assertEquals("1B must be the default recommended model on 8 GB devices for optimal balance", ModelCatalog.ENTRY_LLAMA_3_2_1B.modelId, recommended.modelId)
    }

    @Test
    fun testHardwareCompatibility_budgetLegacyDevice_2GBRam() {
        // Vivo 1820 Specs: Android 8.1 (API 27), 1.84 GB RAM (1,975,000,000 B), 10 GB Storage
        val apiLevel = 27
        val totalRamBytes = 1_975_000_000L
        val freeStorageBytes = 10_000_000_000L

        val result1B = ModelCatalog.ENTRY_LLAMA_3_2_1B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertFalse("Budget device with API 27 and <2GB RAM must be incompatible", result1B.isCompatible)

        val result3B = ModelCatalog.ENTRY_LLAMA_3_2_3B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertFalse("Budget device cannot run 3B", result3B.isCompatible)

        val result8B = ModelCatalog.ENTRY_LLAMA_3_1_8B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        assertFalse("Budget device cannot run 8B", result8B.isCompatible)
    }

    @Test
    fun testHardwareCompatibility_flagshipWorkstation_16GBRam() {
        // High-end Android flagship with 16 GB RAM
        val apiLevel = 35
        val totalRamBytes = 16_000_000_000L
        val freeStorageBytes = 100_000_000_000L

        val result1B = ModelCatalog.ENTRY_LLAMA_3_2_1B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        val result3B = ModelCatalog.ENTRY_LLAMA_3_2_3B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)
        val result8B = ModelCatalog.ENTRY_LLAMA_3_1_8B.isCompatibleWithHardware(apiLevel, totalRamBytes, freeStorageBytes)

        assertTrue("Flagship must support 1B", result1B.isCompatible)
        assertTrue("Flagship must support 3B", result3B.isCompatible)
        assertTrue("Flagship with 16 GB RAM must support 8B", result8B.isCompatible)

        val recommended = ModelCatalog.getRecommendedEntry(totalRamBytes)
        assertEquals("3B is recommended on flagship devices for maximum reasoning power", ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId, recommended.modelId)
    }

    // =========================================================================
    // SECTION 4: Incompatible Model Format Rejection (GGUF)
    // =========================================================================

    @Test
    fun testGgufRejection_byExtensionAndMagicBytes() {
        // 1. Extension check
        val ggufFile = File(testModelsDir, "llama3_2_1b.gguf").apply {
            writeBytes("GGUF_TEST_FILE".toByteArray())
        }
        assertTrue("GGUF extension must be recognized as GGUF", assetManager.isGgufFile(ggufFile))

        // 2. Binary magic bytes check (0x47 0x47 0x55 0x46 = 'GGUF')
        val disguisedFile = File(testModelsDir, "disguised_llama.pte")
        FileOutputStream(disguisedFile).use { fos ->
            fos.write(byteArrayOf(0x47, 0x47, 0x55, 0x46, 0x01, 0x00, 0x00, 0x00))
        }
        assertTrue("File with GGUF magic bytes must be identified even if renamed to .pte", assetManager.isGgufFile(disguisedFile))

        // 3. Clean PTE file should not be detected as GGUF
        val validPteDummy = File(testModelsDir, "valid_model.pte").apply {
            writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        }
        assertFalse("Valid binary should not be identified as GGUF", assetManager.isGgufFile(validPteDummy))
    }

    // =========================================================================
    // SECTION 5: Model Selection and Switching in DownloadEngine
    // =========================================================================

    @Test
    fun testModelDownloadEngine_selectionSwitchesActiveStateAndPaths() {
        // Initial state is 1B
        assertEquals(ModelCatalog.ENTRY_LLAMA_3_2_1B.modelId, engine.selectedEntry.value.modelId)
        assertEquals(803033472L, engine.downloadFlow.value.totalBytes)

        // Select 3B
        val entry3B = ModelCatalog.ENTRY_LLAMA_3_2_3B
        engine.selectCatalogEntry(entry3B)

        assertEquals(ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId, engine.selectedEntry.value.modelId)
        assertEquals(1984524288L, engine.downloadFlow.value.totalBytes)
        assertTrue("Final model file name must match 3B", engine.finalModelFile.name == entry3B.modelFileName)
        assertTrue("Partial model file must match 3B .part quarantine", engine.partialModelFile.name == ".${entry3B.modelFileName}.part")
    }

    // =========================================================================
    // SECTION 6: Model Asset Management & Safe Deletion Policy
    // =========================================================================

    @Test
    fun testModelAssetManager_safeDeletionPreventsDeletingActiveModel() {
        val activeModelId = ModelCatalog.ENTRY_LLAMA_3_2_1B.modelId

        // Mock 1B installed
        val pte1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.modelFileName).apply {
            writeBytes(ByteArray(100))
        }
        assertTrue(pte1B.exists())

        // Attempting to delete the active model MUST fail
        val deleteActiveResult = assetManager.deleteModel(activeModelId, activeModelId)
        assertFalse("Deleting the currently active model must be strictly rejected", deleteActiveResult)
        assertTrue("Active model file must remain on disk", pte1B.exists())

        // Mock a secondary installed model (3B) in its subdirectory
        val sub3B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId).apply { mkdirs() }
        val pte3B = File(sub3B, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelFileName).apply {
            writeBytes(ByteArray(100))
        }
        assertTrue(pte3B.exists())

        // Deleting non-active model MUST succeed
        val delete3BResult = assetManager.deleteModel(ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId, activeModelId)
        assertTrue("Deleting inactive secondary model must succeed", delete3BResult)
        assertFalse("Inactive model directory should be deleted", sub3B.exists())
    }

    // =========================================================================
    // SECTION 7: Mock Model Switching Lifecycle in ExecuTorchLlamaEngine
    // =========================================================================

    @Test
    fun testExecuTorchLlamaEngine_switchModel_lifecycle() = runBlocking {
        var createAndLoadCount = 0
        var stopCount = 0
        var lastLoadedModelPath: String? = null

        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
                createAndLoadCount++
                lastLoadedModelPath = modelPath
                return Object()
            }

            override fun generate(
                moduleInstance: Any,
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
                onStats: (Float) -> Unit
            ): Int {
                onToken("Test token")
                onStats(10.0f)
                return 1
            }

            override fun stop(module: Any) {
                stopCount++
            }
        }

        val llamaEngine = ExecuTorchLlamaEngine(assetManager, mockBridge)

        // Mock 1B files
        val pte1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.modelFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.modelSizeBytes)
            raf.close()
        }
        val tok1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerSizeBytes)
            raf.close()
        }

        // Mock 3B files in subdirectory
        val sub3B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId).apply { mkdirs() }
        val pte3B = File(sub3B, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_3B.modelSizeBytes)
            raf.close()
        }
        val tok3B = File(sub3B, ModelCatalog.ENTRY_LLAMA_3_2_3B.tokenizerFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_3B.tokenizerSizeBytes)
            raf.close()
        }

        // 1. Initial load with 1B
        val loadResult = llamaEngine.loadModel()
        assertTrue("Initial load of 1B should succeed", loadResult.isSuccess)
        assertEquals(1, createAndLoadCount)
        assertEquals(0, stopCount)
        assertEquals(ModelRuntimeState.READY, llamaEngine.runtimeState.value)

        // 2. Switch to 3B
        val switchResult = llamaEngine.switchModel(ModelCatalog.ENTRY_LLAMA_3_2_3B)
        assertTrue("Switch to 3B should succeed", switchResult.isSuccess)
        assertEquals("Native stop() must be called to unload previous module", 1, stopCount)
        assertEquals("createAndLoadModule must be called for the new model", 2, createAndLoadCount)
        assertEquals(pte3B.absolutePath, lastLoadedModelPath)
        assertEquals(ModelRuntimeState.READY, llamaEngine.runtimeState.value)
    }

    @Test
    fun testExecuTorchLlamaEngine_switchModel_rollbackOnTargetFailure() = runBlocking {
        var createAndLoadCount = 0
        var stopCount = 0
        var shouldThrowOnTarget = true
        var lastLoadedModelPath: String? = null

        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
                createAndLoadCount++
                lastLoadedModelPath = modelPath
                if (modelPath.contains("3b") && shouldThrowOnTarget) {
                    throw RuntimeException("Simulated native JNI error during 3B allocation")
                }
                return Object()
            }

            override fun generate(
                moduleInstance: Any,
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
                onStats: (Float) -> Unit
            ): Int {
                return 1
            }

            override fun stop(module: Any) {
                stopCount++
            }
        }

        val llamaEngine = ExecuTorchLlamaEngine(assetManager, mockBridge)

        // Mock 1B files
        val pte1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.modelFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.modelSizeBytes)
            raf.close()
        }
        val tok1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerSizeBytes)
            raf.close()
        }

        // Mock 3B files in subdirectory
        val sub3B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelId).apply { mkdirs() }
        val pte3B = File(sub3B, ModelCatalog.ENTRY_LLAMA_3_2_3B.modelFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_3B.modelSizeBytes)
            raf.close()
        }
        val tok3B = File(sub3B, ModelCatalog.ENTRY_LLAMA_3_2_3B.tokenizerFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_3B.tokenizerSizeBytes)
            raf.close()
        }

        // 1. Initial load 1B succeeds
        val initialLoad = llamaEngine.loadModel()
        assertTrue(initialLoad.isSuccess)
        assertEquals(ModelRuntimeState.READY, llamaEngine.runtimeState.value)
        assertEquals(pte1B.absolutePath, llamaEngine.loadedModelPath)

        // 2. Switch to 3B fails at JNI level -> initiates rollback to 1B
        val switchResult = llamaEngine.switchModel(ModelCatalog.ENTRY_LLAMA_3_2_3B)
        assertFalse("Switch result must be failure due to JNI error", switchResult.isSuccess)
        assertTrue(switchResult.exceptionOrNull()?.message?.contains("Successfully rolled back") == true)

        // 3. Verify rollback restored previous model into READY state
        assertEquals("Runtime state must be restored to READY following rollback", ModelRuntimeState.READY, llamaEngine.runtimeState.value)
        assertEquals("Loaded model path must be restored to 1B", pte1B.absolutePath, llamaEngine.loadedModelPath)
        assertEquals("Stop must be called for previous model before target attempt", 1, stopCount)
        assertEquals("createAndLoadModule must be called: 1 (init 1B) + 1 (failed 3B) + 1 (rollback 1B) = 3", 3, createAndLoadCount)
    }

    @Test
    fun testExecuTorchLlamaEngine_switchModel_preFlightRejectionKeepsActiveModelUntouched() = runBlocking {
        var createAndLoadCount = 0
        var stopCount = 0

        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
                createAndLoadCount++
                return Object()
            }
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int = 1
            override fun stop(module: Any) { stopCount++ }
        }

        val llamaEngine = ExecuTorchLlamaEngine(assetManager, mockBridge)

        // Mock 1B
        val pte1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.modelFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.modelSizeBytes)
            raf.close()
        }
        val tok1B = File(testModelsDir, ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerFileName).apply {
            val raf = java.io.RandomAccessFile(this, "rw")
            raf.setLength(ModelCatalog.ENTRY_LLAMA_3_2_1B.tokenizerSizeBytes)
            raf.close()
        }

        llamaEngine.loadModel()
        assertEquals(1, createAndLoadCount)
        assertEquals(0, stopCount)
        assertEquals(ModelRuntimeState.READY, llamaEngine.runtimeState.value)

        // Attempt switch to 3B when 3B files DO NOT exist on disk
        val switchMissingResult = llamaEngine.switchModel(ModelCatalog.ENTRY_LLAMA_3_2_3B)
        assertFalse(switchMissingResult.isSuccess)

        // Active model must NOT have been stopped or unloaded!
        assertEquals("Current model must NOT be stopped if pre-flight fails", 0, stopCount)
        assertEquals("No new module load should be attempted", 1, createAndLoadCount)
        assertEquals("Active model remains 1B", pte1B.absolutePath, llamaEngine.loadedModelPath)
        assertEquals(ModelRuntimeState.READY, llamaEngine.runtimeState.value)
    }
}
