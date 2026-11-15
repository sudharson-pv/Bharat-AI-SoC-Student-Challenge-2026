package com.learnmate.ai

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.localization.AppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UniversalPlatformFoundationTest {

    // ── 1. Universal Learner Profile & Orthogonal Dimensions ───────────────────

    @Test
    fun testOrthogonalLearnerDimensions() {
        // Enforce: Education level and Learner category are distinct orthogonal enums
        assertNotEquals(
            "EducationLevel and LearnerCategory must not be the same class",
            EducationLevel::class.java,
            LearnerCategory::class.java
        )

        // Verify EducationLevel values
        assertEquals(EducationLevel.PRIMARY, EducationLevel.fromString("PRIMARY"))
        assertEquals(EducationLevel.SECONDARY, EducationLevel.fromString("SECONDARY"))
        assertEquals(EducationLevel.HIGHER_SECONDARY, EducationLevel.fromString("HIGHER_SECONDARY"))
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("DIPLOMA"))
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("POLYTECHNIC"))
        assertEquals(EducationLevel.DIPLOMA, EducationLevel.fromString("VOCATIONAL_DIPLOMA"))
        assertEquals(EducationLevel.UNDERGRADUATE, EducationLevel.fromString("UNDERGRADUATE"))
        assertEquals(EducationLevel.POSTGRADUATE, EducationLevel.fromString("POSTGRADUATE"))
        assertEquals(EducationLevel.DOCTORAL, EducationLevel.fromString("DOCTORAL"))
        assertEquals(EducationLevel.CONTINUING_EDUCATION, EducationLevel.fromString("CONTINUING_EDUCATION"))
        assertNotEquals("DIPLOMA must be distinct from UNDERGRADUATE", EducationLevel.DIPLOMA, EducationLevel.UNDERGRADUATE)

        // Verify LearnerCategory values
        assertEquals(LearnerCategory.SCHOOL_STUDENT, LearnerCategory.fromString("SCHOOL_STUDENT"))
        assertEquals(LearnerCategory.DIPLOMA_STUDENT, LearnerCategory.fromString("DIPLOMA_STUDENT"))
        assertEquals(LearnerCategory.DIPLOMA_STUDENT, LearnerCategory.fromString("POLYTECHNIC"))
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL_TRAINEE"))
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL"))
        assertEquals(LearnerCategory.VOCATIONAL_TRAINEE, LearnerCategory.fromString("VOCATIONAL_TRADES"))
        assertEquals(LearnerCategory.ENGINEERING_STUDENT, LearnerCategory.fromString("ENGINEERING_STUDENT"))
        assertEquals(LearnerCategory.ENGINEERING_STUDENT, LearnerCategory.fromString("COLLEGE_STUDENT"))
        assertEquals(LearnerCategory.ARTS_SCIENCE_STUDENT, LearnerCategory.fromString("ARTS_SCIENCE_STUDENT"))
        assertEquals(LearnerCategory.MANAGEMENT_MBA_STUDENT, LearnerCategory.fromString("MANAGEMENT_MBA_STUDENT"))
        assertEquals(LearnerCategory.COMPETITIVE_EXAM_ASPIRANT, LearnerCategory.fromString("COMPETITIVE_EXAM_ASPIRANT"))
        assertEquals(LearnerCategory.RESEARCH_SCHOLAR, LearnerCategory.fromString("RESEARCH_SCHOLAR"))
        assertEquals(LearnerCategory.LIFELONG_LEARNER, LearnerCategory.fromString("LIFELONG_LEARNER"))

        // Verify LearningMode values
        assertEquals(LearningMode.ACADEMIC, LearningMode.fromString("ACADEMIC"))
        assertEquals(LearningMode.COMPETITIVE_EXAM, LearningMode.fromString("COMPETITIVE_EXAM"))
        assertEquals(LearningMode.RESEARCH, LearningMode.fromString("RESEARCH"))
        assertEquals(LearningMode.PROFESSIONAL_SKILLS, LearningMode.fromString("PROFESSIONAL_SKILLS"))
    }

    @Test
    fun testUniversalLearnerProfileCreation() {
        val profile = LearnerProfile(
            userId = "learner_upsc_01",
            name = "Ananya Sharma",
            targetDomain = "Civil Services & Public Administration",
            subdomain = "Indian Polity & Governance",
            goal = "UPSC CSE Rank < 100",
            targetRole = "IAS Officer",
            educationLevel = EducationLevel.POSTGRADUATE,
            learnerCategory = LearnerCategory.COMPETITIVE_EXAM_ASPIRANT,
            preferredLanguageCode = "hi",
            dailyStudyMinutes = 90,
            isMinor = false,
            guardianConsentGiven = true
        )

        assertEquals("learner_upsc_01", profile.userId)
        assertEquals(EducationLevel.POSTGRADUATE, profile.educationLevel)
        assertEquals(LearnerCategory.COMPETITIVE_EXAM_ASPIRANT, profile.learnerCategory)
        assertEquals("hi", profile.preferredLanguageCode)
        assertFalse(profile.isMinor)
        assertTrue(profile.guardianConsentGiven)
    }

    // ── 2. Curriculum Tree & Prerequisite Engine ────────────────────────────────

    @Test
    fun testCurriculumTreeContainmentAndBreadcrumb() {
        val nodes = CurriculumEngine.getUniversalBaselineNodes()

        // 1. Root nodes check: contains School, MBA, UPSC, Research, and Engineering
        val roots = CurriculumEngine.findRootNodes(nodes)
        assertTrue("Must have multiple discipline roots", roots.size >= 8)
        assertTrue(roots.any { it.nodeId == "node_root_school" })
        assertTrue(roots.any { it.nodeId == "node_root_mba" })
        assertTrue(roots.any { it.nodeId == "node_root_upsc" })
        assertTrue(roots.any { it.nodeId == "node_root_research" })
        assertTrue(roots.any { it.nodeId == "node_root_vlsi" })

        // 2. Children of UPSC discipline
        val upscChildren = CurriculumEngine.findChildren(nodes, "node_root_upsc")
        assertEquals(1, upscChildren.size)
        assertEquals("node_upsc_polity", upscChildren[0].nodeId)

        // 3. Topics under UPSC Polity
        val polityTopics = CurriculumEngine.findChildren(nodes, "node_upsc_polity")
        assertEquals(2, polityTopics.size)

        // 4. Breadcrumb Path resolution
        val path = CurriculumEngine.getBreadcrumbPath(nodes, "node_upsc_fund_rights")
        assertEquals(
            listOf(
                "Civil Services & Public Administration",
                "Indian Polity & Constitution",
                "Fundamental Rights (Articles 12-35)"
            ),
            path
        )
    }

    @Test
    fun testCurriculumContainmentCycleDetection() {
        // Normal acyclic tree
        val normalNodes = CurriculumEngine.getUniversalBaselineNodes()
        val resultAcyclic = CurriculumEngine.detectContainmentCycle(normalNodes)
        assertFalse("Baseline nodes must have no containment cycles", resultAcyclic.hasCycle)

        // Artificial cyclic tree: Node A -> Node B -> Node A
        val cyclicNodes = listOf(
            CurriculumNodeEntity("node_A", "node_B", "pack_1", "MODULE", "A", "Title A"),
            CurriculumNodeEntity("node_B", "node_A", "pack_1", "MODULE", "B", "Title B")
        )
        val resultCyclic = CurriculumEngine.detectContainmentCycle(cyclicNodes)
        assertTrue("Must detect containment cycle", resultCyclic.hasCycle)
        assertTrue("Cycle path must contain node_A and node_B", resultCyclic.cyclePath.contains("node_A") && resultCyclic.cyclePath.contains("node_B"))
    }

    @Test
    fun testPrerequisiteTransitiveResolutionAndValidation() {
        val prereqs = listOf(
            NodePrerequisiteEntity("p1", "topic_cmos_inverter", "topic_pn_junction", "MANDATORY"),
            NodePrerequisiteEntity("p2", "topic_sta", "topic_cmos_inverter", "MANDATORY"),
            NodePrerequisiteEntity("p3", "topic_sta", "topic_clock_tree", "RECOMMENDED")
        )

        // Transitive prerequisites for topic_sta
        val transitive = CurriculumEngine.getTransitivePrerequisites("topic_sta", prereqs)
        assertEquals(setOf("topic_cmos_inverter", "topic_pn_junction", "topic_clock_tree"), transitive)

        // Validation 1: Learner completed nothing
        val val1 = CurriculumEngine.validatePrerequisites("topic_sta", emptySet(), prereqs)
        assertFalse("topic_sta should be locked without prerequisites", val1.isUnlocked)
        assertEquals(listOf("topic_cmos_inverter"), val1.missingMandatoryNodeIds)
        assertEquals(listOf("topic_clock_tree"), val1.missingRecommendedNodeIds)

        // Validation 2: Learner completed mandatory prerequisite only
        val val2 = CurriculumEngine.validatePrerequisites("topic_sta", setOf("topic_cmos_inverter"), prereqs)
        assertTrue("topic_sta should unlock when mandatory prereq is satisfied", val2.isUnlocked)
        assertTrue("No missing mandatory prereqs", val2.missingMandatoryNodeIds.isEmpty())
        assertEquals(listOf("topic_clock_tree"), val2.missingRecommendedNodeIds)
    }

    @Test
    fun testPrerequisiteCycleDetection() {
        val nodes = listOf(
            CurriculumNodeEntity("n1", null, "p1", "TOPIC", "1", "T1"),
            CurriculumNodeEntity("n2", null, "p1", "TOPIC", "2", "T2"),
            CurriculumNodeEntity("n3", null, "p1", "TOPIC", "3", "T3")
        )

        // Acyclic graph: n1 requires n2, n2 requires n3
        val acyclicEdges = listOf(
            NodePrerequisiteEntity("e1", "n1", "n2", "MANDATORY"),
            NodePrerequisiteEntity("e2", "n2", "n3", "MANDATORY")
        )
        val resAcyclic = CurriculumEngine.detectPrerequisiteCycle(nodes, acyclicEdges)
        assertFalse(resAcyclic.hasCycle)

        // Cyclic graph: n1 requires n2, n2 requires n3, n3 requires n1!
        val cyclicEdges = listOf(
            NodePrerequisiteEntity("e1", "n1", "n2", "MANDATORY"),
            NodePrerequisiteEntity("e2", "n2", "n3", "MANDATORY"),
            NodePrerequisiteEntity("e3", "n3", "n1", "MANDATORY")
        )
        val resCyclic = CurriculumEngine.detectPrerequisiteCycle(nodes, cyclicEdges)
        assertTrue("Must detect prerequisite cycle", resCyclic.hasCycle)
    }

    // ── 3. Multi-Track Storage & Ownership Isolation ───────────────────────────

    @Test
    fun testMultiTrackRepositoryOperations() = runBlocking {
        val repo = LearningRepository()
        val userId = "test_multitrack_user"

        // 1. Create Profile
        val profile = LearnerProfile(
            userId = userId,
            name = "Kavitha Raman",
            targetDomain = "Computer Science and IT",
            dailyStudyMinutes = 45
        )
        repo.saveProfile(profile)

        // 2. Profile creation synthesizes default track
        val initialTracks = repo.getTracksForUser(userId)
        assertEquals(1, initialTracks.size)
        assertTrue(initialTracks[0].isTrackActive)

        // 3. Add second track for MBA studies
        val mbaTrack = repo.createTrack(
            userId = userId,
            title = "Executive MBA Prep",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "node_root_mba",
            dailyCommitmentMinutes = 60,
            setAsActive = true
        )
        assertNotNull(mbaTrack)
        assertTrue(mbaTrack.isTrackActive)

        // 4. Verify user has 2 tracks now
        val updatedTracks = repo.getTracksForUser(userId)
        assertEquals(2, updatedTracks.size)
        val activeTrack = repo.getActiveTrackForUser(userId)
        assertEquals("Executive MBA Prep", activeTrack?.title)

        // 5. Add third track for UPSC exam prep
        val upscTrack = repo.createTrack(
            userId = userId,
            title = "Civil Services 2027",
            learningMode = LearningMode.COMPETITIVE_EXAM,
            targetCurriculumNodeId = "node_root_upsc",
            targetExamName = "UPSC Prelims",
            dailyCommitmentMinutes = 90,
            setAsActive = true
        )
        assertEquals(3, repo.getTracksForUser(userId).size)
        assertEquals("Civil Services 2027", repo.getActiveTrackForUser(userId)?.title)

        // 6. Switch back to MBA track
        repo.switchActiveTrack(userId, mbaTrack.trackId)
        val switchedActive = repo.getActiveTrackForUser(userId)
        assertEquals(mbaTrack.trackId, switchedActive?.trackId)
        assertEquals("Executive MBA Prep", switchedActive?.title)
    }

    @Test
    fun testSafeUnresolvedTrackStateForUnknownDomains() = runBlocking {
        val repo = LearningRepository()
        val userId = "unresolved_user_01"

        // Learner inputs a domain not yet installed as a content pack (e.g. "Marine Biology")
        val newTrack = repo.createTrack(
            userId = userId,
            title = "Marine Biology Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = null // Safe Unresolved-Track State
        )

        assertNull("Target curriculum node must be null for uninstalled content pack", newTrack.targetCurriculumNodeId)
        assertEquals("Marine Biology Track", newTrack.title)
        assertTrue("User data is completely preserved without crashing or forcing engineering fallback", newTrack.isTrackActive)
    }

    // ── 4. Network & Privacy Permission Audit (Gate 6.16.3) ───────────────────

    @Test
    fun testNoNetworkPermissionAudit() {
        val manifestFile = File("F:/rea/zzzz_lama_proj/app/src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile)

        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val permissions = mutableListOf<String>()
        for (i in 0 until permissionNodes.length) {
            val node = permissionNodes.item(i)
            val name = node.attributes.getNamedItem("android:name")?.nodeValue ?: ""
            permissions.add(name)
            // Zero dangerous / tracking / telemetry permissions allowed
            assertFalse(
                "Telemetry, tracking, or invasive permissions are strictly forbidden: $name",
                name.contains("LOCATION", ignoreCase = true) ||
                    name.contains("CONTACTS", ignoreCase = true) ||
                    name.contains("PHONE_STATE", ignoreCase = true) ||
                    name.contains("RECORD_AUDIO", ignoreCase = true) ||
                    name.contains("CAMERA", ignoreCase = true)
            )
        }
        // Gate 6.16.3: Network permissions allowed strictly for model distribution
        assertTrue("Must include INTERNET for model download", permissions.contains("android.permission.INTERNET"))
        assertTrue("Must include ACCESS_NETWORK_STATE for download engine", permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    // ── 5. Four-Language Localization Parity ────────────────────────────────────

    @Test
    fun testFourLanguageLocalizationParity() {
        val resDir = File("F:/rea/zzzz_lama_proj/app/src/main/res")
        val langs = listOf("values", "values-ta", "values-hi", "values-kn")

        val keySets = langs.associateWith { langDir ->
            val stringsFile = File(resDir, "$langDir/strings.xml")
            assertTrue("strings.xml must exist for $langDir", stringsFile.exists())
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")
            val keys = mutableSetOf<String>()
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val key = node.attributes.getNamedItem("name")?.nodeValue
                if (key != null) keys.add(key)
            }
            keys
        }

        val requiredGate6Keys = listOf(
            "track_dashboard_title",
            "track_dashboard_subtitle",
            "track_active_badge",
            "track_btn_switch",
            "track_btn_new",
            "track_btn_browse_curriculum",
            "track_mode_academic",
            "track_mode_competitive",
            "track_mode_research",
            "track_mode_skills",
            "track_commitment_format",
            "track_create_title",
            "track_create_label_title",
            "track_create_label_category",
            "track_create_label_mode",
            "track_create_label_discipline",
            "track_create_label_exam",
            "track_create_btn_confirm",
            "curriculum_browser_title",
            "curriculum_browser_subtitle",
            "curriculum_tag_discipline",
            "curriculum_tag_course",
            "curriculum_tag_topic",
            "curriculum_btn_create_track",
            "profile_title",
            "label_learner_category",
            "label_education_level_title",
            "label_ui_language",
            "label_content_language",
            "label_minor_consent",
            "btn_save_profile"
        )

        for (lang in langs) {
            val keys = keySets[lang] ?: emptySet()
            for (reqKey in requiredGate6Keys) {
                assertTrue("Language $lang must define string key '$reqKey'", keys.contains(reqKey))
            }
        }
    }

    // ── 6. AI Kill Switch & Verified Textbook Fallback Safety ──────────────────

    @Test
    fun testTwoIndependentUsersWithSeparateTracks() = runBlocking {
        val repo = LearningRepository()
        val userA = "user_alpha"
        val userB = "user_beta"

        // Setup User A
        repo.saveProfile(LearnerProfile(userId = userA, name = "Alpha User", targetDomain = "Computer Science and IT"))
        val trackA1 = repo.getTracksForUser(userA)[0]
        val trackA2 = repo.createTrack(userA, "Alpha Second Track", LearningMode.RESEARCH, "node_root_research", setAsActive = false)

        // Setup User B
        repo.saveProfile(LearnerProfile(userId = userB, name = "Beta User", targetDomain = "Management and MBA"))
        val trackB1 = repo.getTracksForUser(userB)[0]

        // Verify initial state
        assertEquals(trackA1.trackId, repo.getActiveTrackForUser(userA)?.trackId)
        assertEquals(trackB1.trackId, repo.getActiveTrackForUser(userB)?.trackId)

        // Switch active track for User A
        repo.switchActiveTrack(userA, trackA2.trackId)

        // Verify User A switched
        assertEquals(trackA2.trackId, repo.getActiveTrackForUser(userA)?.trackId)
        val allUserATracks = repo.getTracksForUser(userA)
        val trackA1Updated = allUserATracks.find { it.trackId == trackA1.trackId }
        assertFalse(trackA1Updated?.isTrackActive ?: true)

        // Verify User B was NOT affected at all
        assertEquals(trackB1.trackId, repo.getActiveTrackForUser(userB)?.trackId)
        val allUserBTracks = repo.getTracksForUser(userB)
        val trackB1Updated = allUserBTracks.find { it.trackId == trackB1.trackId }
        assertTrue(trackB1Updated?.isTrackActive ?: false)
    }

    @Test
    fun testDuplicatePrerequisitesHandling() {
        // Two duplicate edges between the same nodes
        val dupePrereqs = listOf(
            NodePrerequisiteEntity("p_dup_1", "topic_sta", "topic_cmos_inverter", "MANDATORY"),
            NodePrerequisiteEntity("p_dup_2", "topic_sta", "topic_cmos_inverter", "MANDATORY"),
            NodePrerequisiteEntity("p_normal", "topic_cmos_inverter", "topic_pn_junction", "MANDATORY")
        )

        // Transitive prerequisites must deduplicate cleanly
        val transitive = CurriculumEngine.getTransitivePrerequisites("topic_sta", dupePrereqs)
        assertEquals(setOf("topic_cmos_inverter", "topic_pn_junction"), transitive)

        // Validation must not double-count missing nodes
        val validation = CurriculumEngine.validatePrerequisites("topic_sta", emptySet(), dupePrereqs)
        assertEquals(listOf("topic_cmos_inverter"), validation.missingMandatoryNodeIds)
    }

    @Test
    fun testInvalidCurriculumNodeReferences() {
        val brokenNodes = listOf(
            CurriculumNodeEntity("node_orphan", "node_ghost_parent", "pack_1", "TOPIC", "ORPHAN", "Orphan Topic")
        )

        // Resolving breadcrumb path for a node pointing to a non-existent parent must not throw NPE or loop
        val path = CurriculumEngine.getBreadcrumbPath(brokenNodes, "node_orphan")
        assertEquals(listOf("Orphan Topic"), path)

        // Finding roots: orphan whose parent is not in node list must be safely treated or excluded without crashing
        val roots = CurriculumEngine.findRootNodes(brokenNodes)
        // brokenNodes parentNodeId is not null, so roots is empty
        assertEquals(0, roots.size)
    }

    @Test
    fun testEmptyCurriculumPackAndUnresolvedTrackPreservation() = runBlocking {
        val repo = LearningRepository()
        val userId = "user_marine_bio"

        // Create profile for domain with no installed content pack
        val profile = LearnerProfile(
            userId = userId,
            name = "Siddharth",
            targetDomain = "Marine Biology & Oceanography",
            goal = "Coral reef ecology study",
            targetRole = "Marine Ecologist",
            dailyStudyMinutes = 60
        )
        repo.saveProfile(profile)

        val tracks = repo.getTracksForUser(userId)
        assertEquals(1, tracks.size)
        val defaultTrack = tracks[0]

        // Enforce: Must NOT fall back to Engineering or VLSI
        assertNull("Uninstalled domain must have null targetCurriculumNodeId", defaultTrack.targetCurriculumNodeId)
        assertFalse("Must not fall back to VLSI", defaultTrack.title.contains("VLSI", ignoreCase = true))
        assertFalse("Must not fall back to Electronics", defaultTrack.title.contains("Electronics", ignoreCase = true))
        assertTrue("Must preserve user domain in title", defaultTrack.title.contains("Marine Biology"))
        assertEquals(60, defaultTrack.dailyCommitmentMinutes)
    }

    @Test
    fun testAiKillSwitchActiveByDefault() = runBlocking {
        val repo = LearningRepository()
        val assetManager = ModelAssetManager(File("F:/rea/zzzz_lama_proj/app/build"))
        val engine = ExecuTorchLlamaEngine(assetManager)
        val aiTutorRepo = AiTutorRepository(engine, assetManager, repo)

        // Verify kill switch is ACTIVE by default
        assertFalse("AI generation must be disabled by default (kill switch active)", aiTutorRepo.isAiEnabled.value)

        // When AI is disabled, askTutor must return verified curriculum guidance
        val res = aiTutorRepo.askTutor(
            sessionId = "test_session_01",
            userId = "test_user_01",
            userQuestion = "What is a PN junction barrier potential?",
            currentTopic = "Electronics and VLSI",
            onTokenDelta = { _: String, _: String -> }
        )

        assertTrue(res.isSuccess)
        val message = res.getOrThrow()
        assertFalse("Response must NOT be flagged as AI generated", message.isAiGenerated)
        assertTrue("Response must contain verified curriculum tag", message.text.contains("Verified Curriculum Reference — AI Guidance Disabled"))
        assertTrue("Response must contain accredited disclaimer", message.text.contains(AiTutorRepository.DISCLAIMER))
    }
}

