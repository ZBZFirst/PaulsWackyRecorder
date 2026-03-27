package com.example.templei.feature.soundboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Screen3SettingsStore(private val context: Context) {

    data class FavoritePage(
        val pageId: String,
        val assignments: Map<Int, String>,
    )

    data class FavoritePagesState(
        val pages: List<FavoritePage>,
        val selectedPageId: String?,
    )

    fun saveFavoritePagesState(
        state: FavoritePagesState,
        favoriteSlotCount: Int,
    ) {
        val normalizedPages = state.pages
            .mapNotNull { page ->
                page.pageId.takeIf { it.isNotBlank() }?.let { pageId ->
                    FavoritePage(
                        pageId = pageId,
                        assignments = page.assignments.filterKeys { slotIndex ->
                            slotIndex in 0 until favoriteSlotCount
                        }.filterValues { clipId ->
                            clipId.isNotBlank()
                        },
                    )
                }
            }
            .ifEmpty { listOf(FavoritePage(DEFAULT_FAVORITE_PAGE_ID, emptyMap())) }

        val payload = JSONArray()
        normalizedPages.forEach { page ->
            val pageObject = JSONObject()
                .put("pageId", page.pageId)
            val assignmentsObject = JSONObject()
            page.assignments.forEach { (slotIndex, clipId) ->
                assignmentsObject.put(slotIndex.toString(), clipId)
            }
            pageObject.put("assignments", assignmentsObject)
            payload.put(pageObject)
        }

        val selectedPageId = state.selectedPageId
            ?.takeIf { pageId -> normalizedPages.any { it.pageId == pageId } }
            ?: normalizedPages.first().pageId

        prefs().edit()
            .putString(KEY_FAVORITE_PAGES, payload.toString())
            .putString(KEY_SELECTED_FAVORITE_PAGE_ID, selectedPageId)
            .apply()
    }

    fun loadFavoritePagesState(favoriteSlotCount: Int): FavoritePagesState {
        val raw = prefs().getString(KEY_FAVORITE_PAGES, null).orEmpty()
        val parsedPages = runCatching { JSONArray(raw) }.getOrNull()
            ?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val pageId = item.optString("pageId", "").takeIf { it.isNotBlank() } ?: continue
                        val assignmentsObject = item.optJSONObject("assignments") ?: JSONObject()
                        val assignments = buildMap {
                            assignmentsObject.keys().forEach { slotKey ->
                                val slotIndex = slotKey.toIntOrNull() ?: return@forEach
                                val clipId = assignmentsObject.optString(slotKey, "").takeIf { it.isNotBlank() } ?: return@forEach
                                if (slotIndex in 0 until favoriteSlotCount) {
                                    put(slotIndex, clipId)
                                }
                            }
                        }
                        add(FavoritePage(pageId = pageId, assignments = assignments))
                    }
                }
            }
            .orEmpty()

        val pages = if (parsedPages.isNotEmpty()) {
            parsedPages
        } else {
            listOf(
                FavoritePage(
                    pageId = DEFAULT_FAVORITE_PAGE_ID,
                    assignments = loadLegacyFavoriteAssignments(favoriteSlotCount),
                )
            )
        }

        val selectedPageId = prefs().getString(KEY_SELECTED_FAVORITE_PAGE_ID, null)
            ?.takeIf { pageId -> pages.any { it.pageId == pageId } }
            ?: pages.firstOrNull()?.pageId

        return FavoritePagesState(
            pages = pages,
            selectedPageId = selectedPageId,
        )
    }

    fun saveSelectedFavoritePageId(pageId: String?) {
        prefs().edit().putString(KEY_SELECTED_FAVORITE_PAGE_ID, pageId).apply()
    }

    fun loadSelectedFavoritePageId(): String? {
        return prefs().getString(KEY_SELECTED_FAVORITE_PAGE_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun saveSelectedAssignmentSlotIndex(index: Int, favoriteSlotCount: Int) {
        prefs().edit().putInt(KEY_SELECTED_ASSIGNMENT_SLOT, index.coerceIn(0, favoriteSlotCount - 1)).apply()
    }

    fun loadSelectedAssignmentSlotIndex(favoriteSlotCount: Int): Int {
        return prefs().getInt(KEY_SELECTED_ASSIGNMENT_SLOT, 0).coerceIn(0, favoriteSlotCount - 1)
    }

    fun loadConfig(defaults: Defaults): SoundboardConfig {
        val prefs = prefs()
        val policyName = prefs.getString(KEY_CACHE_POLICY, defaults.defaultCachePolicy.name) ?: defaults.defaultCachePolicy.name
        val cachePolicy = runCatching { CachePolicy.valueOf(policyName) }.getOrDefault(defaults.defaultCachePolicy)
        return SoundboardConfig(
            maxStreams = prefs.getInt(KEY_MAX_STREAMS, defaults.defaultMaxStreams).coerceIn(1, 8),
            cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, defaults.defaultCooldownMs).coerceIn(0L, 500L),
            maxCacheSize = prefs.getInt(KEY_MAX_CACHE_SIZE, defaults.defaultMaxCacheSize).coerceIn(8, 36),
            unloadOnFolderChange = prefs.getBoolean(KEY_UNLOAD_ON_FOLDER_CHANGE, defaults.defaultUnloadOnFolderChange),
            cachePolicy = cachePolicy
        )
    }

    fun saveConfig(config: SoundboardConfig) {
        prefs().edit()
            .putInt(KEY_MAX_STREAMS, config.maxStreams)
            .putLong(KEY_COOLDOWN_MS, config.cooldownMs)
            .putInt(KEY_MAX_CACHE_SIZE, config.maxCacheSize)
            .putBoolean(KEY_UNLOAD_ON_FOLDER_CHANGE, config.unloadOnFolderChange)
            .putString(KEY_CACHE_POLICY, config.cachePolicy.name)
            .apply()
    }

    data class Defaults(
        val defaultMaxStreams: Int,
        val defaultCooldownMs: Long,
        val defaultMaxCacheSize: Int,
        val defaultUnloadOnFolderChange: Boolean,
        val defaultCachePolicy: CachePolicy
    )

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadLegacyFavoriteAssignments(favoriteSlotCount: Int): Map<Int, String> {
        val raw = prefs().getString(KEY_FAVORITE_ASSIGNMENTS, null).orEmpty()
        if (raw.isBlank()) return emptyMap()

        return raw.split("||")
            .mapNotNull { chunk ->
                val parts = chunk.split("::", limit = 2)
                val index = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val clipId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (index !in 0 until favoriteSlotCount) return@mapNotNull null
                index to clipId
            }
            .toMap()
    }

    companion object {
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_MAX_STREAMS = "max_streams"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"
        private const val KEY_UNLOAD_ON_FOLDER_CHANGE = "unload_on_folder_change"
        private const val KEY_CACHE_POLICY = "cache_policy"
        private const val KEY_FAVORITE_PAGES = "favorite_pages"
        private const val KEY_SELECTED_FAVORITE_PAGE_ID = "selected_favorite_page_id"
        private const val KEY_FAVORITE_ASSIGNMENTS = "favorite_assignments"
        private const val KEY_SELECTED_ASSIGNMENT_SLOT = "selected_assignment_slot"
        const val DEFAULT_FAVORITE_PAGE_ID = "favorite_page_1"
    }
}
