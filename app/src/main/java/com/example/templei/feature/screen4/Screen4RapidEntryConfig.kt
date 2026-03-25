package com.example.templei.feature.screen4

enum class RapidEntryFillMode {
    MANUAL,
    CURRENT_DATE,
    CURRENT_TIME,
    CURRENT_TIMESTAMP,
    FIXED_VALUE,
}

data class RapidEntryFillRule(
    val mode: RapidEntryFillMode,
    val fixedValue: String? = null,
)

data class RapidEntryConfig(
    val activeColumnIds: Set<Long>,
    val fillRulesByColumnId: Map<Long, RapidEntryFillRule>,
)
