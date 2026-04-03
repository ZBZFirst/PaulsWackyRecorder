package com.example.templei.feature.mainhub

import android.content.Context

/**
 * Persists whether the Main hub should keep showing the first-open intro card.
 */
class MainHubIntroStore(
    private val context: Context,
) {
    fun loadState(): MainHubIntroState {
        val prefs = prefs()
        return MainHubIntroState(
            introDismissed = prefs.getBoolean(KEY_INTRO_DISMISSED, false),
            setupStarted = prefs.getBoolean(KEY_SETUP_STARTED, false),
        )
    }

    fun markSetupStarted() {
        prefs().edit()
            .putBoolean(KEY_SETUP_STARTED, true)
            .apply()
    }

    fun dismissIntro() {
        prefs().edit()
            .putBoolean(KEY_INTRO_DISMISSED, true)
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        private const val PREFS_NAME = "main_hub_intro"
        private const val KEY_INTRO_DISMISSED = "intro_dismissed"
        private const val KEY_SETUP_STARTED = "setup_started"
    }
}

data class MainHubIntroState(
    val introDismissed: Boolean,
    val setupStarted: Boolean,
)
