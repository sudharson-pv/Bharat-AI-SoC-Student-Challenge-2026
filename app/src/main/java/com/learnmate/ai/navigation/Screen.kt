package com.learnmate.ai.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object DiagnosticAssessment : Screen("diagnostic_assessment")
    data object SkillGapDashboard : Screen("skill_gap_dashboard")
    data object Roadmap : Screen("roadmap")
    data object DailyStudyPlan : Screen("daily_study_plan")
    data object AiTutor : Screen("ai_tutor")
    data object LanguageSelection : Screen("language_selection")
    data object ModelDownload : Screen("model_download")
    data object ModelDiagnostics : Screen("model_diagnostics")
    data object Settings : Screen("settings")
    data object MultiTrackDashboard : Screen("multi_track_dashboard")
    data object CurriculumBrowser : Screen("curriculum_browser")
    data object UniversalProfile : Screen("universal_profile")
    data object ContentLibrary : Screen("content_library")
    data object LearningModule : Screen("learning_module/{nodeId}") {
        fun createRoute(nodeId: String): String = "learning_module/$nodeId"
    }
}
