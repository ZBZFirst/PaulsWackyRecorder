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

    suspend fun deleteMeasurementById(rowId: Long): Boolean {
        val row = dao.getRowById(rowId) ?: return false
        dao.deleteRow(row.id)
        return true
    }

    suspend fun updateMeasurement(
        rowId: Long,
        updatedValuesByColumnId: Map<Long, String>,
        activeColumns: List<ActiveColumn>,
    ): Result<Unit> {
        val candidateDraft = DraftRow(valuesByColumnId = updatedValuesByColumnId.toMutableMap())
        val validationError = validateDraft(candidateDraft, activeColumns)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        val row = dao.getRowById(rowId)
            ?: return Result.failure(IllegalArgumentException("Row $rowId not found"))

        database.withTransaction {
            val cells = activeColumns.map { column ->
                CellEntity(
                    rowId = row.id,
                    columnId = column.columnId,
                    templateId = column.templateId,
                    value = updatedValuesByColumnId[column.columnId].orEmpty().trim(),
                )
            }
            dao.upsertCells(cells)
        }
        return Result.success(Unit)
    }

    suspend fun addColumn(label: String, sourceTemplateId: Long?): Result<Long> {
        val templates = dao.getTemplates()
        if (templates.isEmpty()) {
            return Result.failure(IllegalStateException("No templates available"))
        }
        val template = if (sourceTemplateId == null) {
            templates.first()
        } else {
            templates.firstOrNull { it.id == sourceTemplateId } ?: templates.first()
        }

        val nextPosition = dao.getMaxColumnPosition() + 1
        val columnId = dao.insertColumn(
            ColumnEntity(
                templateId = template.id,
                position = nextPosition,
                label = label.ifBlank { template.defaultLabel },
                isActive = true,
            )
        )
        return Result.success(columnId)
    }

    suspend fun loadMeasurementDraftFromRow(rowId: Long): DraftRow {
        val cells = dao.getCellsForRow(rowId)
        return DraftRow(
            valuesByColumnId = cells.associate { it.columnId to it.value }.toMutableMap()
        )
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
