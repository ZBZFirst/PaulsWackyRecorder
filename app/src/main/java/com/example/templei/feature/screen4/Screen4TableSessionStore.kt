package com.example.templei.feature.screen4

import android.content.Context

/**
 * Persists the currently active Screen 4 table workspace selection so
 * long-form and short-form activities resolve the same workspace context.
 */
class Screen4TableSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveActiveWorkspaceId(workspaceId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_WORKSPACE_ID, workspaceId).apply()
    }

    fun loadActiveWorkspaceId(): Long? {
        if (!prefs.contains(KEY_ACTIVE_WORKSPACE_ID)) return null
        return prefs.getLong(KEY_ACTIVE_WORKSPACE_ID, NO_WORKSPACE)
            .takeIf { it > 0L }
    }

    fun clearActiveWorkspaceId() {
        prefs.edit().remove(KEY_ACTIVE_WORKSPACE_ID).apply()
    }

    companion object {
        private const val PREFS_NAME = "screen4_table_session_store"
        private const val KEY_ACTIVE_WORKSPACE_ID = "active_workspace_id"
        private const val NO_WORKSPACE = -1L
    }
}
