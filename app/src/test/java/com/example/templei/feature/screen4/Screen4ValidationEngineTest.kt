package com.example.templei.feature.screen4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Screen4ValidationEngineTest {
    private val engine = Screen4ValidationEngine(Screen4ColumnTypeRegistry)

    @Test
    fun date_validator_respects_selected_date_format() {
        val yyyyDateColumn = testColumn(constraintType = "date_mdy_dash_yyyy")
        val yyDateColumn = testColumn(constraintType = "date_mdy_dash_yy")

        assertNull(engine.validate(yyyyDateColumn, "12-31-2024"))
        assertEquals("must match MM-DD-YYYY", engine.validate(yyyyDateColumn, "12-31-24"))

        assertNull(engine.validate(yyDateColumn, "12-31-24"))
        assertEquals("must match MM-DD-YY", engine.validate(yyDateColumn, "12-31-2024"))
    }

    @Test
    fun time_validator_accepts_supported_24h_and_12h_variants() {
        val timeColumn = testColumn(constraintType = "time_hh_mm_ss")

        assertNull(engine.validate(timeColumn, "09:15"))
        assertNull(engine.validate(timeColumn, "09:15:30"))
        assertNull(engine.validate(timeColumn, "09:15:30.123"))
        assertNull(engine.validate(timeColumn, "9:15 AM"))
        assertNull(engine.validate(timeColumn, "09:15:30 pm"))
        assertNull(engine.validate(timeColumn, "09:15:30.123 PM"))
        assertEquals("must match a supported time format", engine.validate(timeColumn, "25:99"))
    }

    @Test
    fun temporal_validation_respects_separator_and_timestamp_precision() {
        val slashDateColumn = testColumn(constraintType = "date_mdy_slash_yyyy")
        val timestampColumn = testColumn(constraintType = "timestamp_mdy_dash_second")

        assertNull(engine.validate(slashDateColumn, "12/31/2024"))
        assertEquals("must match MM/DD/YYYY", engine.validate(slashDateColumn, "12-31-2024"))
        assertNull(engine.validate(timestampColumn, "12-31-2024 09:15:30"))
        assertEquals(
            "must match MM-DD-YYYY HH:MM:SS",
            engine.validate(timestampColumn, "12/31/2024 09:15:30"),
        )
    }

    @Test
    fun grouped_and_decimal_number_validation_respects_required_formatting() {
        val groupedColumn = testColumn(constraintType = "number_grouped")
        val groupedDecimalColumn = testColumn(constraintType = "decimal_grouped_2")

        assertNull(engine.validate(groupedColumn, "1,234,567"))
        assertEquals("must match grouped format like 1,234", engine.validate(groupedColumn, "1234567"))

        assertNull(engine.validate(groupedDecimalColumn, "1,234.56"))
        assertEquals("must match grouped format like 1,234.56", engine.validate(groupedDecimalColumn, "1234.56"))
    }

    @Test
    fun semantic_network_and_location_types_are_enforced() {
        assertNull(engine.validate(testColumn("ipv4"), "192.168.1.1"))
        assertEquals("must be a valid IPv4 address", engine.validate(testColumn("ipv4"), "300.1.1.1"))

        assertNull(engine.validate(testColumn("mac_address"), "AA:BB:CC:DD:EE:FF"))
        assertEquals("must be a valid MAC address", engine.validate(testColumn("mac_address"), "GG:BB:CC:DD:EE:FF"))

        assertNull(engine.validate(testColumn("zipcode"), "12345-6789"))
        assertEquals(
            "must match ZIP format (12345 or 12345-6789)",
            engine.validate(testColumn("zipcode"), "ABCDE"),
        )
    }

    @Test
    fun semantic_financial_and_ratio_types_are_enforced() {
        assertNull(engine.validate(testColumn("currency_usd"), "$1,234.56"))
        assertEquals(
            "must match currency format like $1,234.56",
            engine.validate(testColumn("currency_usd"), "1234.56"),
        )

        assertNull(engine.validate(testColumn("percent"), "75%"))
        assertEquals("must be 0-100 (optional % suffix)", engine.validate(testColumn("percent"), "175%"))

        assertNull(engine.validate(testColumn("percent_decimal"), "0.75"))
        assertEquals("must be a decimal between 0 and 1", engine.validate(testColumn("percent_decimal"), "1.75"))
    }

    @Test
    fun seeded_quantity_column_enforces_signed_decimal_range() {
        val quantityColumn = testColumn(
            constraintType = "decimal_2",
            fakerKey = "screen4.default.quantity",
        )

        assertNull(engine.validate(quantityColumn, "-999.99"))
        assertNull(engine.validate(quantityColumn, "125.5"))
        assertEquals(
            "must be between -999.99 and 999.99",
            engine.validate(quantityColumn, "1000.00"),
        )
        assertEquals(
            "must be a signed number with up to 2 decimals",
            engine.validate(quantityColumn, "12.345"),
        )
    }

    private fun testColumn(constraintType: String, fakerKey: String = "screen4.test") = ActiveColumn(
        columnId = 1L,
        templateId = 1L,
        fakerKey = fakerKey,
        constraintType = constraintType,
        label = "sample",
        maxLength = 64,
        required = false,
    )
}
