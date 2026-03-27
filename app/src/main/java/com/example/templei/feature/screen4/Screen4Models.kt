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

data class Screen4TableTemplateDefinition(
    val key: String,
    val label: String,
    val description: String,
    val seeds: List<Screen4ColumnSeed>,
)

data class Screen4ColumnSeed(
    val fakerKey: String,
    val label: String,
    val constraintType: String,
    val maxLength: Int,
    val required: Boolean,
)

data class Screen4ColumnConfiguration(
    val columnId: Long,
    val label: String,
    val required: Boolean,
    val maxLength: Int,
    val numericPolicy: Screen4NumericFormatPolicy? = null,
)

object Screen4TemplateCatalog {
    const val DEFAULT_TEMPLATE_KEY = "serial_measurement"
    const val GROCERY_LIST_TEMPLATE_KEY = "grocery_list"
    const val CONTACT_LIST_TEMPLATE_KEY = "contact_list"
    const val PATIENT_VITALS_TEMPLATE_KEY = "patient_vitals"

    fun defaults(): List<ColumnTemplateEntity> = listOf(
        ColumnTemplateEntity(fakerKey = "faker.number.digits", defaultLabel = "Sample ID", constraintType = "INTEGER", maxLength = 12, required = true),
        ColumnTemplateEntity(fakerKey = "faker.address.city", defaultLabel = "Site", constraintType = "TEXT", maxLength = 48, required = true),
        ColumnTemplateEntity(fakerKey = "faker.science.unit", defaultLabel = "Reading", constraintType = "DECIMAL", maxLength = 16, required = true),
        ColumnTemplateEntity(fakerKey = "faker.lorem.word", defaultLabel = "Notes", constraintType = "TEXT", maxLength = 64, required = false),
    )

    fun tableTemplates(): List<Screen4TableTemplateDefinition> = listOf(
        Screen4TableTemplateDefinition(
            key = DEFAULT_TEMPLATE_KEY,
            label = "Serial Measurement",
            description = "General-purpose measurement rows with date, time, item, quantity, and comment.",
            seeds = listOf(
                Screen4ColumnSeed("screen4.default.id", "ID", "integer", 16, true),
                Screen4ColumnSeed("screen4.default.date", "Date", "date_mdy_slash_yyyy", 10, true),
                Screen4ColumnSeed("screen4.default.time", "Time", "time_hh_mm_am_pm", 11, true),
                Screen4ColumnSeed("screen4.default.item", "Item", "text", 64, true),
                Screen4ColumnSeed("screen4.default.quantity", "Quantity", "decimal", 8, true),
                Screen4ColumnSeed("screen4.default.comment", "Comment", "text", 256, false),
            ),
        ),
        Screen4TableTemplateDefinition(
            key = GROCERY_LIST_TEMPLATE_KEY,
            label = "Grocery List",
            description = "Track shopping items, quantity, unit, aisle, and notes.",
            seeds = listOf(
                Screen4ColumnSeed("screen4.grocery.id", "ID", "integer", 16, true),
                Screen4ColumnSeed("screen4.grocery.date", "Date", "date_mdy_slash_yyyy", 10, false),
                Screen4ColumnSeed("screen4.grocery.item", "Item", "text", 64, true),
                Screen4ColumnSeed("screen4.grocery.quantity", "Quantity", "decimal", 8, true),
                Screen4ColumnSeed("screen4.grocery.unit", "Unit", "text", 24, false),
                Screen4ColumnSeed("screen4.grocery.aisle", "Aisle", "text", 24, false),
                Screen4ColumnSeed("screen4.grocery.note", "Note", "text", 128, false),
            ),
        ),
        Screen4TableTemplateDefinition(
            key = CONTACT_LIST_TEMPLATE_KEY,
            label = "Contact List",
            description = "Basic contact capture with names, phone, email, city, and notes.",
            seeds = listOf(
                Screen4ColumnSeed("screen4.contact.id", "ID", "integer", 16, true),
                Screen4ColumnSeed("screen4.contact.first_name", "First Name", "text", 40, true),
                Screen4ColumnSeed("screen4.contact.last_name", "Last Name", "text", 40, false),
                Screen4ColumnSeed("screen4.contact.phone", "Phone", "text", 24, false),
                Screen4ColumnSeed("screen4.contact.email", "Email", "text", 64, false),
                Screen4ColumnSeed("screen4.contact.city", "City", "text", 40, false),
                Screen4ColumnSeed("screen4.contact.note", "Note", "text", 128, false),
            ),
        ),
        Screen4TableTemplateDefinition(
            key = PATIENT_VITALS_TEMPLATE_KEY,
            label = "Patient Vitals",
            description = "Patient identifier with vital signs like heart rate, blood pressure, temperature, and oxygen saturation.",
            seeds = listOf(
                Screen4ColumnSeed("screen4.vitals.patient_id", "Patient ID", "integer", 16, true),
                Screen4ColumnSeed("screen4.vitals.date", "Date", "date_mdy_slash_yyyy", 10, true),
                Screen4ColumnSeed("screen4.vitals.time", "Time", "time_hh_mm_am_pm", 11, true),
                Screen4ColumnSeed("screen4.vitals.heart_rate", "Heart Rate", "integer", 8, true),
                Screen4ColumnSeed("screen4.vitals.temperature", "Temperature", "decimal", 8, true),
                Screen4ColumnSeed("screen4.vitals.systolic", "Systolic", "integer", 8, true),
                Screen4ColumnSeed("screen4.vitals.diastolic", "Diastolic", "integer", 8, true),
                Screen4ColumnSeed("screen4.vitals.respiration", "Respiratory Rate", "integer", 8, false),
                Screen4ColumnSeed("screen4.vitals.oxygen", "Oxygen Saturation", "integer", 8, false),
                Screen4ColumnSeed("screen4.vitals.comment", "Comment", "text", 128, false),
            ),
        ),
    )

    fun templateByKey(key: String): Screen4TableTemplateDefinition? =
        tableTemplates().firstOrNull { it.key == key }
}
