package com.learnmate.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.learnmate.ai.localization.LocaleHelper
import com.learnmate.ai.navigation.LearnMateNavGraph
import com.learnmate.ai.ui.theme.LearnMateTheme
import com.learnmate.ai.viewmodel.LearningViewModel
import com.learnmate.ai.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as LearnMateApplication
        MainViewModel.Factory(app.appPreferences)
    }

    private val learningViewModel: LearningViewModel by viewModels {
        val app = application as LearnMateApplication
        LearningViewModel.Factory(app.learningRepository, app.aiTutorRepository, app.appPreferences, app.aiCoreEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val currentLanguage by viewModel.currentLanguage.collectAsState()
            val themePreference by viewModel.isDarkThemePreference.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = themePreference ?: isSystemDark

            val baseContext = LocalContext.current
            val localizedContext = remember(currentLanguage, baseContext) {
                LocaleHelper.updateLocale(baseContext, currentLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                LocalActivityResultRegistryOwner provides this
            ) {
                val initialScreen = intent?.getStringExtra("start_screen") ?: com.learnmate.ai.navigation.Screen.Splash.route
                val modelScenario = intent?.getStringExtra("model_scenario")
                LearnMateTheme(darkTheme = isDarkTheme) {
                    LearnMateNavGraph(
                        learningViewModel = learningViewModel,
                        currentLanguage = currentLanguage,
                        isDarkTheme = isDarkTheme,
                        onLanguageSelected = { lang ->
                            viewModel.selectLanguage(lang)
                        },
                        onToggleDarkTheme = { isDark ->
                            viewModel.setDarkTheme(isDark)
                        },
                        startDestination = initialScreen,
                        modelScenario = modelScenario
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handleImportIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImportIntent(intent)
    }

    private fun handleImportIntent(intent: android.content.Intent?) {
        val packPath = intent?.getStringExtra("pack_path")
        if (!packPath.isNullOrEmpty()) {
            val file = java.io.File(packPath)
            if (file.exists()) {
                val baseStorage = java.io.File(filesDir, "content_packs")
                learningViewModel.importContentPack(file, baseStorage) { success, msg ->
                    android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

