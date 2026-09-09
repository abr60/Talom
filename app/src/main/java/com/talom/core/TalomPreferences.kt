package com.talom.core

import android.content.Context

/**
 * App-level prefs stored in `talom_preferences` (theme, pull schedule, font, identity).
 * AI keys stay in [com.talom.core.ai.AiPreferences].
 */
class TalomPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun pullHour(): Int = prefs.getInt(KEY_PULL_HOUR, DEFAULT_PULL_HOUR)

    fun pullMinute(): Int = prefs.getInt(KEY_PULL_MINUTE, DEFAULT_PULL_MINUTE)

    fun pullTimeLabel(): String = "%02d:%02d".format(pullHour(), pullMinute())

    fun setPullTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_PULL_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_PULL_MINUTE, minute.coerceIn(0, 59))
            .putString(KEY_PULL_TIME, "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
            .apply()
    }

    fun fontPreference(): String =
        prefs.getString(KEY_FONT, FONT_APP_DEFAULT) ?: FONT_APP_DEFAULT

    fun setFontPreference(value: String) {
        prefs.edit().putString(KEY_FONT, value).apply()
    }

    fun userIdentity(): String = prefs.getString(KEY_USER_IDENTITY, "") ?: ""

    fun setUserIdentity(value: String) {
        prefs.edit().putString(KEY_USER_IDENTITY, value.trim()).apply()
    }

    companion object {
        const val PREFS_NAME = "talom_preferences"
        const val KEY_PULL_TIME = "pull_time"
        const val KEY_PULL_HOUR = "pull_hour"
        const val KEY_PULL_MINUTE = "pull_minute"
        const val KEY_FONT = "font_preference"
        const val KEY_USER_IDENTITY = "user_identity"
        const val FONT_APP_DEFAULT = "app_default"
        const val FONT_SYSTEM_DEFAULT = "system_default"
        const val DEFAULT_PULL_HOUR = 22
        const val DEFAULT_PULL_MINUTE = 30
        const val DEFAULT_OLLAMA_ENDPOINT = "http://192.168.0.111:11434"
        const val FALLBACK_OLLAMA_ENDPOINT = "http://localhost:11434"
    }
}
