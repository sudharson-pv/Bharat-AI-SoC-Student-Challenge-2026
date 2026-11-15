package com.learnmate.ai

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.StudyTask
import com.learnmate.ai.viewmodel.LearningUiState
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
class LearningViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var appFilesDir: File
    private lateinit var assetManager: ModelAssetManager
    private lateinit var learningRepository: LearningRepository
    private lateinit var aiTutorRepository: AiTutorRepository
    private lateinit var viewModel: LearningViewModel
    private val generateCallCount = AtomicInteger(0)

    class TestLlmBridge(private val counter: AtomicInteger) : ExecuTorchLlamaEngine.NativeLlmBridge {
        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
            return "TestNativeModule"
        }

        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            counter.incrementAndGet()
            onToken("LearningViewModel test response.")
            onStats(15.0f)
            return 0
        }

        override fun stop(moduleInstance: Any) {}
    }

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        appFilesDir = tempFolder.newFolder("vm_test_files")
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
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState_emptyWhenNoProfile() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        assertTrue(viewModel.profileState.value is LearningUiState.Empty)
        assertTrue(viewModel.roadmapState.value is LearningUiState.Idle)
    }

    @Test
    fun testCreateProfile_successStateAndLoadsRoadmap() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        val profile = LearnerProfile(
            userId = "default_learner",
            name = "Test Learner",
            targetDomain = "Electronics and VLSI",
            goal = "Master Semiconductor Physics",
            targetRole = "VLSI Engineer"
        )
        viewModel.createOrUpdateProfile(profile)

        assertTrue(viewModel.profileState.value is LearningUiState.Success)
        val loaded = (viewModel.profileState.value as LearningUiState.Success).data
        assertEquals("Test Learner", loaded.name)
        assertTrue(viewModel.roadmapState.value is LearningUiState.Empty)
    }

    @Test
    fun testToggleStudyTask_updatesTaskList() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        val task = StudyTask(
            id = "task_001",
            userId = "default_learner",
            roadmapStepId = "step_01",
            dayIndex = 1,
            weekIndex = 1,
            title = "Study Diodes",
            topic = "Semiconductor Physics",
            durationMinutes = 20,
            isCompleted = false
        )
        learningRepository.saveStudyTasks(listOf(task))

        val profile = LearnerProfile(
            userId = "default_learner",
            name = "Test Learner",
            targetDomain = "Electronics and VLSI"
        )
        viewModel.createOrUpdateProfile(profile)

        assertTrue(viewModel.studyTasksState.value is LearningUiState.Success)
        val initialTasks = (viewModel.studyTasksState.value as LearningUiState.Success).data
        assertFalse(initialTasks.first().isCompleted)

        viewModel.toggleStudyTask("task_001", true)

        val updatedTasks = (viewModel.studyTasksState.value as LearningUiState.Success).data
        assertTrue(updatedTasks.first().isCompleted)
    }

    @Test
    fun testAiToggleSwitch_delegatesToRepository() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        // Kill switch is active by default
        assertFalse(viewModel.isAiEnabled.value)
        viewModel.setAiEnabled(true)
        assertTrue(viewModel.isAiEnabled.value)
        viewModel.setAiEnabled(false)
        assertFalse(viewModel.isAiEnabled.value)
    }

    @Test
    fun testMultiTrackCreationAndSwitching() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        // Create initial profile
        val profile = LearnerProfile(
            userId = "default_learner",
            name = "Priya Krishnan",
            targetDomain = "Computer Science and IT"
        )
        viewModel.createOrUpdateProfile(profile)

        // Verify default track is present
        assertTrue(viewModel.tracksState.value is LearningUiState.Success)
        val initialTracks = (viewModel.tracksState.value as LearningUiState.Success).data
        assertEquals(1, initialTracks.size)

        // Create second track for Civil Services
        viewModel.createLearningTrack(
            title = "UPSC CSE 2027",
            learningMode = LearningMode.COMPETITIVE_EXAM,
            targetCurriculumNodeId = "node_root_upsc",
            dailyCommitmentMinutes = 90,
            targetExamName = "UPSC Prelims"
        )

        val tracksAfterAdd = (viewModel.tracksState.value as LearningUiState.Success).data
        assertEquals(2, tracksAfterAdd.size)
        assertEquals("UPSC CSE 2027", viewModel.activeTrack.value?.title)

        // Switch back to initial track
        val firstTrackId = initialTracks[0].trackId
        viewModel.switchActiveTrack(firstTrackId)

        assertEquals(firstTrackId, viewModel.activeTrack.value?.trackId)
    }

    @Test
    fun testChatSessionAndQuestionFlow() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)

        viewModel.startOrGetChatSession("Electronics and VLSI", "Physics Q&A")

        assertNotNull(viewModel.activeSession.value)
        assertEquals("Electronics and VLSI", viewModel.activeSession.value?.domain)

        viewModel.sendTutorQuestion("Explain Ohm's Law")

        assertFalse(viewModel.isGenerating.value)
        assertEquals("", viewModel.streamingDelta.value)
        assertTrue(viewModel.chatMessages.value.isNotEmpty())
    }

    @Test
    fun testContentPackInstallAndUninstall() = runTest(testDispatcher) {
        viewModel = LearningViewModel(learningRepository, aiTutorRepository)
        val packFile = File("cbse_sci10_starter.lmpack").let {
            if (it.exists()) it else File("app/cbse_sci10_starter.lmpack")
        }
        assertTrue("Reference pack file must exist", packFile.exists())
        val baseStorage = tempFolder.newFolder("content_packs_storage")

        viewModel.loadInstalledPacks()
        assertEquals(0, viewModel.installedPacks.value.size)

        viewModel.importContentPack(packFile, baseStorage)
        assertNotNull(viewModel.importStatusMessage.value)
        assertTrue(viewModel.importStatusMessage.value!!.contains("Successfully installed"))

        val installedDir = File(baseStorage, "installed/cbse_sci10_starter")
        assertTrue("Installed pack directory should exist", installedDir.exists())
        assertTrue("Resource ch1 guide must exist", File(installedDir, "resources/ch1_equations_guide.txt").exists())

        viewModel.clearImportStatusMessage()
        assertNull(viewModel.importStatusMessage.value)

        viewModel.uninstallContentPack("cbse_sci10_starter", baseStorage)
        assertFalse(installedDir.exists())
    }
}
