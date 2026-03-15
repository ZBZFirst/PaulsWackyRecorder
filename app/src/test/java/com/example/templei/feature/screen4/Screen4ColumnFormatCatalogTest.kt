package com.example.templei.feature.screen4

import org.junit.Assert.assertTrue
import org.junit.Test

class Screen4ColumnFormatCatalogTest {

    @Test
    fun date_group_exposes_multiple_date_types() {
        val dateGroup = Screen4ColumnFormatCatalog.allGroups().first { it.key == "date" }
        assertTrue(dateGroup.typeNames.contains("date_mdy_dash_yyyy"))
        assertTrue(dateGroup.typeNames.contains("date_mdy_dash_yy"))
    }

    @Test
    fun time_group_options_include_widget_metadata_and_preview_examples() {
        val options = Screen4ColumnFormatCatalog.optionsForGroup("time")
        assertTrue(options.isNotEmpty())
        assertTrue(options.any { it.typeName == "time_hh_mm_ss" && it.uiWidget.isNotBlank() && it.previewExample.contains(":") })
    }
}
