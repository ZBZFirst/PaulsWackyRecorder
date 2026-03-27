package com.example.templei.feature.screen4

import androidx.room.withTransaction

class Screen4Repository(
    private val database: Screen4Database,
    private val draftStore: Screen4DraftStore,
    private val rapidEntryStore: Screen4RapidEntryStore,
    private val tableSessionStore: Screen4TableSessionStore,
    private val workbookCatalog: Screen4WorkbookCatalog,
) {
    private data class DraftValidationResult(
        val normalizedValuesByColumnId: Map<Long, String>,
        val errorMessage: String?,
    )

    private val dao = database.screen4Dao()
    private val validationEngine = Screen4ValidationEngine(Screen4ColumnTypeRegistry, workbookCatalog)
    private val sessionStateMachine = Screen4WorkspaceSessionStateMachine()
    private var activeWorkspaceId: Long = NO_ACTIVE_WORKSPACE

    suspend fun ensureSchema() {
        ensureWorkspace()

        if (dao.getTemplates().isEmpty()) {
            val templates = Screen4TemplateCatalog.defaults()
            dao.insertTemplates(templates)
        }
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
                metadata = workbookCatalog.findDefinitionForTemplate(template.fakerKey, template.constraintType),
                numericPolicy = Screen4NumericFormatPolicy.fromFakerKey(template.fakerKey),
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

    suspend fun activeWorkspace(): TableWorkspaceEntity? = dao.getActiveWorkspaceById(activeWorkspaceId)

    suspend fun createAndSelectWorkspace(name: String, templateKey: String?): TableWorkspaceEntity {
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
        if (templateKey != null) {
            seedTemplateColumns(workspaceId, templateKey)
        }
        draftStore.clearDraft()
        return dao.getActiveWorkspaceById(workspaceId)
            ?: TableWorkspaceEntity(id = workspaceId, name = trimmedName, createdAtMillis = System.currentTimeMillis())
    }

    suspend fun initializeActiveWorkspaceColumns(templateKey: String?) {
        if (dao.getActiveColumns(activeWorkspaceId).isNotEmpty()) return
        if (templateKey == null) return
        seedTemplateColumns(activeWorkspaceId, templateKey)
    }

    suspend fun selectWorkspace(workspaceId: Long): Boolean {
        val workspace = dao.getActiveWorkspaceById(workspaceId) ?: return false
        activeWorkspaceId = workspace.id
        tableSessionStore.saveActiveWorkspaceId(workspace.id)
        sessionStateMachine.onWorkspaceSelectedActive()
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
        } else {
            val newWorkspaceId = dao.insertWorkspace(
                TableWorkspaceEntity(
                    name = DEFAULT_WORKSPACE_NAME,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
            activeWorkspaceId = newWorkspaceId
            tableSessionStore.saveActiveWorkspaceId(newWorkspaceId)
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
        draftStore.clearDraft()
        return true
    }

    suspend fun renameActiveWorkspace(name: String): TableWorkspaceEntity? {
        val current = dao.getActiveWorkspaceById(activeWorkspaceId) ?: return null
        val trimmed = name.trim().ifBlank { current.name }
        val renamed = dao.renameWorkspace(current.id, trimmed) > 0
        if (!renamed) return null
        return dao.getActiveWorkspaceById(current.id)
    }

    suspend fun deleteActiveWorkspace(): Boolean {
        val current = dao.getActiveWorkspaceById(activeWorkspaceId) ?: return false
        val deleted = database.withTransaction {
            dao.deleteWorkspace(current.id) > 0
        }
        if (!deleted) return false

        val fallback = dao.getActiveWorkspaces().firstOrNull()
        if (fallback != null) {
            activeWorkspaceId = fallback.id
            tableSessionStore.saveActiveWorkspaceId(fallback.id)
            sessionStateMachine.onWorkspaceSelectedActive()
        } else {
            val newWorkspaceId = dao.insertWorkspace(
                TableWorkspaceEntity(
                    name = DEFAULT_WORKSPACE_NAME,
                    createdAtMillis = System.currentTimeMillis(),
                )
            )
            activeWorkspaceId = newWorkspaceId
            tableSessionStore.saveActiveWorkspaceId(newWorkspaceId)
            sessionStateMachine.onWorkspaceSelectedActive()
        }

        draftStore.clearDraft()
        return true
    }

    suspend fun commitMeasurement(draftRow: DraftRow, activeColumns: List<ActiveColumn>): Result<Long> {
        if (activeColumns.isEmpty()) {
            return Result.failure(IllegalStateException("Cannot commit rows for an empty table. Add columns first."))
        }

        val validation = validateDraft(draftRow, activeColumns)
        if (validation.errorMessage != null) {
            return Result.failure(IllegalArgumentException(validation.errorMessage))
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
                    value = validation.normalizedValuesByColumnId[column.columnId].orEmpty(),
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
        val validation = validateDraft(candidateDraft, activeColumns)
        if (validation.errorMessage != null) {
            return Result.failure(IllegalArgumentException(validation.errorMessage))
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
                    value = validation.normalizedValuesByColumnId[column.columnId].orEmpty(),
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

    suspend fun addWorkbookColumn(metadataColumnName: String, label: String): Result<Long> {
        val metadata = workbookCatalog.findColumnDefinition(metadataColumnName)
            ?: return Result.failure(IllegalArgumentException("Workbook column metadata not found for $metadataColumnName"))

        val fakerKey = "${Screen4WorkbookCatalog.WORKBOOK_TEMPLATE_PREFIX}${metadata.columnName}"
        val template = dao.getTemplateByFakerKey(fakerKey) ?: run {
            val templateEntity = ColumnTemplateEntity(
                fakerKey = fakerKey,
                defaultLabel = label.ifBlank { metadata.displayName },
                constraintType = metadata.columnName,
                maxLength = metadata.maxLength ?: metadata.uiExampleValue.length.coerceAtLeast(32),
                required = metadata.required,
            )
            val id = dao.insertTemplate(templateEntity)
            templateEntity.copy(id = id)
        }

        val nextPosition = dao.getMaxColumnPosition(activeWorkspaceId) + 1
        val columnId = dao.insertColumn(
            ColumnEntity(
                workspaceId = activeWorkspaceId,
                templateId = template.id,
                position = nextPosition,
                label = label.ifBlank { metadata.displayName },
                isActive = true,
            )
        )
        return Result.success(columnId)
    }

    suspend fun addNumericPolicyColumn(
        label: String,
        numericPolicy: Screen4NumericFormatPolicy,
    ): Result<Long> {
        val fallbackLabel = label.ifBlank { numericPolicy.displayLabel() }
        val fakerKey = numericPolicy.toFakerKey()
        val template = dao.getTemplateByFakerKey(fakerKey) ?: run {
            val templateEntity = ColumnTemplateEntity(
                fakerKey = fakerKey,
                defaultLabel = fallbackLabel,
                constraintType = numericPolicy.constraintType(),
                maxLength = numericPolicy.inputMaxLength(),
                required = false,
            )
            val id = dao.insertTemplate(templateEntity)
            templateEntity.copy(id = id)
        }

        val nextPosition = dao.getMaxColumnPosition(activeWorkspaceId) + 1
        val columnId = dao.insertColumn(
            ColumnEntity(
                workspaceId = activeWorkspaceId,
                templateId = template.id,
                position = nextPosition,
                label = fallbackLabel,
                isActive = true,
            )
        )
        return Result.success(columnId)
    }

    suspend fun configureColumn(configuration: Screen4ColumnConfiguration): Result<Unit> {
        val column = dao.getColumnById(activeWorkspaceId, configuration.columnId)
            ?: return Result.failure(IllegalArgumentException("Column ${configuration.columnId} not found"))
        val templatesById = dao.getTemplates().associateBy { it.id }
        val currentTemplate = templatesById[column.templateId]
            ?: return Result.failure(IllegalStateException("Template ${column.templateId} not found"))

        val normalizedLabel = configuration.label.trim().ifBlank { column.label.ifBlank { currentTemplate.defaultLabel } }
        val numericPolicy = configuration.numericPolicy
        val resolvedConstraintType = numericPolicy?.constraintType() ?: currentTemplate.constraintType
        val resolvedMaxLength = (numericPolicy?.inputMaxLength() ?: configuration.maxLength).coerceAtLeast(1)
        val required = configuration.required
        val baseFakerKey = numericPolicy?.toFakerKey()
            ?: Screen4NumericFormatPolicy.normalizedTemplateFakerKey(currentTemplate.fakerKey)
        val configuredFakerKey = buildConfiguredTemplateFakerKey(baseFakerKey, resolvedMaxLength, required)

        val template = dao.getTemplates().firstOrNull { existing ->
            existing.fakerKey == configuredFakerKey &&
                existing.constraintType.equals(resolvedConstraintType, ignoreCase = true) &&
                existing.maxLength == resolvedMaxLength &&
                existing.required == required
        } ?: run {
            val newTemplate = ColumnTemplateEntity(
                fakerKey = configuredFakerKey,
                defaultLabel = normalizedLabel,
                constraintType = resolvedConstraintType,
                maxLength = resolvedMaxLength,
                required = required,
            )
            val id = dao.insertTemplate(newTemplate)
            newTemplate.copy(id = id)
        }

        dao.updateColumn(
            column.copy(
                templateId = template.id,
                label = normalizedLabel,
            )
        )
        return Result.success(Unit)
    }

    suspend fun pruneColumns(columnIds: List<Long>, _activeColumns: List<ActiveColumn>): Result<Int> {
        if (columnIds.isEmpty()) return Result.success(0)
        // The delete-columns flow now allows deactivating any active column, including baseline required columns.

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

    fun availableTableTemplates(): List<Screen4TableTemplateDefinition> =
        Screen4TemplateCatalog.tableTemplates()

    private suspend fun seedTemplateColumns(workspaceId: Long, templateKey: String) {
        if (dao.getActiveColumns(workspaceId).isNotEmpty()) return

        val seeded = Screen4TemplateCatalog.templateByKey(templateKey)?.seeds
            ?: Screen4TemplateCatalog.templateByKey(Screen4TemplateCatalog.DEFAULT_TEMPLATE_KEY)?.seeds
            ?: emptyList()

        val templates = dao.getTemplates()
        val templatesByFakerKey = templates.associateBy { it.fakerKey }

        val columns = seeded.mapIndexed { index, seed ->
            val template = templatesByFakerKey[seed.fakerKey] ?: run {
                val insertedId = dao.insertTemplate(
                    ColumnTemplateEntity(
                        fakerKey = seed.fakerKey,
                        defaultLabel = seed.label,
                        constraintType = seed.constraintType,
                        maxLength = seed.maxLength,
                        required = seed.required,
                    )
                )
                ColumnTemplateEntity(
                    id = insertedId,
                    fakerKey = seed.fakerKey,
                    defaultLabel = seed.label,
                    constraintType = seed.constraintType,
                    maxLength = seed.maxLength,
                    required = seed.required,
                )
            }

            ColumnEntity(
                workspaceId = workspaceId,
                templateId = template.id,
                position = index,
                label = seed.label,
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

    private fun buildConfiguredTemplateFakerKey(
        baseFakerKey: String,
        maxLength: Int,
        required: Boolean,
    ): String {
        return "${Screen4NumericFormatPolicy.normalizedTemplateFakerKey(baseFakerKey)}::config|max=$maxLength|required=$required"
    }

    private fun validateDraft(draftRow: DraftRow, activeColumns: List<ActiveColumn>): DraftValidationResult {
        val normalizedValuesByColumnId = mutableMapOf<Long, String>()
        activeColumns.forEach { column ->
            val value = validationEngine.normalizeForCommit(column, draftRow.valuesByColumnId[column.columnId].orEmpty())
            normalizedValuesByColumnId[column.columnId] = value

            if (column.required && value.isBlank()) {
                return DraftValidationResult(normalizedValuesByColumnId, "${column.label} is required")
            }
            if (value.length > column.maxLength) {
                return DraftValidationResult(
                    normalizedValuesByColumnId,
                    "${column.label} exceeds max length ${column.maxLength}"
                )
            }
            val validationError = validationEngine.validate(column, value, Screen4ValidationStage.COMMIT)
            if (validationError != null) {
                return DraftValidationResult(normalizedValuesByColumnId, "${column.label}: $validationError")
            }
        }
        return DraftValidationResult(normalizedValuesByColumnId, null)
    }

    companion object {
        private const val DEFAULT_WORKSPACE_NAME = "Default Table"
        private const val NO_ACTIVE_WORKSPACE = -1L
    }
}
