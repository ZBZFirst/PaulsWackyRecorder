package com.example.templei.feature.screen4

enum class AutoValueSource {
    SYSTEM_TIME_UNIX_MS,
}

data class RapidEntryConfig(
    val activeColumnIds: Set<Long>,
    val autoColumns: Map<Long, AutoValueSource>,
)
