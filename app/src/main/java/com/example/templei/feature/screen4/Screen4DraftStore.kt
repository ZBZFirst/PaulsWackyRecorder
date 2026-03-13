package com.example.templei.feature.screen4

import android.content.Context

class Screen4DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("screen4_draft_store", Context.MODE_PRIVATE)

    fun saveDraft(valuesByColumnId: Map<Long, String>) {
        val serialized = valuesByColumnId.entries.joinToString("||") { (columnId, value) ->
            "$columnId::${value.replace("||", " ").replace("::", " ")}" 
        }
        prefs.edit().putString(KEY_DRAFT, serialized).apply()
    }

    fun loadDraft(): MutableMap<Long, String> {
        val raw = prefs.getString(KEY_DRAFT, "").orEmpty()
        if (raw.isBlank()) return mutableMapOf()
        return raw.split("||")
            .mapNotNull {
                val parts = it.split("::")
                if (parts.size != 2) return@mapNotNull null
                parts[0].toLongOrNull()?.let { id -> id to parts[1] }
            }
            .toMap()
            .toMutableMap()
    }

    fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    companion object {
        private const val KEY_DRAFT = "draft_row_values"
    }
}
