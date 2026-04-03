package com.example.templei.feature.screen2

import android.content.Context
import android.net.Uri
import com.example.templei.feature.storage.PersistedTreeUriValidator

/**
 * Persists Screen 2 recording destination state.
 */
class Screen2RecorderStore(private val context: Context) {
    private val treeUriValidator = PersistedTreeUriValidator(context)

    fun saveFolderUri(uri: Uri?) {
        prefs().edit()
            .putString(KEY_FOLDER_URI, uri?.toString())
            .apply()
    }

    fun loadFolderUri(): Uri? {
        val raw = prefs().getString(KEY_FOLDER_URI, null) ?: return null
        val uri = Uri.parse(raw)
        val normalized = treeUriValidator.normalize(uri, requireWrite = true)
        if (normalized == null) {
            saveFolderUri(null)
        }
        return normalized
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        private const val PREFS_NAME = "screen2_recorder"
        private const val KEY_FOLDER_URI = "folder_uri"
    }
}
