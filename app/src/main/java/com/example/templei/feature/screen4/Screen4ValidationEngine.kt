package com.example.templei.feature.screen4

import java.util.UUID

/**
 * Phase 2 validator engine.
 *
 * Validation remains deterministic and explicit: the column's semantic type is
 * resolved by constraint type, then dispatched to a known validator branch.
 */
class Screen4ValidationEngine(
    private val registry: Screen4ColumnTypeRegistry,
) {
    fun validate(column: ActiveColumn, value: String): String? {
        if (value.isBlank()) return null

        val definition = registry.resolveByConstraintType(column.constraintType)
        return when (definition.validatorKey) {
            "text" -> null
            "integer" -> if (INTEGER_REGEX.matches(value)) null else "must be an integer"
            "decimal" -> if (DECIMAL_REGEX.matches(value)) null else "must be a decimal number"
            "uuid4" -> validateUuid4(value)
            "email" -> if (EMAIL_REGEX.matches(value)) null else "must be a valid email"
            "phone" -> if (PHONE_REGEX.matches(value)) null else "must be a valid phone number"
            "date" -> validateDate(value)
            "time" -> validateTime(value)
            "timestamp_unix_ms" -> validateUnixMillis(value)
            else -> null
        }
    }

    private fun validateUuid4(value: String): String? {
        return try {
            val parsed = UUID.fromString(value)
            if (parsed.version() == 4) null else "must be a UUIDv4"
        } catch (_: IllegalArgumentException) {
            "must be a UUID"
        }
    }

    private fun validateUnixMillis(value: String): String? {
        if (!INTEGER_REGEX.matches(value)) return "must be unix milliseconds"
        return if (value.length >= 13) null else "must be unix milliseconds"
    }

    private fun validateDate(value: String): String? {
        return if (DATE_MDY_DASH_YYYY_REGEX.matches(value) || DATE_MDY_DASH_YY_REGEX.matches(value)) {
            null
        } else {
            "must match MM-DD-YYYY or MM-DD-YY"
        }
    }

    private fun validateTime(value: String): String? {
        return if (
            TIME_HH_MM_REGEX.matches(value) ||
            TIME_HH_MM_SS_REGEX.matches(value) ||
            TIME_HH_MM_SS_MMM_REGEX.matches(value) ||
            TIME_HH_MM_AM_PM_REGEX.matches(value) ||
            TIME_HH_MM_SS_AM_PM_REGEX.matches(value) ||
            TIME_HH_MM_SS_MMM_AM_PM_REGEX.matches(value)
        ) {
            null
        } else {
            "must match a supported time format"
        }
    }

    companion object {
        private val INTEGER_REGEX = Regex("^-?\\d+$")
        private val DECIMAL_REGEX = Regex("^-?\\d+(\\.\\d+)?$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        private val PHONE_REGEX = Regex("^\\+?[0-9()\\-\\s]{7,20}$")
        private val DATE_MDY_DASH_YYYY_REGEX = Regex("^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])-\\d{4}$")
        private val DATE_MDY_DASH_YY_REGEX = Regex("^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])-\\d{2}$")
        private val TIME_HH_MM_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        private val TIME_HH_MM_SS_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$")
        private val TIME_HH_MM_SS_MMM_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d\\.\\d{3}$")
        private val TIME_HH_MM_AM_PM_REGEX = Regex("^(0?[1-9]|1[0-2]):[0-5]\\d\\s?(AM|PM)$", RegexOption.IGNORE_CASE)
        private val TIME_HH_MM_SS_AM_PM_REGEX = Regex("^(0?[1-9]|1[0-2]):[0-5]\\d:[0-5]\\d\\s?(AM|PM)$", RegexOption.IGNORE_CASE)
        private val TIME_HH_MM_SS_MMM_AM_PM_REGEX = Regex("^(0?[1-9]|1[0-2]):[0-5]\\d:[0-5]\\d\\.\\d{3}\\s?(AM|PM)$", RegexOption.IGNORE_CASE)
    }
}
