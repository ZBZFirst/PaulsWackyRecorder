package com.example.templei.feature.soundboard

import android.content.Context

class Screen3SettingsStore(private val context: Context) {

    fun saveFavoriteAssignments(assignments: Map<Int, String>) {
        val packed = assignments.entries
            .sortedBy { it.key }
            .joinToString(separator = "||") { "${it.key}::${it.value}" }
        prefs().edit().putString(KEY_FAVORITE_ASSIGNMENTS, packed).apply()
    }

    fun loadFavoriteAssignments(favoriteSlotCount: Int): Map<Int, String> {
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

    private companion object {
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_MAX_STREAMS = "max_streams"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"
        private const val KEY_UNLOAD_ON_FOLDER_CHANGE = "unload_on_folder_change"
        private const val KEY_CACHE_POLICY = "cache_policy"
        private const val KEY_FAVORITE_ASSIGNMENTS = "favorite_assignments"
        private const val KEY_SELECTED_ASSIGNMENT_SLOT = "selected_assignment_slot"
    }
}
