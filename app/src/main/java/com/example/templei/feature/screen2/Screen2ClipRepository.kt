package com.example.templei.feature.screen2

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns Screen 2 folder persistence and WAV export into the selected SAF tree.
 */
class Screen2ClipRepository(
    private val context: Context,
    private val store: Screen2RecorderStore = Screen2RecorderStore(context),
) {

    fun selectedFolderUri() = store.loadFolderUri()

    fun saveSelectedFolderUri(uri: android.net.Uri?) {
        store.saveFolderUri(uri)
    }

    fun selectedFolderLabel(): String? {
        val uri = selectedFolderUri() ?: return null
        val document = DocumentFile.fromTreeUri(context, uri)
        return document?.name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment
    }

    fun buildClipFileName(): String {
        return "screen2_clip_${TIMESTAMP_FORMATTER.format(Date())}.wav"
    }

    fun suggestedClipBaseName(): String {
        return "screen2_clip_${TIMESTAMP_FORMATTER.format(Date())}"
    }

    fun listWavFiles(): List<WavClip> {
        val folderUri = selectedFolderUri() ?: return emptyList()
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return folder.listFiles()
            .filter { it.isFile && (it.name?.endsWith(".wav", ignoreCase = true) == true) }
            .sortedBy { it.name?.lowercase().orEmpty() }
            .map { document ->
                WavClip(
                    displayName = document.name ?: "unknown.wav",
                    document = document,
                )
            }
    }

    @Throws(IOException::class)
    fun saveRecording(tempFile: File, fileName: String): SavedClip {
        val folderUri = selectedFolderUri() ?: throw IOException("No folder selected")
        val folder = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IOException("Selected folder is unavailable")
        val outputDocument = folder.createFile(MIME_TYPE_WAV, fileName)
            ?: throw IOException("Could not create target file")

        tempFile.inputStream().use { input ->
            context.contentResolver.openOutputStream(outputDocument.uri)?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Could not open target output stream")
        }

        return SavedClip(
            fileName = outputDocument.name ?: fileName,
            folderLabel = folder.name ?: selectedFolderLabel().orEmpty(),
        )
    }

    fun deleteWav(document: DocumentFile): Boolean {
        return document.delete()
    }

    data class SavedClip(
        val fileName: String,
        val folderLabel: String,
    )

    data class WavClip(
        val displayName: String,
        val document: DocumentFile,
    )

    private companion object {
        private const val MIME_TYPE_WAV = "audio/wav"
        private val TIMESTAMP_FORMATTER = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
}
