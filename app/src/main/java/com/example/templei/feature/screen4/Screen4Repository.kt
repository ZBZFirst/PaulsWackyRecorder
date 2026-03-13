package com.example.templei.feature.screen4

import androidx.room.withTransaction

class Screen4Repository(
    private val database: Screen4Database,
    private val draftStore: Screen4DraftStore,
) {
    private val dao = database.screen4Dao()

    suspend fun ensureSchema() {
        if (dao.getTemplates().isNotEmpty()) return

        val templates = Screen4TemplateCatalog.defaults()
        dao.insertTemplates(templates)
        val createdTemplates = dao.getTemplates()
        val columns = createdTemplates.mapIndexed { index, template ->
            ColumnEntity(
                templateId = template.id,
                position = index,
                label = template.defaultLabel,
                isActive = true,
            )
        }
        dao.insertColumns(columns)
    }

    suspend fun loadActiveColumns(): List<ActiveColumn> {
        val templatesById = dao.getTemplates().associateBy { it.id }
        return dao.getActiveColumns().mapNotNull { column ->
            val template = templatesById[column.templateId] ?: return@mapNotNull null
            ActiveColumn(
                columnId = column.id,
                templateId = template.id,
                fakerKey = template.fakerKey,
                label = column.label,
                maxLength = template.maxLength,
                required = template.required,
            )
        }
    }

    fun loadDraft(): DraftRow = DraftRow(valuesByColumnId = draftStore.loadDraft())

    fun saveDraft(draftRow: DraftRow) {
        draftStore.saveDraft(draftRow.valuesByColumnId)
    }

    suspend fun commitMeasurement(draftRow: DraftRow, activeColumns: List<ActiveColumn>): Result<Long> {
        val validationError = validateDraft(draftRow, activeColumns)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        val rowId = database.withTransaction {
            val primaryTemplate = activeColumns.firstOrNull()?.templateId ?: 0L
            val insertedRowId = dao.insertRow(
                RowEntity(
                    templateId = primaryTemplate,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
            val cells = activeColumns.map { column ->
                CellEntity(
                    rowId = insertedRowId,
                    columnId = column.columnId,
                    templateId = column.templateId,
                    value = draftRow.valuesByColumnId[column.columnId].orEmpty().trim(),
                )
            }
            dao.insertCells(cells)
            insertedRowId
        }

        draftStore.clearDraft()
        return Result.success(rowId)
    }

    suspend fun deleteLatestMeasurement(): Boolean {
        val latest = dao.getLatestRow() ?: return false
        dao.deleteRow(latest.id)
        return true
    }

    suspend fun loadTable(limit: Int): TableViewModel {
        val columns = loadActiveColumns()
        val rows = dao.getRows(limit)
        val rowIds = rows.map { it.id }
        val cells = if (rowIds.isEmpty()) emptyList() else dao.getCellsForRows(rowIds)
        val cellsByRow = cells.groupBy { it.rowId }

        val viewRows = rows.map { row ->
            val rowCells = cellsByRow[row.id].orEmpty().associate { it.columnId to it.value }
            TableViewRow(
                rowId = row.id,
                createdAtMillis = row.createdAtMillis,
                valuesByColumnId = rowCells,
            )
        }
        return TableViewModel(columns = columns, rows = viewRows)
    }

    private fun validateDraft(draftRow: DraftRow, activeColumns: List<ActiveColumn>): String? {
        activeColumns.forEach { column ->
            val value = draftRow.valuesByColumnId[column.columnId].orEmpty().trim()
            if (column.required && value.isBlank()) {
                return "${column.label} is required"
            }
            if (value.length > column.maxLength) {
                return "${column.label} exceeds max length ${column.maxLength}"
            }
        }
        return null
    }
}
