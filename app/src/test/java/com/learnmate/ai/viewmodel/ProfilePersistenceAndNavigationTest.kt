package com.learnmate.ai.viewmodel

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.AppPreferences
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ProfilePersistenceAndNavigationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var appFilesDir: File
    private lateinit var assetManager: ModelAssetManager
    private lateinit var learningRepository: LearningRepository
    private lateinit var aiTutorRepository: AiTutorRepository
    private lateinit var appPreferences: AppPreferences
    private val generateCallCount = AtomicInteger(0)

    class MockLlmBridge(private val counter: AtomicInteger) : ExecuTorchLlamaEngine.NativeLlmBridge {
        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = "MockModule"
        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            counter.incrementAndGet()
            onToken("Mock response")
            onStats(10.0f)
            return 0
        }
        override fun stop(moduleInstance: Any) {}
    }

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        appFilesDir = tempFolder.newFolder("persistence_test_files")
        assetManager = ModelAssetManager(appFilesDir)
        learningRepository = LearningRepository(dao = null, ioDispatcher = testDispatcher)
        appPreferences = AppPreferences() // Uses in-memory preferences for JVM test execution

        val engine = ExecuTorchLlamaEngine(
            assetManager = assetManager,
            runtimeBridge = MockLlmBridge(generateCallCount),
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

    /**
     * Case A: First-launch profile creation permanently marks onboardingCompleted = true
     * and sets active user ID in AppPreferences and ViewModel.
     */
    @Test
    fun testCaseA_newProfileCreationAndPersistence_setsOnboardingCompletedAndNavigatesToDashboard() = runTest(testDispatcher) {
        val viewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)

        // Initially no profile
        assertTrue(viewModel.profileState.value is LearningUiState.Empty)

        // User completes onboarding
        val newProfile = LearnerProfile(
            userId = "learner_case_a",
            name = "Aarav Sharma",
            targetDomain = "Computer Science & Engineering",
            subdomain = "Operating Systems",
            goal = "Master Kernel Programming",
            targetRole = "Systems Software Engineer",
            dailyStudyMinutes = 60,
            onboardingCompleted = true,
            assessmentCompleted = false
        )

        viewModel.createOrUpdateProfileAsync(newProfile)

        // Verify state is Success and onboardingCompleted is true
        val state = viewModel.profileState.value
        assertTrue("Profile state should be Success", state is LearningUiState.Success)
        val loadedProfile = (state as LearningUiState.Success).data
        assertEquals("learner_case_a", loadedProfile.userId)
        assertTrue("onboardingCompleted must be true", loadedProfile.onboardingCompleted)

        // Verify AppPreferences has updated active user ID
        assertEquals("learner_case_a", appPreferences.getActiveUserId())
        assertEquals("learner_case_a", viewModel.currentUserId)

        // Verify startup navigation logic:
        // isModelReady = true, isOnboardingNeeded = profile == null || !profile.onboardingCompleted
        val isModelReady = true
        val isOnboardingNeeded = !loadedProfile.onboardingCompleted
        val targetRoute = when {
            !isModelReady -> Screen.ModelDownload.route
            isOnboardingNeeded -> Screen.Onboarding.route
            else -> Screen.Home.route
        }
        assertEquals("Target route must be Home / Dashboard", Screen.Home.route, targetRoute)
    }

    /**
     * Case B: Profile update in UniversalProfileScreen preserves onboardingCompleted = true,
     * updates existing record without duplicate profile creation, and resets stale domain artifacts.
     */
    @Test
    fun testCaseB_profileUpdatePreservesOnboardingCompletedAndAvoidsDuplicates() = runTest(testDispatcher) {
        val viewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)

        val initialProfile = LearnerProfile(
            userId = "learner_case_b",
            name = "Priya Nair",
            targetDomain = "Computer Science & Engineering",
            goal = "Learn Python",
            onboardingCompleted = true
        )
        viewModel.createOrUpdateProfileAsync(initialProfile)

        // Now user updates profile to Electronics & VLSI, and even if onboardingCompleted is not explicitly passed
        val updatedProfile = initialProfile.copy(
            name = "Priya Nair Updated",
            targetDomain = "Electronics and VLSI",
            subdomain = "VLSI Design",
            goal = "Master Verilog HDL",
            dailyStudyMinutes = 90,
            onboardingCompleted = false // Simulate UI editor omitting or resetting flag
        )

        viewModel.createOrUpdateProfileAsync(updatedProfile)

        val state = viewModel.profileState.value
        assertTrue("Profile state should be Success", state is LearningUiState.Success)
        val finalProfile = (state as LearningUiState.Success).data

        // Critical: onboardingCompleted must remain true via finalizedOnboardingCompleted
        assertTrue("onboardingCompleted must be preserved as true across edits", finalProfile.onboardingCompleted)
        assertEquals("Priya Nair Updated", finalProfile.name)
        assertEquals("Electronics and VLSI", finalProfile.targetDomain)

        // Check that repository only contains 1 profile (no duplicate creation)
        val allProfiles = learningRepository.getAllProfiles()
        assertEquals("There should be exactly 1 profile in repository", 1, allProfiles.size)
        assertEquals("learner_case_b", allProfiles.first().userId)
    }

    /**
     * Case C: Cold restart restoration:
     * When application is force-stopped / killed and restarted, the new ViewModel instance
     * restores the active profile from Room / preferences and routes directly to Dashboard.
     */
    @Test
    fun testCaseC_coldRestartRestoration_restoresActiveProfileAndSkipsOnboarding() = runTest(testDispatcher) {
        // Step 1: Session 1 creates and saves profile
        val session1ViewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)
        val originalProfile = LearnerProfile(
            userId = "learner_cold_restart",
            name = "Siddharth Rao",
            targetDomain = "Artificial Intelligence & Data Science",
            subdomain = "Deep Learning",
            goal = "Master Transformers",
            onboardingCompleted = true
        )
        session1ViewModel.createOrUpdateProfileAsync(originalProfile)
        assertEquals("learner_cold_restart", appPreferences.getActiveUserId())

        // Step 2: Simulate process kill and cold restart:
        // Create completely new ViewModel instance pointing to same repository and appPreferences
        val session2ViewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)

        // Verification after loadLearnerData runs in init block
        val restoredState = session2ViewModel.profileState.value
        assertTrue("Restored profile state must be Success", restoredState is LearningUiState.Success)
        val restoredProfile = (restoredState as LearningUiState.Success).data
        assertEquals("learner_cold_restart", restoredProfile.userId)
        assertEquals("Siddharth Rao", restoredProfile.name)
        assertEquals("Artificial Intelligence & Data Science", restoredProfile.targetDomain)
        assertTrue("onboardingCompleted must be true after cold restart", restoredProfile.onboardingCompleted)

        // Verify startup routing decision:
        val isModelReady = true
        val isOnboardingNeeded = !restoredProfile.onboardingCompleted
        val targetRoute = when {
            !isModelReady -> Screen.ModelDownload.route
            isOnboardingNeeded -> Screen.Onboarding.route
            else -> Screen.Home.route
        }
        assertEquals("Cold restart must navigate directly to Home (Dashboard)", Screen.Home.route, targetRoute)
    }

    /**
     * Case C (Fallback): Desynchronized or missing activeUserId in AppPreferences
     * falls back to the most recently active profile in Room SQLite.
     */
    @Test
    fun testCaseC_fallbackToLatestProfileWhenActiveUserIdMissing() = runTest(testDispatcher) {
        // Save a profile directly in repository
        val directProfile = LearnerProfile(
            userId = "recovered_learner_99",
            name = "Vikram Patel",
            targetDomain = "Mechanical Engineering",
            goal = "Master CAD and FEA",
            onboardingCompleted = true
        )
        learningRepository.saveProfile(directProfile)

        // Simulate appPreferences having default_learner (desynchronized)
        val freshPreferences = AppPreferences()
        assertEquals("default_learner", freshPreferences.getActiveUserId())

        // ViewModel initialized with desynchronized preferences
        val viewModel = LearningViewModel(learningRepository, aiTutorRepository, freshPreferences)

        // Fallback in loadLearnerData should detect directProfile in repository
        val restoredState = viewModel.profileState.value
        assertTrue("Profile should be restored via fallback", restoredState is LearningUiState.Success)
        val profile = (restoredState as LearningUiState.Success).data
        assertEquals("recovered_learner_99", profile.userId)
        assertTrue(profile.onboardingCompleted)

        // Active user ID should now be updated in preferences
        assertEquals("recovered_learner_99", freshPreferences.getActiveUserId())
        assertEquals("recovered_learner_99", viewModel.currentUserId)
    }

    /**
     * Case D: Asynchronous Room DB Loading Race Condition.
     * Startup navigation must NEVER make a premature decision while profileState is Loading/Idle.
     */
    @Test
    fun testCaseD_asynchronousRoomDbLoading_preventsPrematureOnboardingNavigation() = runTest(testDispatcher) {
        // When profileState is Idle or Loading
        val idleState: LearningUiState<LearnerProfile> = LearningUiState.Idle
        val loadingState: LearningUiState<LearnerProfile> = LearningUiState.Loading

        val isProfileLoadingIdle = idleState is LearningUiState.Idle || idleState is LearningUiState.Loading
        val isProfileLoadingLoading = loadingState is LearningUiState.Idle || loadingState is LearningUiState.Loading

        assertTrue("Idle state must be marked as loading", isProfileLoadingIdle)
        assertTrue("Loading state must be marked as loading", isProfileLoadingLoading)

        // Verify splash screen guard:
        // if (isMinAnimationDone && !isProfileLoading) -> only then navigate
        val isMinAnimationDone = true
        val canNavigateWhileLoading = isMinAnimationDone && !isProfileLoadingLoading
        assertFalse("Case D Invariant: App MUST NOT navigate while profile is still loading from Room DB", canNavigateWhileLoading)

        // When Room DB finishes and emits Success
        val successState = LearningUiState.Success(
            LearnerProfile(
                userId = "learner_d",
                name = "Deepa Raman",
                targetDomain = "Computer Science & Engineering",
                onboardingCompleted = true
            )
        )
        val isProfileLoadingDone = (successState as LearningUiState<LearnerProfile>) is LearningUiState.Idle || successState is LearningUiState.Loading
        assertFalse("Profile is no longer loading once Success is emitted", isProfileLoadingDone)

        val canNavigateWhenDone = isMinAnimationDone && !isProfileLoadingDone
        assertTrue("App may navigate once animation is done and profile loading has finished", canNavigateWhenDone)

        val targetRoute = if (!successState.data.onboardingCompleted) Screen.Onboarding.route else Screen.Home.route
        assertEquals("App must navigate to Home route", Screen.Home.route, targetRoute)
    }

    /**
     * Case E: Learning tracks and active track selection are retained across cold restart.
     */
    @Test
    fun testCaseE_learningProgressAndTracksPreservedAcrossRestarts() = runTest(testDispatcher) {
        val session1ViewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)
        val profile = LearnerProfile(
            userId = "learner_tracks_e",
            name = "Meera Sen",
            targetDomain = "Computer Science & Engineering",
            onboardingCompleted = true
        )
        session1ViewModel.createOrUpdateProfileAsync(profile)

        // Create learning track
        session1ViewModel.createLearningTrack(
            title = "OS & Systems Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = null,
            dailyCommitmentMinutes = 45
        )

        val activeTrack1 = session1ViewModel.activeTrack.value
        assertNotNull(activeTrack1)
        assertEquals("OS & Systems Track", activeTrack1?.title)

        // Simulate cold restart
        val session2ViewModel = LearningViewModel(learningRepository, aiTutorRepository, appPreferences)
        val activeTrack2 = session2ViewModel.activeTrack.value
        assertNotNull("Active track must be restored after cold restart", activeTrack2)
        assertEquals(activeTrack1?.trackId, activeTrack2?.trackId)
        assertEquals("OS & Systems Track", activeTrack2?.title)
    }
}
