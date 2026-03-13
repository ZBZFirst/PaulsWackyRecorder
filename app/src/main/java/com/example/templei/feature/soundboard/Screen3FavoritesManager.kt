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

    private val slotToClipId = mutableMapOf<Int, String>()
    private var selectedSlotIndex: Int = 0

    fun initialize() {
        selectedSlotIndex = settingsStore.loadSelectedAssignmentSlotIndex(favoriteSlotCount)
        slotToClipId.clear()
        slotToClipId.putAll(settingsStore.loadFavoriteAssignments(favoriteSlotCount))
    }

    fun selectedSlotIndex(): Int = selectedSlotIndex

    fun selectSlot(slotIndex: Int) {
        selectedSlotIndex = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        settingsStore.saveSelectedAssignmentSlotIndex(selectedSlotIndex, favoriteSlotCount)
    }

    fun assignClip(slotIndex: Int, clipId: String) {
        val clamped = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        slotToClipId[clamped] = clipId
        selectedSlotIndex = clamped
        persist()
    }

    fun assignClipToSelectedSlot(clipId: String) {
        slotToClipId[selectedSlotIndex] = clipId
        persist()
    }

    fun clearSelectedSlot() {
        slotToClipId.remove(selectedSlotIndex)
        persist()
    }

    fun clipIdForSlot(slotIndex: Int): String? {
        val clamped = slotIndex.coerceIn(0, favoriteSlotCount - 1)
        return slotToClipId[clamped]
    }

    fun assignedClipIds(): Set<String> = slotToClipId.values.toSet()

    fun exportAssignments(): Map<Int, String> = slotToClipId.toMap()

    fun buildSlotItems(clipNameById: Map<String, String>): List<Screen3FavoriteSlotItem> {
        return (0 until favoriteSlotCount).map { slotIndex ->
            val clipId = slotToClipId[slotIndex]
            val slotNumber = slotIndex + 1
            val label = when {
                clipId == null -> emptyLabelForSlot(slotNumber)
                clipNameById[clipId] != null -> assignedLabelForSlot(slotNumber, clipNameById.getValue(clipId))
                else -> savedUnknownLabelForSlot(slotNumber)
            }
            Screen3FavoriteSlotItem(
                slotIndex = slotIndex,
                label = label,
                assignedClipId = clipId
            )
        }
    }

    private fun persist() {
        settingsStore.saveFavoriteAssignments(slotToClipId)
        settingsStore.saveSelectedAssignmentSlotIndex(selectedSlotIndex, favoriteSlotCount)
    }
}
