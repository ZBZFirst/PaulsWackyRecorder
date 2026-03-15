package com.example.templei.feature.screen4

/**
 * Formats separator-driven fields as the user types so operators mostly enter raw values.
 *
 * Current coverage:
 * - Temporal semantic types (date/time/timestamp) with pattern separators.
 * - Grouped numeric semantic types (e.g., x,xxx,xxx).
 */
object Screen4FieldInputFormatter {
    fun supports(constraintType: String): Boolean {
        val normalized = constraintType.trim().lowercase()
        return normalized.startsWith("date_") ||
            normalized.startsWith("time_") ||
            normalized.startsWith("timestamp_mdy_") ||
            normalized == "number_grouped" ||
            normalized == "decimal_grouped_2"
    }

    fun format(constraintType: String, rawInput: String): String {
        val normalized = constraintType.trim().lowercase()
        return when {
            normalized.startsWith("date_mdy_slash_yyyy") -> formatDate(rawInput, '/', 4)
            normalized.startsWith("date_mdy_dash_yyyy") -> formatDate(rawInput, '-', 4)
            normalized.startsWith("date_mdy_slash_yy") -> formatDate(rawInput, '/', 2)
            normalized.startsWith("date_mdy_dash_yy") -> formatDate(rawInput, '-', 2)
            normalized == "time_hh_mm" -> formatTime(rawInput, includeSeconds = false, includeMillis = false)
            normalized == "time_hh_mm_ss" -> formatTime(rawInput, includeSeconds = true, includeMillis = false)
            normalized == "time_hh_mm_ss_mmm" -> formatTime(rawInput, includeSeconds = true, includeMillis = true)
            normalized == "time_hh_mm_am_pm" -> formatTimeWithAmPm(rawInput, includeSeconds = false, includeMillis = false)
            normalized == "time_hh_mm_ss_am_pm" -> formatTimeWithAmPm(rawInput, includeSeconds = true, includeMillis = false)
            normalized == "time_hh_mm_ss_mmm_am_pm" -> formatTimeWithAmPm(rawInput, includeSeconds = true, includeMillis = true)
            normalized == "timestamp_mdy_slash_minute" -> formatTimestamp(rawInput, '/', precision = TimestampPrecision.MINUTE)
            normalized == "timestamp_mdy_dash_minute" -> formatTimestamp(rawInput, '-', precision = TimestampPrecision.MINUTE)
            normalized == "timestamp_mdy_slash_second" -> formatTimestamp(rawInput, '/', precision = TimestampPrecision.SECOND)
            normalized == "timestamp_mdy_dash_second" -> formatTimestamp(rawInput, '-', precision = TimestampPrecision.SECOND)
            normalized == "timestamp_mdy_slash_millisecond" -> formatTimestamp(rawInput, '/', precision = TimestampPrecision.MILLISECOND)
            normalized == "timestamp_mdy_dash_millisecond" -> formatTimestamp(rawInput, '-', precision = TimestampPrecision.MILLISECOND)
            normalized == "number_grouped" -> formatGroupedInteger(rawInput)
            normalized == "decimal_grouped_2" -> formatGroupedDecimal(rawInput, decimalDigits = 2)
            else -> rawInput
        }
    }

    private fun formatDate(rawInput: String, separator: Char, yearDigits: Int): String {
        val digits = rawInput.filter(Char::isDigit).take(4 + yearDigits)
        val month = digits.take(2)
        val day = digits.drop(2).take(2)
        val year = digits.drop(4).take(yearDigits)

        val builder = StringBuilder()
        if (month.isNotEmpty()) builder.append(month)
        if (day.isNotEmpty()) {
            if (builder.length == 2) builder.append(separator)
            builder.append(day)
        }
        if (year.isNotEmpty()) {
            if (builder.length == 5) builder.append(separator)
            builder.append(year)
        }
        return builder.toString()
    }

    private fun formatTime(rawInput: String, includeSeconds: Boolean, includeMillis: Boolean): String {
        val maxDigits = when {
            includeMillis -> 9
            includeSeconds -> 6
            else -> 4
        }
        val digits = rawInput.filter(Char::isDigit).take(maxDigits)
        return formatTimeDigits(digits, includeSeconds, includeMillis)
    }

    private fun formatTimeWithAmPm(rawInput: String, includeSeconds: Boolean, includeMillis: Boolean): String {
        val digits = rawInput.filter(Char::isDigit)
        val letters = rawInput.filter(Char::isLetter).uppercase().take(2)
        val formattedTime = formatTimeDigits(
            digits = digits.take(if (includeMillis) 9 else if (includeSeconds) 6 else 4),
            includeSeconds = includeSeconds,
            includeMillis = includeMillis,
        )
        return if (letters.isNotEmpty()) "$formattedTime $letters".trim() else formattedTime
    }

    private fun formatTimestamp(rawInput: String, separator: Char, precision: TimestampPrecision): String {
        val digits = rawInput.filter(Char::isDigit).take(
            when (precision) {
                TimestampPrecision.MINUTE -> 12
                TimestampPrecision.SECOND -> 14
                TimestampPrecision.MILLISECOND -> 17
            }
        )

        val datePart = formatDate(digits.take(8), separator, yearDigits = 4)
        val timeDigits = digits.drop(8)
        if (timeDigits.isEmpty()) return datePart

        val timePart = formatTimeDigits(
            digits = timeDigits,
            includeSeconds = precision != TimestampPrecision.MINUTE,
            includeMillis = precision == TimestampPrecision.MILLISECOND,
        )
        return "$datePart $timePart".trim()
    }

    private fun formatTimeDigits(digits: String, includeSeconds: Boolean, includeMillis: Boolean): String {
        val hh = digits.take(2)
        val mm = digits.drop(2).take(2)
        val ss = digits.drop(4).take(2)
        val mmm = digits.drop(6).take(3)

        val builder = StringBuilder()
        if (hh.isNotEmpty()) builder.append(hh)
        if (mm.isNotEmpty()) {
            if (builder.length == 2) builder.append(':')
            builder.append(mm)
        }
        if (includeSeconds && ss.isNotEmpty()) {
            if (builder.length >= 5) builder.append(':')
            builder.append(ss)
        }
        if (includeMillis && mmm.isNotEmpty()) {
            if (builder.isNotEmpty()) builder.append('.')
            builder.append(mmm)
        }
        return builder.toString()
    }

    private fun formatGroupedInteger(rawInput: String): String {
        val digits = rawInput.filter(Char::isDigit).trimStart('0')
        val normalized = if (digits.isEmpty()) "" else digits
        if (normalized.isEmpty()) return ""

        return normalized.reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
    }

    private fun formatGroupedDecimal(rawInput: String, decimalDigits: Int): String {
        val digits = rawInput.filter(Char::isDigit)
        if (digits.isEmpty()) return ""

        val wholeDigits = digits.dropLast(decimalDigits).ifEmpty { "0" }
        val fractionalDigits = digits.takeLast(decimalDigits).padStart(decimalDigits, '0')
        return "${formatGroupedInteger(wholeDigits)}.$fractionalDigits"
    }

    private enum class TimestampPrecision { MINUTE, SECOND, MILLISECOND }
}
