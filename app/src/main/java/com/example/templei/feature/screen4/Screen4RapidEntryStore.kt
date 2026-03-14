package com.example.templei.feature.screen4

import android.content.Context

class Screen4RapidEntryStore(context: Context) {
    private val prefs = context.getSharedPreferences("screen4_rapid_entry_store", Context.MODE_PRIVATE)

    fun save(config: RapidEntryConfig) {
        val active = config.activeColumnIds.joinToString(",")
        val auto = config.autoColumns.entries.joinToString("||") { (columnId, source) ->
            "$columnId::${source.name}"
        }
        prefs.edit()
            .putString(KEY_ACTIVE_COLUMN_IDS, active)
            .putString(KEY_AUTO_COLUMNS, auto)
            .apply()
    }

    fun load(): RapidEntryConfig {
        val activeRaw = prefs.getString(KEY_ACTIVE_COLUMN_IDS, "").orEmpty()
        val autoRaw = prefs.getString(KEY_AUTO_COLUMNS, "").orEmpty()

        val active = if (activeRaw.isBlank()) {
            emptySet()
        } else {
            activeRaw.split(",")
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        }

        val auto = if (autoRaw.isBlank()) {
            emptyMap()
        } else {
            autoRaw.split("||")
                .mapNotNull {
                    val parts = it.split("::")
                    if (parts.size != 2) return@mapNotNull null
                    val columnId = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val source = runCatching { AutoValueSource.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                    columnId to source
                }
                .toMap()
        }

        return RapidEntryConfig(activeColumnIds = active, autoColumns = auto)
    }

    companion object {
        private const val KEY_ACTIVE_COLUMN_IDS = "active_column_ids"
        private const val KEY_AUTO_COLUMNS = "auto_columns"
    }
}
