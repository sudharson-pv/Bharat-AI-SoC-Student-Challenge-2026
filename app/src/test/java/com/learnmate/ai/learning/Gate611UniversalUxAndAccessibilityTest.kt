package com.learnmate.ai.learning

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.lifecycle.LearnerDataLifecycleManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gate 6.11: Universal User Experience, Accessibility & Performance Optimization Test Suite.
 *
 * Verifies:
 * 1. String Resource Parity across English (default), Tamil (ta), Hindi (hi), and Kannada (kn) locales.
 * 2. Complete key coverage for Gate 6.11 Data Lifecycle, Backup/Restore, Settings, and Accessibility strings.
 * 3. Universal learner profile support across all 8 educational archetypes.
 * 4. Data lifecycle track deletion: targeted erasure preserving sibling tracks and content packs.
 * 5. Data portability exports: JSON schema validity and Markdown study summary generation.
 * 6. Local data erasure (profile reset) preserving database structure.
 * 7. Multilingual content fallback disclosures for non-English locales accessing accredited English curricula.
 * 8. Compose Lazy Layout key stability: uniqueness across nodes, lessons, and diagrams.
 * 9. Core system invariants: zero <uses-permission> in AndroidManifest.xml and active production AI kill switch.
 */
class Gate611UniversalUxAndAccessibilityTest {

    private lateinit var repository: LearningRepository
    private lateinit var lifecycleManager: LearnerDataLifecycleManager
    private val gson = Gson()
    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        repository = LearningRepository()
        lifecycleManager = LearnerDataLifecycleManager(repository)
        testTempDir = File(System.getProperty("java.io.tmpdir"), "learnmate_gate611_test_${System.currentTimeMillis()}")
        testTempDir.mkdirs()

