package com.example.templei.feature.screen4

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the Screen 4 working session plus named bar and song snapshots.
 */
class Screen4SequenceStore(private val context: Context) {

    fun loadWorkingState(): Screen4WorkingSequenceState? {
        val raw = prefs().getString(KEY_WORKING_STATE, null).orEmpty()
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return parsed.toWorkingState()
    }

    fun saveWorkingState(state: Screen4WorkingSequenceState) {
        prefs().edit()
            .putString(KEY_WORKING_STATE, state.toJson().toString())
            .apply()
    }

    fun loadSavedBars(): List<Screen4SavedBarSnapshot> {
        val raw = prefs().getString(KEY_SAVED_BARS, null).orEmpty()
        val parsed = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until parsed.length()) {
                val item = parsed.optJSONObject(index) ?: continue
                item.toSavedBarSnapshot()?.let(::add)
            }
        }
    }

    fun saveBar(snapshot: Screen4SavedBarSnapshot): Boolean {
        val existing = loadSavedBars()
        val replacedExisting = existing.any { it.name.equals(snapshot.name, ignoreCase = true) }
        val updated = listOf(snapshot) + existing.filterNot { it.name.equals(snapshot.name, ignoreCase = true) }
        prefs().edit()
            .putString(KEY_SAVED_BARS, updated.toBarJsonArray().toString())
            .apply()
        return replacedExisting
    }

    fun loadSavedSongs(): List<Screen4SavedSongSnapshot> {
        val raw = prefs().getString(KEY_SAVED_SONGS, null).orEmpty()
        val parsed = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until parsed.length()) {
                val item = parsed.optJSONObject(index) ?: continue
                item.toSavedSongSnapshot()?.let(::add)
            }
        }
    }

    fun saveSong(snapshot: Screen4SavedSongSnapshot): Boolean {
        val existing = loadSavedSongs()
        val replacedExisting = existing.any { it.name.equals(snapshot.name, ignoreCase = true) }
        val updated = listOf(snapshot) + existing.filterNot { it.name.equals(snapshot.name, ignoreCase = true) }
        prefs().edit()
            .putString(KEY_SAVED_SONGS, updated.toSongJsonArray().toString())
            .apply()
        return replacedExisting
    }

    private fun Screen4WorkingSequenceState.toJson(): JSONObject {
        return JSONObject()
            .put("bpm", bpm)
            .put("displayedFavoritePageId", displayedFavoritePageId)
            .put("playBars", playBars.toPlayBarsJson())
    }

    private fun JSONObject.toWorkingState(): Screen4WorkingSequenceState {
        return Screen4WorkingSequenceState(
            bpm = optInt("bpm", Screen4Coordinator.DEFAULT_BPM),
            displayedFavoritePageId = optString("displayedFavoritePageId", "").takeIf { it.isNotBlank() },
            playBars = optJSONArray("playBars").toPlayBars(),
        )
    }

    private fun JSONObject.toSavedBarSnapshot(): Screen4SavedBarSnapshot? {
        val name = optString("name", "").trim().takeIf { it.isNotBlank() } ?: return null
        return Screen4SavedBarSnapshot(
            name = name,
            savedAtMs = optLong("savedAtMs", 0L),
            steps = optJSONArray("steps").toFavoriteReferenceList(),
        )
    }

    private fun JSONObject.toSavedSongSnapshot(): Screen4SavedSongSnapshot? {
        val name = optString("name", "").trim().takeIf { it.isNotBlank() } ?: return null
        return Screen4SavedSongSnapshot(
            name = name,
            savedAtMs = optLong("savedAtMs", 0L),
            bpm = optInt("bpm", Screen4Coordinator.DEFAULT_BPM),
            displayedFavoritePageId = optString("displayedFavoritePageId", "").takeIf { it.isNotBlank() },
            playBars = optJSONArray("playBars").toPlayBars(),
        )
    }

    private fun List<Screen4SavedBarSnapshot>.toBarJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { snapshot ->
                array.put(
                    JSONObject()
                        .put("name", snapshot.name)
                        .put("savedAtMs", snapshot.savedAtMs)
                        .put("steps", snapshot.steps.toFavoriteReferenceJson()),
                )
            }
        }
    }

    private fun List<Screen4SavedSongSnapshot>.toSongJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { snapshot ->
                array.put(
                    JSONObject()
                        .put("name", snapshot.name)
                        .put("savedAtMs", snapshot.savedAtMs)
                        .put("bpm", snapshot.bpm)
                        .put("displayedFavoritePageId", snapshot.displayedFavoritePageId)
                        .put("playBars", snapshot.playBars.toPlayBarsJson()),
                )
            }
        }
    }

    private fun List<List<Screen4FavoritePadReference?>>.toPlayBarsJson(): JSONArray {
        return JSONArray().also { array ->
            forEach { barSteps ->
                array.put(barSteps.toFavoriteReferenceJson())
            }
        }
    }

    private fun JSONArray?.toPlayBars(): List<List<Screen4FavoritePadReference?>> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                add(optJSONArray(index).toFavoriteReferenceList())
            }
        }
    }

    private fun List<Screen4FavoritePadReference?>.toFavoriteReferenceJson(): JSONArray {
        return JSONArray().also { array ->
            forEach { reference ->
                if (reference == null) {
                    array.put(JSONObject.NULL)
                } else {
                    array.put(
                        JSONObject()
                            .put("pageId", reference.pageId)
                            .put("slotIndex", reference.slotIndex)
                            .put("clipId", reference.clipId),
                    )
                }
            }
        }
    }

    private fun JSONArray?.toFavoriteReferenceList(): List<Screen4FavoritePadReference?> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index)
                val pageId = item?.optString("pageId", "").orEmpty().takeIf { it.isNotBlank() }
                val slotIndex = item?.optInt("slotIndex", -1) ?: -1
                add(
                    if (pageId == null || slotIndex < 0) {
                        null
                    } else {
                        Screen4FavoritePadReference(
                            pageId = pageId,
                            slotIndex = slotIndex,
                            clipId = item.optString("clipId", "").takeIf { it.isNotBlank() },
                        )
                    }
                )
            }
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private companion object {
        private const val PREFS_NAME = "screen4_music_controller"
        private const val KEY_WORKING_STATE = "working_sequence_state"
        private const val KEY_SAVED_BARS = "saved_bar_snapshots"
        private const val KEY_SAVED_SONGS = "saved_song_snapshots"
    }
}
