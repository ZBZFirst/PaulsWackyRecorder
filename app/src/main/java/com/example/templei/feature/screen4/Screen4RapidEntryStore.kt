package com.example.templei.feature.screen4

import android.content.Context

class Screen4RapidEntryStore(context: Context) {
    private val prefs = context.getSharedPreferences("screen4_rapid_entry_store", Context.MODE_PRIVATE)

    fun save(config: RapidEntryConfig) {
        val active = config.activeColumnIds.joinToString(",")
        val rules = config.fillRulesByColumnId.entries.joinToString("||") { (columnId, rule) ->
            "$columnId::${rule.mode.name}::${rule.fixedValue.orEmpty()}"
        }
        prefs.edit()
            .putString(KEY_ACTIVE_COLUMN_IDS, active)
            .putString(KEY_FILL_RULES, rules)
            .apply()
    }

    fun load(): RapidEntryConfig {
        val activeRaw = prefs.getString(KEY_ACTIVE_COLUMN_IDS, "").orEmpty()
        val rulesRaw = prefs.getString(KEY_FILL_RULES, "").orEmpty()

        val active = if (activeRaw.isBlank()) {
            emptySet()
        } else {
            activeRaw.split(",")
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        }

        val rules = if (rulesRaw.isBlank()) {
            emptyMap()
        } else {
            rulesRaw.split("||")
                .mapNotNull {
                    val parts = it.split("::", limit = 3)
                    if (parts.size < 2) return@mapNotNull null
                    val columnId = parts[0].toLongOrNull() ?: return@mapNotNull null
                    val mode = runCatching { RapidEntryFillMode.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                    val fixedValue = parts.getOrNull(2)?.ifBlank { null }
                    columnId to RapidEntryFillRule(mode = mode, fixedValue = fixedValue)
                }
                .toMap()
        }

        return RapidEntryConfig(activeColumnIds = active, fillRulesByColumnId = rules)
    }

    companion object {
        private const val KEY_ACTIVE_COLUMN_IDS = "active_column_ids"
        private const val KEY_FILL_RULES = "fill_rules"
    }
}
