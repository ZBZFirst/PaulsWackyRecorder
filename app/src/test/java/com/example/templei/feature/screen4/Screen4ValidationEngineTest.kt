package com.example.templei.feature.screen4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Screen4ValidationEngineTest {
    private val engine = Screen4ValidationEngine(Screen4ColumnTypeRegistry)

    @Test
    fun date_validator_accepts_mm_dd_yyyy_and_mm_dd_yy() {
        val dateColumn = ActiveColumn(
            columnId = 1L,
            templateId = 1L,
            fakerKey = "screen4.test",
            constraintType = "date_mdy_dash_yyyy",
            label = "sample_date",
            maxLength = 32,
            required = false,
        )

        assertNull(engine.validate(dateColumn, "12-31-2024"))
        assertNull(engine.validate(dateColumn, "12-31-24"))
        assertEquals("must match MM-DD-YYYY or MM-DD-YY", engine.validate(dateColumn, "2024-12-31"))
    }

    @Test
    fun time_validator_accepts_supported_24h_and_12h_variants() {
        val timeColumn = ActiveColumn(
            columnId = 2L,
            templateId = 1L,
            fakerKey = "screen4.test",
            constraintType = "time_hh_mm_ss",
            label = "sample_time",
            maxLength = 32,
            required = false,
        )

        assertNull(engine.validate(timeColumn, "09:15"))
        assertNull(engine.validate(timeColumn, "09:15:30"))
        assertNull(engine.validate(timeColumn, "09:15:30.123"))
        assertNull(engine.validate(timeColumn, "9:15 AM"))
        assertNull(engine.validate(timeColumn, "09:15:30 pm"))
        assertNull(engine.validate(timeColumn, "09:15:30.123 PM"))
        assertEquals("must match a supported time format", engine.validate(timeColumn, "25:99"))
    }
    @Test
    fun temporal_validation_respects_selected_separator_and_timestamp_type() {
        val slashDateColumn = ActiveColumn(
            columnId = 3L,
            templateId = 1L,
            fakerKey = "screen4.test",
            constraintType = "date_mdy_slash_yyyy",
            label = "slash_date",
            maxLength = 32,
            required = false,
        )
        val timestampColumn = ActiveColumn(
            columnId = 4L,
            templateId = 1L,
            fakerKey = "screen4.test",
            constraintType = "timestamp_mdy_dash_second",
            label = "timestamp",
            maxLength = 32,
            required = false,
        )

        assertNull(engine.validate(slashDateColumn, "12/31/2024"))
        assertEquals("must match MM/DD/YYYY", engine.validate(slashDateColumn, "12-31-2024"))
        assertNull(engine.validate(timestampColumn, "12-31-2024 09:15:30"))
        assertEquals(
            "must match MM-DD-YYYY HH:MM:SS",
            engine.validate(timestampColumn, "12/31/2024 09:15:30"),
        )
    }

    @Test
    fun grouped_number_validation_respects_separator_format() {
        val groupedColumn = ActiveColumn(
            columnId = 5L,
            templateId = 1L,
            fakerKey = "screen4.test",
            constraintType = "number_grouped",
            label = "grouped",
            maxLength = 32,
            required = false,
        )

        assertNull(engine.validate(groupedColumn, "1,234,567"))
        assertEquals("must match grouped format like 1,234", engine.validate(groupedColumn, "1234567"))
    }

}
