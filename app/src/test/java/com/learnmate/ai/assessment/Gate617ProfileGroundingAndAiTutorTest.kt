package com.learnmate.ai.assessment

import com.learnmate.ai.ai.ActiveModelManifest
import com.learnmate.ai.ai.ExactPromptBudgeter
import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.LearnMateAiCoreEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gate 6.17 Product Integrity & Profile Grounding Regression Suite.
 *
 * Enforces:
 * 1. Strict domain-to-content grounding: No science questions in Computer Science, VLSI, Biotech, etc.
 * 2. Distinct question banks across all 6 verified profile disciplines.
 * 3. Custom domain handling without defaulting to School Science.
 * 4. Roadmap milestones strictly aligned with the target domain.
 * 5. AI Tutor "No Unsolicited Code" directive for conceptual questions.
 * 6. Explicit code generation detection for programming prompts.
 * 7. Token budget enforcement <= 144 tokens.
 */
class Gate617ProfileGroundingAndAiTutorTest {

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
        val appFilesDir = tempFolder.newFolder("gate617_grounding_files")
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

    private val csProfile = LearnerProfile(
        userId = "test_cs",
        name = "CS Learner",
        targetDomain = "Computer Science & AI",
        subdomain = "Data Structures & Algorithms",
        goal = "Master advanced data structures and algorithms"
    )

    private val vlsiProfile = LearnerProfile(
        userId = "test_vlsi",
        name = "VLSI Learner",
        targetDomain = "Electronics & VLSI",
        subdomain = "Digital VLSI & RTL",
        goal = "RTL Verilog design and STA timing closure"
    )

    private val biotechProfile = LearnerProfile(
        userId = "test_biotech",
        name = "Biotech Learner",
        targetDomain = "Biology & Biotechnology",
        subdomain = "Molecular Biology",
        goal = "PCR, cloning, and genetic engineering"
    )

    private val foodTechProfile = LearnerProfile(
        userId = "test_food",
        name = "Food Tech Learner",
        targetDomain = "Food Technology",
        subdomain = "Food Preservation",
        goal = "Thermal pasteurization and shelf-life kinetics"
    )

    private val schoolScienceProfile = LearnerProfile(
        userId = "test_sci",
        name = "Science Learner",
        targetDomain = "Secondary School Science & Math",
        subdomain = "Secondary Physics",
        goal = "Class 10 science board exam preparation"
    )

    private val customDomainProfile = LearnerProfile(
        userId = "test_robotics",
        name = "Robotics Learner",
        targetDomain = "Robotics & Kinematics",
        subdomain = "Inverse Kinematics",
        goal = "Autonomous robotic manipulator path planning"
    )

    // ── 1. Diagnostic Question Domain Relevance ───────────────────────────────

    @Test
    fun testComputerScienceDiagnosticQuestions_StrictDomainRelevance() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(csProfile)
        assertEquals("Must generate exactly 10 questions", 10, questions.size)

        val forbiddenTopics = listOf("Newtonian", "Chemical", "Optics", "Respiration", "Equilibrium", "Acids", "Genetics")
        for (q in questions) {
            for (forbidden in forbiddenTopics) {
                assertFalse(
                    "CS question '${q.questionText}' must not contain forbidden science topic '$forbidden'",
                    q.topic.contains(forbidden, ignoreCase = true) || q.questionText.contains(forbidden, ignoreCase = true)
                )
            }
        }

