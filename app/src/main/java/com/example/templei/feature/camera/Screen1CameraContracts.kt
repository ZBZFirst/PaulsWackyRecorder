package com.example.templei.feature.camera

import android.net.Uri

/**
 * Screen 1 camera state and media definitions.
 */
enum class Screen1CameraFeedState {
    Stopped,
    Starting,
    Live,
    Error
}

enum class Screen1CameraCaptureState {
    Idle,
    Recording
}

enum class Screen1MediaType {
    Photo,
    Video
}

enum class Screen1StorageMode {
    Shared,
    Separate
}

enum class Screen1FolderTarget {
    Shared,
    Photo,
    Video
}

data class Screen1MediaEntry(
    val stableId: String,
    val type: Screen1MediaType,
    val displayName: String,
    val lastModifiedMillis: Long,
    val uri: Uri
)
