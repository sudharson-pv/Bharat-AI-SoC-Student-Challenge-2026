package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.*
import com.learnmate.ai.data.db.*
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.LearningMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Gate 6.3 Universal Content Ecosystem & Multi-Track Ingestion Verification Tests
 */
class Gate63ContentEcosystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val keyPair = ContentPackSecurity.generateTestKeyPair()
    private val md = MessageDigest.getInstance("SHA-256")
    private lateinit var baseStorageDir: File

    @Before
    fun setUp() {
        baseStorageDir = tempFolder.newFolder("gate63_content_packs_storage")
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }

    @org.junit.After
    fun tearDown() {
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] = ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64
        ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
    }

    private fun sha256(bytes: ByteArray): String {
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createSampleManifest(
        packId: String = "pack_gate63_sci10",
        formatVersion: String = "1.0.0",
        payloads: List<PayloadFileEntry> = emptyList()
    ): ContentPackManifest {
        return ContentPackManifest(
            formatVersion = formatVersion,
            packId = packId,
            version = "1.0.0",
            minAppVersion = "1.4.0",
            title = "CBSE Class 10 Science Starter",
            category = "K12",
            primaryLanguage = "en",
            supportedLanguages = listOf("en", "ta"),
            authorOrProvider = "NCERT & CBSE Curriculum Team",
            provenanceNotice = "Official Curriculum Content",
            publisherKeyId = "staging_ref_key",
            dependencies = emptyList(),
            curriculumNodes = listOf(
                CurriculumNodeItem("node_cbse10_root", null, "DISCIPLINE", "CBSE-SCI-10", "Class 10 Science", "Full curriculum", 120, 1),
                CurriculumNodeItem("node_chem_01", "node_cbse10_root", "TOPIC", "CHEM-01", "Chemical Reactions and Equations", "Chapter 1", 30, 1),
                CurriculumNodeItem("node_phys_01", "node_cbse10_root", "TOPIC", "PHYS-01", "Light - Reflection and Refraction", "Chapter 10", 30, 2)
            ),
            prerequisites = emptyList(),
            resources = listOf(
                ResourceItem(
                    resourceId = "res_chem01_en",
                    nodeId = "node_chem_01",
                    languageCode = "en",
                    verifiedExplanation = "A chemical reaction involves breaking and making of bonds between atoms.",
                    referenceTitle = "NCERT Science Class 10",
                    referenceAuthor = "NCERT",
                    referenceEdition = "2024",
                    referenceChapter = "1",
                    referenceSection = "1.1",
                    referenceIdentifier = "NCERT-SCI10-CH1"
                ),
                ResourceItem(
                    resourceId = "res_chem01_ta",
                    nodeId = "node_chem_01",
                    languageCode = "ta",
                    verifiedExplanation = "வேதிவினைகள் அணுக்களுக்கிடையே உள்ள பிணைப்புகளை உடைத்து உருவாக்குகின்றன.",
                    referenceTitle = "NCERT Science Class 10 (Tamil)",
                    referenceAuthor = "NCERT",
                    referenceEdition = "2024",
                    referenceChapter = "1",
                    referenceSection = "1.1",
                    referenceIdentifier = "NCERT-SCI10-CH1-TA"
                )
            ),
            payloadFiles = payloads
        )
    }

    // ── Phase 1: Ed25519 Publisher-Key Audit Verification ──────────────────────

    @Test
    fun testEd25519ManifestSignatureVerification_ValidInstalledPack() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Official Chapter 1 Text".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))

        val packFile = File(tempFolder.root, "valid_manifest.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val installResult = installer.installPack(packFile, keyPair.public)
        assertTrue("Pack installation must succeed", installResult is ContentPackInstaller.InstallResult.Success)

        // Mandatory Gate 6.3 requirement: auditInstalledPackIntegrity re-verifies Ed25519 manifest signature
        val auditPassed = installer.auditInstalledPackIntegrity(manifest.packId, keyPair.public)
        assertTrue("Installed pack with valid Ed25519 signature must pass audit", auditPassed)
    }

    @Test
    fun testEd25519ManifestSignatureVerification_TamperedManifestFailsAudit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Official Chapter 1 Text".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))

        val packFile = File(tempFolder.root, "tamper_manifest.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))
        installer.installPack(packFile, keyPair.public)

        val installedDir = File(baseStorageDir, "installed/${manifest.packId}")
        val manifestFile = File(installedDir, "manifest.json")
        assertTrue("Installed manifest.json must exist", manifestFile.exists())

        // Attacker tampers with installed manifest.json post-installation
        val originalText = manifestFile.readText()
        val tamperedText = originalText.replace("Class 10 Science", "Hacked Malicious Curriculum")
        manifestFile.writeText(tamperedText)

        // Audit must detect cryptographic signature invalidation over modified manifest.json
        val auditResult = installer.auditInstalledPackIntegrity(manifest.packId, keyPair.public)
        assertFalse("Tampered manifest.json must fail cryptographic audit", auditResult)
    }

    @Test
    fun testEd25519ManifestSignatureVerification_TamperedSignatureFailsAudit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Official Chapter 1 Text".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))

        val packFile = File(tempFolder.root, "tamper_sig.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))
        installer.installPack(packFile, keyPair.public)

        val sigFile = File(baseStorageDir, "installed/${manifest.packId}/signature.bin")
        assertTrue("Installed signature.bin must exist", sigFile.exists())

        // Corrupt signature bytes
        val sigBytes = sigFile.readBytes()
        sigBytes[10] = (sigBytes[10].toInt() xor 0xFF).toByte()
        sigFile.writeBytes(sigBytes)

        val auditResult = installer.auditInstalledPackIntegrity(manifest.packId, keyPair.public)
        assertFalse("Corrupted signature.bin must fail cryptographic audit", auditResult)
    }

    @Test
    fun testEd25519ManifestSignatureVerification_MissingSignatureFailsAudit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Official Chapter 1 Text".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))

        val packFile = File(tempFolder.root, "missing_sig.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))
        installer.installPack(packFile, keyPair.public)

        val sigFile = File(baseStorageDir, "installed/${manifest.packId}/signature.bin")
        sigFile.delete()

        val auditResult = installer.auditInstalledPackIntegrity(manifest.packId, keyPair.public)
        assertFalse("Missing signature.bin must fail cryptographic audit", auditResult)
    }

    // ── Phase 1: Publisher-Key Revocation ──────────────────────────────────────

    @Test
    fun testPublisherKeyRevocation_BlocksArchiveValidation() {
        val revokedKeyPair = ContentPackSecurity.generateTestKeyPair()
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] = java.util.Base64.getEncoder().encodeToString(revokedKeyPair.public.encoded)
        val keyHash = ContentPackSecurity.calculateSha256(revokedKeyPair.public.encoded)

        val payload = "Revoked publisher payload".toByteArray()
        val path = "resources/revoked.txt"
        val manifest = createSampleManifest(
            packId = "pack_revoked_test",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "revoked_publisher.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, revokedKeyPair, mapOf(path to payload))

        try {
            // Revoke the key
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.add(keyHash)

            val validation = ContentPackSecurity.validateArchive(packFile, revokedKeyPair.public)
            assertFalse("Revoked publisher key must fail validation", validation.isValid)
            assertEquals("REVOKED_PUBLISHER_KEY", validation.errorCode)
        } finally {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.remove(keyHash)
        }
    }

    @Test
    fun testPublisherKeyRevocation_BlocksInstalledPackAudit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val revokedKeyPair = ContentPackSecurity.generateTestKeyPair()
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] = java.util.Base64.getEncoder().encodeToString(revokedKeyPair.public.encoded)
        val keyHash = ContentPackSecurity.calculateSha256(revokedKeyPair.public.encoded)

        val payload = "Revoked installed payload".toByteArray()
        val path = "resources/revoked_installed.txt"
        val manifest = createSampleManifest(
            packId = "pack_revoked_installed",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "revoked_installed.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, revokedKeyPair, mapOf(path to payload))
        val installRes = installer.installPack(packFile, revokedKeyPair.public)
        assertTrue("Install must succeed before revocation", installRes is ContentPackInstaller.InstallResult.Success)

        try {
            // Revoke the key
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.add(keyHash)

            val auditPassed = installer.auditInstalledPackIntegrity(manifest.packId, revokedKeyPair.public)
            assertFalse("Installed pack from revoked publisher key must fail audit", auditPassed)
        } finally {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.remove(keyHash)
        }
    }

    // ── Phase 2: ContentPackDocumentImporter Streaming & SAF Invariants ───────

    @Test
    fun testContentPackDocumentImporter_RejectsNonLmpackExtension() = runBlocking {
        val importer = ContentPackDocumentImporter(baseStorageDir = baseStorageDir, dao = null)
        val dummyBytes = "Dummy payload".toByteArray()

        val result = importer.importFromInputStream(
            inputStream = ByteArrayInputStream(dummyBytes),
            fileName = "malicious_archive.zip",
            declaredSize = dummyBytes.size.toLong()
        )

        assertTrue(result is ContentPackDocumentImporter.ImportResult.Failure)
        assertEquals("INVALID_FILE_EXTENSION", (result as ContentPackDocumentImporter.ImportResult.Failure).errorCode)
    }

    @Test
    fun testContentPackDocumentImporter_RejectsOversizedPack() = runBlocking {
        val importer = ContentPackDocumentImporter(baseStorageDir = baseStorageDir, dao = null)
        val dummyBytes = "Dummy payload".toByteArray()

        val result = importer.importFromInputStream(
            inputStream = ByteArrayInputStream(dummyBytes),
            fileName = "oversized_curriculum.lmpack",
            declaredSize = ContentPackSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES + 1024L
        )

        assertTrue(result is ContentPackDocumentImporter.ImportResult.Failure)
        assertEquals("FILE_TOO_LARGE", (result as ContentPackDocumentImporter.ImportResult.Failure).errorCode)
    }

    @Test
    fun testContentPackDocumentImporter_SuccessfulStreamingInstallAndCleanup() = runBlocking {
        val importer = ContentPackDocumentImporter(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Streaming SAF Pack content".toByteArray()
        val path = "resources/saf_ch1.txt"
        val manifest = createSampleManifest(
            packId = "pack_saf_success",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "saf_stream_pack.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val progressStages = mutableListOf<String>()
        val result = FileInputStream(packFile).use { stream ->
            importer.importFromInputStream(
                inputStream = stream,
                fileName = "saf_stream_pack.lmpack",
                declaredSize = packFile.length(),
                trustedPublicKey = keyPair.public,
                onProgress = { stage, _ -> progressStages.add(stage) }
            )
        }

        assertTrue("Import must succeed", result is ContentPackDocumentImporter.ImportResult.Success)
        val success = result as ContentPackDocumentImporter.ImportResult.Success
        assertEquals("pack_saf_success", success.packId)
        assertEquals("CBSE Class 10 Science Starter", success.title)
        assertEquals(3, success.nodesInstalled)

        // Verify staging directory is cleaned up
        val stagingDir = File(baseStorageDir, ".staging")
        val stagingFiles = stagingDir.listFiles() ?: emptyArray()
        assertTrue(
            "Staging temporary import files must be cleaned up, found: ${stagingFiles.map { it.name }}",
            stagingFiles.none { it.name.startsWith("saf_import_") }
        )

        // Verify installed pack is intact
        assertTrue(File(baseStorageDir, "installed/pack_saf_success/$path").exists())
    }

    // ── Phase 5 & 8: Multi-Track Assignment & Lifecycle ────────────────────────

    @Test
    fun testMultiTrackAssignment_SharesCurriculumAcrossMultipleTracks() = runBlocking {
        val storedTracks = mutableListOf<LearnerTrackEntity>()
        val fakeNodes = listOf(
            CurriculumNodeEntity("node_cbse10_root", null, "pack_gate63_sci10", "DISCIPLINE", "CBSE-SCI-10", "Science 10", "Desc", 120, 1),
            CurriculumNodeEntity("node_chem_01", "node_cbse10_root", "pack_gate63_sci10", "TOPIC", "CHEM-01", "Chemical Reactions", "Desc", 30, 1),
            CurriculumNodeEntity("node_phys_01", "node_cbse10_root", "pack_gate63_sci10", "TOPIC", "PHYS-01", "Light", "Desc", 30, 2)
        )

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTracksForUser" -> storedTracks.filter { it.userId == args[0] }
                "upsertTrack" -> {
                    val track = args[0] as LearnerTrackEntity
                    storedTracks.removeIf { it.trackId == track.trackId && it.userId == track.userId }
                    storedTracks.add(track)
                    null
                }
                "getNodesForPack" -> {
                    val pId = args[0] as String
                    fakeNodes.filter { it.packId == pId }
                }
                "deleteContentPackSync" -> null
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)
        val userId = "learner_multi_01"

        // 1. Create two independent learner tracks
        val trackA = repository.createTrack(userId = userId, title = "CBSE Board Exam 2026", learningMode = LearningMode.COMPETITIVE_EXAM)
        val trackB = repository.createTrack(userId = userId, title = "Science Olympiad Preparation", learningMode = LearningMode.ACADEMIC)

        // 2. Assign root curriculum node to Track A, and specific chapter node to Track B
        repository.assignCurriculumToTrack(userId = userId, trackId = trackA.trackId, curriculumNodeId = "node_cbse10_root")
        repository.assignCurriculumToTrack(userId = userId, trackId = trackB.trackId, curriculumNodeId = "node_chem_01")

        // 3. Verify getAssignedTracksForPack identifies both tracks
        val assignedTracks = repository.getAssignedTracksForPack(userId = userId, packId = "pack_gate63_sci10")
        assertEquals(2, assignedTracks.size)
        assertTrue(assignedTracks.any { it.trackId == trackA.trackId })
        assertTrue(assignedTracks.any { it.trackId == trackB.trackId })

        // 4. Unassign Track A; Track B must remain intact
        repository.unassignCurriculumFromTrack(userId = userId, trackId = trackA.trackId)
        val remainingAssigned = repository.getAssignedTracksForPack(userId = userId, packId = "pack_gate63_sci10")
        assertEquals(1, remainingAssigned.size)
        assertEquals(trackB.trackId, remainingAssigned.first().trackId)

        // Track A still exists in repository, only curriculum detachment occurred
        val userTracks = repository.getTracksForUser(userId)
        val reloadedTrackA = userTracks.first { it.trackId == trackA.trackId }
        assertNull(reloadedTrackA.targetCurriculumNodeId)
    }

    @Test
    fun testSafeUninstallation_BlockedWhenTracksAssignedWithoutForce() = runBlocking {
        val storedTracks = mutableListOf<LearnerTrackEntity>()
        val fakeNodes = listOf(
            CurriculumNodeEntity("node_chem_01", null, "pack_protect_uninstall", "TOPIC", "CHEM-01", "Chem", "Desc", 30, 1)
        )

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTracksForUser" -> storedTracks.filter { it.userId == args[0] }
                "upsertTrack" -> {
                    val track = args[0] as LearnerTrackEntity
                    storedTracks.removeIf { it.trackId == track.trackId && it.userId == track.userId }
                    storedTracks.add(track)
                    null
                }
                "getNodesForPack" -> {
                    val pId = args[0] as String
                    fakeNodes.filter { it.packId == pId }
                }
                "deleteContentPackSync" -> null
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)
        val userId = "learner_guard_01"

        // Create track and assign pack
        val track = repository.createTrack(userId = userId, title = "Active Track", learningMode = LearningMode.ACADEMIC)
        repository.assignCurriculumToTrack(userId = userId, trackId = track.trackId, curriculumNodeId = "node_chem_01")

        // Create fake installed pack on disk
        val packInstalledDir = File(baseStorageDir, "installed/pack_protect_uninstall")
        packInstalledDir.mkdirs()
        File(packInstalledDir, "manifest.json").writeText("{}")

        // Attempt uninstall without force
        val uninstallResult = repository.uninstallContentPack(
            packId = "pack_protect_uninstall",
            baseStorageDir = baseStorageDir,
            forceIfAssigned = false,
            currentUserId = userId
        )

        assertFalse("Uninstallation must be rejected when active tracks are assigned", uninstallResult)
        assertTrue("Installed pack directory must remain intact", packInstalledDir.exists())

        // Force uninstall succeeds
        val forceUninstallResult = repository.uninstallContentPack(
            packId = "pack_protect_uninstall",
            baseStorageDir = baseStorageDir,
            forceIfAssigned = true,
            currentUserId = userId
        )

        assertTrue("Force uninstallation must succeed", forceUninstallResult)
        assertFalse("Installed pack directory must be removed", packInstalledDir.exists())
    }

    // ── Phase 6 & 7: Multilingual Resource Resolution ─────────────────────────

    @Test
    fun testMultilingualResourceResolution_MatchesRequestedLanguage() = runBlocking {
        val fakeResources = listOf(
            CurriculumResourceEntity("res_en", "node_chem_01", "en", "English explanation", "Book", "Author", "1st", "1", "1.1", "ID-1"),
            CurriculumResourceEntity("res_ta", "node_chem_01", "ta", "தமிழ் விளக்கம்", "Book TA", "Author", "1st", "1", "1.1", "ID-2")
        )

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getResourceForNode" -> {
                    val nId = args[0] as String
                    val lCode = args[1] as String
                    fakeResources.firstOrNull { it.nodeId == nId && it.languageCode == lCode }
                }
                "getResourcesForNode" -> {
                    val nId = args[0] as String
                    fakeResources.filter { it.nodeId == nId }
                }
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)

        // Request Tamil
        val resolution = repository.resolveResourceForNode(nodeId = "node_chem_01", preferredLanguageCode = "ta")
        assertNotNull(resolution.resource)
        assertEquals("ta", resolution.resource?.languageCode)
        assertEquals("ta", resolution.languageUsed)
        assertFalse("Direct match must not be flagged as fallback", resolution.isFallback)
        assertNull(resolution.fallbackNotice)
    }

    @Test
    fun testMultilingualResourceResolution_FallsBackToEnglishWithNotice() = runBlocking {
        val fakeResources = listOf(
            CurriculumResourceEntity("res_en", "node_chem_01", "en", "English explanation", "Book", "Author", "1st", "1", "1.1", "ID-1")
        )

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getResourceForNode" -> {
                    val nId = args[0] as String
                    val lCode = args[1] as String
                    fakeResources.firstOrNull { it.nodeId == nId && it.languageCode == lCode }
                }
                "getResourcesForNode" -> {
                    val nId = args[0] as String
                    fakeResources.filter { it.nodeId == nId }
                }
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)

        // Request Hindi when only English is available
        val resolution = repository.resolveResourceForNode(nodeId = "node_chem_01", preferredLanguageCode = "hi")
        assertNotNull(resolution.resource)
        assertEquals("en", resolution.resource?.languageCode)
        assertEquals("en", resolution.languageUsed)
        assertTrue("Fallback must be clearly indicated", resolution.isFallback)
        assertNotNull(resolution.fallbackNotice)
        assertTrue("Notice must inform user of fallback", resolution.fallbackNotice!!.contains("Resource unavailable in Hindi. Showing primary English content."))
    }
}