        // Verify representative CS topics
        val topics = questions.map { it.topic }
        assertTrue("Must test Data Structures", topics.any { it.contains("Data Structure", ignoreCase = true) || it.contains("List", ignoreCase = true) })
        assertTrue("Must test Sorting Algorithms", topics.any { it.contains("Sorting", ignoreCase = true) })
        assertTrue("Must test Binary Search Trees", topics.any { it.contains("Tree", ignoreCase = true) })
        assertTrue("Must test Graph Algorithms", topics.any { it.contains("Graph", ignoreCase = true) })
        assertTrue("Must test Operating Systems", topics.any { it.contains("Operating System", ignoreCase = true) || it.contains("Concurrency", ignoreCase = true) })
    }

    @Test
    fun testVlsiDiagnosticQuestions_StrictDomainRelevance() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(vlsiProfile)
        assertEquals(10, questions.size)

        val topics = questions.map { it.topic }
        assertTrue("Must test CMOS or Semiconductor", topics.any { it.contains("CMOS", ignoreCase = true) || it.contains("Semiconductor", ignoreCase = true) || it.contains("Inverter", ignoreCase = true) })
        assertTrue("Must test Timing Constraints", topics.any { it.contains("Timing", ignoreCase = true) || it.contains("Setup", ignoreCase = true) })

        for (q in questions) {
            assertFalse("VLSI questions must not contain Biology", q.topic.contains("Cellular", ignoreCase = true))
            assertFalse("VLSI questions must not contain Newtonian", q.topic.contains("Newtonian", ignoreCase = true))
        }
    }

    @Test
    fun testBiotechnologyDiagnosticQuestions_StrictDomainRelevance() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(biotechProfile)
        assertEquals(10, questions.size)

        val topics = questions.map { it.topic }
        assertTrue("Must test DNA or Central Dogma", topics.any { it.contains("DNA", ignoreCase = true) || it.contains("Dogma", ignoreCase = true) })
        assertTrue("Must test PCR", topics.any { it.contains("PCR", ignoreCase = true) || it.contains("Polymerase", ignoreCase = true) })
    }

    @Test
    fun testFoodTechnologyDiagnosticQuestions_StrictDomainRelevance() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(foodTechProfile)
        assertEquals(10, questions.size)

        val topics = questions.map { it.topic }
        assertTrue("Must test Pasteurization or Thermal Processing", topics.any { it.contains("Pasteurization", ignoreCase = true) || it.contains("Thermal", ignoreCase = true) })
        assertTrue("Must test Water Activity or HACCP", topics.any { it.contains("Water Activity", ignoreCase = true) || it.contains("HACCP", ignoreCase = true) })
    }

    @Test
    fun testSchoolScienceDiagnosticQuestions_StrictDomainRelevance() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(schoolScienceProfile)
        assertEquals(10, questions.size)

        val topics = questions.map { it.topic }
        assertTrue("Must test Mechanics", topics.any { it.contains("Mechanics", ignoreCase = true) || it.contains("Newton", ignoreCase = true) })
        assertTrue("Must test Chemical Reactions", topics.any { it.contains("Chemical", ignoreCase = true) || it.contains("Bonding", ignoreCase = true) })
    }

    @Test
    fun testCustomDiscipline_NeverDefaultsToSchoolScience() {
        val questions = aiCoreEngine.generateTailoredDomainQuestions(customDomainProfile)
        assertEquals(10, questions.size)

        for (q in questions) {
            assertEquals("Custom questions must have custom domain", customDomainProfile.targetDomain, q.domain)
            assertFalse("Custom domain must not contain Newtonian Mechanics", q.topic.contains("Newtonian Mechanics", ignoreCase = true))
            assertFalse("Custom domain must not contain Chemical Bonding", q.topic.contains("Chemical Bonding", ignoreCase = true))
        }
    }

    // ── 2. Roadmap Grounding ──────────────────────────────────────────────────

    @Test
    fun testComputerScienceRoadmap_NoSciencePollution() {
        val roadmap = DeterministicRoadmapEngine.generateRoadmap(csProfile, null)
        assertTrue("Roadmap must have 3 to 5 steps", roadmap.steps.size in 3..5)

        val forbiddenInCs = listOf("Newtonian", "Chemical", "Optics", "Thermodynamics", "Acids", "Cellular", "Electromagnetism")
        for (step in roadmap.steps) {
            for (forbidden in forbiddenInCs) {
                assertFalse(
                    "CS roadmap step '${step.topic}' must not contain '$forbidden'",
                    step.topic.contains(forbidden, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun testRoadmapSynthesis_DoesNotInheritUnrelatedWeaknesses() {
        // Simulate dirty skill-gap report with mismatched Science topics
        val pollutedReport = SkillGapReport(
            userId = csProfile.userId,
            domain = csProfile.targetDomain,
            subdomain = csProfile.subdomain,
            overallScorePercent = 40,
            totalQuestions = 10,
            correctCount = 4,
            strengths = listOf("Basic Arithmetic"),
            weaknesses = listOf("Chemical Reactions, Bonding & Stoichiometry", "Acids, Bases and Salts"),
            skillGaps = emptyList()
        )

        val roadmap = DeterministicRoadmapEngine.generateRoadmap(csProfile, pollutedReport)
        for (step in roadmap.steps) {
            assertFalse("CS roadmap must not include Chemical Reactions weakness", step.topic.contains("Chemical", ignoreCase = true))
            assertFalse("CS roadmap must not include Acids weakness", step.topic.contains("Acids", ignoreCase = true))
        }
    }

    // ── 3. AI Tutor: No Unsolicited Code ──────────────────────────────────────

    @Test
    fun testAiTutor_ExplicitCodeDetection() {
        // Conceptual questions -> must be false
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain binary search trees in simple terms."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("What is a MOSFET and how does it work?"))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain PCR amplification for a beginner."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("What is pasteurization in Food Technology?"))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain QuickSort time complexity without writing code."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain QuickSort without code."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain QuickSort in simple terms."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("Explain QuickSort time complexity no code."))
        assertFalse(AiTutorRepository.isExplicitCodeRequest("தமிழில் CMOS inverter-ஐ எளிமையாக விளக்கவும்."))

        // Explicit code requests -> must be true
        assertTrue(AiTutorRepository.isExplicitCodeRequest("Write a Verilog module for a 4-bit counter."))
        assertTrue(AiTutorRepository.isExplicitCodeRequest("Write a Python implementation of binary search."))
        assertTrue(AiTutorRepository.isExplicitCodeRequest("Write code for depth first search in Python."))
        assertTrue(AiTutorRepository.isExplicitCodeRequest("Implement a linked list in C++."))
    }

    // ── 4. Token Budget Hard Limit ────────────────────────────────────────────

    @Test
    fun testTokenBudget_ClampsStrictlyTo144ForStructuredWorkflowsAnd1000ForAiTutor() {
        assertEquals("Max output headroom for structured workflows must be 144", 144, ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)
        assertEquals("ActiveModelManifest max new tokens must be 1000", 1000, ActiveModelManifest.MAX_NEW_TOKENS)
        assertEquals("ActiveModelManifest max AI tutor new tokens must be 1000", 1000, ActiveModelManifest.MAX_AI_TUTOR_NEW_TOKENS)
        assertTrue(ExactPromptBudgeter.MAX_OUTPUT_HEADROOM <= ActiveModelManifest.MAX_NEW_TOKENS)
        assertEquals(512, ExactPromptBudgeter.MAX_PREFILL_CEILING + ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)
    }

    // ── 5. Domain Normalization ───────────────────────────────────────────────

    @Test
    fun testTopicTaxonomy_DomainNormalization() {
        assertEquals("Computer Science and IT", TopicTaxonomy.normalizeDomain("Computer Science & AI"))
        assertEquals("Computer Science and IT", TopicTaxonomy.normalizeDomain("Computer Science"))
        assertEquals("Computer Science and IT", TopicTaxonomy.normalizeDomain("cs_it"))
        assertEquals("Electronics and VLSI", TopicTaxonomy.normalizeDomain("Electronics & VLSI"))
        assertEquals("Biotechnology", TopicTaxonomy.normalizeDomain("Biology & Biotechnology"))
        assertEquals("Food Technology", TopicTaxonomy.normalizeDomain("Food Technology"))
        assertEquals("School Science", TopicTaxonomy.normalizeDomain("Secondary School Science & Math"))
        assertEquals("Robotics & Kinematics", TopicTaxonomy.normalizeDomain("Robotics & Kinematics"))
    }

    // ── 6. End-to-End Profile Domain Switch & Content Isolation ───────────────

    @Test
    fun testProfileDomainSwitch_IsolatesRoadmapAndTasks_ZeroCrossContamination() = kotlinx.coroutines.runBlocking {
        val testDispatcher = Dispatchers.Unconfined
        val repository = com.learnmate.ai.data.repository.LearningRepository(dao = null, ioDispatcher = testDispatcher)
        val aiTutorRepo = com.learnmate.ai.data.repository.AiTutorRepository(
            learningRepository = repository,
            engine = ExecuTorchLlamaEngine(assetManager, TestLlmBridge(), testDispatcher),
            assetManager = assetManager,
            ioDispatcher = testDispatcher
        )
        val viewModel = com.learnmate.ai.viewmodel.LearningViewModel(
            learningRepository = repository,
            aiTutorRepository = aiTutorRepo,
            aiCoreEngine = aiCoreEngine
        )

        // Step 1: Initialize with Computer Science Profile
        viewModel.createOrUpdateProfileAsync(csProfile)
        assertEquals(csProfile.targetDomain, (viewModel.profileState.value as com.learnmate.ai.viewmodel.LearningUiState.Success).data.targetDomain)

        // Generate CS diagnostic questions and submit to create CS roadmap + tasks
        val csQuestions = viewModel.getOrGenerateDiagnosticAssessment(csProfile)
        assertEquals(10, csQuestions.size)
        assertTrue(csQuestions.any { it.topic.contains("Data Structure", ignoreCase = true) || it.topic.contains("List", ignoreCase = true) })

        val csAnswers = csQuestions.associate { it.id to it.correctIndex }
        viewModel.submitDiagnosticAssessment(csProfile, csQuestions, csAnswers)

        // Verify CS roadmap is active and contains CS steps
        val csRoadmapState = viewModel.roadmapState.value
        assertTrue("CS Roadmap must be Success", csRoadmapState is com.learnmate.ai.viewmodel.LearningUiState.Success)
        val csRoadmap = (csRoadmapState as com.learnmate.ai.viewmodel.LearningUiState.Success).data
        assertEquals("Computer Science and IT", TopicTaxonomy.normalizeDomain(csRoadmap.domain))
        assertTrue(csRoadmap.steps.any { it.topic.contains("Algorithm", ignoreCase = true) || it.topic.contains("Binary Search", ignoreCase = true) })

        // Verify CS tasks exist and have NO chemistry/biology
        val csTasksState = viewModel.studyTasksState.value
        assertTrue("CS Study Tasks must be Success", csTasksState is com.learnmate.ai.viewmodel.LearningUiState.Success)
        val csTasks = (csTasksState as com.learnmate.ai.viewmodel.LearningUiState.Success).data
        assertTrue(csTasks.isNotEmpty())
        for (t in csTasks) {
            assertFalse("CS tasks must not contain Acids", t.topic.contains("Acid", ignoreCase = true))
            assertFalse("CS tasks must not contain Chemical", t.topic.contains("Chemical", ignoreCase = true))
        }

        // Step 2: Switch Profile Domain to Electronics & VLSI
        viewModel.createOrUpdateProfileAsync(vlsiProfile)
        assertEquals(vlsiProfile.targetDomain, (viewModel.profileState.value as com.learnmate.ai.viewmodel.LearningUiState.Success).data.targetDomain)

        // Verify old CS roadmap and old CS tasks are immediately hidden (Empty state requiring diagnostic)
        assertTrue("Roadmap must be Empty after switching to VLSI before taking diagnostic", viewModel.roadmapState.value is com.learnmate.ai.viewmodel.LearningUiState.Empty)
        assertTrue("Study tasks must be Empty after switching to VLSI before taking diagnostic", viewModel.studyTasksState.value is com.learnmate.ai.viewmodel.LearningUiState.Empty)

        // Step 3: Generate fresh VLSI diagnostic questions
        val vlsiQuestions = viewModel.getOrGenerateDiagnosticAssessment(vlsiProfile)
        assertEquals(10, vlsiQuestions.size)
        for (q in vlsiQuestions) {
            assertFalse("VLSI questions must not contain Linked List", q.questionText.contains("linked list", ignoreCase = true))
            assertFalse("VLSI questions must not contain Sorting", q.topic.contains("Sorting", ignoreCase = true))
            assertFalse("VLSI questions must not contain Acid", q.topic.contains("Acid", ignoreCase = true))
        }
        assertTrue("Must contain CMOS or Timing", vlsiQuestions.any { it.topic.contains("CMOS", ignoreCase = true) || it.topic.contains("Timing", ignoreCase = true) })

        // Submit VLSI diagnostic assessment
        val vlsiAnswers = vlsiQuestions.associate { it.id to it.correctIndex }
        viewModel.submitDiagnosticAssessment(vlsiProfile, vlsiQuestions, vlsiAnswers)

        // Step 4: Verify fresh VLSI roadmap is active with ZERO CS or Chemistry leakage
        val vlsiRoadmapState = viewModel.roadmapState.value
        assertTrue("VLSI Roadmap must be Success", vlsiRoadmapState is com.learnmate.ai.viewmodel.LearningUiState.Success)
        val vlsiRoadmap = (vlsiRoadmapState as com.learnmate.ai.viewmodel.LearningUiState.Success).data
        assertEquals("Electronics and VLSI", TopicTaxonomy.normalizeDomain(vlsiRoadmap.domain))
        for (step in vlsiRoadmap.steps) {
            assertFalse("VLSI roadmap must not contain Big-O", step.topic.contains("Big-O", ignoreCase = true))
            assertFalse("VLSI roadmap must not contain Binary Search", step.topic.contains("Binary Search", ignoreCase = true))
            assertFalse("VLSI roadmap must not contain Acid", step.topic.contains("Acid", ignoreCase = true))
        }

        // Step 5: Verify fresh VLSI study tasks have ZERO CS algorithms or Chemistry tasks
        val vlsiTasksState = viewModel.studyTasksState.value
        assertTrue("VLSI Tasks must be Success", vlsiTasksState is com.learnmate.ai.viewmodel.LearningUiState.Success)
        val vlsiTasks = (vlsiTasksState as com.learnmate.ai.viewmodel.LearningUiState.Success).data
        assertTrue(vlsiTasks.isNotEmpty())
        for (t in vlsiTasks) {
            assertFalse("VLSI tasks must not contain Big-O", t.topic.contains("Big-O", ignoreCase = true))
            assertFalse("VLSI tasks must not contain Acids", t.topic.contains("Acid", ignoreCase = true))
            assertFalse("VLSI tasks must not contain Chemical", t.topic.contains("Chemical", ignoreCase = true))
        }
    }
}
