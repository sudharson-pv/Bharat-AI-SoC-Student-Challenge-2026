package com.learnmate.ai.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.learnmate.ai.data.AppPreferences
import com.learnmate.ai.localization.AppLanguage
import kotlinx.coroutines.flow.StateFlow

class MainViewModel(private val appPreferences: AppPreferences) : ViewModel() {

    val currentLanguage: StateFlow<AppLanguage> = appPreferences.currentLanguage
    val isDarkThemePreference: StateFlow<Boolean?> = appPreferences.isDarkTheme

    fun selectLanguage(language: AppLanguage) {
        appPreferences.setLanguage(language)
    }

    fun setDarkTheme(isDark: Boolean) {
        appPreferences.setThemePreference(isDark)
    }

    class Factory(private val appPreferences: AppPreferences) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(appPreferences) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

