package com.example.templei.feature.screen4

enum class RapidEntryFillMode {
    MANUAL,
    CURRENT_DATE,
    CURRENT_TIME,
    CURRENT_TIMESTAMP,
    CURRENT_GPS,
    FIXED_VALUE,
    SEQUENCE_INCREMENT,
    SEQUENCE_DECREMENT,
}

enum class RapidEntryStepUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    MINUTE,
    HOUR,
}

data class RapidEntryFillRule(
    val mode: RapidEntryFillMode,
    val fixedValue: String? = null,
    val sequenceSeedValue: String? = null,
    val stepAmount: Int = 1,
    val stepUnit: RapidEntryStepUnit? = null,
)

data class RapidEntryConfig(
    val activeColumnIds: Set<Long>,
    val fillRulesByColumnId: Map<Long, RapidEntryFillRule>,
)
