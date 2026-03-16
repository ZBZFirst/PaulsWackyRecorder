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

    fun configureRapidEntry(activeColumnIds: Set<Long>): RapidEntryConfig {
        val allIds = activeColumns.map { it.columnId }.toSet()
        val sanitizedActive = (if (activeColumnIds.isEmpty()) allIds else activeColumnIds).intersect(allIds)
        val autoColumns = activeColumns
            .filter { it.columnId in sanitizedActive }
            .filter { shouldAutoFill(it) }
            .associate { it.columnId to AutoValueSource.SYSTEM_TIME_UNIX_MS }

        rapidEntryConfig = RapidEntryConfig(
            activeColumnIds = sanitizedActive,
            autoColumns = autoColumns,
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

    suspend fun deleteLatestMeasurement(visibleRows: Int): Pair<Boolean, TableViewModel> {
        val deleted = repository.deleteLatestMeasurement()
        val table = repository.loadTable(limit = visibleRows)
        return deleted to table
    }

    suspend fun startNewTable(visibleRows: Int): TableViewModel {
        repository.startNewTable()
        selectedRapidEntry = repository.loadRapidEntryConfig()
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
            val value = when {
                column.columnId in rapidEntryConfig.autoColumns -> autoValueFor(rapidEntryConfig.autoColumns.getValue(column.columnId))
                column.columnId in activeIds -> base[column.columnId].orEmpty()
                else -> ""
            }
            column.columnId to value
        }.toMutableMap()

        return DraftRow(valuesByColumnId = composed)
    }

    private fun nextRapidEntryDraft(): DraftRow {
        val next = activeColumns.associate { column ->
            val retained = if (column.columnId in rapidEntryConfig.autoColumns) "" else draftRow.valuesByColumnId[column.columnId].orEmpty()
            column.columnId to retained
        }.toMutableMap()
        return DraftRow(valuesByColumnId = next)
    }

    private fun shouldAutoFill(column: ActiveColumn): Boolean {
        val normalized = column.constraintType.trim().uppercase()
        return normalized == "TIMESTAMP" || normalized == "TIMESTAMP_UNIX_MS"
    }

    private fun autoValueFor(source: AutoValueSource): String {
        return when (source) {
            AutoValueSource.SYSTEM_TIME_UNIX_MS -> System.currentTimeMillis().toString()
        }
    }

    private fun hydrateRapidEntryConfig(candidate: RapidEntryConfig): RapidEntryConfig {
        val allIds = activeColumns.map { it.columnId }.toSet()
        val active = if (candidate.activeColumnIds.isEmpty()) allIds else candidate.activeColumnIds.intersect(allIds)
        val auto = candidate.autoColumns.filterKeys { it in active && it in allIds }
        val hydrated = RapidEntryConfig(activeColumnIds = active, autoColumns = auto)
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
