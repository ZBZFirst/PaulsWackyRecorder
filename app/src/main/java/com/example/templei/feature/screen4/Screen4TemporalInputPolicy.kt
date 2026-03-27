package com.example.templei.feature.screen4

import java.util.Calendar
import java.util.Locale

/**
 * Shared temporal picker policy for Screen 4 date, time, and timestamp fields.
 */
object Screen4TemporalInputPolicy {
    data class DateParts(
        val year: Int,
        val monthOfYearZeroBased: Int,
        val dayOfMonth: Int,
    )

    data class TimeParts(
        val hourOfDay: Int,
        val minute: Int,
    )

    fun usesTimestampPicker(column: ActiveColumn, resolvedType: ColumnTypeDefinition): Boolean {
        return resolvedType.uiWidget == "TimestampInput" || column.constraintType.startsWith("timestamp_")
    }

    fun parseExistingDate(value: String): DateParts? {
        val trimmed = value.trim()
        val compactDigits = trimmed.filter(Char::isDigit)
        if (compactDigits.length == 6 || compactDigits.length == 8) {
            val month = compactDigits.take(2).toIntOrNull()?.minus(1) ?: return null
            val day = compactDigits.drop(2).take(2).toIntOrNull() ?: return null
            val rawYear = compactDigits.drop(4)
            val year = rawYear.toIntOrNull()?.let { if (rawYear.length == 2) 2000 + it else it } ?: return null
            return DateParts(year = year, monthOfYearZeroBased = month, dayOfMonth = day)
        }

        val parts = trimmed.split('/', '-')
        if (parts.size < 3) return null
        val month = parts[0].toIntOrNull()?.minus(1) ?: return null
        val day = parts[1].toIntOrNull() ?: return null
        val rawYear = parts[2].takeWhile { it.isDigit() }
        val year = rawYear.toIntOrNull()?.let { if (rawYear.length == 2) 2000 + it else it } ?: return null
        return DateParts(year = year, monthOfYearZeroBased = month, dayOfMonth = day)
    }

    fun parseExistingTime(value: String): TimeParts? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val isAmPm = trimmed.uppercase(Locale.US).contains("AM") || trimmed.uppercase(Locale.US).contains("PM")
        val timeSection = trimmed.substringBefore(' ').trim()
        val parts = timeSection.split(':')
        if (parts.size < 2) return null
        var hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (isAmPm) {
            val upper = trimmed.uppercase(Locale.US)
            val isPm = upper.contains("PM")
            hour = when {
                isPm && hour < 12 -> hour + 12
                !isPm && hour == 12 -> 0
                else -> hour
            }
        }
        return TimeParts(hourOfDay = hour, minute = minute)
    }

    fun parseExistingTimestamp(value: String): Pair<DateParts, TimeParts>? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val splitIndex = trimmed.indexOf(' ')
        if (splitIndex <= 0) return null
        val datePart = parseExistingDate(trimmed.substring(0, splitIndex)) ?: return null
        val timePart = parseExistingTime(trimmed.substring(splitIndex + 1)) ?: return null
        return datePart to timePart
    }

    fun formatDate(constraintType: String, year: Int, monthOfYearZeroBased: Int, dayOfMonth: Int): String {
        val month = monthOfYearZeroBased + 1
        return when {
            constraintType.contains("slash_yy") ->
                String.format(Locale.US, "%02d/%02d/%02d", month, dayOfMonth, year % 100)
            constraintType.contains("dash_yy") ->
                String.format(Locale.US, "%02d-%02d-%02d", month, dayOfMonth, year % 100)
            constraintType.contains("slash") ->
                String.format(Locale.US, "%02d/%02d/%04d", month, dayOfMonth, year)
            else ->
                String.format(Locale.US, "%02d-%02d-%04d", month, dayOfMonth, year)
        }
    }

    fun formatDateForColumn(column: ActiveColumn, year: Int, monthOfYearZeroBased: Int, dayOfMonth: Int): String {
        return if (column.metadata?.uiInputType?.uppercase(Locale.US) == "NUMPAD") {
            if (column.constraintType.endsWith("_yy")) {
                String.format(Locale.US, "%02d%02d%02d", monthOfYearZeroBased + 1, dayOfMonth, year % 100)
            } else {
                String.format(Locale.US, "%02d%02d%04d", monthOfYearZeroBased + 1, dayOfMonth, year)
            }
        } else {
            formatDate(column.constraintType, year, monthOfYearZeroBased, dayOfMonth)
        }
    }

    fun formatTime(constraintType: String, hourOfDay: Int, minute: Int): String {
        return when {
            constraintType.contains("am_pm") -> {
                val displayHour = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                val meridiem = if (hourOfDay >= 12) "PM" else "AM"
                when {
                    constraintType.contains("_ss_mmm") ->
                        String.format(Locale.US, "%02d:%02d:00.000 %s", displayHour, minute, meridiem)
                    constraintType.contains("_ss") ->
                        String.format(Locale.US, "%02d:%02d:00 %s", displayHour, minute, meridiem)
                    else ->
                        String.format(Locale.US, "%02d:%02d %s", displayHour, minute, meridiem)
                }
            }

            constraintType.contains("_ss_mmm") ->
                String.format(Locale.US, "%02d:%02d:00.000", hourOfDay, minute)
            constraintType.contains("_ss") ->
                String.format(Locale.US, "%02d:%02d:00", hourOfDay, minute)
            else ->
                String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
        }
    }

    fun formatTimestamp(
        constraintType: String,
        year: Int,
        monthOfYearZeroBased: Int,
        dayOfMonth: Int,
        hourOfDay: Int,
        minute: Int,
    ): String {
        if (constraintType == "timestamp_unix_ms") {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthOfYearZeroBased)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return calendar.timeInMillis.toString()
        }

        val date = formatDate(constraintType.replace("timestamp_", "date_"), year, monthOfYearZeroBased, dayOfMonth)
        val time = when {
            constraintType.contains("millisecond") -> String.format(Locale.US, "%02d:%02d:00.000", hourOfDay, minute)
            constraintType.contains("second") -> String.format(Locale.US, "%02d:%02d:00", hourOfDay, minute)
            else -> String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
        }
        return "$date $time"
    }
}