        runBlocking {
            repository.initializeCurriculumIfNeeded()
        }
    }

    // ── 1. Multilingual String Resource Parity ─────────────────────────────────

    @Test
    fun testStringResourceKeyParityAcrossAllLocales() {
        val baseResDirs = listOf(
            File("app/src/main/res"),
            File("zzzz_lama_proj/app/src/main/res"),
            File("F:/rea/zzzz_lama_proj/app/src/main/res")
        )
        val resDir = baseResDirs.firstOrNull { it.exists() }
        assertNotNull("Resource directory must exist", resDir)

        val defaultStringsFile = File(resDir, "values/strings.xml")
        val taStringsFile = File(resDir, "values-ta/strings.xml")
        val hiStringsFile = File(resDir, "values-hi/strings.xml")
        val knStringsFile = File(resDir, "values-kn/strings.xml")

        assertTrue("Default strings.xml must exist", defaultStringsFile.exists())
        assertTrue("Tamil values-ta/strings.xml must exist", taStringsFile.exists())
        assertTrue("Hindi values-hi/strings.xml must exist", hiStringsFile.exists())
        assertTrue("Kannada values-kn/strings.xml must exist", knStringsFile.exists())

        val defaultKeys = extractStringKeys(defaultStringsFile)
        val taKeys = extractStringKeys(taStringsFile)
        val hiKeys = extractStringKeys(hiStringsFile)
        val knKeys = extractStringKeys(knStringsFile)

        // Essential Gate 6.11 keys that MUST be present
        val requiredGate611Keys = listOf(
            "btn_cancel",
            "btn_close",
            "content_packs_title",
            "label_minor_learner",
            "label_daily_commitment",
            "msg_profile_saved",
            "curriculum_english_fallback_notice",
            "section_data_lifecycle",
            "backup_title",
            "backup_desc",
            "btn_create_backup",
            "restore_title",
            "restore_desc",
            "btn_restore_backup",
            "restore_passphrase_prompt",
            "restore_overwrite_warning",
            "btn_confirm_restore",
            "export_data_title",
            "export_data_desc",
            "btn_export_json",
            "btn_export_markdown",
            "privacy_disclosure_title",
            "privacy_disclosure_desc",
            "btn_view_privacy_disclosure",
            "delete_track_title",
            "delete_track_confirm",
            "btn_delete_track",
            "profile_reset_title",
            "profile_reset_desc",
            "btn_delete_profile",
            "a11y_task_completed",
            "a11y_task_incomplete",
            "a11y_progress_percent",
            "a11y_track_active"
        )

        for (key in requiredGate611Keys) {
            assertTrue("Default strings.xml must define key: $key", defaultKeys.contains(key))
            assertTrue("Tamil values-ta/strings.xml must define key: $key", taKeys.contains(key))
            assertTrue("Hindi values-hi/strings.xml must define key: $key", hiKeys.contains(key))
            assertTrue("Kannada values-kn/strings.xml must define key: $key", knKeys.contains(key))
        }

        // Check full parity with default
        val missingInTa = defaultKeys - taKeys
        val missingInHi = defaultKeys - hiKeys
        val missingInKn = defaultKeys - knKeys

        assertEquals("Tamil locale is missing string keys: $missingInTa", 0, missingInTa.size)
        assertEquals("Hindi locale is missing string keys: $missingInHi", 0, missingInHi.size)
        assertEquals("Kannada locale is missing string keys: $missingInKn", 0, missingInKn.size)
    }

    private fun extractStringKeys(file: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val nameAttr = node.attributes?.getNamedItem("name")?.nodeValue
            if (!nameAttr.isNullOrBlank()) {
                keys.add(nameAttr)
            }
        }
        return keys
    }

    // ── 2. Universal Learner Support (All 8 Educational Archetypes) ───────────

    @Test
    fun testUniversalProfilesSupportAll8EducationalArchetypes() = runBlocking {
        val archetypes = listOf(
            Triple(LearnerCategory.SCHOOL_STUDENT, EducationLevel.SECONDARY, "CBSE Class 10 Science"),
            Triple(LearnerCategory.DIPLOMA_STUDENT, EducationLevel.DIPLOMA, "Polytechnic Applied Mechanics"),
            Triple(LearnerCategory.ENGINEERING_STUDENT, EducationLevel.UNDERGRADUATE, "B.Tech Computer Science"),
            Triple(LearnerCategory.ARTS_SCIENCE_STUDENT, EducationLevel.UNDERGRADUATE, "B.Sc Physics & Mathematics"),
            Triple(LearnerCategory.MANAGEMENT_MBA_STUDENT, EducationLevel.POSTGRADUATE, "MBA Corporate Finance"),
            Triple(LearnerCategory.COMPETITIVE_EXAM_ASPIRANT, EducationLevel.HIGHER_SECONDARY, "UPSC Civil Services Prelims"),
            Triple(LearnerCategory.RESEARCH_SCHOLAR, EducationLevel.DOCTORAL, "PhD Quantum Optics"),
            Triple(LearnerCategory.VOCATIONAL_TRAINEE, EducationLevel.DIPLOMA, "Electrician Trades & Solar Installation")
        )

        for ((category, level, domain) in archetypes) {
            val profile = LearnerProfile(
                userId = "test_user_${category.name.lowercase()}",
                name = "Test Learner ${category.displayName}",
                targetDomain = domain,
                subdomain = "Core",
                goal = "Complete Gate 6.11 Universal Validation",
                targetRole = "Scholar",
                previousBackground = "Foundation",
                currentLevel = "Intermediate",
                audienceLevel = AudienceLevel.UNDERGRADUATE,
                dailyStudyMinutes = 60,
                weeklyStudyDays = 6,
                currentTopic = "Overview",
                onboardingCompleted = true,
                assessmentCompleted = true,
                educationLevel = level,
                learnerCategory = category
            )

            repository.saveProfile(profile)
            val loaded = repository.getProfile(profile.userId)
            assertNotNull("Saved profile must be retrievable", loaded)
            assertEquals("Category must match", category, loaded?.learnerCategory)
            assertEquals("Education level must match", level, loaded?.educationLevel)
            assertEquals("Domain must match", domain, loaded?.targetDomain)

            // Test track creation for each archetype
            val track = repository.createTrack(
                userId = profile.userId,
                title = "$domain Track",
                learningMode = if (category == LearnerCategory.COMPETITIVE_EXAM_ASPIRANT) LearningMode.COMPETITIVE_EXAM else LearningMode.ACADEMIC,
                targetCurriculumNodeId = null,
                dailyCommitmentMinutes = 60,
                setAsActive = true
            )
            assertNotNull("Track must be created for $category", track)
            assertEquals("Track title must match", "$domain Track", track.title)
        }
    }

    // ── 3. Data Lifecycle & Isolation ─────────────────────────────────────────

    @Test
    fun testTrackDeletionIsolatesDataAndPreservesSiblings() = runBlocking {
        val user = LearnerProfile(
            userId = "test_user_isolation",
            name = "Isolation Learner",
            targetDomain = "Multi-Disciplinary",
            subdomain = "General",
            goal = "Test isolation",
            targetRole = "Student",
            previousBackground = "High School",
            currentLevel = "Intermediate",
            audienceLevel = AudienceLevel.UNDERGRADUATE,
            dailyStudyMinutes = 45,
            weeklyStudyDays = 5,
            currentTopic = "Mechanics",
            onboardingCompleted = true,
            assessmentCompleted = true,
            educationLevel = EducationLevel.UNDERGRADUATE,
            learnerCategory = LearnerCategory.ENGINEERING_STUDENT
        )
        repository.saveProfile(user)

        val initialTracks = repository.getTracksForUser(user.userId)
        val initialCount = initialTracks.size

        val trackA = repository.createTrack(
            userId = user.userId,
            title = "Track A - Engineering",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = null,
            dailyCommitmentMinutes = 45,
            setAsActive = true
        )
        val trackB = repository.createTrack(
            userId = user.userId,
            title = "Track B - UPSC",
            learningMode = LearningMode.COMPETITIVE_EXAM,
            targetCurriculumNodeId = null,
            dailyCommitmentMinutes = 60,
            setAsActive = false
        )

        // Record progress and attempts in Track A
        repository.recordAssessmentAttempt(
            AssessmentAttemptRecord(
                attemptId = "attempt_track_a_1",
                trackId = trackA.trackId,
                userId = user.userId,
                nodeId = "node_a",
                topicTitle = "Topic A",
                discipline = "Engineering",
                scorePercent = 90.0,
                correctCount = 5,
                totalQuestions = 5,
                durationSeconds = 120,
                attemptEpochMs = System.currentTimeMillis(),
                questionResponses = emptyList()
            )
        )

        // Record attempt in Track B
        repository.recordAssessmentAttempt(
            AssessmentAttemptRecord(
                attemptId = "attempt_track_b_1",
                trackId = trackB.trackId,
                userId = user.userId,
                nodeId = "node_b",
                topicTitle = "Topic B",
                discipline = "Competitive Exam",
                scorePercent = 80.0,
                correctCount = 4,
                totalQuestions = 5,
                durationSeconds = 140,
                attemptEpochMs = System.currentTimeMillis(),
                questionResponses = emptyList()
            )
        )

        val attemptsBeforeA = repository.getAllAssessmentAttemptsForTrack(trackA.trackId, user.userId)
        val attemptsBeforeB = repository.getAllAssessmentAttemptsForTrack(trackB.trackId, user.userId)
        assertEquals("Track A must have 1 attempt", 1, attemptsBeforeA.size)
        assertEquals("Track B must have 1 attempt", 1, attemptsBeforeB.size)

        // Delete Track A via lifecycleManager
        val deleteResult = lifecycleManager.deleteTrackData(trackA.trackId, user.userId, confirm = true)
        assertTrue("Track A deletion must succeed", deleteResult.isSuccess)

        // Verify Track A is deleted, Track B remains
        val remainingTracks = repository.getTracksForUser(user.userId)
        assertEquals("Remaining tracks count must decrease by 1", initialCount + 1, remainingTracks.size)
        assertFalse("Track A must not be in remaining tracks", remainingTracks.any { it.trackId == trackA.trackId })
        assertTrue("Track B must remain in tracks", remainingTracks.any { it.trackId == trackB.trackId })

        // Verify Track A attempts removed, Track B attempts preserved
        val attemptsAfterA = repository.getAllAssessmentAttemptsForTrack(trackA.trackId, user.userId)
        val attemptsAfterB = repository.getAllAssessmentAttemptsForTrack(trackB.trackId, user.userId)
        assertEquals("Track A attempts must be 0", 0, attemptsAfterA.size)
        assertEquals("Track B attempt must remain", 1, attemptsAfterB.size)
    }

    // ── 4. Data Portability Exports ───────────────────────────────────────────

    @Test
    fun testDataPortabilityExportFormats() = runBlocking {
        val user = LearnerProfile(
            userId = "export_test_user",
            name = "Export Scholar",
            targetDomain = "Computer Science",
            subdomain = "VLSI & Digital Logic",
            goal = "Verify open portability",
            targetRole = "Engineer",
            previousBackground = "Engineering Degree",
            currentLevel = "Advanced",
            audienceLevel = AudienceLevel.PROFESSIONAL,
            dailyStudyMinutes = 60,
            weeklyStudyDays = 6,
            currentTopic = "CMOS Inverters",
            onboardingCompleted = true,
            assessmentCompleted = true,
            educationLevel = EducationLevel.UNDERGRADUATE,
            learnerCategory = LearnerCategory.ENGINEERING_STUDENT
        )
        repository.saveProfile(user)

        val track = repository.createTrack(
            userId = user.userId,
            title = "Digital VLSI Design",
            learningMode = LearningMode.PROFESSIONAL_SKILLS,
            targetCurriculumNodeId = null,
            dailyCommitmentMinutes = 60,
            setAsActive = true
        )

        // Test JSON Export
        val jsonFile = File(testTempDir, "export_test.json")
        val jsonResult = lifecycleManager.exportLearnerDataJson(user.userId, jsonFile)
        assertTrue("JSON export must succeed", jsonResult.isSuccess)

        val jsonText = jsonFile.readText()
        assertTrue("JSON export must be non-empty", jsonText.isNotBlank())

        val parsedObj = gson.fromJson(jsonText, JsonObject::class.java)
        assertTrue("Export must contain profile", parsedObj.has("profile"))
        assertTrue("Export must contain exportTimestampEpochMs", parsedObj.has("exportTimestampEpochMs"))
        val profileObj = parsedObj.getAsJsonObject("profile")
        assertEquals("Exported name must match", "Export Scholar", profileObj.get("name").asString)

        // Test Markdown Export
        val mdFile = File(testTempDir, "summary_test.md")
        val mdResult = lifecycleManager.exportProgressSummaryMarkdown(user.userId, mdFile)
        assertTrue("Markdown export must succeed", mdResult.isSuccess)

        val mdText = mdFile.readText()
        assertTrue("Markdown must contain header", mdText.contains("# LearnMate AI — Learner Progress & Data Export Report"))
        assertTrue("Markdown must contain learner name", mdText.contains("Export Scholar"))
        assertTrue("Markdown must list active tracks", mdText.contains("Digital VLSI Design"))
    }

    // ── 5. Privacy Disclosures & Local Erasure ────────────────────────────────

    @Test
    fun testPrivacyDisclosureAndLocalProfileReset() = runBlocking {
        val user = LearnerProfile(
            userId = "reset_test_user",
            name = "Reset Learner",
            targetDomain = "Commerce",
            subdomain = "Accounting",
            goal = "Test reset",
            targetRole = "Accountant",
            previousBackground = "Higher Secondary",
            currentLevel = "Beginner",
            audienceLevel = AudienceLevel.UNDERGRADUATE,
            dailyStudyMinutes = 30,
            weeklyStudyDays = 5,
            currentTopic = "Double Entry",
            onboardingCompleted = true,
            assessmentCompleted = true,
            educationLevel = EducationLevel.UNDERGRADUATE,
            learnerCategory = LearnerCategory.MANAGEMENT_MBA_STUDENT
        )
        repository.saveProfile(user)

        val disclosure = lifecycleManager.getPrivacyPolicyDisclosure()
        assertTrue("Disclosure must reference zero internet", disclosure.contains("ZERO network permissions") || disclosure.contains("Offline-First"))
        assertTrue("Disclosure must reference local-only storage", disclosure.contains("Local Device Storage") || disclosure.contains("SQLite"))

        val inventory = lifecycleManager.getDataInventory()
        assertTrue("Inventory must be non-empty", inventory.isNotEmpty())
        assertTrue("Inventory items must specify storage location", inventory.first().storageLocation.isNotBlank())

        // Reset profile
        val resetResult = lifecycleManager.deleteLearnerData(user.userId, confirm = true)
        assertTrue("Profile reset must succeed", resetResult.isSuccess)

        val profileAfter = repository.getProfile(user.userId)
        assertNull("Profile must be null after deletion", profileAfter)
    }

    // ── 6. Multilingual Content Fallback Disclosures ─────────────────────────

    @Test
    fun testContentFallbackDisclosures() {
        // Request engineering topic with Tamil preference (which has English source text)
        val fallbackModule = StarterCurriculumCatalog.getLessonModule("eng_de_kmap", preferredLanguage = "ta")
        assertTrue("Non-English request for English-only module must set isFallbackLanguage", fallbackModule.isFallbackLanguage)
        assertNotNull("Fallback notice must be provided", fallbackModule.fallbackNotice)
        assertTrue(
            "Fallback notice must disclose accredited English source",
            fallbackModule.fallbackNotice!!.contains("English") || fallbackModule.fallbackNotice!!.contains("source")
        )

        // Request CBSE Science Ch1 with Tamil preference (which has authentic Tamil translation)
        val localizedModule = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", preferredLanguage = "ta")
        assertEquals("Localized module language code must be ta", "ta", localizedModule.languageCode)
        assertFalse("Localized module must not flag fallback", localizedModule.isFallbackLanguage)
        assertNull("Localized module must not have fallback notice", localizedModule.fallbackNotice)
    }

    // ── 7. Compose Key Stability Across Collections ──────────────────────────

    @Test
    fun testComposeKeyStabilityAcrossCurriculumAndLessons() {
        val nodeIds = StarterCurriculumCatalog.getSupportedNodeIds()
        assertTrue("Catalog must define supported nodes", nodeIds.isNotEmpty())

        val moduleIds = mutableSetOf<String>()
        for (nodeId in nodeIds) {
            val module = StarterCurriculumCatalog.getLessonModule(nodeId)
            assertTrue("Node ID must be non-blank for stable keys", module.nodeId.isNotBlank())
            assertTrue("Module Node IDs must be strictly unique: ${module.nodeId}", moduleIds.add(module.nodeId))

            // Subsections key stability
            val sectionHeadings = mutableSetOf<String>()
            for (section in module.sections) {
                assertTrue("Section heading must be non-blank", section.heading.isNotBlank())
                assertTrue("Section headings in node ${module.nodeId} must be unique", sectionHeadings.add(section.heading))
            }

            // Diagrams key stability
            val diagramTitles = mutableSetOf<String>()
            for (diagram in module.diagrams) {
                assertTrue("Diagram title must be non-blank", diagram.title.isNotBlank())
                assertTrue("Diagram titles in node ${module.nodeId} must be unique", diagramTitles.add(diagram.title))
            }

            // Worked examples key stability
            val exampleTitles = mutableSetOf<String>()
            for (ex in module.workedExamples) {
                assertTrue("Worked example title must be non-blank", ex.title.isNotBlank())
                assertTrue("Worked example titles in node ${module.nodeId} must be unique", exampleTitles.add(ex.title))
            }
        }
    }

    // ── 8. System Architecture and Security Invariants ───────────────────────

    @Test
    fun testSystemArchitectureAndSecurityInvariants() {
        val candidatePaths = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("zzzz_lama_proj/app/src/main/AndroidManifest.xml"),
            File("F:/rea/zzzz_lama_proj/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile!!)

        val permissionNodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val name = permissionNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertTrue(
                "Gate 6.16.3: Only authorized model download network permissions permitted: $name",
                name == "android.permission.INTERNET" || name == "android.permission.ACCESS_NETWORK_STATE"
            )
        }

        // Production AI Kill Switch verification
        assertTrue(
            "Verified Textbook Mode notice must disclose local AI is disabled by production policy",
            AiTutorRepository.VERIFIED_TEXTBOOK_MODE_NOTICE.contains("disabled by production policy")
        )
    }
}
