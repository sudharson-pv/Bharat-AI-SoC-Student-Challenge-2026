package com.learnmate.ai.data

import android.content.Context
import android.content.SharedPreferences
import com.learnmate.ai.localization.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class AppPreferences(context: Context? = null) {
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("learnmate_preferences", Context.MODE_PRIVATE)

    private val inMemoryPrefs = mutableMapOf<String, Any?>()

    private val _currentLanguage = MutableStateFlow(loadLanguage())
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(loadThemePreference())
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private fun loadLanguage(): AppLanguage {
        val code = prefs?.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code)
            ?: (inMemoryPrefs[KEY_LANGUAGE] as? String)
            ?: AppLanguage.ENGLISH.code
        return AppLanguage.fromCode(code)
    }

    private fun loadThemePreference(): Boolean? {
        return if (prefs != null) {
            if (prefs.contains(KEY_DARK_THEME)) prefs.getBoolean(KEY_DARK_THEME, false) else null
        } else {
            inMemoryPrefs[KEY_DARK_THEME] as? Boolean
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
        inMemoryPrefs[KEY_LANGUAGE] = language.code
        _currentLanguage.value = language
    }

    fun setThemePreference(isDark: Boolean?) {
        val editor = prefs?.edit()
        if (isDark == null) {
            editor?.remove(KEY_DARK_THEME)
            inMemoryPrefs.remove(KEY_DARK_THEME)
        } else {
            editor?.putBoolean(KEY_DARK_THEME, isDark)
            inMemoryPrefs[KEY_DARK_THEME] = isDark
        }
        editor?.apply()
        _isDarkTheme.value = isDark
    }

    fun getActiveUserId(): String {
        return prefs?.getString(KEY_ACTIVE_USER_ID, "default_learner")
            ?: (inMemoryPrefs[KEY_ACTIVE_USER_ID] as? String)
            ?: "default_learner"
    }

    fun setActiveUserId(userId: String) {
        prefs?.edit()?.putString(KEY_ACTIVE_USER_ID, userId)?.apply()
        inMemoryPrefs[KEY_ACTIVE_USER_ID] = userId
    }

    /**
     * Gate 6.16.6: Persistent active model selection across application restarts.
     */
    fun getActiveModelId(): String {
        return prefs?.getString(KEY_ACTIVE_MODEL_ID, com.learnmate.ai.ai.ActiveModelManifest.MODEL_ID)
            ?: (inMemoryPrefs[KEY_ACTIVE_MODEL_ID] as? String)
            ?: com.learnmate.ai.ai.ActiveModelManifest.MODEL_ID
    }

    fun setActiveModelId(modelId: String) {
        prefs?.edit()?.putString(KEY_ACTIVE_MODEL_ID, modelId)?.apply()
        inMemoryPrefs[KEY_ACTIVE_MODEL_ID] = modelId
    }

    /**
     * Master Final Task Phase 3: Configurable production HTTPS model distribution endpoint.
     * Allows operator / institution to supply a custom Cloudflare R2, AWS S3, or CDN URL.
     */
    fun getCustomModelCdnUrl(): String? {
        return (prefs?.getString(KEY_CUSTOM_MODEL_CDN_URL, null)
            ?: (inMemoryPrefs[KEY_CUSTOM_MODEL_CDN_URL] as? String))?.takeIf { it.isNotBlank() }
    }

    fun setCustomModelCdnUrl(url: String?) {
        if (url.isNullOrBlank()) {
            prefs?.edit()?.remove(KEY_CUSTOM_MODEL_CDN_URL)?.apply()
            inMemoryPrefs.remove(KEY_CUSTOM_MODEL_CDN_URL)
        } else {
            prefs?.edit()?.putString(KEY_CUSTOM_MODEL_CDN_URL, url.trim())?.apply()
            inMemoryPrefs[KEY_CUSTOM_MODEL_CDN_URL] = url.trim()
        }
    }

    /**
     * Gate 6.16 P1.1: Unique installation ID for device-specific assignment personalization.
     * Persisted across app restarts, generated once per installation.
     */
    fun getInstallationId(): String {
        var id = prefs?.getString(KEY_INSTALLATION_ID, null)
            ?: (inMemoryPrefs[KEY_INSTALLATION_ID] as? String)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs?.edit()?.putString(KEY_INSTALLATION_ID, id)?.apply()
            inMemoryPrefs[KEY_INSTALLATION_ID] = id
        }
        return id
    }

    fun setInstallationIdForTesting(id: String) {
        prefs?.edit()?.putString(KEY_INSTALLATION_ID, id)?.apply()
        inMemoryPrefs[KEY_INSTALLATION_ID] = id
    }

    companion object {
        private const val KEY_LANGUAGE = "selected_language_code"
        private const val KEY_DARK_THEME = "dark_theme_override"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"
        private const val KEY_INSTALLATION_ID = "app_installation_id"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_CUSTOM_MODEL_CDN_URL = "custom_model_cdn_url"
    }
}

