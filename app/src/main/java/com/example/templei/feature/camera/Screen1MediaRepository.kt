package com.example.templei.feature.camera

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.storage.PersistedTreeUriValidator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns Screen 1 capture storage configuration and persisted media saves.
 */
class Screen1MediaRepository(
    private val context: Context
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val treeUriValidator = PersistedTreeUriValidator(appContext)

    fun createPendingMediaFile(type: Screen1MediaType): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = when (type) {
            Screen1MediaType.Photo -> "${CameraFeature.photoFilePrefix}${timestamp}.jpg"
            Screen1MediaType.Video -> "${CameraFeature.videoFilePrefix}${timestamp}.mp4"
        }
        return File(appContext.cacheDir, fileName)
    }

    fun storageMode(): Screen1StorageMode {
        return when (preferences.getString(KEY_STORAGE_MODE, Screen1StorageMode.Shared.name)) {
            Screen1StorageMode.Separate.name -> Screen1StorageMode.Separate
            else -> Screen1StorageMode.Shared
        }
    }

    fun setStorageMode(mode: Screen1StorageMode) {
        when (mode) {
            Screen1StorageMode.Shared -> {
                val currentShared = sharedFolderUri()
                if (currentShared == null) {
                    val seeded = photoFolderUri() ?: videoFolderUri()
                    if (seeded != null) {
                        preferences.edit().putString(KEY_SHARED_FOLDER_URI, seeded.toString()).apply()
                    }
                }
            }

            Screen1StorageMode.Separate -> {
                val shared = sharedFolderUri()
                if (shared != null) {
                    val editor = preferences.edit()
                    if (photoFolderUri() == null) {
                        editor.putString(KEY_PHOTO_FOLDER_URI, shared.toString())
                    }
                    if (videoFolderUri() == null) {
                        editor.putString(KEY_VIDEO_FOLDER_URI, shared.toString())
                    }
                    editor.apply()
                }
            }
        }
        preferences.edit().putString(KEY_STORAGE_MODE, mode.name).apply()
    }

    fun saveFolderUri(target: Screen1FolderTarget, uri: Uri) {
        val editor = preferences.edit()
        when (target) {
            Screen1FolderTarget.Shared -> {
                editor.putString(KEY_SHARED_FOLDER_URI, uri.toString())
            }

            Screen1FolderTarget.Photo -> {
                editor.putString(KEY_PHOTO_FOLDER_URI, uri.toString())
            }

            Screen1FolderTarget.Video -> {
                editor.putString(KEY_VIDEO_FOLDER_URI, uri.toString())
            }
        }
        editor.apply()
    }

    fun selectedFolderUri(target: Screen1FolderTarget): Uri? {
        return when (target) {
            Screen1FolderTarget.Shared -> sharedFolderUri()
            Screen1FolderTarget.Photo -> photoFolderUri()
            Screen1FolderTarget.Video -> videoFolderUri()
        }
    }

    fun selectedFolderLabel(target: Screen1FolderTarget): String? {
        return selectedFolderUri(target)?.let(::folderLabelForUri)
    }

    fun selectedFolderUri(type: Screen1MediaType): Uri? {
        return when (storageMode()) {
            Screen1StorageMode.Shared -> sharedFolderUri()
            Screen1StorageMode.Separate -> when (type) {
                Screen1MediaType.Photo -> photoFolderUri()
                Screen1MediaType.Video -> videoFolderUri()
            }
        }
    }

    fun selectedFolderLabel(type: Screen1MediaType): String? {
        return selectedFolderUri(type)?.let(::folderLabelForUri)
    }

    fun hasConfiguredDestination(type: Screen1MediaType): Boolean {
        return selectedFolderUri(type) != null
    }

    fun saveCapturedMedia(tempFile: File, type: Screen1MediaType): Screen1MediaEntry {
        val destinationUri = selectedFolderUri(type)
            ?: throw IllegalStateException("No destination folder selected for ${type.name.lowercase(Locale.US)} captures.")
        val folder = DocumentFile.fromTreeUri(appContext, destinationUri)
            ?.takeIf { it.canWrite() }
            ?: throw IllegalStateException("Selected destination folder is unavailable.")
        val displayName = tempFile.name
        val mimeType = when (type) {
            Screen1MediaType.Photo -> PHOTO_MIME_TYPE
            Screen1MediaType.Video -> VIDEO_MIME_TYPE
        }
        val document = folder.createFile(mimeType, displayName)
            ?: throw IllegalStateException("Unable to create destination file in the selected folder.")
        contentResolver.openOutputStream(document.uri, "w")?.use { output ->
            tempFile.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to open destination output stream.")
        return Screen1MediaEntry(
            stableId = document.uri.toString(),
            type = type,
            displayName = buildDisplayName(type, displayName, System.currentTimeMillis()),
            lastModifiedMillis = System.currentTimeMillis(),
            uri = document.uri,
        )
    }

    fun listMediaEntries(): List<Screen1MediaEntry> {
        return when (storageMode()) {
            Screen1StorageMode.Shared -> {
                sharedFolderUri()?.let(::listSharedFolderEntries).orEmpty()
            }

            Screen1StorageMode.Separate -> {
                sequenceOf(photoFolderUri(), videoFolderUri())
                    .filterNotNull()
                    .distinctBy(Uri::toString)
                    .flatMap(::listSharedFolderEntries)
                    .toList()
            }
        }.sortedByDescending(Screen1MediaEntry::lastModifiedMillis)
    }

    private fun listSharedFolderEntries(folderUri: Uri): List<Screen1MediaEntry> {
        val folder = DocumentFile.fromTreeUri(appContext, folderUri)
            ?.takeIf { it.canRead() }
            ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile }
            .mapNotNull { document ->
                val type = inferMediaType(document) ?: return@mapNotNull null
                val lastModified = document.lastModified().takeIf { it > 0L } ?: 0L
                val displayName = buildDisplayName(type, document.name.orEmpty(), lastModified)
                Screen1MediaEntry(
                    stableId = document.uri.toString(),
                    type = type,
                    displayName = displayName,
                    lastModifiedMillis = lastModified,
                    uri = document.uri,
                )
            }
    }

    private fun inferMediaType(document: DocumentFile): Screen1MediaType? {
        val name = document.name.orEmpty().lowercase(Locale.US)
        val mimeType = document.type.orEmpty().lowercase(Locale.US)
        return when {
            name.endsWith(".jpg") || name.endsWith(".jpeg") || mimeType.startsWith("image/") -> Screen1MediaType.Photo
            name.endsWith(".mp4") || mimeType.startsWith("video/") -> Screen1MediaType.Video
            else -> null
        }
    }

    private fun buildDisplayName(type: Screen1MediaType, fileName: String, modifiedMillis: Long): String {
        val typeLabel = when (type) {
            Screen1MediaType.Photo -> "Photo"
            Screen1MediaType.Video -> "Video"
        }
        val timestamp = timestampFormatter.format(Date(modifiedMillis.takeIf { it > 0L } ?: System.currentTimeMillis()))
        return "$typeLabel - $timestamp - $fileName"
    }

    private fun folderLabelForUri(uri: Uri): String {
        return DocumentFile.fromTreeUri(appContext, uri)?.name
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment
            ?: uri.toString()
    }

    private fun sharedFolderUri(): Uri? = loadPersistedFolderUri(KEY_SHARED_FOLDER_URI)
    private fun photoFolderUri(): Uri? = loadPersistedFolderUri(KEY_PHOTO_FOLDER_URI)
    private fun videoFolderUri(): Uri? = loadPersistedFolderUri(KEY_VIDEO_FOLDER_URI)

    private fun loadPersistedFolderUri(key: String): Uri? {
        val raw = preferences.getString(key, null) ?: return null
        val uri = Uri.parse(raw)
        val normalized = treeUriValidator.normalize(uri, requireWrite = true)
        if (normalized == null) {
            preferences.edit().remove(key).apply()
        }
        return normalized
    }

    private companion object {
        private const val PREFS_NAME = "screen1_media_repository"
        private const val KEY_STORAGE_MODE = "storage_mode"
        private const val KEY_SHARED_FOLDER_URI = "shared_folder_uri"
        private const val KEY_PHOTO_FOLDER_URI = "photo_folder_uri"
        private const val KEY_VIDEO_FOLDER_URI = "video_folder_uri"
        private const val PHOTO_MIME_TYPE = "image/jpeg"
        private const val VIDEO_MIME_TYPE = "video/mp4"
    }
}
