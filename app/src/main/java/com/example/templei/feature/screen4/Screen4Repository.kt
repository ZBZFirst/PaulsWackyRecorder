package com.example.templei.feature.screen4

import androidx.room.withTransaction

class Screen4Repository(
    private val database: Screen4Database,
    private val draftStore: Screen4DraftStore,
    private val rapidEntryStore: Screen4RapidEntryStore,
    private val tableSessionStore: Screen4TableSessionStore,
) {
    private val dao = database.screen4Dao()
    private val validationEngine = Screen4ValidationEngine(Screen4ColumnTypeRegistry)
    private val sessionStateMachine = Screen4WorkspaceSessionStateMachine()
    private var activeWorkspaceId: Long = NO_ACTIVE_WORKSPACE

    suspend fun ensureSchema() {
        ensureWorkspace()

        if (dao.getTemplates().isEmpty()) {
            val templates = Screen4TemplateCatalog.defaults()
            dao.insertTemplates(templates)
        }

        ensureWorkspaceColumns(activeWorkspaceId)
    }

    suspend fun loadActiveColumns(): List<ActiveColumn> {
        val templatesById = dao.getTemplates().associateBy { it.id }
        return dao.getActiveColumns(activeWorkspaceId).mapNotNull { column ->
            val template = templatesById[column.templateId] ?: return@mapNotNull null
            ActiveColumn(
                columnId = column.id,
                templateId = template.id,
                fakerKey = template.fakerKey,
                constraintType = template.constraintType,
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

    fun loadRapidEntryConfig(): RapidEntryConfig = rapidEntryStore.load()

    fun saveRapidEntryConfig(config: RapidEntryConfig) {
        rapidEntryStore.save(config)
    }

    suspend fun listActiveWorkspaces(): List<TableWorkspaceEntity> = dao.getActiveWorkspaces()

    suspend fun createAndSelectWorkspace(name: String): TableWorkspaceEntity {
        val trimmedName = name.trim().ifBlank { DEFAULT_WORKSPACE_NAME }
        val workspaceId = dao.insertWorkspace(
            TableWorkspaceEntity(
                name = trimmedName,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
        activeWorkspaceId = workspaceId
        tableSessionStore.saveActiveWorkspaceId(workspaceId)
        sessionStateMachine.onWorkspaceSelectedActive()
        ensureWorkspaceColumns(workspaceId)
        draftStore.clearDraft()
        return dao.getActiveWorkspaceById(workspaceId)
            ?: TableWorkspaceEntity(id = workspaceId, name = trimmedName, createdAtMillis = System.currentTimeMillis())
    }

    suspend fun selectWorkspace(workspaceId: Long): Boolean {
        val workspace = dao.getActiveWorkspaceById(workspaceId) ?: return false
        activeWorkspaceId = workspace.id
        tableSessionStore.saveActiveWorkspaceId(workspace.id)
        sessionStateMachine.onWorkspaceSelectedActive()
        ensureWorkspaceColumns(workspace.id)
        draftStore.clearDraft()
        return true
    }

    suspend fun listArchivedWorkspaces(): List<TableWorkspaceEntity> = dao.getArchivedWorkspaces()

    suspend fun archiveActiveWorkspace(): Boolean {
        val current = dao.getActiveWorkspaceById(activeWorkspaceId) ?: return false
        sessionStateMachine.onWorkspaceSelectedActive()
        val archived = dao.archiveWorkspace(current.id, System.currentTimeMillis()) > 0
        if (!archived) return false
        sessionStateMachine.onWorkspaceArchived()

        val fallback = dao.getActiveWorkspaces().firstOrNull()
        if (fallback != null) {
            activeWorkspaceId = fallback.id
            tableSessionStore.saveActiveWorkspaceId(fallback.id)
            ensureWorkspaceColumns(fallback.id)
        } else {
            val newWorkspaceId = dao.insertWorkspace(
                TableWorkspaceEntity(
                    name = DEFAULT_WORKSPACE_NAME,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
            activeWorkspaceId = newWorkspaceId
            tableSessionStore.saveActiveWorkspaceId(newWorkspaceId)
            ensureWorkspaceColumns(newWorkspaceId)
        }

        draftStore.clearDraft()
        sessionStateMachine.onWorkspaceSelectedActive()
        return true
    }

    suspend fun restoreWorkspace(workspaceId: Long): Boolean {
        val archived = dao.getWorkspaceById(workspaceId)?.archivedAtMillis != null
        if (!archived) return false

        val restored = dao.restoreWorkspace(workspaceId) > 0
        if (!restored) return false

        activeWorkspaceId = workspaceId
        tableSessionStore.saveActiveWorkspaceId(workspaceId)
        sessionStateMachine.onWorkspaceSelectedActive()
        ensureWorkspaceColumns(workspaceId)
        draftStore.clearDraft()
        return true
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
                    workspaceId = activeWorkspaceId,
                    templateId = primaryTemplate,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
            val cells = activeColumns.map { column ->
                CellEntity(
                    workspaceId = activeWorkspaceId,
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
        val latest = dao.getLatestRow(activeWorkspaceId) ?: return false
        dao.deleteRow(activeWorkspaceId, latest.id)
        return true
    }

    suspend fun startNewTable() {
        database.withTransaction {
            dao.deleteAllRows(activeWorkspaceId)
        }
        draftStore.clearDraft()
    }

    suspend fun deleteMeasurementById(rowId: Long): Boolean {
        val row = dao.getRowById(activeWorkspaceId, rowId) ?: return false
        dao.deleteRow(activeWorkspaceId, row.id)
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

        val row = dao.getRowById(activeWorkspaceId, rowId)
            ?: return Result.failure(IllegalArgumentException("Row $rowId not found"))

        database.withTransaction {
            val cells = activeColumns.map { column ->
                CellEntity(
                    workspaceId = activeWorkspaceId,
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

    suspend fun addColumn(label: String, constraintType: String): Result<Long> {
        val resolvedType = Screen4ColumnTypeRegistry.resolveByConstraintType(constraintType).name
        val template = dao.getTemplateByConstraintType(resolvedType)
            ?: createDynamicTemplate(resolvedType, label.ifBlank { resolvedType })

        val nextPosition = dao.getMaxColumnPosition(activeWorkspaceId) + 1
        val columnId = dao.insertColumn(
            ColumnEntity(
                workspaceId = activeWorkspaceId,
                templateId = template.id,
                position = nextPosition,
                label = label.ifBlank { template.defaultLabel },
                isActive = true,
            )
        )
        return Result.success(columnId)
    }

    suspend fun pruneColumns(columnIds: List<Long>, activeColumns: List<ActiveColumn>): Result<Int> {
        if (columnIds.isEmpty()) return Result.success(0)

        val requiredColumnIds = activeColumns.filter { it.required }.map { it.columnId }.toSet()
        if (columnIds.any { it in requiredColumnIds }) {
            return Result.failure(IllegalArgumentException("Required columns cannot be pruned"))
        }

        dao.deactivateColumns(activeWorkspaceId, columnIds)
        return Result.success(columnIds.size)
    }

    suspend fun loadMeasurementDraftFromRow(rowId: Long): DraftRow {
        val cells = dao.getCellsForRow(activeWorkspaceId, rowId)
        return DraftRow(
            valuesByColumnId = cells.associate { it.columnId to it.value }.toMutableMap()
        )
    }

    suspend fun loadTable(limit: Int): TableViewModel {
        val columns = loadActiveColumns()
        val rows = dao.getRows(activeWorkspaceId, limit)
        val rowIds = rows.map { it.id }
        val cells = if (rowIds.isEmpty()) emptyList() else dao.getCellsForRows(activeWorkspaceId, rowIds)
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

    private suspend fun ensureWorkspaceColumns(workspaceId: Long) {
        if (dao.getActiveColumns(workspaceId).isNotEmpty()) return

        val createdTemplates = dao.getTemplates()
        val columns = createdTemplates.mapIndexed { index, template ->
            ColumnEntity(
                workspaceId = workspaceId,
                templateId = template.id,
                position = index,
                label = template.defaultLabel,
                isActive = true,
            )
        }
        dao.insertColumns(columns)
    }

    private suspend fun ensureWorkspace() {
        val requestedWorkspaceId = tableSessionStore.loadActiveWorkspaceId()

        if (requestedWorkspaceId != null) {
            val requestedWorkspace = dao.getActiveWorkspaceById(requestedWorkspaceId)
            if (requestedWorkspace != null) {
                activeWorkspaceId = requestedWorkspace.id
                sessionStateMachine.onWorkspaceSelectedActive()
                return
            }
            tableSessionStore.clearActiveWorkspaceId()
        }

        val existing = dao.getActiveWorkspaces().firstOrNull()
        if (existing != null) {
            activeWorkspaceId = existing.id
            tableSessionStore.saveActiveWorkspaceId(existing.id)
            sessionStateMachine.onWorkspaceSelectedActive()
            return
        }

        activeWorkspaceId = dao.insertWorkspace(
            TableWorkspaceEntity(
                name = DEFAULT_WORKSPACE_NAME,
                createdAtMillis = System.currentTimeMillis(),
            )
        )
        tableSessionStore.saveActiveWorkspaceId(activeWorkspaceId)
        sessionStateMachine.onWorkspaceSelectedActive()
    }

    private suspend fun createDynamicTemplate(resolvedType: String, fallbackLabel: String): ColumnTemplateEntity {
        val definition = Screen4ColumnTypeRegistry.resolveByConstraintType(resolvedType)
        val maxLength = when (definition.primitiveType) {
            Screen4PrimitiveType.INTEGER -> 16
            Screen4PrimitiveType.DECIMAL -> 24
            Screen4PrimitiveType.TIMESTAMP -> 16
            else -> 64
        }

        val template = ColumnTemplateEntity(
            fakerKey = "screen4.dynamic.$resolvedType",
            defaultLabel = fallbackLabel,
            constraintType = definition.name,
            maxLength = maxLength,
            required = false,
        )
        val id = dao.insertTemplate(template)
        return template.copy(id = id)
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
            val validationError = validationEngine.validate(column, value)
            if (validationError != null) {
                return "${column.label}: $validationError"
            }
        }
        return null
    }

    companion object {
        private const val DEFAULT_WORKSPACE_NAME = "Default Table"
        private const val NO_ACTIVE_WORKSPACE = -1L
    }
}
