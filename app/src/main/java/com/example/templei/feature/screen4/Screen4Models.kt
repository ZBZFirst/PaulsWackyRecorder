package com.example.templei.feature.screen4

data class ActiveColumn(
    val columnId: Long,
    val templateId: Long,
    val fakerKey: String,
    val constraintType: String,
    val label: String,
    val maxLength: Int,
    val required: Boolean,
    val metadata: Screen4WorkbookColumnDefinition? = null,
    val numericPolicy: Screen4NumericFormatPolicy? = null,
)

data class DraftRow(
    val valuesByColumnId: MutableMap<Long, String>,
)

data class TableViewRow(
    val rowId: Long,
    val createdAtMillis: Long,
    val valuesByColumnId: Map<Long, String>,
)

data class TableViewModel(
    val columns: List<ActiveColumn>,
    val rows: List<TableViewRow>,
)

object Screen4TemplateCatalog {
    const val DEFAULT_TEMPLATE_KEY = "serial_measurement"

    fun defaults(): List<ColumnTemplateEntity> = listOf(
        ColumnTemplateEntity(fakerKey = "faker.number.digits", defaultLabel = "Sample ID", constraintType = "INTEGER", maxLength = 12, required = true),
        ColumnTemplateEntity(fakerKey = "faker.address.city", defaultLabel = "Site", constraintType = "TEXT", maxLength = 48, required = true),
        ColumnTemplateEntity(fakerKey = "faker.science.unit", defaultLabel = "Reading", constraintType = "DECIMAL", maxLength = 16, required = true),
        ColumnTemplateEntity(fakerKey = "faker.lorem.word", defaultLabel = "Notes", constraintType = "TEXT", maxLength = 64, required = false),
    )
}
