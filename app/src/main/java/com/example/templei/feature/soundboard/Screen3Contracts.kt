package com.example.templei.feature.soundboard

import android.net.Uri

/**
 * Contracts for Screen 3 soundboard orchestration.
 *
 * Intent: keep Activity code focused on Android UI/lifecycle wiring while feature
 * logic is handled by coordinator/helper modules.
 */
sealed interface Screen3Intent {
    data object Initialize : Screen3Intent
    data object PickFolder : Screen3Intent
    data class FolderPicked(val uri: Uri?) : Screen3Intent
    data object RescanLibrary : Screen3Intent

    data class SelectFolderIndex(val index: Int) : Screen3Intent
    data object PreviousFolder : Screen3Intent
    data object NextFolder : Screen3Intent

    data class TapFavoriteSlot(val slotIndex: Int) : Screen3Intent
    data class LongPressFavoriteSlot(val slotIndex: Int) : Screen3Intent
    data object ClearSelectedFavoriteSlot : Screen3Intent

    data class TapClip(val clipId: String) : Screen3Intent
    data class LongPressClip(val clipId: String) : Screen3Intent
    data class AssignClipToSlot(val clipId: String, val slotIndex: Int) : Screen3Intent

    data class SetBrowserCollapsed(val collapsed: Boolean) : Screen3Intent
    data class SetFavoritesCollapsed(val collapsed: Boolean) : Screen3Intent
    data class SetControlsCollapsed(val collapsed: Boolean) : Screen3Intent

    data class ApplySettings(val config: SoundboardConfig) : Screen3Intent
    data object ResetSettings : Screen3Intent
}

/** Renderable clip row model for the folder clip browser list. */
data class Screen3ClipItem(
    val id: String,
    val label: String,
    val playable: Boolean
)

/** Renderable favorite slot model for the favorites pad section. */
data class Screen3FavoriteSlotItem(
    val slotIndex: Int,
    val label: String,
    val assignedClipId: String?,
    val isMissingSource: Boolean = false,
)

/**
 * Full render model for Screen 3 UI.
 *
 * Keep this model view-focused and avoid leaking mutable implementation state.
 */
data class Screen3ViewState(
    val state: SoundboardStateMachine.State,
    val folderNames: List<String>,
    val selectedFolderIndex: Int,
    val clipItems: List<Screen3ClipItem>,
    val favorites: List<Screen3FavoriteSlotItem>,
    val selectedAssignmentSlotIndex: Int,
    val isBrowserCollapsed: Boolean,
    val isFavoritesCollapsed: Boolean,
    val isControlsCollapsed: Boolean,
    val hasRootSelection: Boolean
)

/**
 * One-off UI effects emitted by soundboard orchestration.
 *
 * Effects should be consumed once and not persisted as long-lived view state.
 */
sealed interface Screen3Effect {
    data class ShowToast(val message: String) : Screen3Effect
    data class ShowAssignDialog(val clipId: String) : Screen3Effect
    data object ShowClearSlotDialog : Screen3Effect
    data object OpenFolderPicker : Screen3Effect
    data class NavigateToMainWithError(val message: String) : Screen3Effect
}
