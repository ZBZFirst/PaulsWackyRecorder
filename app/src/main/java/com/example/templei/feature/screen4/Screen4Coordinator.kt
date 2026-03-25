package com.example.templei.feature.screen4

/**
 * Phase 1 command-routing boundary between Screen4Activity and Screen4MeasurementEngine.
 *
 * Screen4Activity should stay a thin shell and route operations through this coordinator.
 */
class Screen4Coordinator(
    private val measurementEngine: Screen4MeasurementEngine,
    private val workbookCatalog: Screen4WorkbookCatalog,
) {
    private val validationEngine = Screen4ValidationEngine(Screen4ColumnTypeRegistry, workbookCatalog)

    suspend fun initialize(): TableViewModel = measurementEngine.initialize()

    suspend fun initializeColumnsForActiveWorkspace(initializeDefaultColumns: Boolean, visibleRows: Int): TableViewModel =
        measurementEngine.initializeColumnsForActiveWorkspace(initializeDefaultColumns, visibleRows)

    fun beginRapidEntry(): DraftRow = measurementEngine.beginRapidEntry()

    fun startNewDraft(): DraftRow = measurementEngine.startNewDraft()


    fun configureRapidEntry(
        activeColumnIds: Set<Long>,
        fillRulesByColumnId: Map<Long, RapidEntryFillRule> = emptyMap(),
    ): RapidEntryConfig = measurementEngine.configureRapidEntry(activeColumnIds, fillRulesByColumnId)

    fun currentRapidEntryConfig(): RapidEntryConfig = measurementEngine.currentRapidEntryConfig()

    fun userInputEvent(columnId: Long, value: String): DraftRow =
        measurementEngine.userInputEvent(columnId, value)

    suspend fun commitMeasurement(visibleRows: Int, useRapidEntryConfig: Boolean = false): Result<TableViewModel> =
        measurementEngine.commitMeasurement(visibleRows, useRapidEntryConfig)

    suspend fun deleteLatestMeasurement(visibleRows: Int): Pair<Boolean, TableViewModel> =
        measurementEngine.deleteLatestMeasurement(visibleRows)

    suspend fun listActiveWorkspaces(): List<TableWorkspaceEntity> =
        measurementEngine.listActiveWorkspaces()

    suspend fun activeWorkspace(): TableWorkspaceEntity? =
        measurementEngine.activeWorkspace()

    suspend fun createAndSelectWorkspace(name: String, visibleRows: Int, initializeDefaultColumns: Boolean): TableViewModel =
        measurementEngine.createAndSelectWorkspace(name, visibleRows, initializeDefaultColumns)

    suspend fun selectWorkspace(workspaceId: Long, visibleRows: Int): Boolean =
        measurementEngine.selectWorkspace(workspaceId, visibleRows)

    suspend fun listArchivedWorkspaces(): List<TableWorkspaceEntity> =
        measurementEngine.listArchivedWorkspaces()

    suspend fun archiveActiveWorkspace(visibleRows: Int): Boolean =
        measurementEngine.archiveActiveWorkspace(visibleRows)

    suspend fun restoreWorkspace(workspaceId: Long, visibleRows: Int): Boolean =
        measurementEngine.restoreWorkspace(workspaceId, visibleRows)

    suspend fun deleteSelectedMeasurement(rowId: Long, visibleRows: Int): Pair<Boolean, TableViewModel> =
        measurementEngine.deleteSelectedMeasurement(rowId, visibleRows)

    suspend fun startNewTable(visibleRows: Int): TableViewModel =
        measurementEngine.startNewTable(visibleRows)

    suspend fun beginEditFromRow(rowId: Long): DraftRow = measurementEngine.beginEditFromRow(rowId)

    suspend fun applyDraftToRow(rowId: Long, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.applyDraftToRow(rowId, visibleRows)

    suspend fun addColumn(label: String, constraintType: String, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.addColumn(label, constraintType, visibleRows)

    suspend fun addWorkbookColumn(metadataColumnName: String, label: String, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.addWorkbookColumn(metadataColumnName, label, visibleRows)

    suspend fun addNumericPolicyColumn(
        label: String,
        numericPolicy: Screen4NumericFormatPolicy,
        visibleRows: Int,
    ): Result<TableViewModel> = measurementEngine.addNumericPolicyColumn(label, numericPolicy, visibleRows)

    suspend fun pruneColumns(columnIds: List<Long>, visibleRows: Int): Result<TableViewModel> =
        measurementEngine.pruneColumns(columnIds, visibleRows)

    suspend fun tableModel(visibleRows: Int): TableViewModel = measurementEngine.tableModel(visibleRows)


    fun workbookColumnGroups(): List<Screen4WorkbookColumnGroup> = workbookCatalog.selectableGroups()

    fun workbookColumnSubgroups(groupKey: String): List<Screen4WorkbookColumnSubgroup> =
        workbookCatalog.subgroupsForGroup(groupKey)

    fun workbookColumnsForGroup(groupKey: String, subgroupKey: String): List<Screen4WorkbookColumnDefinition> =
        workbookCatalog.columnsForGroup(groupKey, subgroupKey)

    fun activeColumns(): List<ActiveColumn> = measurementEngine.activeColumns()

    fun currentDraft(): DraftRow = measurementEngine.currentDraft()


    fun resolveColumnType(column: ActiveColumn): ColumnTypeDefinition =
        Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)

    fun normalizeField(column: ActiveColumn, value: String): String =
        validationEngine.normalizeForInput(column, value)

    fun validateField(column: ActiveColumn, value: String): String? {
        val trimmed = validationEngine.normalizeForInput(column, value)
        if (column.required && trimmed.isBlank()) return "${column.label} is required"
        if (trimmed.length > column.maxLength) return "${column.label} exceeds max length ${column.maxLength}"
        return validationEngine.validate(column, trimmed, Screen4ValidationStage.INPUT)
    }

}
