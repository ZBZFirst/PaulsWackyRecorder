package com.example.templei.feature.camera

import android.content.Context
import androidx.core.net.toUri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns Screen 1 app-local photo and video storage.
 */
class Screen1MediaRepository(
    private val context: Context
) {
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun createMediaFile(type: Screen1MediaType): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = when (type) {
            Screen1MediaType.Photo -> "${CameraFeature.photoFilePrefix}${timestamp}.jpg"
            Screen1MediaType.Video -> "${CameraFeature.videoFilePrefix}${timestamp}.mp4"
        }
        val directory = directoryFor(type)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return File(directory, fileName)
    }

    fun listMediaEntries(): List<Screen1MediaEntry> {
        return listOf(Screen1MediaType.Photo, Screen1MediaType.Video)
            .flatMap { type ->
                directoryFor(type)
                    .listFiles()
                    .orEmpty()
                    .filter { it.isFile }
                    .map { file ->
                        val modified = file.lastModified()
                        val typeLabel = when (type) {
                            Screen1MediaType.Photo -> "Photo"
                            Screen1MediaType.Video -> "Video"
                        }
                        Screen1MediaEntry(
                            file = file,
                            type = type,
                            displayName = "$typeLabel - ${timestampFormatter.format(Date(modified))}",
                            lastModifiedMillis = modified,
                            uri = file.toUri()
                        )
                    }
            }
            .sortedByDescending(Screen1MediaEntry::lastModifiedMillis)
    }

    private fun directoryFor(type: Screen1MediaType): File {
        val baseDirectory = context.getExternalFilesDir(null) ?: context.filesDir
        val childName = when (type) {
            Screen1MediaType.Photo -> CameraFeature.photoDirectoryName
            Screen1MediaType.Video -> CameraFeature.videoDirectoryName
        }
        return File(baseDirectory, childName)
    }
}
