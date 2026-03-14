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
}
