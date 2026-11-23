package com.learnmate.ai.assessment

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.LearnMateAiCoreEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class Gate617FullProductIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var appFilesDir: File
    private lateinit var assetManager: ModelAssetManager
    private lateinit var learningRepository: LearningRepository
    private lateinit var aiTutorRepository: AiTutorRepository
    private lateinit var aiCoreEngine: LearnMateAiCoreEngine
    private val generateCallCount = AtomicInteger(0)

    class TestLlmBridge(private val counter: AtomicInteger) : ExecuTorchLlamaEngine.NativeLlmBridge {
        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = "TestModule"
        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            counter.incrementAndGet()
            onToken("Deterministic response.")
            onStats(15.0f)
            return 0
        }
        override fun stop(moduleInstance: Any) {}
    }

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        appFilesDir = tempFolder.newFolder("gate617_test_files")
        assetManager = ModelAssetManager(appFilesDir)
        learningRepository = LearningRepository(dao = null, ioDispatcher = testDispatcher)
        val engine = ExecuTorchLlamaEngine(
            assetManager = assetManager,
            runtimeBridge = TestLlmBridge(generateCallCount),
            inferenceDispatcher = testDispatcher
        )
        runBlocking {
            engine.loadModel("dummy/model.pte", "dummy/tokenizer.bin")
        }
        aiTutorRepository = AiTutorRepository(engine, assetManager, learningRepository, ioDispatcher = testDispatcher)
        aiCoreEngine = LearnMateAiCoreEngine(engine, assetManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMultiDomainProfileConfiguration() {
        // Profile A: Biotechnology
        val profileA = LearnerProfile(
            userId = "user_biotech",
            name = "Ananya Sharma",
            targetDomain = "biotechnology",
            subdomain = "Molecular Biology & Genetic Engineering",
            goal = "Master recombinant DNA technology and CRISPR-Cas9 genome editing",
            targetRole = "Biotech Research Scientist",
            currentLevel = "Intermediate",
            dailyStudyMinutes = 60,
            weeklyStudyDays = 5,
            preferredLanguageCode = "en",
            educationLevel = EducationLevel.POSTGRADUATE,
            onboardingCompleted = true
        )
        assertEquals("biotechnology", profileA.targetDomain)
        assertEquals(60, profileA.dailyStudyMinutes)
        assertTrue(profileA.onboardingCompleted)

        // Profile B: Food Technology
        val profileB = LearnerProfile(
            userId = "user_foodtech",
            name = "Vikram Patel",
            targetDomain = "food_tech",
            subdomain = "Food Preservation & Processing Quality",
            goal = "Implement HACCP and aseptic thermal packaging standards",
            targetRole = "Quality Assurance Manager",
            currentLevel = "Beginner",
            dailyStudyMinutes = 45,
            weeklyStudyDays = 6,
            preferredLanguageCode = "hi",
            educationLevel = EducationLevel.UNDERGRADUATE,
            onboardingCompleted = true
        )
        assertEquals("food_tech", profileB.targetDomain)
        assertEquals("hi", profileB.preferredLanguageCode)

        // Profile C: Electronics / VLSI
        val profileC = LearnerProfile(
            userId = "user_vlsi",
            name = "Karthik Raja",
            targetDomain = "engineering",
            subdomain = "Digital VLSI Design",
            goal = "Design low-power CMOS logic and static timing analysis",
            targetRole = "VLSI Verification Engineer",
            currentLevel = "Advanced",
            dailyStudyMinutes = 90,
            weeklyStudyDays = 5,
            preferredLanguageCode = "ta",
            educationLevel = EducationLevel.UNDERGRADUATE,
            onboardingCompleted = true
        )
        assertEquals("engineering", profileC.targetDomain)
        assertEquals("ta", profileC.preferredLanguageCode)

        // Profile D: Computer Science
        val profileD = LearnerProfile(
            userId = "user_cs",
            name = "Rahul Verma",
            targetDomain = "engineering",
            subdomain = "Data Structures & Algorithms",
            goal = "Master graph algorithms and dynamic programming",
            targetRole = "Software Engineer",
            currentLevel = "Intermediate",
            dailyStudyMinutes = 60,
            weeklyStudyDays = 5,
            preferredLanguageCode = "en",
            educationLevel = EducationLevel.UNDERGRADUATE,
            onboardingCompleted = true
        )
        assertEquals("Data Structures & Algorithms", profileD.subdomain)

        // Profile E: School Science
        val profileE = LearnerProfile(
            userId = "user_school",
            name = "Priya Rao",
            targetDomain = "school_science",
            subdomain = "Class 10 CBSE Science",
            goal = "Score above 95% in Board Exams for Physics & Chemistry",
            targetRole = "High School Student",
            currentLevel = "Beginner",
            dailyStudyMinutes = 30,
            weeklyStudyDays = 7,
            preferredLanguageCode = "kn",
            educationLevel = EducationLevel.SECONDARY,
            onboardingCompleted = true
        )
        assertEquals("school_science", profileE.targetDomain)
        assertEquals("kn", profileE.preferredLanguageCode)

        // Profile F: Custom Domain
        val profileF = LearnerProfile(
            userId = "user_custom",
            name = "Dr. S. Raman",
            targetDomain = "custom",
            subdomain = "Astrophysics & Orbital Mechanics",
            goal = "Understand orbital perturbations and interplanetary trajectories",
            targetRole = "Independent Researcher",
            currentLevel = "Intermediate",
            dailyStudyMinutes = 60,
            weeklyStudyDays = 4,
            preferredLanguageCode = "en",
            educationLevel = EducationLevel.DOCTORAL,
            onboardingCompleted = true
        )
        assertEquals("custom", profileF.targetDomain)
    }

    @Test
    fun testDiagnosticAssessmentGenerationAcrossDomains() = runTest(testDispatcher) {
        val profileA = LearnerProfile(
            userId = "user_biotech",
            name = "Ananya",
            targetDomain = "biotechnology",
            subdomain = "Molecular Biology",
            educationLevel = EducationLevel.POSTGRADUATE
        )
        val questionsBiotech = aiCoreEngine.generateDiagnosticAssessment(profileA)
        assertEquals(10, questionsBiotech.size)
        questionsBiotech.forEach { q ->
            assertTrue(q.isValid)
            assertEquals(4, q.options.size)
            assertTrue(q.correctIndex in 0..3)
            assertTrue(q.explanation.isNotBlank())
        }
        assertTrue(questionsBiotech.any { it.questionText.contains("Central Dogma", ignoreCase = true) || it.questionText.contains("DNA", ignoreCase = true) })
        assertTrue(questionsBiotech.any { it.questionText.contains("PCR", ignoreCase = true) })
        assertTrue(questionsBiotech.any { it.questionText.contains("CRISPR", ignoreCase = true) })

        val profileB = LearnerProfile(
            userId = "user_foodtech",
            name = "Vikram",
            targetDomain = "food_tech",
            subdomain = "Food Processing",
            educationLevel = EducationLevel.UNDERGRADUATE
        )
        val questionsFoodTech = aiCoreEngine.generateDiagnosticAssessment(profileB)
        assertEquals(10, questionsFoodTech.size)
        questionsFoodTech.forEach { q ->
            assertTrue(q.isValid)
            assertEquals(4, q.options.size)
            assertTrue(q.correctIndex in 0..3)
            assertTrue(q.explanation.isNotBlank())
        }
        assertTrue(questionsFoodTech.any { it.questionText.contains("Pasteurization", ignoreCase = true) || it.questionText.contains("Thermal", ignoreCase = true) })
        assertTrue(questionsFoodTech.any { it.questionText.contains("Water activity", ignoreCase = true) || it.questionText.contains("aw", ignoreCase = true) })
        assertTrue(questionsFoodTech.any { it.questionText.contains("HACCP", ignoreCase = true) })
    }

    @Test
    fun testDeterministicDiagnosticGrading() = runTest(testDispatcher) {
        val profile = LearnerProfile(
            userId = "learner_101",
            name = "Test Learner",
            targetDomain = "biotechnology"
        )
        val questions = aiCoreEngine.generateDiagnosticAssessment(profile)
        assertEquals(10, questions.size)

        // 8 correct, 2 incorrect -> 80%
        val answers80 = mutableMapOf<String, Int>()
        questions.forEachIndexed { idx, q ->
            if (idx < 8) {
                answers80[q.id] = q.correctIndex
            } else {
                answers80[q.id] = (q.correctIndex + 1) % 4
            }
        }
        val report80 = DeterministicAssessmentEngine.evaluateAssessment(profile, questions, answers80)
        assertEquals(80, report80.overallScorePercent)
        assertEquals(8, report80.correctCount)
        assertEquals(10, report80.totalQuestions)
        assertEquals(2, report80.weaknesses.size)
        assertEquals(8, report80.strengths.size)

        // 3 correct, 7 incorrect -> 30%
        val answers30 = mutableMapOf<String, Int>()
        questions.forEachIndexed { idx, q ->
            if (idx < 3) {
                answers30[q.id] = q.correctIndex
            } else {
                answers30[q.id] = (q.correctIndex + 1) % 4
            }
        }
        val report30 = DeterministicAssessmentEngine.evaluateAssessment(profile, questions, answers30)
        assertEquals(30, report30.overallScorePercent)
        assertEquals(3, report30.correctCount)
        assertEquals(10, report30.totalQuestions)
        assertEquals(7, report30.weaknesses.size)
        assertEquals(3, report30.strengths.size)
    }

    @Test
    fun testSequentialDayLockingAndPassingCheckpointUnlocksNextDay() = runTest(testDispatcher) {
        val viewModel = LearningViewModel(learningRepository, aiTutorRepository, null, aiCoreEngine)
        val profile = LearnerProfile(
            userId = "user_seq",
            name = "Seq Learner",
            targetDomain = "biotechnology",
            subdomain = "Cloning & PCR"
        )
        viewModel.createOrUpdateProfile(profile)
        testScheduler.advanceUntilIdle()

        // Initial state: Day 1 unlocked, Day 2 locked
        assertTrue(viewModel.isDayUnlocked(1))
        assertFalse(viewModel.isDayUnlocked(2))

        // Create Day 1 tasks
        val task1 = StudyTask(
            id = "task_day1_1",
            userId = "user_seq",
            roadmapStepId = "step_1",
            dayIndex = 1,
            weekIndex = 1,
            title = "Central Dogma Lecture",
            topic = "Central Dogma",
            durationMinutes = 30,
            isCompleted = true
        )
        val task2 = StudyTask(
            id = "task_day1_2",
            userId = "user_seq",
            roadmapStepId = "step_1",
            dayIndex = 1,
            weekIndex = 1,
            title = "PCR Principles & Primers",
            topic = "PCR Amplification",
            durationMinutes = 30,
            isCompleted = true
        )
        learningRepository.saveStudyTasks(listOf(task1, task2), "track_user_seq_default")

        // Checkpoint questions
        val checkpointQuestions = aiCoreEngine.generateDiagnosticAssessment(profile).take(5)

        // 1. Attempt with FAIL: 2/5 correct (40% < 60%)
        val failAnswers = checkpointQuestions.mapIndexed { idx, q ->
            q.id to (if (idx < 2) q.correctIndex else (q.correctIndex + 1) % 4)
        }.toMap()

        val failResult = viewModel.submitDayCheckpointAssessmentAsync(
            profile = profile,
            dayIndex = 1,
            topic = "Central Dogma & PCR",
            questions = checkpointQuestions,
            answers = failAnswers,
            attemptNumber = 1
        )

        assertNotNull(failResult)
        assertFalse(failResult.isPassed)
        assertEquals(40, failResult.scorePercent)
        assertNotNull(failResult.remedialAdvice)

        // Verify Day 2 REMAINS LOCKED after failure
        assertFalse("Day 2 must remain locked after checkpoint failure", viewModel.isDayUnlocked(2))

        // 2. Retake with PASS: 4/5 correct (80% >= 60%)
        val passAnswers = checkpointQuestions.mapIndexed { idx, q ->
            q.id to (if (idx < 4) q.correctIndex else (q.correctIndex + 1) % 4)
        }.toMap()

        val passResult = viewModel.submitDayCheckpointAssessmentAsync(
            profile = profile,
            dayIndex = 1,
            topic = "Central Dogma & PCR",
            questions = checkpointQuestions,
            answers = passAnswers,
            attemptNumber = 2
        )

        assertNotNull(passResult)
        assertTrue(passResult.isPassed)
        assertEquals(80, passResult.scorePercent)

        // Verify Day 2 is now UNLOCKED
        assertTrue("Day 2 must unlock after passing Day 1 checkpoint", viewModel.isDayUnlocked(2))
    }

    @Test
    fun testAssignmentGenerationAndGrading() = runTest(testDispatcher) {
        val profile = LearnerProfile(
            userId = "user_asgn",
            name = "Assignment Tester",
            targetDomain = "biotechnology"
        )
        val viewModel = LearningViewModel(learningRepository, aiTutorRepository, null, aiCoreEngine)
        val assignment = viewModel.generateAssignmentForDay(profile, 1, "Central Dogma")

        assertNotNull(assignment)
        assertEquals(1, assignment.dayIndex)
        assertTrue(assignment.questions.isNotEmpty())

        val answers = assignment.questions.associate { it.id to it.correctIndex }
        val resultRecorded = viewModel.submitAssignmentAsync(profile, assignment, answers)

        assertNotNull(resultRecorded)
        assertTrue(resultRecorded.isSubmitted)
        assertEquals(100, resultRecorded.scorePercent)

        val savedAsgn = viewModel.completedAssignments.value[1]
        assertNotNull(savedAsgn)
        assertEquals(100, savedAsgn?.scorePercent)
    }
}
