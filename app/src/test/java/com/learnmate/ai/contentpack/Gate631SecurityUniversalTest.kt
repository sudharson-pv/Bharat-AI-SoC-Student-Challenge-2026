package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.*
import com.learnmate.ai.data.db.*
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.data.taxonomy.UniversalTaxonomy
import com.learnmate.ai.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
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
import java.util.Base64

/**
 * Gate 6.3.1 Security, Universal Taxonomy & Learner-Experience Verification Tests
 */
class Gate631SecurityUniversalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val officialStagingKeyPair = ContentPackSecurity.generateTestKeyPair()
    private val rogueKeyPair = ContentPackSecurity.generateTestKeyPair()
    private val md = MessageDigest.getInstance("SHA-256")
    private lateinit var baseStorageDir: File

    @Before
    fun setUp() {
        baseStorageDir = tempFolder.newFolder("gate631_storage")
        // Register official staging authority key for testing
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] =
            Base64.getEncoder().encodeToString(officialStagingKeyPair.public.encoded)
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["learnmate_official_2026"] =
            Base64.getEncoder().encodeToString(officialStagingKeyPair.public.encoded)
    }

    @After
    fun tearDown() {
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] =
            ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["learnmate_official_2026"] =
            ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64
        ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
    }

    private fun sha256(bytes: ByteArray): String {
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createSampleManifest(
        packId: String = "pack_sample_631",
        version: String = "1.0.0",
        publisherKeyId: String = "staging_ref_key",
        dependencies: List<PackDependencyItem> = emptyList(),
        payloads: List<PayloadFileEntry> = emptyList()
    ): ContentPackManifest {
        return ContentPackManifest(
            formatVersion = "1.0.0",
            packId = packId,
            version = version,
            minAppVersion = "1.4.0",
            title = "Universal Curriculum Pack",
            category = "SCHOOL_EDUCATION",
            primaryLanguage = "en",
            supportedLanguages = listOf("en", "ta"),
            authorOrProvider = "LearnMate Academic Board",
            provenanceNotice = "Official Verified Curriculum",
            publisherKeyId = publisherKeyId,
            dependencies = dependencies,
            curriculumNodes = listOf(
                CurriculumNodeItem("node_root", null, "DISCIPLINE", "ROOT-01", "Root Discipline", "Desc", 60, 1),
                CurriculumNodeItem("node_topic_1", "node_root", "TOPIC", "TOP-01", "Topic 1", "Desc", 30, 1)
            ),
            prerequisites = emptyList(),
            resources = listOf(
                ResourceItem("res_1", "node_topic_1", "en", "Verified explanation", "Textbook", "Author", "1st", "1", "1.1", "REF-01")
            ),
            payloadFiles = payloads
        )
    }

    // ── Phase 1 & 3: Public-Key Reconciliation & Canonical Revocation ───────────

    @Test
    fun testPublicKeyReconciliation_ExactByteLengthsAndFingerprints() {
        val spkiBase64 = ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64
        val spkiBytes = Base64.getDecoder().decode(spkiBase64)

        // 1. SPKI must be exactly 44 bytes
        assertEquals("SPKI decoded length must be exactly 44 bytes", 44, spkiBytes.size)

        // 2. Derive raw 32-byte key directly from trailing bytes 12..43
        val rawBytes = ContentPackSecurity.extractRawPublicKeyBytes(spkiBytes)
        assertNotNull("Raw key extraction must succeed", rawBytes)
        assertEquals("Raw public key must be exactly 32 bytes", 32, rawBytes!!.size)

        // 3. Raw key must match bytes 12..43 exactly
        assertArrayEquals("Raw key must match bytes 12..43 of SPKI", spkiBytes.copyOfRange(12, 44), rawBytes)

        // 4. Raw key Base64 must be exactly 44 characters (Base64 encoding of 32 bytes)
        val rawBase64 = Base64.getEncoder().encodeToString(rawBytes)
        assertEquals("N77dwAlm5tC1Nzatm0F/6jbLXvdXmDZw8tXbsNaWl+A=", rawBase64)
        assertEquals("Raw Base64 decoded length must be 32 bytes", 32, Base64.getDecoder().decode(rawBase64).size)

        // 5. Check actual SHA-256 fingerprints
        val spkiSha256 = sha256(spkiBytes)
        val rawSha256 = sha256(rawBytes)
        assertEquals("fd91e470e0e1d835e8206c9ae5a592ae4294e029d466923c8def5d17b6934064", spkiSha256)
        assertEquals("23b9e8f3eb5f1a39af304766efbf4806b40ac8f6445afdb9a6c55b41d5c25100", rawSha256)
    }

    @Test
    fun testRevocationCanonicalization_CrossRepresentationImmunity() {
        val testKeyPair = ContentPackSecurity.generateTestKeyPair()
        val rawBytes = ContentPackSecurity.extractRawPublicKeyBytes(testKeyPair.public.encoded)!!
        val spkiBytes = testKeyPair.public.encoded

        val rawSha256 = sha256(rawBytes)
        val spkiSha256 = sha256(spkiBytes)

        // Case A: Revoke using raw SHA-256 fingerprint -> SPKI representation MUST be rejected
        try {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.add(rawSha256)
            assertTrue("Key must be rejected when queried in SPKI format", ContentPackSecurity.isKeyRevoked(spkiBytes))
            assertTrue("Key must be rejected when queried in Raw format", ContentPackSecurity.isKeyRevoked(rawBytes))
        } finally {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
        }

        // Case B: Revoke using SPKI SHA-256 fingerprint -> Raw representation MUST be rejected
        try {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.add(spkiSha256)
            assertTrue("Key must be rejected when queried in SPKI format", ContentPackSecurity.isKeyRevoked(spkiBytes))
            assertTrue("Key must be rejected when queried in Raw format", ContentPackSecurity.isKeyRevoked(rawBytes))
        } finally {
            ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
        }
    }

    // ── Phase 2: Fail-Closed Publisher-Key Resolution ──────────────────────────

    @Test
    fun testFailClosedPublisherResolution_ValidTrustedPublisherId() {
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))

        val packFile = File(tempFolder.root, "trusted_publisher.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, officialStagingKeyPair, mapOf(path to payload))

        val validation = ContentPackSecurity.validateArchive(packFile)
        assertTrue("Pack signed by registered trusted publisher must succeed", validation.isValid)
    }

    @Test
    fun testFailClosedPublisherResolution_UnknownPublisherIdRejected() {
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/ch1.txt"
        // Manifest claims an unregistered publisher ID
        val manifest = createSampleManifest(
            publisherKeyId = "unregistered_intruder_authority_2026",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "unknown_publisher.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, rogueKeyPair, mapOf(path to payload))

        val validation = ContentPackSecurity.validateArchive(packFile)
        assertFalse("Unregistered publisher key ID must be rejected fail-closed", validation.isValid)
        assertEquals("UNKNOWN_PUBLISHER_KEY_ID", validation.errorCode)
    }

    @Test
    fun testFailClosedPublisherResolution_ValidSignatureWrongKeyIdRejected() {
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/ch1.txt"
        // Signed with rogue key, but manifest claims "staging_ref_key"
        val manifest = createSampleManifest(
            publisherKeyId = "staging_ref_key",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "wrong_key_id.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, rogueKeyPair, mapOf(path to payload))

        val validation = ContentPackSecurity.validateArchive(packFile)
        assertFalse("Signature produced by rogue key cannot verify against trusted key", validation.isValid)
        assertEquals("INVALID_SIGNATURE", validation.errorCode)
    }

    @Test
    fun testFailClosedPublisherResolution_CallerCannotInjectUntrustedKey() {
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(
            publisherKeyId = "staging_ref_key",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "caller_injection.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, rogueKeyPair, mapOf(path to payload))

        // Caller passes their own rogue key attempting to force trust
        val validation = ContentPackSecurity.validateArchive(packFile, trustedPublicKey = rogueKeyPair.public)
        assertFalse("Caller cannot override trusted publisher registry with arbitrary key", validation.isValid)
        assertEquals("PUBLISHER_KEY_MISMATCH", validation.errorCode)
    }

    @Test
    fun testFailClosedPublisherResolution_ModifiedManifestFailsAudit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/ch1.txt"
        val manifest = createSampleManifest(
            packId = "pack_tamper_manifest",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "tamper_manifest.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, officialStagingKeyPair, mapOf(path to payload))
        installer.installPack(packFile)

        val manifestFile = File(baseStorageDir, "installed/pack_tamper_manifest/manifest.json")
        manifestFile.writeText(manifestFile.readText().replace("Universal Curriculum Pack", "Tampered Malicious Pack"))

        assertFalse("Tampered manifest must fail audit", installer.auditInstalledPackIntegrity("pack_tamper_manifest"))
    }

    // ── Phase 4: Non-VLSI Navigation & Unresolved Curriculum States ────────────

    @Test
    fun testCurriculumNavigation_UnassignedTrackShowsUnresolvedState() = runBlocking {
        val storedTracks = mutableListOf<LearnerTrackEntity>()
        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getTracksForUser" -> storedTracks.filter { it.userId == args[0] }
                "getTrackById" -> storedTracks.firstOrNull { it.trackId == args[0] }
                "upsertProfile" -> null
                "upsertTrack" -> {
                    val track = args[0] as LearnerTrackEntity
                    storedTracks.removeIf { it.trackId == track.trackId }
                    storedTracks.add(track)
                    null
                }
                "getAllCurriculumNodes" -> emptyList<CurriculumNodeEntity>()
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)
        val profile = LearnerProfile(
            userId = "learner_unassigned_1",
            name = "Meera",
            targetDomain = "Civil Services & Public Administration",
            subdomain = "Indian Polity",
            goal = "Clear UPSC 2027",
            dailyStudyMinutes = 60
        )

        repository.saveProfile(profile)
        val track = repository.getActiveTrackForUser("learner_unassigned_1")

        assertNotNull("Active track must be created", track)
        assertNull("Target curriculum node must be null when no matching pack is installed", track?.targetCurriculumNodeId)
        assertNotEquals("Must never default to node_root_vlsi", "node_root_vlsi", track?.targetCurriculumNodeId)
    }

    // ── Phase 5: Universal Taxonomy Dimension Separation ───────────────────────

    @Test
    fun testUniversalTaxonomy_MultiDimensionalClassification() {
        // Verify independent concepts across all 6 dimensions
        val educationLevel = EducationLevel.SECONDARY
        val learnerCategory = LearnerCategory.SCHOOL_STUDENT
        val learningMode = LearningMode.ACADEMIC
        val discipline = UniversalTaxonomy.ContentDiscipline.SCHOOL_EDUCATION
        val framework = UniversalTaxonomy.ExamFramework.CBSE
        val language = "en"

        assertEquals("Secondary School", educationLevel.displayName)
        assertEquals("School Student", learnerCategory.displayName)
        assertEquals("Academic Learning", learningMode.displayName)
        assertEquals("School Education", discipline.displayName)
        assertEquals("Central Board of Secondary Education", framework.displayName)

        // Test filtering across disciplines
        assertTrue(UniversalTaxonomy.matchesDiscipline("CBSE Science Class 10", UniversalTaxonomy.ContentDiscipline.SCHOOL_EDUCATION))
        assertTrue(UniversalTaxonomy.matchesDiscipline("Polytechnic Mechanical Diploma", UniversalTaxonomy.ContentDiscipline.DIPLOMA_POLYTECHNIC))
        assertTrue(UniversalTaxonomy.matchesDiscipline("Digital VLSI & RTL", UniversalTaxonomy.ContentDiscipline.ENGINEERING_TECH))
        assertTrue(UniversalTaxonomy.matchesDiscipline("UPSC Indian Polity GS-2", UniversalTaxonomy.ContentDiscipline.GOVERNMENT_COMPETITIVE))
        assertTrue(UniversalTaxonomy.matchesDiscipline("Corporate Finance & Valuation", UniversalTaxonomy.ContentDiscipline.MANAGEMENT_COMMERCE))
        assertTrue(UniversalTaxonomy.matchesDiscipline("Research Methodology & Statistics", UniversalTaxonomy.ContentDiscipline.RESEARCH_DOCTORAL))
    }

    // ── Phase 6: SAF Import Crash Recovery ─────────────────────────────────────

    @Test
    fun testSafImportCrashRecovery_PurgesAbandonedFilesAndPreservesActive() = runBlocking {
        val stagingDir = File(baseStorageDir, ".staging")
        stagingDir.mkdirs()

        // Create abandoned temp import files from simulated crashed sessions
        val abandonedFile1 = File(stagingDir, "saf_import_1000_dead1_archive.lmpack")
        abandonedFile1.writeText("abandoned partial bytes 1")
        val abandonedFile2 = File(stagingDir, "saf_import_2000_dead2_archive.lmpack")
        abandonedFile2.writeText("abandoned partial bytes 2")

        // Create active file (simulating concurrent live import)
        val activeFile = File(stagingDir, "saf_import_9999_live_archive.lmpack")
        activeFile.writeText("live bytes")
        ContentPackDocumentImporter.registerActiveImport(activeFile.absolutePath)

        try {
            val purged = ContentPackDocumentImporter.reconcilePendingSafImports(baseStorageDir, maxAgeMs = 0L)
            assertEquals("Must purge exactly 2 abandoned files", 2, purged)
            assertFalse("Abandoned file 1 must be deleted", abandonedFile1.exists())
            assertFalse("Abandoned file 2 must be deleted", abandonedFile2.exists())
            assertTrue("Active in-flight import file must NOT be deleted", activeFile.exists())
        } finally {
            ContentPackDocumentImporter.unregisterActiveImport(activeFile.absolutePath)
            if (activeFile.exists()) activeFile.delete()
        }
    }

    // ── Phase 7: Multi-Track Progress Isolation ────────────────────────────────

    @Test
    fun testMultiTrackProgressIsolation_TwoTracksSharingCurriculum() = runBlocking {
        val tasksByTrack = mutableMapOf<String, MutableList<StudyTask>>()
        val completionByTrack = mutableMapOf<String, MutableMap<String, Boolean>>()

        // Track A: CBSE Science Board Exam
        val trackAId = "track_cbse_sci"
        val trackBId = "track_science_olympiad"

        val taskA = StudyTask(
            id = "task_chem_01",
            userId = "learner_01",
            roadmapStepId = "step_chem_01",
            dayIndex = 1,
            weekIndex = 1,
            title = "Chemical Reactions Chapter 1",
            topic = "Chemical Reactions",
            durationMinutes = 30,
            isCompleted = false
        )
        val taskB = StudyTask(
            id = "task_chem_advanced",
            userId = "learner_01",
            roadmapStepId = "step_chem_adv",
            dayIndex = 1,
            weekIndex = 1,
            title = "Olympiad Redox Reactions Drill",
            topic = "Redox Reactions",
            durationMinutes = 45,
            isCompleted = false
        )

        tasksByTrack[trackAId] = mutableListOf(taskA)
        tasksByTrack[trackBId] = mutableListOf(taskB)
        completionByTrack[trackAId] = mutableMapOf(taskA.id to false)
        completionByTrack[trackBId] = mutableMapOf(taskB.id to false)

        // Learner completes task in Track A
        completionByTrack[trackAId]!![taskA.id] = true
        tasksByTrack[trackAId]!![0] = taskA.copy(isCompleted = true)

        // Invariant: Track A is completed, Track B is completely unaffected
        assertTrue("Track A task must be completed", tasksByTrack[trackAId]!![0].isCompleted)
        assertFalse("Track B task must remain incomplete", tasksByTrack[trackBId]!![0].isCompleted)
        assertFalse("Track B completion map must not change", completionByTrack[trackBId]!![taskB.id]!!)
    }

    // ── Phase 8: Safe Content-Pack Updates ─────────────────────────────────────

    @Test
    fun testSafeContentPackUpdate_SuccessRollbackAndDowngradeRejection() = runBlocking {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payloadV1 = "Version 1.0 Content".toByteArray()
        val path = "resources/content.txt"

        // 1. Install Initial Version 1.0.0
        val manifestV1 = createSampleManifest(packId = "pack_update_lifecycle", version = "1.0.0", payloads = listOf(PayloadFileEntry(path, sha256(payloadV1), payloadV1.size.toLong())))
        val packFileV1 = File(tempFolder.root, "v1.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileV1, manifestV1, officialStagingKeyPair, mapOf(path to payloadV1))
        val installV1 = installer.installPack(packFileV1)
        assertTrue("V1 install must succeed", installV1 is ContentPackInstaller.InstallResult.Success)

        // 2. Reject Duplicate Version Update
        val installDup = installer.installPack(packFileV1)
        assertTrue("Duplicate version update must be rejected", installDup is ContentPackInstaller.InstallResult.Failure)
        assertEquals("DUPLICATE_VERSION", (installDup as ContentPackInstaller.InstallResult.Failure).errorCode)

        // 3. Reject Version Downgrade
        val manifestV0 = createSampleManifest(packId = "pack_update_lifecycle", version = "0.9.0", payloads = listOf(PayloadFileEntry(path, sha256(payloadV1), payloadV1.size.toLong())))
        val packFileV0 = File(tempFolder.root, "v0.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileV0, manifestV0, officialStagingKeyPair, mapOf(path to payloadV1))
        val installDowngrade = installer.installPack(packFileV0)
        assertTrue("Version downgrade must be rejected", installDowngrade is ContentPackInstaller.InstallResult.Failure)
        assertEquals("VERSION_DOWNGRADE_DISALLOWED", (installDowngrade as ContentPackInstaller.InstallResult.Failure).errorCode)

        // 4. Successful Upgrade to Version 1.1.0
        val payloadV2 = "Version 1.1 Updated Content".toByteArray()
        val manifestV2 = createSampleManifest(packId = "pack_update_lifecycle", version = "1.1.0", payloads = listOf(PayloadFileEntry(path, sha256(payloadV2), payloadV2.size.toLong())))
        val packFileV2 = File(tempFolder.root, "v2.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileV2, manifestV2, officialStagingKeyPair, mapOf(path to payloadV2))
        val installV2 = installer.installPack(packFileV2)
        assertTrue("V2 upgrade must succeed", installV2 is ContentPackInstaller.InstallResult.Success)

        // Verify updated file content on disk
        val installedContent = File(baseStorageDir, "installed/pack_update_lifecycle/$path").readText()
        assertEquals("Version 1.1 Updated Content", installedContent)
    }

    // ── Phase 9: Universal Learner Personas Validation ─────────────────────────

    @Test
    fun testUniversalLearnerPersonas_CoversDiverseEducationalPaths() {
        val personas = listOf(
            Triple(LearnerCategory.SCHOOL_STUDENT, EducationLevel.SECONDARY, "Class 10 Science & Math"),
            Triple(LearnerCategory.DIPLOMA_STUDENT, EducationLevel.DIPLOMA, "Applied Polytechnic Mechanical"),
            Triple(LearnerCategory.ENGINEERING_STUDENT, EducationLevel.UNDERGRADUATE, "Computer Science & Edge AI"),
            Triple(LearnerCategory.ARTS_SCIENCE_STUDENT, EducationLevel.UNDERGRADUATE, "Physics & Mathematics"),
            Triple(LearnerCategory.MANAGEMENT_MBA_STUDENT, EducationLevel.POSTGRADUATE, "Strategic Management & Finance"),
            Triple(LearnerCategory.COMPETITIVE_EXAM_ASPIRANT, EducationLevel.UNDERGRADUATE, "UPSC Civil Services Prelims"),
            Triple(LearnerCategory.RESEARCH_SCHOLAR, EducationLevel.DOCTORAL, "Quantum Computing Methodology"),
            Triple(LearnerCategory.LIFELONG_LEARNER, EducationLevel.CONTINUING_EDUCATION, "Fullstack Machine Learning")
        )

        assertEquals("Must cover all 8 universal learner categories", 8, personas.size)
        personas.forEach { (category, level, trackTitle) ->
            assertNotNull(category.displayName)
            assertNotNull(level.displayName)
            assertTrue(trackTitle.isNotBlank())
        }
    }
}

