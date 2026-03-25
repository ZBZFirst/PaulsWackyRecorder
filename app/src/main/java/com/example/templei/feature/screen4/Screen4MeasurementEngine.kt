package com.example.templei.feature.screen4

class Screen4MeasurementEngine(
    private val repository: Screen4Repository,
) {
    private var activeColumns: List<ActiveColumn> = emptyList()
    private var draftRow: DraftRow = DraftRow(mutableMapOf())
    private var rapidEntryConfig: RapidEntryConfig = RapidEntryConfig(emptySet(), emptyMap())

    suspend fun initialize(): TableViewModel {
        repository.ensureSchema()
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = repository.loadDraft().normalize(activeColumns)
        return repository.loadTable(limit = DEFAULT_VISIBLE_ROWS)
    }

    suspend fun initializeColumnsForActiveWorkspace(initializeDefaultColumns: Boolean, visibleRows: Int): TableViewModel {
        repository.initializeActiveWorkspaceColumns(initializeDefaultColumns)
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = repository.loadDraft().normalize(activeColumns)
        return repository.loadTable(limit = visibleRows)
    }

    fun beginRapidEntry(): DraftRow {
        draftRow = draftRow.normalize(activeColumns)
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun startNewDraft(): DraftRow {
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun userInputEvent(columnId: Long, value: String): DraftRow {
        draftRow.valuesByColumnId[columnId] = value
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun configureRapidEntry(
        activeColumnIds: Set<Long>,
        fillRulesByColumnId: Map<Long, RapidEntryFillRule> = emptyMap(),
    ): RapidEntryConfig {
        val allIds = activeColumns.map { it.columnId }.toSet()
        val sanitizedActive = (if (activeColumnIds.isEmpty()) allIds else activeColumnIds).intersect(allIds)
        val rules = activeColumns
            .filter { it.columnId in sanitizedActive }
            .associate { column ->
                column.columnId to (
                    fillRulesByColumnId[column.columnId]
                        ?.takeIf { it.isCompatibleWith(column) }
                        ?: defaultFillRuleFor(column)
                )
            }

        rapidEntryConfig = RapidEntryConfig(
            activeColumnIds = sanitizedActive,
            fillRulesByColumnId = rules,
        )
        repository.saveRapidEntryConfig(rapidEntryConfig)
        return rapidEntryConfig
    }

    suspend fun commitMeasurement(visibleRows: Int, useRapidEntryConfig: Boolean = false): Result<TableViewModel> {
        val candidateDraft = if (useRapidEntryConfig) {
            buildRapidEntryDraft()
        } else {
            draftRow
        }

        return repository.commitMeasurement(candidateDraft, activeColumns).mapCatching {
            draftRow = if (useRapidEntryConfig) {
                nextRapidEntryDraft()
            } else {
                DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
            }
            repository.saveDraft(draftRow)
            repository.loadTable(limit = visibleRows)
        }
    }


    suspend fun listActiveWorkspaces(): List<TableWorkspaceEntity> = repository.listActiveWorkspaces()

    suspend fun activeWorkspace(): TableWorkspaceEntity? = repository.activeWorkspace()

    suspend fun createAndSelectWorkspace(name: String, visibleRows: Int, initializeDefaultColumns: Boolean): TableViewModel {
        repository.createAndSelectWorkspace(name, initializeDefaultColumns)
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        repository.saveDraft(draftRow)
        return repository.loadTable(limit = visibleRows)
    }

    suspend fun selectWorkspace(workspaceId: Long, visibleRows: Int): Boolean {
        val selected = repository.selectWorkspace(workspaceId)
        if (!selected) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        repository.saveDraft(draftRow)
        return true
    }


    suspend fun listArchivedWorkspaces(): List<TableWorkspaceEntity> = repository.listArchivedWorkspaces()

    suspend fun archiveActiveWorkspace(visibleRows: Int): Boolean {
        val archived = repository.archiveActiveWorkspace()
        if (!archived) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        repository.saveDraft(draftRow)
        return true
    }

    suspend fun restoreWorkspace(workspaceId: Long, visibleRows: Int): Boolean {
        val restored = repository.restoreWorkspace(workspaceId)
        if (!restored) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        repository.saveDraft(draftRow)
        return true
    }

    suspend fun deleteLatestMeasurement(visibleRows: Int): Pair<Boolean, TableViewModel> {
        val deleted = repository.deleteLatestMeasurement()
        val table = repository.loadTable(limit = visibleRows)
        return deleted to table
    }

    suspend fun startNewTable(visibleRows: Int): TableViewModel {
        repository.startNewTable()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
        return repository.loadTable(limit = visibleRows)
    }

    suspend fun deleteSelectedMeasurement(rowId: Long, visibleRows: Int): Pair<Boolean, TableViewModel> {
        val deleted = repository.deleteMeasurementById(rowId)
        val table = repository.loadTable(limit = visibleRows)
        return deleted to table
    }

    suspend fun beginEditFromRow(rowId: Long): DraftRow {
        draftRow = repository.loadMeasurementDraftFromRow(rowId).normalize(activeColumns)
        repository.saveDraft(draftRow)
        return draftRow
    }

    suspend fun applyDraftToRow(rowId: Long, visibleRows: Int): Result<TableViewModel> {
        return repository.updateMeasurement(rowId, draftRow.valuesByColumnId, activeColumns).mapCatching {
            repository.loadTable(limit = visibleRows)
        }
    }

    suspend fun addColumn(label: String, constraintType: String, visibleRows: Int): Result<TableViewModel> {
        return repository.addColumn(label = label, constraintType = constraintType)
            .mapCatching {
                activeColumns = repository.loadActiveColumns()
                rapidEntryConfig = hydrateRapidEntryConfig(rapidEntryConfig)
                draftRow = draftRow.normalize(activeColumns)
                repository.saveDraft(draftRow)
                repository.loadTable(limit = visibleRows)
            }
    }

    suspend fun addWorkbookColumn(metadataColumnName: String, label: String, visibleRows: Int): Result<TableViewModel> {
        return repository.addWorkbookColumn(metadataColumnName = metadataColumnName, label = label)
            .mapCatching {
                activeColumns = repository.loadActiveColumns()
                rapidEntryConfig = hydrateRapidEntryConfig(rapidEntryConfig)
                draftRow = draftRow.normalize(activeColumns)
                repository.saveDraft(draftRow)
                repository.loadTable(limit = visibleRows)
            }
    }

    suspend fun addNumericPolicyColumn(
        label: String,
        numericPolicy: Screen4NumericFormatPolicy,
        visibleRows: Int,
    ): Result<TableViewModel> {
        return repository.addNumericPolicyColumn(label = label, numericPolicy = numericPolicy)
            .mapCatching {
                activeColumns = repository.loadActiveColumns()
                rapidEntryConfig = hydrateRapidEntryConfig(rapidEntryConfig)
                draftRow = draftRow.normalize(activeColumns)
                repository.saveDraft(draftRow)
                repository.loadTable(limit = visibleRows)
            }
    }

    suspend fun pruneColumns(columnIds: List<Long>, visibleRows: Int): Result<TableViewModel> {
        return repository.pruneColumns(columnIds, activeColumns).mapCatching {
            activeColumns = repository.loadActiveColumns()
            rapidEntryConfig = hydrateRapidEntryConfig(rapidEntryConfig)
            draftRow = draftRow.normalize(activeColumns)
            repository.saveDraft(draftRow)
            repository.loadTable(limit = visibleRows)
        }
    }

    suspend fun tableModel(visibleRows: Int): TableViewModel = repository.loadTable(limit = visibleRows)

    fun activeColumns(): List<ActiveColumn> = activeColumns

    fun currentDraft(): DraftRow = draftRow

    fun currentRapidEntryConfig(): RapidEntryConfig = rapidEntryConfig

    private fun buildRapidEntryDraft(): DraftRow {
        val base = draftRow.normalize(activeColumns).valuesByColumnId
        val activeIds = if (rapidEntryConfig.activeColumnIds.isEmpty()) {
            activeColumns.map { it.columnId }.toSet()
        } else {
            rapidEntryConfig.activeColumnIds
        }

        val composed = activeColumns.associate { column ->
            val fillRule = rapidEntryConfig.fillRulesByColumnId[column.columnId]
            val value = when {
                fillRule != null && fillRule.mode != RapidEntryFillMode.MANUAL -> autoValueFor(column, fillRule)
                column.columnId in activeIds -> base[column.columnId].orEmpty()
                else -> ""
            }
            column.columnId to value
        }.toMutableMap()

        return DraftRow(valuesByColumnId = composed)
    }

    private fun nextRapidEntryDraft(): DraftRow {
        val next = activeColumns.associate { column ->
            val fillRule = rapidEntryConfig.fillRulesByColumnId[column.columnId]
            val retained = if (fillRule != null && fillRule.mode != RapidEntryFillMode.MANUAL) {
                ""
            } else {
                draftRow.valuesByColumnId[column.columnId].orEmpty()
            }
            column.columnId to retained
        }.toMutableMap()
        return DraftRow(valuesByColumnId = next)
    }

    private fun defaultFillRuleFor(column: ActiveColumn): RapidEntryFillRule {
        val resolvedType = Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)
        return when {
            Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_TIMESTAMP)
            Screen4InputUiPolicy.usesDatePicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_DATE)
            Screen4InputUiPolicy.usesTimePicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_TIME)
            else -> RapidEntryFillRule(RapidEntryFillMode.MANUAL)
        }
    }

    private fun autoValueFor(column: ActiveColumn, rule: RapidEntryFillRule): String {
        val now = java.util.Calendar.getInstance()
        return when (rule.mode) {
            RapidEntryFillMode.MANUAL -> draftRow.valuesByColumnId[column.columnId].orEmpty()
            RapidEntryFillMode.CURRENT_DATE -> Screen4TemporalInputPolicy.formatDateForColumn(
                column,
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH),
            )
            RapidEntryFillMode.CURRENT_TIME -> Screen4TemporalInputPolicy.formatTime(
                column.constraintType,
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
            )
            RapidEntryFillMode.CURRENT_TIMESTAMP -> Screen4TemporalInputPolicy.formatTimestamp(
                column.constraintType,
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH),
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
            )
            RapidEntryFillMode.FIXED_VALUE -> rule.fixedValue.orEmpty()
        }
    }

    private fun hydrateRapidEntryConfig(candidate: RapidEntryConfig): RapidEntryConfig {
        val allIds = activeColumns.map { it.columnId }.toSet()
        val active = if (candidate.activeColumnIds.isEmpty()) allIds else candidate.activeColumnIds.intersect(allIds)
        val hydratedRules = activeColumns
            .filter { it.columnId in active }
            .associate { column ->
                column.columnId to (
                    candidate.fillRulesByColumnId[column.columnId]
                        ?.takeIf { it.isCompatibleWith(column) }
                        ?: defaultFillRuleFor(column)
                )
            }
        val hydrated = RapidEntryConfig(activeColumnIds = active, fillRulesByColumnId = hydratedRules)
        repository.saveRapidEntryConfig(hydrated)
        return hydrated
    }

    private fun DraftRow.normalize(columns: List<ActiveColumn>): DraftRow {
        val normalized = columns.associate { column ->
            column.columnId to valuesByColumnId[column.columnId].orEmpty()
        }.toMutableMap()
        return DraftRow(valuesByColumnId = normalized)
    }

    companion object {
        const val DEFAULT_VISIBLE_ROWS = 20
    }
}

private fun RapidEntryFillRule.isCompatibleWith(
    column: ActiveColumn,
): Boolean {
    val resolvedType = Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)
    return when (mode) {
        RapidEntryFillMode.MANUAL,
        RapidEntryFillMode.FIXED_VALUE -> true
        RapidEntryFillMode.CURRENT_DATE -> Screen4InputUiPolicy.usesDatePicker(column, resolvedType)
        RapidEntryFillMode.CURRENT_TIME -> Screen4InputUiPolicy.usesTimePicker(column, resolvedType)
        RapidEntryFillMode.CURRENT_TIMESTAMP -> Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType)
    }
}
