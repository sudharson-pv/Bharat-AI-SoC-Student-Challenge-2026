package com.learnmate.ai.domain

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.LearnMateAiCoreEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.repository.YouTubeDataRepository
import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.domain.focus.FocusAreaIntelligence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Cross-Domain Focus-Area Driven Personalization Regression Suite.
 *
 * Validates the core architectural invariant:
 * The primary driver for educational content generation is the learner's
 * Specific Focus Area, Specialization, and Target Outcome — NOT the broad Domain.
 *
 * Matrix of 6 Evaluation Profiles (A to F):
 * - Profile A: Electronics and VLSI | Analog VLSI
 * - Profile B: Electronics and VLSI | Digital VLSI
 * - Profile C: Computer Science     | Frontend Development
 * - Profile D: Computer Science     | Backend Development
 * - Profile E: Biotechnology        | Genetic Engineering
 * - Profile F: Food Technology      | Food Preservation
 */
class FocusAreaPersonalizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var aiCoreEngine: LearnMateAiCoreEngine
    private lateinit var assetManager: ModelAssetManager

    private class TestLlmBridge : ExecuTorchLlamaEngine.NativeLlmBridge {
        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = "TestModule"
        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int = 0
        override fun stop(moduleInstance: Any) {}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val appFilesDir = tempFolder.newFolder("focus_area_test_files")
        assetManager = ModelAssetManager(appFilesDir)
        val engine = ExecuTorchLlamaEngine(
            assetManager = assetManager,
            runtimeBridge = TestLlmBridge(),
            inferenceDispatcher = Dispatchers.Unconfined
        )
        aiCoreEngine = LearnMateAiCoreEngine(engine, assetManager)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 6 EVALUATION PROFILES (A to F) ────────────────────────────────────────

    val profileA = LearnerProfile(
        userId = "learner_a",
        name = "Learner A - Analog VLSI",
        targetDomain = "Electronics and VLSI",
        subdomain = "Analog VLSI",
        goal = "Design CMOS analog building blocks and operational amplifiers",
        educationLevel = EducationLevel.UNDERGRADUATE,
        currentLevel = "Intermediate"
    )

    val profileB = LearnerProfile(
        userId = "learner_b",
        name = "Learner B - Digital VLSI",
        targetDomain = "Electronics and VLSI",
        subdomain = "Digital VLSI",
        goal = "RTL design, static timing analysis and clock domain crossing",
        educationLevel = EducationLevel.UNDERGRADUATE,
        currentLevel = "Intermediate"
    )

    val profileC = LearnerProfile(
        userId = "learner_c",
        name = "Learner C - Frontend Dev",
        targetDomain = "Computer Science",
        subdomain = "Frontend Development",
        goal = "Build responsive web applications with modern frontend frameworks",
        educationLevel = EducationLevel.UNDERGRADUATE,
        currentLevel = "Beginner"
    )

    val profileD = LearnerProfile(
        userId = "learner_d",
        name = "Learner D - Backend Dev",
        targetDomain = "Computer Science",
        subdomain = "Backend Development",
        goal = "Build scalable REST APIs, microservices and relational database schemas",
        educationLevel = EducationLevel.UNDERGRADUATE,
        currentLevel = "Intermediate"
    )

    val profileE = LearnerProfile(
        userId = "learner_e",
        name = "Learner E - Genetic Engineering",
        targetDomain = "Biotechnology",
        subdomain = "Genetic Engineering",
        goal = "Master recombinant DNA technology, gene cloning, and CRISPR systems",
        educationLevel = EducationLevel.POSTGRADUATE,
        currentLevel = "Intermediate"
    )

    val profileF = LearnerProfile(
        userId = "learner_f",
        name = "Learner F - Food Preservation",
        targetDomain = "Food Technology",
        subdomain = "Food Preservation",
        goal = "Implement thermal processing, water activity control, and hurdle technology",
        educationLevel = EducationLevel.UNDERGRADUATE,
        currentLevel = "Intermediate"
    )

    // ── 1. EXACT QUESTION COUNTS & GROUNDING ──────────────────────────────────

    @Test
    fun testAllProfiles_ReturnExactly10GroundedQuestions() {
        val testProfiles = listOf(profileA, profileB, profileC, profileD, profileE, profileF)

        for (profile in testProfiles) {
            val questions = aiCoreEngine.generateTailoredDomainQuestions(profile)
            assertEquals("Profile ${profile.name} must return exactly 10 questions", 10, questions.size)

            for ((index, q) in questions.withIndex()) {
                assertFalse("Question $index ID must not be blank", q.id.isBlank())
                assertFalse("Question $index text must not be blank", q.questionText.isBlank())
                assertEquals("Question $index must have 4 options", 4, q.options.size)
                assertTrue("Question $index correct index must be 0..3", q.correctIndex in 0..3)
                assertFalse("Question $index explanation must not be blank", q.explanation.isBlank())
                assertFalse("Question $index topic must not be blank", q.topic.isBlank())

                // Subdomain should match the focus area, NOT be defaulted to School Science or blank
                assertFalse("Question $index domain must not be School Science for specialized profile",
                    q.domain.contains("School", ignoreCase = true))
            }
        }
    }

    // ── 2. ZERO OVERLAP IN SHARED DOMAINS ─────────────────────────────────────

    @Test
    fun testProfileA_and_ProfileB_ZeroQuestionOverlap_SameDomainDifferentFocus() {
        // Both share "Electronics and VLSI", but Profile A is Analog and Profile B is Digital
        val questionsA = aiCoreEngine.generateTailoredDomainQuestions(profileA)
        val questionsB = aiCoreEngine.generateTailoredDomainQuestions(profileB)

        assertEquals(10, questionsA.size)
        assertEquals(10, questionsB.size)

        val idsA = questionsA.map { it.id }.toSet()
        val idsB = questionsB.map { it.id }.toSet()
        val idIntersection = idsA.intersect(idsB)
        assertTrue("Analog and Digital VLSI must have 0% question ID overlap: found $idIntersection", idIntersection.isEmpty())

        val textsA = questionsA.map { it.questionText.lowercase() }.toSet()
        val textsB = questionsB.map { it.questionText.lowercase() }.toSet()
        val textIntersection = textsA.intersect(textsB)
        assertTrue("Analog and Digital VLSI must have 0% question text overlap", textIntersection.isEmpty())

        // Verify Analog VLSI contains analog-specific terms and no digital-specific terms
        val analogHasOpAmpOrMirror = questionsA.any {
            it.questionText.contains("amplifier", ignoreCase = true) ||
            it.questionText.contains("mirror", ignoreCase = true) ||
            it.questionText.contains("gm", ignoreCase = true) ||
            it.questionText.contains("miller", ignoreCase = true)
        }
        assertTrue("Profile A questions must test analog circuits", analogHasOpAmpOrMirror)

        val digitalHasSTAorClock = questionsB.any {
            it.questionText.contains("timing analysis", ignoreCase = true) ||
            it.questionText.contains("hold time", ignoreCase = true) ||
            it.questionText.contains("clock", ignoreCase = true) ||
            it.questionText.contains("verilog", ignoreCase = true)
        }
        assertTrue("Profile B questions must test digital circuits / timing", digitalHasSTAorClock)
    }

    @Test
    fun testProfileC_and_ProfileD_ZeroQuestionOverlap_SameDomainDifferentFocus() {
        // Both share "Computer Science", but Profile C is Frontend and Profile D is Backend
        val questionsC = aiCoreEngine.generateTailoredDomainQuestions(profileC)
        val questionsD = aiCoreEngine.generateTailoredDomainQuestions(profileD)

        assertEquals(10, questionsC.size)
        assertEquals(10, questionsD.size)

        val idsC = questionsC.map { it.id }.toSet()
        val idsD = questionsD.map { it.id }.toSet()
        val idIntersection = idsC.intersect(idsD)
        assertTrue("Frontend and Backend must have 0% question ID overlap: found $idIntersection", idIntersection.isEmpty())

        val frontendHasUI = questionsC.any {
            it.questionText.contains("DOM", ignoreCase = true) ||
            it.questionText.contains("CSS", ignoreCase = true) ||
            it.questionText.contains("component", ignoreCase = true) ||
            it.questionText.contains("render", ignoreCase = true)
        }
        assertTrue("Profile C questions must test frontend UI concepts", frontendHasUI)

        val backendHasAPIorDB = questionsD.any {
            it.questionText.contains("REST", ignoreCase = true) ||
            it.questionText.contains("ACID", ignoreCase = true) ||
            it.questionText.contains("index", ignoreCase = true) ||
            it.questionText.contains("database", ignoreCase = true)
        }
        assertTrue("Profile D questions must test backend API/database concepts", backendHasAPIorDB)
    }

    @Test
    fun testProfileE_and_ProfileF_DomainSpecificContent() {
        val questionsE = aiCoreEngine.generateTailoredDomainQuestions(profileE)
        val questionsF = aiCoreEngine.generateTailoredDomainQuestions(profileF)

        assertEquals(10, questionsE.size)
        assertEquals(10, questionsF.size)

        val biotechHasGene = questionsE.any {
            it.questionText.contains("CRISPR", ignoreCase = true) ||
            it.questionText.contains("DNA", ignoreCase = true) ||
            it.questionText.contains("PCR", ignoreCase = true) ||
            it.questionText.contains("cloning", ignoreCase = true)
        }
        assertTrue("Profile E questions must test molecular biology & genetic engineering", biotechHasGene)

        val foodHasPreservation = questionsF.any {
            it.questionText.contains("thermal", ignoreCase = true) ||
            it.questionText.contains("D-value", ignoreCase = true) ||
            it.questionText.contains("water activity", ignoreCase = true) ||
            it.questionText.contains("pasteurization", ignoreCase = true)
        }
        assertTrue("Profile F questions must test food preservation kinetics", foodHasPreservation)
    }

    // ── 3. ROADMAP SYNTHESIS GROUNDED IN FOCUS AREA ───────────────────────────

    @Test
    fun testRoadmapSynthesis_GroundedInFocusAreaNotBroadDomain() {
        val roadmapA = DeterministicRoadmapEngine.generateRoadmap(profileA, null)
        val roadmapB = DeterministicRoadmapEngine.generateRoadmap(profileB, null)
        val roadmapC = DeterministicRoadmapEngine.generateRoadmap(profileC, null)
        val roadmapD = DeterministicRoadmapEngine.generateRoadmap(profileD, null)
        val roadmapE = DeterministicRoadmapEngine.generateRoadmap(profileE, null)
        val roadmapF = DeterministicRoadmapEngine.generateRoadmap(profileF, null)

        // Verify Roadmap subdomains reflect focus area
        assertTrue("Roadmap A subdomain must mention Analog: ${roadmapA.subdomain}",
            roadmapA.subdomain.contains("Analog", ignoreCase = true))
        assertTrue("Roadmap B subdomain must mention Digital: ${roadmapB.subdomain}",
            roadmapB.subdomain.contains("Digital", ignoreCase = true))
        assertTrue("Roadmap C subdomain must mention Frontend: ${roadmapC.subdomain}",
            roadmapC.subdomain.contains("Frontend", ignoreCase = true))
        assertTrue("Roadmap D subdomain must mention Backend: ${roadmapD.subdomain}",
            roadmapD.subdomain.contains("Backend", ignoreCase = true))
        assertTrue("Roadmap E subdomain must mention Genetic Engineering: ${roadmapE.subdomain}",
            roadmapE.subdomain.contains("Genetic", ignoreCase = true))
        assertTrue("Roadmap F subdomain must mention Food Preservation: ${roadmapF.subdomain}",
            roadmapF.subdomain.contains("Preservation", ignoreCase = true))

        // Verify milestones differ between Profile A and Profile B
        val topicsA = roadmapA.steps.map { it.topic }
        val topicsB = roadmapB.steps.map { it.topic }
        assertNotEquals("Roadmap topics for Analog and Digital VLSI must differ", topicsA, topicsB)

        // Verify step descriptions relate to focus area
        val descriptionsA = roadmapA.steps.joinToString(" ") { it.description }
        assertTrue("Step descriptions A should relate to Analog: $descriptionsA",
            descriptionsA.contains("Analog", ignoreCase = true) || descriptionsA.contains("CMOS", ignoreCase = true))
    }

    // ── 4. YOUTUBE QUERY GROUNDED IN FOCUS AREA ───────────────────────────────

    @Test
    fun testYouTubeDataRepository_QueryPrioritizesFocusArea() {
        val repo = YouTubeDataRepository(apiKey = "")

        // Test with Profile A (Analog VLSI)
        val queryA = repo.buildCuratedSearchQuery(profileA, "Op-Amp Frequency Compensation")
        assertTrue("Query A must start with focus area Analog VLSI: $queryA",
            queryA.startsWith("Analog VLSI", ignoreCase = true))
        assertTrue("Query A must contain topic: $queryA",
            queryA.contains("Op-Amp Frequency Compensation", ignoreCase = true))
        assertFalse("Query A must omit broad domain when focus area is present: $queryA",
            queryA.contains("Electronics and VLSI", ignoreCase = true))

        // Test with Profile B (Digital VLSI)
        val queryB = repo.buildCuratedSearchQuery(profileB, "Static Timing Analysis")
        assertTrue("Query B must start with focus area Digital VLSI: $queryB",
            queryB.startsWith("Digital VLSI", ignoreCase = true))
        assertTrue("Query B must contain topic: $queryB",
            queryB.contains("Static Timing Analysis", ignoreCase = true))
        assertFalse("Query B must omit broad domain when focus area is present: $queryB",
            queryB.contains("Electronics and VLSI", ignoreCase = true))

        // Test with Profile C (Frontend Dev)
        val queryC = repo.buildCuratedSearchQuery(profileC, "Responsive Grid Layout")
        assertTrue("Query C must start with Frontend Development: $queryC",
            queryC.startsWith("Frontend Development", ignoreCase = true))
        assertFalse("Query C must omit Computer Science: $queryC",
            queryC.contains("Computer Science", ignoreCase = true))

        // Test with Profile D (Backend Dev)
        val queryD = repo.buildCuratedSearchQuery(profileD, "REST API Design")
        assertTrue("Query D must start with Backend Development: $queryD",
            queryD.startsWith("Backend Development", ignoreCase = true))
        assertFalse("Query D must omit Computer Science: $queryD",
            queryD.contains("Computer Science", ignoreCase = true))

        // Test fallback when focus area is blank
        val blankFocusProfile = profileA.copy(subdomain = "")
        val queryBlank = repo.buildCuratedSearchQuery(blankFocusProfile, "Semiconductor Physics")
        assertTrue("Query with blank focus area should include broad domain: $queryBlank",
            queryBlank.contains("Electronics and VLSI", ignoreCase = true))
    }

    // ── 5. PROFILE UPDATE SEQUENCE (ANALOG -> DIGITAL) ─────────────────────────

    @Test
    fun testProfileUpdateSequence_AnalogToDigital_InvalidatesOldRoadmap() {
        // Step 1: Learner starts with Profile A (Analog VLSI)
        val contextA = LearnerContext.fromProfile(profileA)
        val roadmapA = DeterministicRoadmapEngine.generateRoadmap(profileA, null)
        assertTrue(roadmapA.subdomain.contains("Analog", ignoreCase = true))

        // Step 2: Learner updates profile to Profile B (Digital VLSI)
        val updatedProfile = profileA.copy(
            subdomain = "Digital VLSI",
            goal = "RTL design, static timing analysis and clock domain crossing"
        )
        val contextB = LearnerContext.fromProfile(updatedProfile)

        // Step 3: Fingerprints must change
        assertNotEquals("Fingerprint must change when subdomain and goal change",
            contextA.profileFingerprint, contextB.profileFingerprint)

        // Step 4: Stale diagnostic detection
        assertTrue("Context B must detect stale state from Context A",
            contextB.isDiagnosticStaleForContext(contextA))

        // Step 5: Regenerate roadmap
        val roadmapB = DeterministicRoadmapEngine.generateRoadmap(updatedProfile, null)
        assertTrue(roadmapB.subdomain.contains("Digital", ignoreCase = true))
        assertFalse(roadmapB.subdomain.contains("Analog", ignoreCase = true))

        // Step 6: Step topics must completely reflect Digital VLSI
        val digitalTopics = roadmapB.steps.map { it.topic }
        assertTrue("Digital roadmap must contain timing/RTL topics",
            digitalTopics.any { it.contains("Timing", ignoreCase = true) || it.contains("Verilog", ignoreCase = true) || it.contains("CMOS", ignoreCase = true) })
    }

    // ── 6. AMBIGUITY DETECTION & CLARIFICATION ────────────────────────────────

    @Test
    fun testAmbiguityDetection_IdentifiesBroadTermsAndSuggestsSpecializations() {
        // Broad "vlsi"
        val ambigVlsi = FocusAreaIntelligence.checkAmbiguity("vlsi")
        assertNotNull(ambigVlsi)
        assertTrue(ambigVlsi!!.isAmbiguous)
        assertTrue(ambigVlsi.suggestedSubAreas.any { it.contains("Analog VLSI", ignoreCase = true) })
        assertTrue(ambigVlsi.suggestedSubAreas.any { it.contains("Digital VLSI", ignoreCase = true) })

        // Broad "ai"
        val ambigAi = FocusAreaIntelligence.checkAmbiguity("ai")
        assertNotNull(ambigAi)
        assertTrue(ambigAi!!.isAmbiguous)
        assertTrue(ambigAi.suggestedSubAreas.any { it.contains("Machine Learning", ignoreCase = true) })

        // Broad "web"
        val ambigWeb = FocusAreaIntelligence.checkAmbiguity("web")
        assertNotNull(ambigWeb)
        assertTrue(ambigWeb!!.isAmbiguous)
        assertTrue(ambigWeb.suggestedSubAreas.any { it.contains("Frontend", ignoreCase = true) })
        assertTrue(ambigWeb.suggestedSubAreas.any { it.contains("Backend", ignoreCase = true) })

        // Specific "Analog VLSI" is not ambiguous
        val specific = FocusAreaIntelligence.checkAmbiguity("Analog VLSI")
        assertNull("Specific focus area should not trigger ambiguity", specific)
    }
}
