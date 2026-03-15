package com.example.templei.feature.screen4

data class Screen4FormatGroup(
    val key: String,
    val label: String,
    val typeNames: List<String>,
)

data class Screen4FormatOption(
    val typeName: String,
    val label: String,
    val uiWidget: String,
)

/**
 * Groups semantic column types into operator-facing format families.
 *
 * This lets Screen 4 present a two-step picker (group -> specific format) while
 * continuing to persist the final semantic type in `constraintType`.
 */
object Screen4ColumnFormatCatalog {
    private val groupedTypeNames: Map<String, List<String>> = mapOf(
        "date" to Screen4ColumnTypeRegistry.allDefinitions().map { it.name }.filter {
            it.startsWith("date_") || it in setOf("date", "month_mm", "day_dd", "year_yyyy", "year_yy", "day_of_week")
        },
        "time" to Screen4ColumnTypeRegistry.allDefinitions().map { it.name }.filter {
            it.startsWith("time_") || it.startsWith("timestamp_")
        },
        "number" to Screen4ColumnTypeRegistry.allDefinitions().map { it.name }.filter {
            it.startsWith("decimal") || it.startsWith("number_") || it in setOf(
                "ones", "tens", "hundreds", "thousands", "ten_thousands", "hundred_thousands", "millions", "percent", "percent_decimal"
            )
        },
        "contact" to Screen4ColumnTypeRegistry.allDefinitions().filter {
            it.category == Screen4ColumnCategory.CONTACT
        }.map { it.name },
        "identity" to Screen4ColumnTypeRegistry.allDefinitions().filter {
            it.category == Screen4ColumnCategory.IDENTITY
        }.map { it.name },
        "text" to Screen4ColumnTypeRegistry.allDefinitions().filter {
            it.category == Screen4ColumnCategory.TEXT || it.name == "text"
        }.map { it.name },
        "other" to Screen4ColumnTypeRegistry.allDefinitions().map { it.name },
    ).mapValues { (_, value) -> value.distinct().sorted() }

    private val groups: List<Screen4FormatGroup> = listOf(
        Screen4FormatGroup(key = "date", label = "Date Formats", typeNames = groupedTypeNames.getValue("date")),
        Screen4FormatGroup(key = "time", label = "Time/Timestamp Formats", typeNames = groupedTypeNames.getValue("time")),
        Screen4FormatGroup(key = "number", label = "Numeric Formats", typeNames = groupedTypeNames.getValue("number")),
        Screen4FormatGroup(key = "contact", label = "Contact Formats", typeNames = groupedTypeNames.getValue("contact")),
        Screen4FormatGroup(key = "identity", label = "Identity Formats", typeNames = groupedTypeNames.getValue("identity")),
        Screen4FormatGroup(key = "text", label = "Text Formats", typeNames = groupedTypeNames.getValue("text")),
        Screen4FormatGroup(key = "other", label = "All Semantic Types", typeNames = groupedTypeNames.getValue("other")),
    )

    fun allGroups(): List<Screen4FormatGroup> = groups

    fun optionsForGroup(groupKey: String): List<Screen4FormatOption> {
        val group = groups.firstOrNull { it.key == groupKey } ?: return emptyList()
        return group.typeNames.map { typeName ->
            val definition = Screen4ColumnTypeRegistry.resolveByConstraintType(typeName)
            Screen4FormatOption(
                typeName = definition.name,
                label = typeName.toDisplayLabel(),
                uiWidget = definition.uiWidget,
            )
        }
    }

    private fun String.toDisplayLabel(): String {
        return split('_').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
