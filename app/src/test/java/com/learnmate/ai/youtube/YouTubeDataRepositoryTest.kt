package com.learnmate.ai.youtube

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.LearnMateAiCoreEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.data.repository.YouTubeDataRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.youtube.YouTubeApiError
import com.learnmate.ai.domain.youtube.YouTubeVideoItem
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeDataRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var learningRepository: LearningRepository
    private lateinit var repositoryWithBlankKey: YouTubeDataRepository
    private lateinit var repositoryWithKey: YouTubeDataRepository
    private lateinit var llamaEngine: ExecuTorchLlamaEngine
    private lateinit var aiCoreEngine: LearnMateAiCoreEngine
    private val inferenceCounter = AtomicInteger(0)

    class TestLlamaBridge(private val counter: AtomicInteger) : ExecuTorchLlamaEngine.NativeLlmBridge {
        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = "TestModule"
        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            counter.incrementAndGet()
            onToken("Local Llama 3.2 1B synthesized lecture summary for testing.")
            onStats(18.5f)
            return 0
        }
        override fun stop(moduleInstance: Any) {}
    }

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        val appFilesDir = tempFolder.newFolder("yt_test_files")
        val assetManager = ModelAssetManager(appFilesDir)
        learningRepository = LearningRepository(dao = null, ioDispatcher = testDispatcher)

        llamaEngine = ExecuTorchLlamaEngine(
            assetManager = assetManager,
            runtimeBridge = TestLlamaBridge(inferenceCounter),
            inferenceDispatcher = testDispatcher
        )
        runBlocking {
            llamaEngine.loadModel("dummy/model.pte", "dummy/tokenizer.bin")
        }
        aiCoreEngine = LearnMateAiCoreEngine(llamaEngine, assetManager)

        // Blank key repository for offline/cache testing
        repositoryWithBlankKey = YouTubeDataRepository(
            learningRepository = learningRepository,
            ioDispatcher = testDispatcher,
            apiKey = "",
            packageName = "com.learnmate.ai",
            certSha1 = "0AB7DD30A89042C2CF6DADEF1EBA41775671B9AD"
        )

        // Repository with configured key
        repositoryWithKey = YouTubeDataRepository(
            learningRepository = learningRepository,
            ioDispatcher = testDispatcher,
            apiKey = "TEST_API_KEY_AIzaSyFakeKeyForUnitTests",
            packageName = "com.learnmate.ai",
            certSha1 = "0AB7DD30A89042C2CF6DADEF1EBA41775671B9AD"
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 1. ISO Duration Parsing Tests ────────────────────────────────────────

    @Test
    fun parseIsoDuration_standardMinutesAndSeconds_returnsFormattedString() {
        val formatted = repositoryWithBlankKey.parseIsoDuration("PT15M33S")
        assertEquals("15:33", formatted)
    }

    @Test
    fun parseIsoDuration_withHoursMinutesSeconds_returnsFormattedString() {
        val formatted = repositoryWithBlankKey.parseIsoDuration("PT1H2M10S")
        assertEquals("1:02:10", formatted)
    }

    @Test
    fun parseIsoDuration_singleDigitSeconds_padsWithZero() {
        val formatted = repositoryWithBlankKey.parseIsoDuration("PT3M5S")
        assertEquals("3:05", formatted)
    }

    @Test
    fun parseIsoDuration_onlySeconds_formatsCorrectly() {
        val formatted = repositoryWithBlankKey.parseIsoDuration("PT45S")
        assertEquals("0:45", formatted)
    }

    @Test
    fun parseIsoDuration_onlyHoursAndMinutes_formatsCorrectly() {
        val formatted = repositoryWithBlankKey.parseIsoDuration("PT2H30M")
        assertEquals("2:30:00", formatted)
    }

    @Test
    fun parseIsoDuration_emptyOrInvalidInput_returnsEmptyString() {
        assertEquals("", repositoryWithBlankKey.parseIsoDuration(""))
        assertEquals("", repositoryWithBlankKey.parseIsoDuration("INVALID_DURATION"))
    }

    // ── 2. Pedagogy & Query Formulation Tests ────────────────────────────────

    @Test
    fun getCuratedVideosForLearner_undergraduateBiotech_formulatesProperQueryAndLang() = runBlocking {
        val profile = LearnerProfile(
            userId = "test_user_biotech",
            name = "Ananya Patel",
            targetDomain = "Biotechnology",
            subdomain = "Fermentation Technology",
            educationLevel = EducationLevel.UNDERGRADUATE,
            preferredLanguageCode = "en"
        )

        // Pre-cache video to verify retrieval
        val cachedTasks = listOf(
            StudyTask(
                id = "yt_biotech_4dm_LJWoDOo",
                userId = "system_cache",
                roadmapStepId = "step_youtube",
                dayIndex = 1,
                weekIndex = 1,
                title = "Introduction to Bioreactor Design",
                topic = "Fermentation Technology",
                durationMinutes = 15,
                notes = "YOUTUBE_CACHE|channel:BiotechAcademy|thumb:https://img.youtube.com/vi/4dm_LJWoDOo/hqdefault.jpg|duration:12:45|durationIso:PT12M45S|views:25000|likes:1200|url:https://www.youtube.com/watch?v=4dm_LJWoDOo|desc:Comprehensive bioreactor fermentation engineering overview"
            )
        )
        learningRepository.saveStudyTasks(cachedTasks, "track_cache")

        val result = repositoryWithBlankKey.getCuratedVideosForLearner(
            profile = profile,
            topic = "Fermentation Technology"
        )

        assertTrue(result.isSuccess)
        val videos = result.getOrNull()
        assertNotNull(videos)
        assertTrue(videos!!.isNotEmpty())
        val video = videos.first()
        assertEquals("Introduction to Bioreactor Design", video.title)
        assertEquals("BiotechAcademy", video.channelTitle)
        assertEquals("12:45", video.durationFormatted)
        assertTrue(video.isOfflineCached)
    }

    @Test
    fun getCuratedVideosForLearner_postgraduateFoodTech_retrievesAdvancedTopics() = runBlocking {
        val profile = LearnerProfile(
            userId = "test_user_food",
            name = "Rohan Sharma",
            targetDomain = "Food Technology",
            subdomain = "Thermal Processing & Kinetics",
            educationLevel = EducationLevel.POSTGRADUATE,
            preferredLanguageCode = "hi"
        )

        val cachedTasks = listOf(
            StudyTask(
                id = "yt_food_F9k3L4",
                userId = "system_cache",
                roadmapStepId = "step_youtube",
                dayIndex = 1,
                weekIndex = 1,
                title = "Thermal Death Time (D and z value kinetics)",
                topic = "Thermal Processing & Kinetics",
                durationMinutes = 25,
                notes = "YOUTUBE_CACHE|channel:IIT Kharagpur Food Eng|thumb:https://img.youtube.com/vi/F9k3L4/hqdefault.jpg|duration:24:10|durationIso:PT24M10S|views:18500|likes:950|url:https://www.youtube.com/watch?v=F9k3L4|desc:Advanced mathematical modeling of thermal sterilization"
            )
        )
        learningRepository.saveStudyTasks(cachedTasks, "track_cache")

        val result = repositoryWithBlankKey.getCuratedVideosForLearner(
            profile = profile,
            topic = "Thermal Processing & Kinetics"
        )

        assertTrue(result.isSuccess)
        val videos = result.getOrNull()
        assertNotNull(videos)
        assertEquals("Thermal Death Time (D and z value kinetics)", videos!!.first().title)
        assertEquals("IIT Kharagpur Food Eng", videos.first().channelTitle)
    }

    // ── 3. Offline Caching & Room DB Fallback Tests ──────────────────────────

    @Test
    fun searchVideos_withBlankKeyAndNoCache_returnsMissingKeyError() = runBlocking {
        val result = repositoryWithBlankKey.searchVideos("Nonexistent Topic Without Cache")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is YouTubeApiError)
        assertEquals("MISSING_API_KEY", (error as YouTubeApiError).reason)
        assertEquals(401, error.httpCode)
    }

    @Test
    fun searchVideos_withCachedData_servesOfflineCachedFlag() = runBlocking {
        val cachedTask = StudyTask(
            id = "yt_vlsi_abc123",
            userId = "system_cache",
            roadmapStepId = "step_youtube",
            dayIndex = 1,
            weekIndex = 1,
            title = "CMOS Inverter DC Characteristics",
            topic = "VLSI Design",
            durationMinutes = 20,
            notes = "YOUTUBE_CACHE|channel:NPTEL-NOC IITM|thumb:https://img.youtube.com/vi/abc123/hqdefault.jpg|duration:18:22|durationIso:PT18M22S|views:54000|likes:2100|url:https://www.youtube.com/watch?v=abc123|desc:Static characteristics and noise margin derivation"
        )
        learningRepository.saveStudyTasks(listOf(cachedTask), "track_cache")

        val cachedVideos = repositoryWithBlankKey.getCachedVideosForTopic("VLSI Design")
        assertEquals(1, cachedVideos.size)
        val video = cachedVideos.first()
        assertEquals("abc123", video.id)
        assertEquals("CMOS Inverter DC Characteristics", video.title)
        assertEquals("NPTEL-NOC IITM", video.channelTitle)
        assertEquals("18:22", video.durationFormatted)
        assertTrue(video.isOfflineCached)
    }

    // ── 4. Local Llama 3.2 1B Study Notes Generation Tests ───────────────────

    @Test
    fun generateStudyNotes_withAiCoreEngine_usesLocalLlamaModel() = runBlocking {
        val video = YouTubeVideoItem(
            id = "vid_biotech_1",
            title = "Enzyme Immobilization Techniques",
            description = "Overview of adsorption, covalent binding, and entrapment in calcium alginate beads.",
            channelTitle = "Biochemical Engineering Lectures",
            thumbnailUrl = "https://img.youtube.com/vi/vid_biotech_1/hqdefault.jpg",
            durationFormatted = "15:20",
            topic = "Biotechnology"
        )

        val notes = repositoryWithBlankKey.generateStudyNotesWithLocalLlama(
            video = video,
            transcriptOrDescription = "Enzyme entrapment in calcium alginate protects enzymes from shear forces while maintaining catalytic activity.",
            aiCoreEngine = aiCoreEngine
        )

        assertNotNull(notes)
        assertEquals("vid_biotech_1", notes.videoId)
        assertEquals("Enzyme Immobilization Techniques", notes.videoTitle)
        assertTrue(notes.summary.isNotBlank())
        assertTrue(notes.keyTakeaways.isNotEmpty())
        assertEquals(1, notes.generatedQuestions.size)
        assertEquals("Local On-Device Meta Llama 3.2 1B (ExecuTorch 1.4.0)", notes.sourceProvenance)

        // Verify inference was invoked on local bridge
        assertTrue(inferenceCounter.get() > 0)

        // Verify notes were cached in Room database
        val cachedNotes = repositoryWithBlankKey.getCachedStudyNotes("vid_biotech_1")
        assertNotNull(cachedNotes)
        assertEquals(notes.videoId, cachedNotes!!.videoId)
    }

    @Test
    fun generateStudyNotes_withNullEngine_generatesDeterministicAcademicFallback() = runBlocking {
        val video = YouTubeVideoItem(
            id = "vid_vlsi_2",
            title = "Static Timing Analysis Fundamentals",
            description = "Setup time, hold time, clock skew, and critical path latency constraints.",
            channelTitle = "VLSI Systems Lab",
            thumbnailUrl = "https://img.youtube.com/vi/vid_vlsi_2/hqdefault.jpg",
            durationFormatted = "22:15",
            topic = "VLSI Design"
        )

        val notes = repositoryWithBlankKey.generateStudyNotesWithLocalLlama(
            video = video,
            transcriptOrDescription = "Setup constraint requires data to arrive before clock edge by Tsetup. Hold constraint prevents race conditions.",
            aiCoreEngine = null
        )

        assertNotNull(notes)
        assertTrue(notes.summary.contains("Static Timing Analysis Fundamentals"))
        assertTrue(notes.summary.contains("VLSI Systems Lab"))
        assertTrue(notes.keyTakeaways.size >= 2)
        assertEquals(1, notes.generatedQuestions.size)
    }

    // ── 5. ViewModel Integration Tests ───────────────────────────────────────

    @Test
    fun learningViewModel_loadYouTubeResourcesForTopic_updatesStateCorrectly() = runBlocking {
        val aiTutorRepo = AiTutorRepository(
            engine = llamaEngine,
            assetManager = ModelAssetManager(tempFolder.newFolder("vm_asset")),
            learningRepository = learningRepository,
            ioDispatcher = testDispatcher
        )

        val viewModel = LearningViewModel(
            learningRepository = learningRepository,
            aiTutorRepository = aiTutorRepo,
            appPreferences = null,
            aiCoreEngine = aiCoreEngine,
            youtubeRepository = repositoryWithBlankKey
        )

        // Seed cache
        val cachedTask = StudyTask(
            id = "yt_ferment_v1",
            userId = "system_cache",
            roadmapStepId = "step_youtube",
            dayIndex = 1,
            weekIndex = 1,
            title = "Batch vs Fed-Batch Fermentation",
            topic = "Fermentation Systems",
            durationMinutes = 15,
            notes = "YOUTUBE_CACHE|channel:Bioprocess Tech|thumb:https://img.youtube.com/vi/v1/hqdefault.jpg|duration:16:40|durationIso:PT16M40S|views:12000|likes:800|url:https://www.youtube.com/watch?v=v1|desc:Kinetics comparison"
        )
        learningRepository.saveStudyTasks(listOf(cachedTask), "track_cache")

        val profile = LearnerProfile(
            userId = "vm_test_user",
            name = "Kavitha Raman",
            targetDomain = "Biotechnology",
            subdomain = "Fermentation Systems",
            educationLevel = EducationLevel.UNDERGRADUATE
        )

        viewModel.loadYouTubeResourcesForTopic(profile, "Fermentation Systems")

        val state = viewModel.youtubeVideosState.value
        assertTrue("Expected Success state, got $state", state is LearningUiState.Success)
        val videos = (state as LearningUiState.Success).data
        assertEquals(1, videos.size)
        assertEquals("Batch vs Fed-Batch Fermentation", videos.first().title)
    }
}
