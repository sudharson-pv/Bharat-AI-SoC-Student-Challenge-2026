package com.learnmate.ai.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.learnmate.ai.ai.ModelDownloadEngine
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.localization.AppLanguage
import com.learnmate.ai.ui.screens.*
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import java.io.File

@Composable
fun LearnMateNavGraph(
    navController: NavHostController = rememberNavController(),
    learningViewModel: LearningViewModel,
    currentLanguage: AppLanguage,
    isDarkTheme: Boolean,
    onLanguageSelected: (AppLanguage) -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    startDestination: String = Screen.Splash.route,
    modelScenario: String? = null
) {
    val profileState by learningViewModel.profileState.collectAsState()
    val profile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            val context = LocalContext.current
            val isModelReady = remember {
                val appPrefs = com.learnmate.ai.data.AppPreferences(context)
                val activeModelId = appPrefs.getActiveModelId()
                val assetMgr = com.learnmate.ai.ai.ModelAssetManager(context.filesDir)
                if (assetMgr.isModelInstalled(activeModelId)) {
                    val tok = assetMgr.getTokenizerFile(activeModelId)
                    val expectedTokSha = com.learnmate.ai.ai.ModelCatalog.getEntryById(activeModelId)?.tokenizerSha256
                        ?: com.learnmate.ai.ai.ActiveModelManifest.EXPECTED_TOKENIZER_SHA256
                    com.learnmate.ai.ai.ModelReadinessAuditor.calculateSha256(tok).equals(expectedTokSha, ignoreCase = true)
                } else {
                    false
                }
            }
            val isProfileLoading = profileState is LearningUiState.Idle || profileState is LearningUiState.Loading
            var hasNavigated by remember { androidx.compose.runtime.mutableStateOf(false) }

            SplashScreen(
                isProfileLoading = isProfileLoading,
                onAnimationEnd = {
                    if (hasNavigated) return@SplashScreen
                    val currentProfile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data
                    val isOnboardingNeeded = currentProfile == null || !currentProfile.onboardingCompleted
                    val targetRoute = when {
                        !isModelReady -> Screen.ModelDownload.route
                        isOnboardingNeeded -> Screen.Onboarding.route
                        else -> Screen.Home.route
                    }
                    hasNavigated = true
                    navController.navigate(targetRoute) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                learningViewModel = learningViewModel,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onNavigateBack = { navController.popBackStack() },
                onOnboardingComplete = {
                    navController.navigate(Screen.DiagnosticAssessment.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ModelDownload.route) {
            val context = LocalContext.current
            val downloadEngine = remember {
                val prefs = com.learnmate.ai.data.AppPreferences(context)
                val engine = ModelDownloadEngine(File(context.filesDir, "models")).apply {
                    customCdnBaseUrl = prefs.getCustomModelCdnUrl()
                }
                if (!modelScenario.isNullOrEmpty()) {
                    engine.applyScenario(modelScenario)
                }
                engine
            }
            ModelDownloadScreen(
                downloadEngine = downloadEngine,
                learningViewModel = learningViewModel,
                onNavigateToDiagnostics = { navController.navigate(Screen.ModelDiagnostics.route) },
                onModelReady = {
                    val currentProfile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data
                    val target = if (currentProfile == null || !currentProfile.onboardingCompleted) Screen.Onboarding.route else Screen.Home.route
                    navController.navigate(target) {
                        popUpTo(Screen.ModelDownload.route) { inclusive = true }
                    }
                },
                onSkipOrContinue = {
                    val currentProfile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data
                    val target = if (currentProfile == null || !currentProfile.onboardingCompleted) Screen.Onboarding.route else Screen.Home.route
                    navController.navigate(target) {
                        popUpTo(Screen.ModelDownload.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                learningViewModel = learningViewModel,
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) },
                onNavigateToAssessment = { navController.navigate(Screen.DiagnosticAssessment.route) },
                onNavigateToRoadmap = { navController.navigate(Screen.Roadmap.route) },
                onNavigateToStudyPlan = { navController.navigate(Screen.DailyStudyPlan.route) },
                onNavigateToSkillGap = { navController.navigate(Screen.SkillGapDashboard.route) },
                onNavigateToAiTutor = { navController.navigate(Screen.AiTutor.route) },
                onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToMultiTrack = { navController.navigate(Screen.MultiTrackDashboard.route) },
                onNavigateToCurriculum = { navController.navigate(Screen.CurriculumBrowser.route) },
                onNavigateToUniversalProfile = { navController.navigate(Screen.UniversalProfile.route) },
                onNavigateToContentLibrary = { navController.navigate(Screen.ContentLibrary.route) }
            )
        }

        composable(Screen.ContentLibrary.route) {
            ContentLibraryScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCurriculum = { _ ->
                    navController.navigate(Screen.CurriculumBrowser.route)
                }
            )
        }

        composable(Screen.MultiTrackDashboard.route) {
            MultiTrackDashboardScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCurriculum = { navController.navigate(Screen.CurriculumBrowser.route) },
                onNavigateToRoadmap = { navController.navigate(Screen.Roadmap.route) },
                onNavigateToContentLibrary = { navController.navigate(Screen.ContentLibrary.route) }
            )
        }

        composable(Screen.CurriculumBrowser.route) {
            CurriculumBrowserScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrackDashboard = { navController.navigate(Screen.MultiTrackDashboard.route) },
                onNavigateToContentLibrary = { navController.navigate(Screen.ContentLibrary.route) },
                onNavigateToLearningModule = { nodeId ->
                    navController.navigate(Screen.LearningModule.createRoute(nodeId))
                }
            )
        }

        composable(
            route = Screen.LearningModule.route,
            arguments = listOf(
                androidx.navigation.navArgument("nodeId") {
                    type = androidx.navigation.NavType.StringType
                }
            )
        ) { backStackEntry ->
            val nodeId = backStackEntry.arguments?.getString("nodeId") ?: "cbse_sci10_ch01"
            LearningModuleScreen(
                nodeId = nodeId,
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTopic = { nextNodeId ->
                    navController.navigate(Screen.LearningModule.createRoute(nextNodeId)) {
                        popUpTo(Screen.LearningModule.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.UniversalProfile.route) {
            UniversalProfileScreen(
                learningViewModel = learningViewModel,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAssessment = {
                    navController.navigate(Screen.DiagnosticAssessment.route)
                }
            )
        }

        composable(Screen.DiagnosticAssessment.route) {
            DiagnosticAssessmentScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onAssessmentComplete = {
                    navController.navigate(Screen.Roadmap.route) {
                        popUpTo(Screen.DiagnosticAssessment.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.SkillGapDashboard.route) {
            SkillGapDashboardScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRoadmap = { navController.navigate(Screen.Roadmap.route) },
                onNavigateToStudyPlan = { navController.navigate(Screen.DailyStudyPlan.route) }
            )
        }

        composable(Screen.Roadmap.route) {
            RoadmapScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route)
                    }
                },
                onNavigateToTutorWithTopic = { _ ->
                    navController.navigate(Screen.AiTutor.route)
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Roadmap.route) { inclusive = true }
                    }
                },
                onNavigateToAssessment = {
                    navController.navigate(Screen.DiagnosticAssessment.route)
                }
            )
        }

        composable(Screen.DailyStudyPlan.route) {
            DailyStudyPlanScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAssessment = { navController.navigate(Screen.DiagnosticAssessment.route) }
            )
        }

        composable(Screen.AiTutor.route) {
            AiTutorScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LanguageSelection.route) {
            LanguageSelectionScreen(
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                learningViewModel = learningViewModel,
                currentLanguage = currentLanguage,
                isDarkTheme = isDarkTheme,
                onToggleDarkTheme = onToggleDarkTheme,
                onNavigateToLanguage = { navController.navigate(Screen.LanguageSelection.route) },
                onNavigateToModelDownload = { navController.navigate(Screen.ModelDownload.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.ModelDiagnostics.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ModelDiagnostics.route) {
            com.learnmate.ai.ui.screens.ModelDiagnosticsScreen(
                learningViewModel = learningViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
