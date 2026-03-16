package com.example.templei.feature.screen4

/**
 * Phase 1 command-routing boundary between Screen4Activity and Screen4MeasurementEngine.
 *
 * Screen4Activity should stay a thin shell and route operations through this coordinator.
 */
class Screen4Coordinator(
    private val measurementEngine: Screen4MeasurementEngine,
) {
    private val validationEngine = Screen4ValidationEngine(Screen4ColumnTypeRegistry)

    suspend fun initialize(): TableViewModel = measurementEngine.initialize()

    fun beginRapidEntry(): DraftRow = measurementEngine.beginRapidEntry()

    fun startNewDraft(): DraftRow = measurementEngine.startNewDraft()


    fun configureRapidEntry(activeColumnIds: Set<Long>): RapidEntryConfig =
        measurementEngine.configureRapidEntry(activeColumnIds)

    fun currentRapidEntryConfig(): RapidEntryConfig = measurementEngine.currentRapidEntryConfig()

    fun userInputEvent(columnId: Long, value: String): DraftRow =
        measurementEngine.userInputEvent(columnId, value)

    suspend fun commitMeasurement(visibleRows: Int, useRapidEntryConfig: Boolean = false): Result<TableViewModel> =
        measurementEngine.commitMeasurement(visibleRows, useRapidEntryConfig)

    suspend fun deleteLatestMeasurement(visibleRows: Int): Pair<Boolean, TableViewModel> =
        measurementEngine.deleteLatestMeasurement(visibleRows)

    suspend fun deleteSelectedMeasurement(rowId: Long, visibleRows: Int): Pair<Boolean, TableViewModel> =
        measurementEngine.deleteSelectedMeasurement(rowId, visibleRows)

    suspend fun startNewTable(visibleRows: Int): TableViewModel =
        measurementEngine.startNewTable(visibleRows)

    suspend fun beginEditFromRow(rowId: Long): DraftRow = measurementEngine.beginEditFromRow(rowId)

    suspend fun applyDraftToRow(rowId: Long, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.applyDraftToRow(rowId, visibleRows)

    suspend fun addColumn(label: String, constraintType: String, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.addColumn(label, constraintType, visibleRows)

    suspend fun pruneColumns(columnIds: List<Long>, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.pruneColumns(columnIds, visibleRows)

    suspend fun tableModel(visibleRows: Int): TableViewModel = measurementEngine.tableModel(visibleRows)


    fun columnFormatGroups(): List<Screen4FormatGroup> = Screen4ColumnFormatCatalog.allGroups()

    fun columnFormatOptions(groupKey: String): List<Screen4FormatOption> =
        Screen4ColumnFormatCatalog.optionsForGroup(groupKey)

    fun activeColumns(): List<ActiveColumn> = measurementEngine.activeColumns()

    fun currentDraft(): DraftRow = measurementEngine.currentDraft()


    fun resolveColumnType(column: ActiveColumn): ColumnTypeDefinition =
        Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)

    fun validateField(column: ActiveColumn, value: String): String? {
        val trimmed = value.trim()
        if (column.required && trimmed.isBlank()) return "${column.label} is required"
        if (trimmed.length > column.maxLength) return "${column.label} exceeds max length ${column.maxLength}"
        return validationEngine.validate(column, trimmed)
    }

}
