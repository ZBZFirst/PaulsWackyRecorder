package com.example.templei.feature.screen4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Screen4ColumnTypeRegistryTest {

    @Test
    fun registry_contains_all_107_supported_types() {
        val definitions = Screen4ColumnTypeRegistry.allDefinitions()
        assertEquals(107, definitions.size)
        assertEquals(definitions.size, definitions.map { it.name }.toSet().size)
    }

    @Test
    fun every_definition_has_validator_and_widget_mapping() {
        Screen4ColumnTypeRegistry.allDefinitions().forEach { definition ->
            assertTrue(definition.validatorKey.isNotBlank())
            assertTrue(definition.uiWidget.isNotBlank())
        }
    }

    @Test
    fun legacy_constraint_aliases_resolve_to_expected_semantic_types() {
        assertEquals("text", Screen4ColumnTypeRegistry.resolveByConstraintType("TEXT").name)
        assertEquals("integer", Screen4ColumnTypeRegistry.resolveByConstraintType("INTEGER").name)
        assertEquals("decimal", Screen4ColumnTypeRegistry.resolveByConstraintType("DECIMAL").name)
        assertEquals("timestamp_unix_ms", Screen4ColumnTypeRegistry.resolveByConstraintType("TIMESTAMP").name)
    }
}
