package com.learnmate.ai.localization

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    HINDI("hi", "Hindi", "हिन्दी"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

object LocaleHelper {
    fun updateLocale(context: Context, language: AppLanguage): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}

