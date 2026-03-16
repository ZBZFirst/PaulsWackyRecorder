package com.example.templei.feature.screen4

import java.util.Locale
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

        val seedSpecificValidation = validateSeedSpecific(column, value)
        if (seedSpecificValidation != null) return seedSpecificValidation

        val resolvedType = registry.resolveByConstraintType(column.constraintType).name
        val semanticValidation = validateByResolvedType(resolvedType, value)
        if (semanticValidation != null) return semanticValidation

        val temporalValidation = validateTemporalByType(resolvedType, value)
        if (temporalValidation != null) return temporalValidation

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

    private fun validateSeedSpecific(column: ActiveColumn, value: String): String? {
        return when (column.fakerKey) {
            "screen4.default.quantity" -> validateDefaultQuantity(value)
            else -> null
        }
    }

    private fun validateByResolvedType(resolvedType: String, value: String): String? {
        return when (resolvedType) {
            "zipcode", "postalcode" -> if (ZIP_CODE_REGEX.matches(value)) null else "must match ZIP format (12345 or 12345-6789)"
            "state_abbr" -> if (STATE_ABBR_REGEX.matches(value)) null else "must be a 2-letter state code"
            "country_code" -> if (COUNTRY_CODE_REGEX.matches(value)) null else "must be a 2-letter country code"
            "ipv4" -> if (IPV4_REGEX.matches(value)) null else "must be a valid IPv4 address"
            "ipv6" -> if (IPV6_REGEX.matches(value)) null else "must be a valid IPv6 address"
            "mac_address" -> if (MAC_ADDRESS_REGEX.matches(value)) null else "must be a valid MAC address"
            "url", "uri" -> if (URL_REGEX.matches(value)) null else "must be a valid URL"
            "credit_card_number" -> if (CREDIT_CARD_REGEX.matches(value)) null else "must be 13-19 digits (spaces/dashes allowed)"
            "currency_usd" -> if (CURRENCY_USD_REGEX.matches(value)) null else "must match currency format like $1,234.56"
            "currency_usd_plain" -> if (CURRENCY_USD_PLAIN_REGEX.matches(value)) null else "must match currency format like 1234.56"
            "percent" -> if (PERCENT_REGEX.matches(value)) null else "must be 0-100 (optional % suffix)"
            "percent_decimal" -> if (PERCENT_DECIMAL_REGEX.matches(value)) null else "must be a decimal between 0 and 1"
            "number_scientific" -> if (SCIENTIFIC_NUMBER_REGEX.matches(value)) null else "must match scientific notation like 1.23e4"
            "timestamp_unix_s" -> validateUnixSeconds(value)
            "year_yyyy" -> validateYear(value, 4)
            "year_yy" -> validateYear(value, 2)
            "month_mm" -> validateMonth(value)
            "day_dd" -> validateDay(value)
            "day_of_week" -> validateDayOfWeek(value)
            else -> null
        }
    }

    private fun validateTemporalByType(resolvedType: String, value: String): String? {
        return when (resolvedType) {
            "date_mdy_dash_yyyy" -> if (DATE_MDY_DASH_YYYY_REGEX.matches(value)) null else "must match MM-DD-YYYY"
            "date_mdy_slash_yyyy" -> if (DATE_MDY_SLASH_YYYY_REGEX.matches(value)) null else "must match MM/DD/YYYY"
            "date_mdy_dash_yy" -> if (DATE_MDY_DASH_YY_REGEX.matches(value)) null else "must match MM-DD-YY"
            "date_mdy_slash_yy" -> if (DATE_MDY_SLASH_YY_REGEX.matches(value)) null else "must match MM/DD/YY"
            "timestamp_mdy_dash_minute" -> if (TIMESTAMP_MDY_DASH_MINUTE_REGEX.matches(value)) null else "must match MM-DD-YYYY HH:MM"
            "timestamp_mdy_slash_minute" -> if (TIMESTAMP_MDY_SLASH_MINUTE_REGEX.matches(value)) null else "must match MM/DD/YYYY HH:MM"
            "timestamp_mdy_dash_second" -> if (TIMESTAMP_MDY_DASH_SECOND_REGEX.matches(value)) null else "must match MM-DD-YYYY HH:MM:SS"
            "timestamp_mdy_slash_second" -> if (TIMESTAMP_MDY_SLASH_SECOND_REGEX.matches(value)) null else "must match MM/DD/YYYY HH:MM:SS"
            "timestamp_mdy_dash_millisecond" -> if (TIMESTAMP_MDY_DASH_MILLISECOND_REGEX.matches(value)) null else "must match MM-DD-YYYY HH:MM:SS.mmm"
            "timestamp_mdy_slash_millisecond" -> if (TIMESTAMP_MDY_SLASH_MILLISECOND_REGEX.matches(value)) null else "must match MM/DD/YYYY HH:MM:SS.mmm"
            "time_hh_mm" -> if (TIME_HH_MM_REGEX.matches(value)) null else "must match HH:MM"
            "time_hh_mm_ss" -> if (TIME_HH_MM_SS_REGEX.matches(value)) null else "must match HH:MM:SS"
            "time_hh_mm_ss_mmm" -> if (TIME_HH_MM_SS_MMM_REGEX.matches(value)) null else "must match HH:MM:SS.mmm"
            "time_hh_mm_am_pm" -> if (TIME_HH_MM_AM_PM_REGEX.matches(value)) null else "must match HH:MM AM/PM"
            "time_hh_mm_ss_am_pm" -> if (TIME_HH_MM_SS_AM_PM_REGEX.matches(value)) null else "must match HH:MM:SS AM/PM"
            "time_hh_mm_ss_mmm_am_pm" -> if (TIME_HH_MM_SS_MMM_AM_PM_REGEX.matches(value)) null else "must match HH:MM:SS.mmm AM/PM"
            "number_grouped" -> if (INTEGER_GROUPED_REGEX.matches(value)) null else "must match grouped format like 1,234"
            "decimal_grouped_2" -> if (DECIMAL_GROUPED_2_REGEX.matches(value)) null else "must match grouped format like 1,234.56"
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

    private fun validateUnixSeconds(value: String): String? {
        if (!INTEGER_REGEX.matches(value)) return "must be unix seconds"
        return if (value.length in 10..12) null else "must be unix seconds"
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

    private fun validateYear(value: String, digits: Int): String? {
        val regex = if (digits == 4) YEAR_YYYY_REGEX else YEAR_YY_REGEX
        return if (regex.matches(value)) null else "must be a ${digits}-digit year"
    }

    private fun validateMonth(value: String): String? =
        if (MONTH_MM_REGEX.matches(value)) null else "must be month 01-12"

    private fun validateDay(value: String): String? =
        if (DAY_DD_REGEX.matches(value)) null else "must be day 01-31"

    private fun validateDayOfWeek(value: String): String? {
        val normalized = value.trim().lowercase(Locale.US)
        return if (normalized in DAY_OF_WEEK_VALUES) null else "must be a valid weekday name"
    }

    private fun validateDefaultQuantity(value: String): String? {
        if (!SIGNED_DECIMAL_UP_TO_2_REGEX.matches(value)) {
            return "must be a signed number with up to 2 decimals"
        }

        val parsed = value.toBigDecimalOrNull() ?: return "must be a signed number with up to 2 decimals"
        return if (parsed >= MAX_NEGATIVE_QUANTITY && parsed <= MAX_POSITIVE_QUANTITY) {
            null
        } else {
            "must be between -999.99 and 999.99"
        }
    }

    companion object {
        private val INTEGER_REGEX = Regex("""^-?\d+$""")
        private val DECIMAL_REGEX = Regex("""^-?\d+(\.\d+)?$""")
        private val EMAIL_REGEX = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$""")
        private val PHONE_REGEX = Regex("""^\+?[0-9()\-\s]{7,20}$""")
        private val ZIP_CODE_REGEX = Regex("""^\d{5}(-\d{4})?$""")
        private val STATE_ABBR_REGEX = Regex("""^[A-Za-z]{2}$""")
        private val COUNTRY_CODE_REGEX = Regex("""^[A-Za-z]{2}$""")
        private val IPV4_REGEX = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
        private val IPV6_REGEX = Regex("""^([0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}$|^(([0-9A-Fa-f]{1,4}:){1,7}:|:([0-9A-Fa-f]{1,4}:){1,7}|::)$""")
        private val MAC_ADDRESS_REGEX = Regex("""^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$""")
        private val URL_REGEX = Regex("""^https?://[A-Za-z0-9.-]+(:\d+)?(/\S*)?$""")
        private val CREDIT_CARD_REGEX = Regex("""^(?:\d[ -]*?){13,19}$""")
        private val CURRENCY_USD_REGEX = Regex("""^\$\d{1,3}(,\d{3})*(\.\d{2})?$|^\$\d+(\.\d{2})?$""")
        private val CURRENCY_USD_PLAIN_REGEX = Regex("""^\d+(\.\d{2})?$""")
        private val PERCENT_REGEX = Regex("""^(100(\.0+)?|[0-9]{1,2}(\.\d+)?)%?$""")
        private val PERCENT_DECIMAL_REGEX = Regex("""^(0(\.\d+)?|1(\.0+)?)$""")
        private val SCIENTIFIC_NUMBER_REGEX = Regex("""^-?\d+(\.\d+)?[eE][+-]?\d+$""")
        private val YEAR_YYYY_REGEX = Regex("""^\d{4}$""")
        private val YEAR_YY_REGEX = Regex("""^\d{2}$""")
        private val MONTH_MM_REGEX = Regex("""^(0[1-9]|1[0-2])$""")
        private val DAY_DD_REGEX = Regex("""^(0[1-9]|[12]\d|3[01])$""")
        private val SIGNED_DECIMAL_UP_TO_2_REGEX = Regex("""^-?\d{1,3}(\.\d{1,2})?$""")

        private val DATE_MDY_DASH_YYYY_REGEX = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])-\d{4}$""")
        private val DATE_MDY_SLASH_YYYY_REGEX = Regex("""^(0[1-9]|1[0-2])/(0[1-9]|[12]\d|3[01])/\d{4}$""")
        private val DATE_MDY_DASH_YY_REGEX = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])-\d{2}$""")
        private val DATE_MDY_SLASH_YY_REGEX = Regex("""^(0[1-9]|1[0-2])/(0[1-9]|[12]\d|3[01])/\d{2}$""")

        private val TIME_HH_MM_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
        private val TIME_HH_MM_SS_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$""")
        private val TIME_HH_MM_SS_MMM_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}$""")
        private val TIME_HH_MM_AM_PM_REGEX = Regex("""^(0?[1-9]|1[0-2]):[0-5]\d\s?(AM|PM)$""", RegexOption.IGNORE_CASE)
        private val TIME_HH_MM_SS_AM_PM_REGEX = Regex("""^(0?[1-9]|1[0-2]):[0-5]\d:[0-5]\d\s?(AM|PM)$""", RegexOption.IGNORE_CASE)
        private val TIME_HH_MM_SS_MMM_AM_PM_REGEX = Regex("""^(0?[1-9]|1[0-2]):[0-5]\d:[0-5]\d\.\d{3}\s?(AM|PM)$""", RegexOption.IGNORE_CASE)

        private val TIMESTAMP_MDY_DASH_MINUTE_REGEX = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])-\d{4} ([01]\d|2[0-3]):[0-5]\d$""")
        private val TIMESTAMP_MDY_SLASH_MINUTE_REGEX = Regex("""^(0[1-9]|1[0-2])/(0[1-9]|[12]\d|3[01])/\d{4} ([01]\d|2[0-3]):[0-5]\d$""")
        private val TIMESTAMP_MDY_DASH_SECOND_REGEX = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])-\d{4} ([01]\d|2[0-3]):[0-5]\d:[0-5]\d$""")
        private val TIMESTAMP_MDY_SLASH_SECOND_REGEX = Regex("""^(0[1-9]|1[0-2])/(0[1-9]|[12]\d|3[01])/\d{4} ([01]\d|2[0-3]):[0-5]\d:[0-5]\d$""")
        private val TIMESTAMP_MDY_DASH_MILLISECOND_REGEX = Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])-\d{4} ([01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}$""")
        private val TIMESTAMP_MDY_SLASH_MILLISECOND_REGEX = Regex("""^(0[1-9]|1[0-2])/(0[1-9]|[12]\d|3[01])/\d{4} ([01]\d|2[0-3]):[0-5]\d:[0-5]\d\.\d{3}$""")

        private val INTEGER_GROUPED_REGEX = Regex("""^-?\d{1,3}(,\d{3})*$""")
        private val DECIMAL_GROUPED_2_REGEX = Regex("""^-?\d{1,3}(,\d{3})*\.\d{2}$""")

        private val DAY_OF_WEEK_VALUES = setOf(
            "monday", "mon",
            "tuesday", "tue", "tues",
            "wednesday", "wed",
            "thursday", "thu", "thurs",
            "friday", "fri",
            "saturday", "sat",
            "sunday", "sun",
        )

        private val MAX_POSITIVE_QUANTITY = "999.99".toBigDecimal()
        private val MAX_NEGATIVE_QUANTITY = "-999.99".toBigDecimal()
    }
}
