package com.example.templei.feature.screen4

class Screen4MeasurementEngine(
    private val repository: Screen4Repository,
) {
    private var activeColumns: List<ActiveColumn> = emptyList()
    private var draftRow: DraftRow = DraftRow(mutableMapOf())

    suspend fun initialize(): TableViewModel {
        repository.ensureSchema()
        activeColumns = repository.loadActiveColumns()
        draftRow = repository.loadDraft().normalize(activeColumns)
        return repository.loadTable(limit = DEFAULT_VISIBLE_ROWS)
    }

    fun beginRapidEntry(): DraftRow {
        draftRow = draftRow.normalize(activeColumns)
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun userInputEvent(columnId: Long, value: String): DraftRow {
        draftRow.valuesByColumnId[columnId] = value
        repository.saveDraft(draftRow)
        return draftRow
    }

    suspend fun commitMeasurement(visibleRows: Int): Result<TableViewModel> {
        return repository.commitMeasurement(draftRow, activeColumns).mapCatching {
            draftRow = DraftRow(activeColumns.associate { it.columnId to "" }.toMutableMap())
            repository.saveDraft(draftRow)
            repository.loadTable(limit = visibleRows)
        }
    }

    suspend fun deleteLatestMeasurement(visibleRows: Int): Pair<Boolean, TableViewModel> {
        val deleted = repository.deleteLatestMeasurement()
        val table = repository.loadTable(limit = visibleRows)
        return deleted to table
    }

    suspend fun tableModel(visibleRows: Int): TableViewModel = repository.loadTable(limit = visibleRows)

    fun activeColumns(): List<ActiveColumn> = activeColumns

    fun currentDraft(): DraftRow = draftRow

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
