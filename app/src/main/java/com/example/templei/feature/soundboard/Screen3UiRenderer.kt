package com.example.templei.feature.soundboard

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.example.templei.R

class Screen3UiRenderer(
    private val context: Context,
    private val statusText: TextView,
    private val loadingDetailText: TextView,
    private val loadingProgressBar: ProgressBar,
    private val folderSpinner: Spinner,
    private val previousFolderButton: Button,
    private val nextFolderButton: Button,
    private val assignmentTargetText: TextView,
    private val assignmentRow: LinearLayout,
    private val clipBrowserScroll: ScrollView,
    private val favoritesPad: GridLayout,
    private val browserToggleButton: Button,
    private val favoritesToggleButton: Button
) {
    fun renderState(state: SoundboardStateMachine.State) {
        statusText.text = when (state) {
            SoundboardStateMachine.State.NoRootSelected -> {
                loadingDetailText.text = context.getString(R.string.soundboard_loading_detail_idle)
                loadingProgressBar.isIndeterminate = false
                loadingProgressBar.max = 100
                loadingProgressBar.progress = 0
                context.getString(R.string.soundboard_state_no_root_selected)
            }

            is SoundboardStateMachine.State.Loading -> context.getString(R.string.soundboard_state_loading)

            is SoundboardStateMachine.State.Ready -> buildString {
                loadingDetailText.text = context.getString(
                    R.string.soundboard_loading_detail_ready,
                    state.playableCount,
                    state.cachedCount
                )
                loadingProgressBar.isIndeterminate = false
                loadingProgressBar.max = 100
                loadingProgressBar.progress = 100
                append(
                    context.getString(
                        R.string.soundboard_state_ready,
                        state.playableCount,
                        state.activeStreams,
                        state.cachedCount,
                        state.favorites.count { it.clipId != null }
                    )
                )
            }

            is SoundboardStateMachine.State.Playing -> buildString {
                loadingDetailText.text = context.getString(R.string.soundboard_loading_detail_playing, state.fileName)
                loadingProgressBar.isIndeterminate = false
                loadingProgressBar.max = 100
                loadingProgressBar.progress = 100
                append(context.getString(R.string.soundboard_state_playing, state.fileName, state.activeStreams))
            }

            is SoundboardStateMachine.State.Error -> {
                loadingDetailText.text = context.getString(R.string.soundboard_loading_detail_error)
                loadingProgressBar.isIndeterminate = false
                loadingProgressBar.max = 100
                loadingProgressBar.progress = 0
                context.getString(R.string.soundboard_state_error, state.message)
            }
        }
    }

    fun renderNoRootSelectedVisuals() {
        folderSpinner.isEnabled = false
        previousFolderButton.isEnabled = false
        nextFolderButton.isEnabled = false
        loadingProgressBar.isIndeterminate = false
        loadingProgressBar.max = 100
        loadingProgressBar.progress = 0
        loadingDetailText.text = context.getString(R.string.soundboard_loading_detail_idle)
    }

    fun renderIndexedEmptyVisuals() {
        folderSpinner.isEnabled = false
        previousFolderButton.isEnabled = false
        nextFolderButton.isEnabled = false
        loadingProgressBar.isIndeterminate = false
        loadingProgressBar.max = 100
        loadingProgressBar.progress = 100
        loadingDetailText.text = context.getString(R.string.soundboard_loading_detail_index_empty)
    }

    fun renderFolderHeader(hasMultipleFolders: Boolean, folderCount: Int) {
        folderSpinner.isEnabled = folderCount > 0
        previousFolderButton.isEnabled = hasMultipleFolders
        nextFolderButton.isEnabled = hasMultipleFolders
    }

    fun renderAssignmentTarget(selectedAssignmentSlotIndex: Int) {
        assignmentTargetText.text = context.getString(R.string.soundboard_assignment_target, selectedAssignmentSlotIndex + 1)
    }

    fun renderSectionVisibility(isBrowserCollapsed: Boolean, isFavoritesCollapsed: Boolean) {
        clipBrowserScroll.visibility = if (isBrowserCollapsed) View.GONE else View.VISIBLE
        favoritesPad.visibility = if (isFavoritesCollapsed) View.GONE else View.VISIBLE
        assignmentRow.visibility = if (isFavoritesCollapsed) View.GONE else View.VISIBLE

        browserToggleButton.text = context.getString(
            if (isBrowserCollapsed) R.string.soundboard_section_expand else R.string.soundboard_section_collapse
        )
        favoritesToggleButton.text = context.getString(
            if (isFavoritesCollapsed) R.string.soundboard_section_expand else R.string.soundboard_section_collapse
        )
    }
}
