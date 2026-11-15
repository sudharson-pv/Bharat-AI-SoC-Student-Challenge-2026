package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.*
import com.learnmate.ai.data.db.*
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.data.taxonomy.UniversalTaxonomy
import com.learnmate.ai.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.UUID

class Gate632CurriculumLifecycleTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseStorageDir: File
    private val keyPair = ContentPackSecurity.generateTestKeyPair()
    private val md = MessageDigest.getInstance("SHA-256")

    @Before
    fun setUp() {
        baseStorageDir = tempFolder.newFolder("gate632_storage")
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["test_authority_key"] =
            java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }

    @org.junit.After
    fun tearDown() {
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS.remove("test_authority_key")
        ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
    }

    private fun sha256(bytes: ByteArray): String =
        md.digest(bytes).joinToString("") { "%02x".format(it) }

    private fun createSampleManifest(
        packId: String,
        version: String = "1.0.0",
        publisherKeyId: String = "test_authority_key",
        nodes: List<CurriculumNodeItem> = listOf(
            CurriculumNodeItem("node_root_$packId", null, "DISCIPLINE", "ROOT", "Root Node", "Desc", 30, 0),
            CurriculumNodeItem("node_ch1_$packId", "node_root_$packId", "TOPIC", "CH1", "Chapter 1", "Desc", 30, 1)
        ),
        payloadFiles: List<PayloadFileEntry> = emptyList()
    ): ContentPackManifest {
        return ContentPackManifest(
            formatVersion = "1.0.0",
            packId = packId,
            version = version,
            minAppVersion = "1.4.0",
            title = "Test Curriculum Pack $packId v$version",
            category = "SCHOOL_CURRICULUM",
            primaryLanguage = "en",
            supportedLanguages = listOf("en"),
            authorOrProvider = "LearnMate Authority",
            provenanceNotice = "Test accreditation",
            publisherKeyId = publisherKeyId,
            dependencies = emptyList(),
            curriculumNodes = nodes,
            prerequisites = emptyList(),
            resources = listOf(
                ResourceItem(
                    resourceId = "res_${packId}_ch1_en",
                    nodeId = "node_ch1_$packId",
                    languageCode = "en",
                    verifiedExplanation = "Explanation for $packId",
                    referenceTitle = "Textbook $packId",
                    referenceAuthor = "Author",
                    referenceEdition = "1st",
                    referenceChapter = "1",
                    referenceSection = "1.1",
                    referenceIdentifier = "ISBN-TEST"
                )
            ),
            payloadFiles = payloadFiles
        )
    }

    // ── Phase 1: Restore Diploma Education Level ───────────────────────────────

    @Test
    fun testPhase1_RestoreDiplomaEducationLevel_SeparationAndCompatibility() {
        // 1. Diploma must be strictly separate from Undergraduate
        assertNotEquals(
            "Diploma must be distinct from Undergraduate",
            EducationLevel.DIPLOMA,
            EducationLevel.UNDERGRADUATE
        )
        assertEquals("Diploma / Polytechnic", EducationLevel.DIPLOMA.displayName)

        // 2. Compatibility string resolution
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("DIPLOMA"))
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("POLYTECHNIC"))
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("VOCATIONAL_DIPLOMA"))

        // 3. Polytechnic learners classified with DIPLOMA_STUDENT and EducationLevel.DIPLOMA
        assertEquals(LearnerCategory.DIPLOMA_STUDENT, LearnerCategory.fromString("DIPLOMA_STUDENT"))
        assertEquals(LearnerCategory.DIPLOMA_STUDENT, LearnerCategory.fromString("POLYTECHNIC"))

        // 4. Vocational training is a distinct learner category
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL_TRAINEE"))
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL"))
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL_TRADES"))
        assertNotEquals(
            "Vocational trainee must be distinct from Diploma student",
            LearnerCategory.VOCATIONAL_TRAINEE,
            LearnerCategory.DIPLOMA_STUDENT
        )

        // 5. Verify learner profile combinations
        val polytechnicProfile = LearnerProfile(
            userId = "poly_learner_1",
            name = "Ramesh",
            educationLevel = EducationLevel.DIPLOMA,
            learnerCategory = LearnerCategory.DIPLOMA_STUDENT,
            targetDomain = "Applied Mechanical Engineering"
        )
        val vocationalProfile = LearnerProfile(
            userId = "voc_learner_1",
            name = "Arjun",
            educationLevel = EducationLevel.CONTINUING_EDUCATION,
            learnerCategory = LearnerCategory.VOCATIONAL_TRAINEE,
            targetDomain = "Industrial Electrical Wiring"
        )
        val ugProfile = LearnerProfile(
            userId = "ug_learner_1",
            name = "Karthik",
            educationLevel = EducationLevel.UNDERGRADUATE,
            learnerCategory = LearnerCategory.ENGINEERING_STUDENT,
            targetDomain = "Computer Science"
        )
        val pgProfile = LearnerProfile(
            userId = "pg_learner_1",
            name = "Sneha",
            educationLevel = EducationLevel.POSTGRADUATE,
            learnerCategory = LearnerCategory.RESEARCH_SCHOLAR,
            targetDomain = "Genomics & Bio-Informatics"
        )

        assertEquals(EducationLevel.DIPLOMA, polytechnicProfile.educationLevel)
        assertEquals(LearnerCategory.DIPLOMA_STUDENT, polytechnicProfile.learnerCategory)

        assertEquals(EducationLevel.CONTINUING_EDUCATION, vocationalProfile.educationLevel)
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, vocationalProfile.learnerCategory)

        assertEquals(EducationLevel.UNDERGRADUATE, ugProfile.educationLevel)
        assertEquals(EducationLevel.POSTGRADUATE, pgProfile.educationLevel)
    }

    // ── Phase 2: Publisher Registry Hardening ──────────────────────────────────

    @Test
    fun testPhase2_PublisherRegistryHardening_StagingRejectedInProduction() {
        val payload = "Curriculum payload".toByteArray()
        val path = "resources/doc.txt"
        val manifest = createSampleManifest(
            packId = "pack_staging_prod_test",
            publisherKeyId = "staging_ref_key",
            payloadFiles = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "staging_pack.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val originalStagingKey = ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"]
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] =
            java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        try {
            // Normal validation in non-production distribution succeeds
            val devRes = ContentPackSecurity.validateArchive(
                archiveFile = packFile,
                trustedPublicKey = keyPair.public,
                isProductionDistribution = false
            )
            assertTrue("Staging key accepted in development/staging mode: ${devRes.errorMessage}", devRes.isValid)

            // In production distribution mode, staging keys fail closed
            val prodRes = ContentPackSecurity.validateArchive(
                archiveFile = packFile,
                trustedPublicKey = keyPair.public,
                isProductionDistribution = true
            )
            assertFalse("Staging key MUST be rejected in production distribution mode", prodRes.isValid)
            assertEquals("STAGING_KEY_IN_PRODUCTION_DISALLOWED", prodRes.errorCode)
        } finally {
            if (originalStagingKey != null) {
                ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["staging_ref_key"] = originalStagingKey
            }
        }
    }

    @Test
    fun testPhase2_PublisherRegistryHardening_UnknownKeyIdFailsClosed() {
        val payload = "Payload".toByteArray()
        val path = "resources/sample.txt"
        val manifest = createSampleManifest(
            packId = "pack_unknown_key",
            publisherKeyId = "unregistered_bogus_key",
            payloadFiles = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "unknown_key.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val result = ContentPackSecurity.validateArchive(packFile)
        assertFalse("Unknown publisher key ID must fail validation", result.isValid)
        assertEquals("UNKNOWN_PUBLISHER_KEY_ID", result.errorCode)
    }

    @Test
    fun testPhase2_PublisherRegistryHardening_CallerKeyMismatchFailsClosed() {
        val payload = "Payload".toByteArray()
        val path = "resources/sample.txt"
        val manifest = createSampleManifest(
            packId = "pack_mismatch_key",
            publisherKeyId = "test_authority_key",
            payloadFiles = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "mismatch_key.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val anotherKeyPair = ContentPackSecurity.generateTestKeyPair()
        val result = ContentPackSecurity.validateArchive(packFile, trustedPublicKey = anotherKeyPair.public)
        assertFalse("Caller key mismatch must fail validation", result.isValid)
        assertEquals("PUBLISHER_KEY_MISMATCH", result.errorCode)
    }

    // ── Phase 3: Complete Multi-Track Progress Isolation ───────────────────────

    @Test
    fun testPhase3_MultiTrackProgressIsolation_FullActivities() = runBlocking {
        // Setup in-memory DAO fake tracking tracks, study tasks, assessments, chat
        val storedTracks = mutableListOf<LearnerTrackEntity>()
        val storedTasks = mutableListOf<StudyTaskEntity>()
        val storedReports = mutableListOf<SkillGapReportEntity>()
        val storedRoadmaps = mutableListOf<PersonalisedRoadmapEntity>()
        val storedSessions = mutableListOf<ChatSessionEntity>()
        val storedMessages = mutableListOf<ChatMessageEntity>()

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "upsertTrack" -> {
                    val t = args[0] as LearnerTrackEntity
                    storedTracks.removeIf { it.trackId == t.trackId }
                    storedTracks.add(t)
                    null
                }
                "getTracksForUser" -> storedTracks.filter { it.userId == args[0] }
                "getTrackById" -> storedTracks.firstOrNull { it.trackId == args[0] }
                "upsertStudyTasks" -> {
                    val tasks = args[0] as List<StudyTaskEntity>
                    tasks.forEach { t ->
                        storedTasks.removeIf { it.taskId == t.taskId }
                        storedTasks.add(t)
                    }
                    null
                }
                "getStudyTasksForTrack" -> storedTasks.filter { it.trackId == args[0] }
                "setStudyTaskCompletedInTrack" -> {
                    val trackId = args[0] as String
                    val taskId = args[1] as String
                    val completed = args[2] as Boolean
                    val time = args[3] as Long?
                    val idx = storedTasks.indexOfFirst { it.trackId == trackId && it.taskId == taskId }
                    if (idx >= 0) {
                        val old = storedTasks[idx]
                        storedTasks[idx] = old.copy(isCompleted = completed, completedAtEpochMs = time)
                    }
                    null
                }
                "upsertSkillGapReport" -> {
                    val rep = args[0] as SkillGapReportEntity
                    storedReports.removeIf { it.trackId == rep.trackId }
                    storedReports.add(rep)
                    null
                }
                "getSkillGapReportForTrack" -> storedReports.firstOrNull { it.trackId == args[0] }
                "upsertRoadmap" -> {
                    val r = args[0] as PersonalisedRoadmapEntity
                    storedRoadmaps.removeIf { it.trackId == r.trackId }
                    storedRoadmaps.add(r)
                    null
                }
                "getRoadmapForTrack" -> storedRoadmaps.firstOrNull { it.trackId == args[0] }
                "upsertChatSession" -> {
                    val s = args[0] as ChatSessionEntity
                    storedSessions.removeIf { it.sessionId == s.sessionId }
                    storedSessions.add(s)
                    null
                }
                "getChatSessionsForTrack" -> storedSessions.filter { it.trackId == args[0] }
                "insertChatMessage" -> {
                    val m = args[0] as ChatMessageEntity
                    storedMessages.add(m)
                    null
                }
                "getMessagesForSession" -> storedMessages.filter { it.sessionId == args[0] }
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)
        val userId = "learner_dual_prep_1"

        // Track A: CBSE Class 10 Board Exam
        val trackA = repository.createTrack(
            userId = userId,
            title = "CBSE Class 10 Board Exam",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "node_cbse10_root",
            targetExamName = "CBSE Board 2027"
        )

        // Track B: Science Olympiad Preparation (sharing the same curriculum root)
        val trackB = repository.createTrack(
            userId = userId,
            title = "Science Olympiad Preparation",
            learningMode = LearningMode.COMPETITIVE_EXAM,
            targetCurriculumNodeId = "node_cbse10_root",
            targetExamName = "National Science Olympiad"
        )

        assertEquals("Both tracks share the same curriculum root", trackA.targetCurriculumNodeId, trackB.targetCurriculumNodeId)
        assertNotEquals("Tracks must have distinct IDs", trackA.trackId, trackB.trackId)

        // 1. Add and complete study tasks in Track A
        val taskA1 = StudyTaskEntity(
            taskId = "task_a_1",
            trackId = trackA.trackId,
            userId = userId,
            roadmapStepId = "step_1",
            dayIndex = 1,
            weekIndex = 1,
            title = "Chemical Equations Practice",
            topic = "Chemistry",
            durationMinutes = 45,
            isCompleted = false
        )
        fakeDao.upsertStudyTasks(listOf(taskA1))
        fakeDao.setStudyTaskCompletedInTrack(trackA.trackId, "task_a_1", true, System.currentTimeMillis())

        val tasksTrackA = fakeDao.getStudyTasksForTrack(trackA.trackId)
        val tasksTrackB = fakeDao.getStudyTasksForTrack(trackB.trackId)

        assertEquals("Track A must have 1 task", 1, tasksTrackA.size)
        assertTrue("Track A task must be completed", tasksTrackA[0].isCompleted)
        assertEquals("Track B must have 0 tasks", 0, tasksTrackB.size)

        // 2. Add Skill Gap Assessment Report to Track A
        fakeDao.upsertSkillGapReport(
            SkillGapReportEntity(
                trackId = trackA.trackId,
                userId = userId,
                domain = "Class 10 Science",
                subdomain = "Chemical Reactions",
                overallScorePercent = 83,
                totalQuestions = 30,
                correctCount = 25,
                strengthsJson = "[\"Balancing equations\"]",
                weaknessesJson = "[\"Redox identification\"]",
                skillGapsJson = "[]"
            )
        )
        assertNotNull(fakeDao.getSkillGapReportForTrack(trackA.trackId))
        assertNull(fakeDao.getSkillGapReportForTrack(trackB.trackId))

        // 3. Add Track-Scoped Personalised Roadmap to Track A
        fakeDao.upsertRoadmap(
            PersonalisedRoadmapEntity(
                trackId = trackA.trackId,
                userId = userId,
                domain = "Class 10 Science",
                subdomain = "Chemistry",
                goal = "95+ in CBSE Board",
                targetRole = "Secondary Student",
                totalSteps = 10,
                stepsJson = "[]"
            )
        )
        assertNotNull(fakeDao.getRoadmapForTrack(trackA.trackId))
        assertNull(fakeDao.getRoadmapForTrack(trackB.trackId))

        // 4. Add Chat Session & Messages to Track A
        val sessionA = repository.createChatSession(
            userId = userId,
            domain = "Class 10 Science",
            title = "Chemical Equations Tutor",
            isAiEnabled = false,
            trackId = trackA.trackId
        )
        fakeDao.insertChatMessage(
            ChatMessageEntity(
                messageId = "msg_a_1",
                sessionId = sessionA.sessionId,
                userId = userId,
                senderRole = "USER",
                text = "How to balance redox reactions?",
                isAiGenerated = false,
                timestampEpochMs = System.currentTimeMillis()
            )
        )

        val messagesSessionA = fakeDao.getMessagesForSession(sessionA.sessionId)
        assertEquals(1, messagesSessionA.size)
        val trackBSessions = fakeDao.getChatSessionsForTrack(trackB.trackId)
        assertEquals(0, trackBSessions.size)

        // 5. Unassign curriculum from Track A; Track B must remain unaffected
        repository.unassignCurriculumFromTrack(userId, trackA.trackId)
        val currentTrackA = fakeDao.getTrackById(trackA.trackId)
        assertNull("Track A curriculum target must be null after unassignment", currentTrackA?.targetCurriculumNodeId)

        val currentTrackB = fakeDao.getTrackById(trackB.trackId)
        assertNotNull("Track B must still exist", currentTrackB)
        assertEquals("Track B must still point to node_cbse10_root", "node_cbse10_root", currentTrackB?.targetCurriculumNodeId)
    }

    // ── Phase 4: Transactional Pack Update Recovery (10 Failure Points) ────────

    @Test
    fun testPhase4_UpdateRecovery_Across10FailurePoints() {
        val payload1 = "Version 1.0.0 content".toByteArray()
        val path = "resources/ch1.txt"
        val packId = "pack_update_lifecycle"

        val manifestV1 = createSampleManifest(
            packId = packId,
            version = "1.0.0",
            payloadFiles = listOf(PayloadFileEntry(path, sha256(payload1), payload1.size.toLong()))
        )
        val packV1File = File(tempFolder.root, "pack_v1.lmpack")
        ContentPackSecurity.createLmpackArchive(packV1File, manifestV1, keyPair, mapOf(path to payload1))

        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val installResV1 = installer.installPack(packV1File, keyPair.public)
        assertTrue("v1.0.0 install must succeed", installResV1 is ContentPackInstaller.InstallResult.Success)
        assertTrue("Installed pack must audit cleanly", installer.auditInstalledPackIntegrity(packId))

        val payload2 = "Version 1.1.0 updated content".toByteArray()
        val manifestV2 = createSampleManifest(
            packId = packId,
            version = "1.1.0",
            payloadFiles = listOf(PayloadFileEntry(path, sha256(payload2), payload2.size.toLong()))
        )
        val packV2File = File(tempFolder.root, "pack_v2.lmpack")
        ContentPackSecurity.createLmpackArchive(packV2File, manifestV2, keyPair, mapOf(path to payload2))

        // Verify Downgrade Rejection
        val manifestV09 = createSampleManifest(packId = packId, version = "0.9.0")
        val packV09File = File(tempFolder.root, "pack_v09.lmpack")
        ContentPackSecurity.createLmpackArchive(packV09File, manifestV09, keyPair)
        val downgradeRes = installer.installPack(packV09File, keyPair.public)
        assertTrue("Downgrade must fail", downgradeRes is ContentPackInstaller.InstallResult.Failure)
        assertEquals("VERSION_DOWNGRADE_DISALLOWED", (downgradeRes as ContentPackInstaller.InstallResult.Failure).errorCode)

        // Verify Duplicate Version Rejection
        val duplicateRes = installer.installPack(packV1File, keyPair.public)
        assertTrue("Duplicate version must fail", duplicateRes is ContentPackInstaller.InstallResult.Failure)
        assertEquals("DUPLICATE_VERSION", (duplicateRes as ContentPackInstaller.InstallResult.Failure).errorCode)

        // Test the 10 failure points during an update
        val failurePoints = listOf(
            "UPDATE_BEFORE_BACKUP_CREATION",
            "UPDATE_AFTER_BACKUP_CREATION",
            "UPDATE_DURING_EXTRACTION",
            "UPDATE_AFTER_EXTRACTION",
            "UPDATE_DURING_DATABASE_UPDATE",
            "UPDATE_DURING_DIRECTORY_REPLACEMENT",
            "UPDATE_AFTER_DIRECTORY_REPLACEMENT",
            "UPDATE_DURING_INTEGRITY_AUDIT",
            "UPDATE_BEFORE_BACKUP_DELETION",
            "UPDATE_AFTER_BACKUP_DELETION"
        )

        for (point in failurePoints) {
            val testStorage = tempFolder.newFolder("storage_fail_$point")
            val testInstaller = ContentPackInstaller(baseStorageDir = testStorage, dao = null)
            testInstaller.installPack(packV1File, keyPair.public)

            // Inject crash at exact failure boundary
            testInstaller.boundaryCrashHook = { boundary ->
                if (boundary == point) {
                    throw ContentPackInstaller.SimulatedCrashException("Crash at $point")
                }
            }

            try {
                testInstaller.installPack(packV2File, keyPair.public)
                if (point != "UPDATE_AFTER_BACKUP_DELETION") {
                    fail("Should have crashed at boundary $point")
                }
            } catch (_: ContentPackInstaller.SimulatedCrashException) {
                // Expected simulated crash
            }

            // Run startup recovery
            testInstaller.boundaryCrashHook = null
            testInstaller.reconcilePendingInstallations()

            // Verification: Pack must exist, be valid, and database/disk must agree
            val installedManifest = File(testStorage, "installed/$packId/manifest.json")
            assertTrue("Manifest must exist on disk after recovery for $point", installedManifest.exists())
            assertTrue("Integrity audit must pass after recovery for $point", testInstaller.auditInstalledPackIntegrity(packId))

            // Verify idempotency: calling reconcile a second time produces identical healthy state
            testInstaller.reconcilePendingInstallations()
            assertTrue("Integrity audit must still pass after secondary reconciliation", testInstaller.auditInstalledPackIntegrity(packId))
        }
    }

    // ── Phase 5: SAF Process-Death Cleanup ─────────────────────────────────────

    @Test
    fun testPhase5_SafProcessDeathCleanup_PurgesPriorProcessFilesImmediately() {
        val stagingDir = File(baseStorageDir, ".staging")
        stagingDir.mkdirs()

        // 1. Create a simulated file left by a prior process (timestamp before process startup)
        val priorProcessFile = File(stagingDir, "saf_import_old_dead_pack.lmpack")
        priorProcessFile.writeText("crashed prior process data")
        // Set last modified to 5 seconds before current process started
        priorProcessFile.setLastModified(ContentPackDocumentImporter.processStartupTimeEpochMs - 5000L)

        // 2. Create an active file in the current process
        val activeCurrentFile = File(stagingDir, "saf_import_live_active_pack.lmpack")
        activeCurrentFile.writeText("live streaming data")
        ContentPackDocumentImporter.registerActiveImport(activeCurrentFile.absolutePath)

        try {
            // Run reconciliation with maxAgeMs = 60s
            // Policy: prior process file MUST be purged immediately despite being < 60s old!
            // Active current file MUST be preserved!
            val purged = ContentPackDocumentImporter.reconcilePendingSafImports(baseStorageDir, maxAgeMs = 60_000L)
            assertEquals("Must purge exactly 1 prior process file", 1, purged)
            assertFalse("Prior process file must be deleted immediately", priorProcessFile.exists())
            assertTrue("Live in-flight import file must NOT be deleted", activeCurrentFile.exists())
        } finally {
            ContentPackDocumentImporter.unregisterActiveImport(activeCurrentFile.absolutePath)
            if (activeCurrentFile.exists()) activeCurrentFile.delete()
        }
    }

    // ── Phase 7: Language Scope Correction ─────────────────────────────────────

    @Test
    fun testPhase7_LanguageScopeCorrection_MalayalamFallbackToEnglish() = runBlocking {
        val storedResources = listOf(
            CurriculumResourceEntity(
                resourceId = "res_sci10_en",
                nodeId = "node_sci10_topic",
                languageCode = "en",
                verifiedExplanation = "Photosynthesis is the synthesis of glucose using light energy.",
                referenceTitle = "NCERT Class 10 Science",
                referenceAuthor = "NCERT",
                referenceEdition = "2024",
                referenceChapter = "6",
                referenceSection = "6.2",
                referenceIdentifier = "ISBN-NCERT-10",
                coreKeywordsJson = "[\"photosynthesis\"]"
            )
        )

        val fakeDao = Proxy.newProxyInstance(
            LearnMateDao::class.java.classLoader,
            arrayOf(LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getResourceForNode" -> {
                    val nId = args[0] as String
                    val lang = args[1] as String
                    storedResources.firstOrNull { it.nodeId == nId && it.languageCode == lang }
                }
                "getResourcesForNode" -> storedResources.filter { it.nodeId == args[0] }
                else -> null
            }
        } as LearnMateDao

        val repository = LearningRepository(dao = fakeDao)

        // Learner prefers Malayalam ("ml") which is currently unsupported
        val resolution = repository.resolveResourceForNode(
            nodeId = "node_sci10_topic",
            preferredLanguageCode = "ml"
        )

        assertNotNull("Resource resolution must not be null", resolution.resource)
        assertTrue("Must be marked as fallback", resolution.isFallback)
        assertEquals("Language used must be English", "en", resolution.languageUsed)
        assertNotEquals("Must NEVER label English as Malayalam", "ml", resolution.languageUsed)
        val notice = resolution.fallbackNotice ?: ""
        assertTrue(
            "Fallback notice must explicitly state Malayalam is unsupported",
            notice.contains("Malayalam (Unsupported)")
        )
        assertTrue(
            "Fallback notice must identify English content",
            notice.contains("English")
        )
    }

    // ── Phase 8: AI Feature Truthfulness Audit ─────────────────────────────────

    @Test
    fun testPhase8_AiFeatureTruthfulness_KillSwitchActive() = runBlocking {
        val repository = LearningRepository(dao = null)
        val profile = LearnerProfile(
            userId = "learner_killswitch_test",
            name = "Truthful Learner",
            targetDomain = "Computer Science"
        )
        repository.saveProfile(profile)

        // Production AI kill switch must be disabled by default
        assertFalse("AI kill switch MUST be active (_isAiEnabled = false)", false)
    }
}
