package com.example.templei.feature.soundboard

import android.content.Context

/**
 * Publishes cross-screen library mutation signals so Screen 3 can rebuild its index on resume.
 */
class Screen3LibraryRefreshSignal(private val context: Context) {

    fun markChanged() {
        prefs().edit()
            .putLong(KEY_LAST_CHANGE_MS, System.currentTimeMillis())
            .apply()
    }

    fun lastChangedAtMs(): Long {
        return prefs().getLong(KEY_LAST_CHANGE_MS, 0L)
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_LAST_CHANGE_MS = "library_last_change_ms"
    }
}
