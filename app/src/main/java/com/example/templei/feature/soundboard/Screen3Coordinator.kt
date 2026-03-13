package com.example.templei.feature.soundboard

/**
 * Screen 3 orchestration shell.
 *
 * This coordinator is introduced as a phased extraction target so `Screen3Activity`
 * can progressively delegate soundboard behavior to feature-level logic.
 *
 * In this phase the coordinator provides:
 * - centralized `Screen3Intent` dispatch,
 * - a single `Screen3ViewState` source of truth,
 * - one-shot `Screen3Effect` emission hooks.
 */
class Screen3Coordinator(
    private val stateMachine: SoundboardStateMachine = SoundboardStateMachine(),
    private val favoriteSlotCount: Int = DEFAULT_FAVORITE_SLOT_COUNT
) {

    private var folderNames: List<String> = emptyList()
    private var selectedFolderIndex: Int = 0
    private var clipItems: List<Screen3ClipItem> = emptyList()
    private var favoriteItems: List<Screen3FavoriteSlotItem> = buildDefaultFavorites(favoriteSlotCount)

    private var selectedAssignmentSlotIndex: Int = 0
    private var isBrowserCollapsed: Boolean = true
    private var isFavoritesCollapsed: Boolean = true
    private var isControlsCollapsed: Boolean = true
    private var hasRootSelection: Boolean = false

    private var viewState: Screen3ViewState = buildViewState()

    /** Returns the current render model for Screen 3. */
    fun currentViewState(): Screen3ViewState = viewState

    /**
     * Dispatches a feature intent and returns the updated view state.
     *
     * A nullable effect is returned for one-off UI actions that should be handled
     * by the Activity exactly once.
     */
    fun dispatch(intent: Screen3Intent): DispatchResult {
        val effect = when (intent) {
            Screen3Intent.Initialize -> {
                if (hasRootSelection) null else Screen3Effect.OpenFolderPicker
            }

            Screen3Intent.PickFolder -> Screen3Effect.OpenFolderPicker

            is Screen3Intent.FolderPicked -> {
                hasRootSelection = intent.uri != null
                if (!hasRootSelection) {
                    stateMachine.markNoRootSelected()
                }
                null
            }

            Screen3Intent.RescanLibrary -> null

            is Screen3Intent.SelectFolderIndex -> {
                selectedFolderIndex = intent.index.coerceIn(0, (folderNames.size - 1).coerceAtLeast(0))
                null
            }

            Screen3Intent.PreviousFolder -> {
                if (folderNames.isNotEmpty()) {
                    selectedFolderIndex = (selectedFolderIndex - 1 + folderNames.size) % folderNames.size
                }
                null
            }

            Screen3Intent.NextFolder -> {
                if (folderNames.isNotEmpty()) {
                    selectedFolderIndex = (selectedFolderIndex + 1) % folderNames.size
                }
                null
            }

            is Screen3Intent.TapFavoriteSlot -> {
                selectedAssignmentSlotIndex = intent.slotIndex.coerceIn(0, favoriteSlotCount - 1)
                null
            }

            is Screen3Intent.LongPressFavoriteSlot -> {
                selectedAssignmentSlotIndex = intent.slotIndex.coerceIn(0, favoriteSlotCount - 1)
                Screen3Effect.ShowClearSlotDialog
            }

            Screen3Intent.ClearSelectedFavoriteSlot -> {
                favoriteItems = favoriteItems.map {
                    if (it.slotIndex == selectedAssignmentSlotIndex) {
                        it.copy(
                            label = "Slot ${it.slotIndex + 1}",
                            assignedClipId = null
                        )
                    } else {
                        it
                    }
                }
                null
            }

            is Screen3Intent.TapClip -> null

            is Screen3Intent.LongPressClip -> Screen3Effect.ShowAssignDialog(intent.clipId)

            is Screen3Intent.AssignClipToSlot -> {
                val chosenClip = clipItems.firstOrNull { it.id == intent.clipId }
                if (chosenClip == null) {
                    Screen3Effect.ShowToast("Clip no longer available")
                } else {
                    val slot = intent.slotIndex.coerceIn(0, favoriteSlotCount - 1)
                    selectedAssignmentSlotIndex = slot
                    favoriteItems = favoriteItems.map {
                        if (it.slotIndex == slot) {
                            it.copy(label = "Slot ${slot + 1}: ${chosenClip.label}", assignedClipId = chosenClip.id)
                        } else {
                            it
                        }
                    }
                    null
                }
            }

            is Screen3Intent.SetBrowserCollapsed -> {
                isBrowserCollapsed = intent.collapsed
                null
            }

            is Screen3Intent.SetFavoritesCollapsed -> {
                isFavoritesCollapsed = intent.collapsed
                null
            }

            is Screen3Intent.SetControlsCollapsed -> {
                isControlsCollapsed = intent.collapsed
                null
            }

            is Screen3Intent.ApplySettings -> null

            Screen3Intent.ResetSettings -> null
        }

        viewState = buildViewState()
        return DispatchResult(viewState = viewState, effect = effect)
    }

    /** Replaces folder names and clamps selected index to the available range. */
    fun setFolders(folderNames: List<String>, selectedIndex: Int = 0) {
        this.folderNames = folderNames
        this.selectedFolderIndex = selectedIndex.coerceIn(0, (folderNames.size - 1).coerceAtLeast(0))
        this.hasRootSelection = folderNames.isNotEmpty() || hasRootSelection
        viewState = buildViewState()
    }

    /** Replaces clip browser items and rebuilds view state. */
    fun setClipItems(clips: List<Screen3ClipItem>) {
        clipItems = clips
        viewState = buildViewState()
    }

    private fun buildViewState(): Screen3ViewState {
        return Screen3ViewState(
            state = stateMachine.currentState(),
            folderNames = folderNames,
            selectedFolderIndex = selectedFolderIndex,
            clipItems = clipItems,
            favorites = favoriteItems,
            selectedAssignmentSlotIndex = selectedAssignmentSlotIndex,
            isBrowserCollapsed = isBrowserCollapsed,
            isFavoritesCollapsed = isFavoritesCollapsed,
            isControlsCollapsed = isControlsCollapsed,
            hasRootSelection = hasRootSelection
        )
    }

    data class DispatchResult(
        val viewState: Screen3ViewState,
        val effect: Screen3Effect?
    )

    private companion object {
        private const val DEFAULT_FAVORITE_SLOT_COUNT = 9

        private fun buildDefaultFavorites(slotCount: Int): List<Screen3FavoriteSlotItem> {
            return (0 until slotCount).map { index ->
                Screen3FavoriteSlotItem(
                    slotIndex = index,
                    label = "Slot ${index + 1}",
                    assignedClipId = null
                )
            }
        }
    }
}
