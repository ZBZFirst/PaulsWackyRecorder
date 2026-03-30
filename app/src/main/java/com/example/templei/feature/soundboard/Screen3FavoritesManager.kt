package com.example.templei.feature.soundboard

/**
 * Manages Screen 3 favorite slot assignments and selected slot behavior.
 *
 * This helper keeps favorites-domain state/persistence details out of Activity code
 * so the soundboard can be hosted by other screens with minimal glue.
 */
class Screen3FavoritesManager(
    private val settingsStore: Screen3SettingsStore,
    private val favoriteSlotCount: Int,
    private val emptyLabelForSlot: (slotNumber: Int) -> String,
    private val assignedLabelForSlot: (slotNumber: Int, clipDisplayName: String) -> String,
    private val savedUnknownLabelForSlot: (slotNumber: Int) -> String
) {

    private val pages = mutableListOf<FavoritePageModel>()
    private var selectedSlotIndex: Int = 0
    private var currentPageId: String = Screen3SettingsStore.DEFAULT_FAVORITE_PAGE_ID

    fun initialize() {
        val favoritePagesState = settingsStore.loadFavoritePagesState(favoriteSlotCount)
        selectedSlotIndex = settingsStore.loadSelectedAssignmentSlotIndex(favoriteSlotCount)
        pages.clear()
        favoritePagesState.pages.forEach { page ->
            pages += FavoritePageModel(
                pageId = page.pageId,
                assignments = page.assignments.toMutableMap(),
            )
        }
        if (pages.isEmpty()) {
            pages += FavoritePageModel(
                pageId = Screen3SettingsStore.DEFAULT_FAVORITE_PAGE_ID,
                assignments = mutableMapOf(),
            )
        }
        currentPageId = favoritePagesState.selectedPageId
            ?.takeIf { selectedId -> pages.any { it.pageId == selectedId } }
            ?: pages.first().pageId
        settingsStore.saveSelectedFavoritePageId(currentPageId)
    }

    fun selectedSlotIndex(): Int = selectedSlotIndex
    fun currentPageIndex(): Int = currentPageIndexInternal()
    fun pageCount(): Int = pages.size
    fun currentPageId(): String = currentPageId

    fun selectSlot(slotIndex: Int) {
        selectedSlotIndex = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        settingsStore.saveSelectedAssignmentSlotIndex(selectedSlotIndex, favoriteSlotCount)
    }

    fun moveToPreviousPage(): Boolean {
        val currentIndex = currentPageIndexInternal()
        if (currentIndex <= 0) return false
        currentPageId = pages[currentIndex - 1].pageId
        persist()
        return true
    }

    fun moveToNextPage(): Boolean {
        val currentIndex = currentPageIndexInternal()
        if (currentIndex >= pages.lastIndex) return false
        currentPageId = pages[currentIndex + 1].pageId
        persist()
        return true
    }

    fun addPage() {
        val newPage = FavoritePageModel(
            pageId = buildPageId(),
            assignments = mutableMapOf(),
        )
        pages += newPage
        currentPageId = newPage.pageId
        persist()
    }

    fun removeCurrentPage(): Boolean {
        if (pages.size <= 1) return false
        val currentIndex = currentPageIndexInternal()
        pages.removeAt(currentIndex)
        currentPageId = pages[currentIndex.coerceAtMost(pages.lastIndex)].pageId
        persist()
        return true
    }

    fun assignClip(slotIndex: Int, clipId: String) {
        val clamped = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        currentPageAssignments()[clamped] = clipId
        selectedSlotIndex = clamped
        persist()
    }

    fun assignClipToSelectedSlot(clipId: String) {
        currentPageAssignments()[selectedSlotIndex] = clipId
        persist()
    }

    fun clearSelectedSlot() {
        currentPageAssignments().remove(selectedSlotIndex)
        persist()
    }

    fun clearCurrentPageAssignments() {
        currentPageAssignments().clear()
        persist()
    }

    fun clipIdForSlot(slotIndex: Int): String? {
        val clamped = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        return currentPageAssignments()[clamped]
    }

    fun assignedClipIds(): Set<String> = pages.flatMap { it.assignments.values }.toSet()

    fun exportAssignments(): Map<Int, String> = currentPageAssignments().toMap()

    fun buildSlotItems(clipNameById: Map<String, String>): List<Screen3FavoriteSlotItem> {
        return (0 until favoriteSlotCount).map { slotIndex ->
            val clipId = currentPageAssignments()[slotIndex]
            val slotNumber = slotIndex + 1
            val isMissingSource = clipId != null && clipNameById[clipId] == null
            val label = when {
                clipId == null -> emptyLabelForSlot(slotNumber)
                clipNameById[clipId] != null -> assignedLabelForSlot(slotNumber, clipNameById.getValue(clipId))
                else -> savedUnknownLabelForSlot(slotNumber)
            }
            Screen3FavoriteSlotItem(
                slotIndex = slotIndex,
                label = label,
                assignedClipId = clipId,
                isMissingSource = isMissingSource,
            )
        }
    }

    fun saveCurrentPagePreset(name: String): Screen3SettingsStore.SavedFavoritePagePreset {
        val preset = Screen3SettingsStore.SavedFavoritePagePreset(
            presetId = "favorite_preset_${System.currentTimeMillis()}",
            name = name.ifBlank { "Page ${currentPageIndex() + 1}" },
            assignments = currentPageAssignments().toMap(),
        )
        settingsStore.saveFavoritePagePreset(preset, favoriteSlotCount)
        return preset
    }

    fun savedPagePresets(): List<Screen3SettingsStore.SavedFavoritePagePreset> {
        return settingsStore.loadFavoritePagePresets(favoriteSlotCount)
    }

    fun loadPagePreset(presetId: String): Screen3SettingsStore.SavedFavoritePagePreset? {
        val preset = savedPagePresets().firstOrNull { it.presetId == presetId } ?: return null
        currentPageAssignments().clear()
        currentPageAssignments().putAll(preset.assignments)
        persist()
        return preset
    }

    fun deletePagePreset(presetId: String): Screen3SettingsStore.SavedFavoritePagePreset? {
        val preset = savedPagePresets().firstOrNull { it.presetId == presetId } ?: return null
        if (!settingsStore.deleteFavoritePagePreset(presetId, favoriteSlotCount)) {
            return null
        }
        return preset
    }

    private fun persist() {
        settingsStore.saveFavoritePagesState(
            state = Screen3SettingsStore.FavoritePagesState(
                pages = pages.map { page ->
                    Screen3SettingsStore.FavoritePage(
                        pageId = page.pageId,
                        assignments = page.assignments.toMap(),
                    )
                },
                selectedPageId = currentPageId,
            ),
            favoriteSlotCount = favoriteSlotCount,
        )
        settingsStore.saveSelectedAssignmentSlotIndex(selectedSlotIndex, favoriteSlotCount)
        settingsStore.saveSelectedFavoritePageId(currentPageId)
    }

    private fun currentPageAssignments(): MutableMap<Int, String> {
        return pages[currentPageIndexInternal()].assignments
    }

    private fun currentPageIndexInternal(): Int {
        return pages.indexOfFirst { it.pageId == currentPageId }
            .takeIf { it >= 0 }
            ?: 0
    }

    private fun buildPageId(): String {
        return "favorite_page_${System.currentTimeMillis()}_${pages.size + 1}"
    }

    private data class FavoritePageModel(
        val pageId: String,
        val assignments: MutableMap<Int, String>,
    )
}
