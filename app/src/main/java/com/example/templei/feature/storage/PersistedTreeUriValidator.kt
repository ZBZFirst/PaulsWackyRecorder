package com.example.templei.feature.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Validates persisted SAF tree URIs before feature code trusts them as live folders.
 */
class PersistedTreeUriValidator(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    fun normalize(uri: Uri?, requireWrite: Boolean): Uri? {
        val candidate = uri ?: return null
        if (!hasPersistedPermission(candidate, requireWrite)) {
            return null
        }
        val document = DocumentFile.fromTreeUri(appContext, candidate) ?: return null
        if (!document.exists() || !document.isDirectory || !document.canRead()) {
            return null
        }
        if (requireWrite && !document.canWrite()) {
            return null
        }
        return candidate
    }

    private fun hasPersistedPermission(uri: Uri, requireWrite: Boolean): Boolean {
        return contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri &&
                permission.isReadPermission &&
                (!requireWrite || permission.isWritePermission)
        }
    }
}
