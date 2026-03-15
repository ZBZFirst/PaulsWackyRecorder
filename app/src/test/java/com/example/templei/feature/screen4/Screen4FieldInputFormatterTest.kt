package com.example.templei.feature.screen4

import org.junit.Assert.assertEquals
import org.junit.Test

class Screen4FieldInputFormatterTest {

    @Test
    fun date_formatter_injects_separators_for_dash_pattern() {
        assertEquals("12-31-2024", Screen4FieldInputFormatter.format("date_mdy_dash_yyyy", "12312024"))
    }

    @Test
    fun timestamp_formatter_injects_date_time_separators() {
        assertEquals(
            "12/31/2024 09:15:30",
            Screen4FieldInputFormatter.format("timestamp_mdy_slash_second", "12312024091530"),
        )
    }

    @Test
    fun grouped_number_formatter_inserts_commas() {
        assertEquals("1,234,567", Screen4FieldInputFormatter.format("number_grouped", "1234567"))
        assertEquals("1,234.56", Screen4FieldInputFormatter.format("decimal_grouped_2", "123456"))
    }
}
