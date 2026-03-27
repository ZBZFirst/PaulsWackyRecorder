package com.example.templei.feature.screen4

class Screen4MeasurementEngine(
    private val repository: Screen4Repository,
    private val locationProvider: Screen4LocationProvider? = null,
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

    suspend fun initializeColumnsForActiveWorkspace(templateKey: String?, visibleRows: Int): TableViewModel {
        repository.initializeActiveWorkspaceColumns(templateKey)
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = repository.loadDraft().normalize(activeColumns)
        return repository.loadTable(limit = visibleRows)
    }

    fun beginRapidEntry(): DraftRow {
        draftRow = if (draftRow.valuesByColumnId.isEmpty()) {
            buildNextDraft(useRapidEntryConfig = true, previousCommittedDraft = null)
        } else {
            draftRow.normalize(activeColumns)
        }
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun startNewDraft(): DraftRow {
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun userInputEvent(columnId: Long, value: String): DraftRow {
        draftRow.valuesByColumnId[columnId] = value
        repository.saveDraft(draftRow)
        return draftRow
    }

    fun refreshLiveAutoFillDraft(
        modes: Set<RapidEntryFillMode>,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): DraftRow {
        activeColumns.forEach { column ->
            val fillRule = rapidEntryConfig.fillRulesByColumnId[column.columnId] ?: return@forEach
            if (fillRule.mode !in modes || !fillRule.mode.isAutoFill()) return@forEach
            draftRow.valuesByColumnId[column.columnId] = autoValueFor(
                column = column,
                rule = fillRule,
                priorValue = draftRow.valuesByColumnId[column.columnId].orEmpty(),
                gpsSnapshot = gpsSnapshot,
            )
        }
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

    suspend fun commitMeasurement(
        visibleRows: Int,
        useRapidEntryConfig: Boolean = false,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): Result<TableViewModel> {
        val candidateDraft = buildComposedDraft(useRapidEntryConfig, gpsSnapshot)

        return repository.commitMeasurement(candidateDraft, activeColumns).mapCatching {
            draftRow = buildNextDraft(useRapidEntryConfig, candidateDraft)
            repository.saveDraft(draftRow)
            repository.loadTable(limit = visibleRows)
        }
    }


    suspend fun listActiveWorkspaces(): List<TableWorkspaceEntity> = repository.listActiveWorkspaces()

    suspend fun activeWorkspace(): TableWorkspaceEntity? = repository.activeWorkspace()

    suspend fun createAndSelectWorkspace(name: String, visibleRows: Int, templateKey: String?): TableViewModel {
        repository.createAndSelectWorkspace(name, templateKey)
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
        repository.saveDraft(draftRow)
        return repository.loadTable(limit = visibleRows)
    }

    suspend fun selectWorkspace(workspaceId: Long, visibleRows: Int): Boolean {
        val selected = repository.selectWorkspace(workspaceId)
        if (!selected) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
        repository.saveDraft(draftRow)
        return true
    }


    suspend fun listArchivedWorkspaces(): List<TableWorkspaceEntity> = repository.listArchivedWorkspaces()

    suspend fun archiveActiveWorkspace(visibleRows: Int): Boolean {
        val archived = repository.archiveActiveWorkspace()
        if (!archived) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
        repository.saveDraft(draftRow)
        return true
    }

    suspend fun restoreWorkspace(workspaceId: Long, visibleRows: Int): Boolean {
        val restored = repository.restoreWorkspace(workspaceId)
        if (!restored) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
        repository.saveDraft(draftRow)
        return true
    }

    suspend fun renameActiveWorkspace(name: String): TableWorkspaceEntity? =
        repository.renameActiveWorkspace(name)

    suspend fun deleteActiveWorkspace(visibleRows: Int): Boolean {
        val deleted = repository.deleteActiveWorkspace()
        if (!deleted) return false
        activeColumns = repository.loadActiveColumns()
        rapidEntryConfig = hydrateRapidEntryConfig(repository.loadRapidEntryConfig())
        draftRow = buildNextDraft(useRapidEntryConfig = false, previousCommittedDraft = null)
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

    suspend fun applyDraftToRow(
        rowId: Long,
        visibleRows: Int,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): Result<TableViewModel> {
        val candidateDraft = buildComposedDraft(useRapidEntryConfig = true, gpsSnapshot = gpsSnapshot)
        return repository.updateMeasurement(rowId, candidateDraft.valuesByColumnId, activeColumns).mapCatching {
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

    suspend fun configureColumn(
        configuration: Screen4ColumnConfiguration,
        visibleRows: Int,
    ): Result<TableViewModel> {
        return repository.configureColumn(configuration).mapCatching {
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

    fun availableTableTemplates(): List<Screen4TableTemplateDefinition> =
        repository.availableTableTemplates()

    private fun buildComposedDraft(
        useRapidEntryConfig: Boolean,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): DraftRow {
        val base = draftRow.normalize(activeColumns).valuesByColumnId
        val activeIds = if (rapidEntryConfig.activeColumnIds.isEmpty()) {
            activeColumns.map { it.columnId }.toSet()
        } else {
            rapidEntryConfig.activeColumnIds
        }

        val composed = activeColumns.associate { column ->
            val fillRule = rapidEntryConfig.fillRulesByColumnId[column.columnId]
            val value = when {
                fillRule != null && fillRule.mode.isAutoFill() ->
                    autoValueFor(
                        column,
                        fillRule,
                        priorValue = base[column.columnId].orEmpty(),
                        gpsSnapshot = gpsSnapshot,
                    )
                !useRapidEntryConfig -> base[column.columnId].orEmpty()
                column.columnId in activeIds -> base[column.columnId].orEmpty()
                else -> ""
            }
            column.columnId to value
        }.toMutableMap()

        return DraftRow(valuesByColumnId = composed)
    }

    private fun buildNextDraft(
        useRapidEntryConfig: Boolean,
        previousCommittedDraft: DraftRow?,
    ): DraftRow {
        val activeIds = if (rapidEntryConfig.activeColumnIds.isEmpty()) {
            activeColumns.map { it.columnId }.toSet()
        } else {
            rapidEntryConfig.activeColumnIds
        }
        val next = activeColumns.associate { column ->
            val fillRule = rapidEntryConfig.fillRulesByColumnId[column.columnId]
            val retained = if (fillRule != null && fillRule.mode.isAutoFill()) {
                when (fillRule.mode) {
                    RapidEntryFillMode.SEQUENCE_INCREMENT,
                    RapidEntryFillMode.SEQUENCE_DECREMENT -> {
                        previousCommittedDraft?.valuesByColumnId?.get(column.columnId).orEmpty()
                    }
                    else -> autoValueFor(
                        column = column,
                        rule = fillRule,
                        priorValue = previousCommittedDraft?.valuesByColumnId?.get(column.columnId).orEmpty(),
                    )
                }
            } else if (useRapidEntryConfig && column.columnId in activeIds) {
                previousCommittedDraft?.valuesByColumnId?.get(column.columnId).orEmpty()
            } else {
                ""
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
            isGpsAutoFillColumn(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_GPS)
            else -> RapidEntryFillRule(RapidEntryFillMode.MANUAL)
        }
    }

    private fun autoValueFor(
        column: ActiveColumn,
        rule: RapidEntryFillRule,
        priorValue: String,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): String {
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
            RapidEntryFillMode.CURRENT_GPS -> autoGpsValueFor(column, gpsSnapshot)
            RapidEntryFillMode.FIXED_VALUE -> rule.fixedValue.orEmpty()
            RapidEntryFillMode.SEQUENCE_INCREMENT ->
                advanceSequenceValue(column, rule, priorValue, direction = 1)
            RapidEntryFillMode.SEQUENCE_DECREMENT ->
                advanceSequenceValue(column, rule, priorValue, direction = -1)
        }
    }

    private fun autoGpsValueFor(
        column: ActiveColumn,
        gpsSnapshot: Screen4LocationSnapshot? = null,
    ): String {
        val location = gpsSnapshot ?: locationProvider?.currentLocation() ?: return ""
        return when (column.gpsColumnRole(Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType))) {
            Screen4GpsColumnRole.LATITUDE -> location.latitudeText()
            Screen4GpsColumnRole.LONGITUDE -> location.longitudeText()
            Screen4GpsColumnRole.COORDINATE -> location.coordinateText()
            Screen4GpsColumnRole.NONE -> ""
        }
    }

    private fun advanceSequenceValue(
        column: ActiveColumn,
        rule: RapidEntryFillRule,
        priorValue: String,
        direction: Int,
    ): String {
        if (isNumericSequenceColumn(column, Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType))) {
            return advanceNumericSequenceValue(column, rule, priorValue, direction)
        }

        val isFirstValue = priorValue.isBlank()
        val seedValue = priorValue.ifBlank { rule.sequenceSeedValue.orEmpty() }
        val stepAmount = rule.stepAmount.coerceAtLeast(1)
        val stepUnit = rule.stepUnit ?: return seedValue
        val calendar = java.util.Calendar.getInstance()

        return when {
            Screen4InputUiPolicy.usesDatePicker(column, Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)) -> {
                val parsed = Screen4TemporalInputPolicy.parseExistingDate(seedValue)
                    ?: currentDateParts(calendar)
                calendar.set(parsed.year, parsed.monthOfYearZeroBased, parsed.dayOfMonth)
                if (!isFirstValue) {
                    applyTemporalStep(calendar, stepUnit, stepAmount * direction)
                }
                Screen4TemporalInputPolicy.formatDateForColumn(
                    column,
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                )
            }
            Screen4InputUiPolicy.usesTimestampPicker(column, Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)) -> {
                val parsed = Screen4TemporalInputPolicy.parseExistingTimestamp(seedValue)
                val dateParts = parsed?.first ?: currentDateParts(calendar)
                val timeParts = parsed?.second ?: currentTimeParts(calendar)
                calendar.set(
                    dateParts.year,
                    dateParts.monthOfYearZeroBased,
                    dateParts.dayOfMonth,
                    timeParts.hourOfDay,
                    timeParts.minute,
                    0,
                )
                if (!isFirstValue) {
                    applyTemporalStep(calendar, stepUnit, stepAmount * direction)
                }
                Screen4TemporalInputPolicy.formatTimestamp(
                    column.constraintType,
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                )
            }
            Screen4InputUiPolicy.usesTimePicker(column, Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)) -> {
                val parsed = Screen4TemporalInputPolicy.parseExistingTime(seedValue) ?: currentTimeParts(calendar)
                calendar.set(
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                    parsed.hourOfDay,
                    parsed.minute,
                    0,
                )
                if (!isFirstValue) {
                    applyTemporalStep(calendar, stepUnit, stepAmount * direction)
                }
                Screen4TemporalInputPolicy.formatTime(
                    column.constraintType,
                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                    calendar.get(java.util.Calendar.MINUTE),
                )
            }
            else -> seedValue
        }
    }

    private fun advanceNumericSequenceValue(
        column: ActiveColumn,
        rule: RapidEntryFillRule,
        priorValue: String,
        direction: Int,
    ): String {
        val numericPolicy = column.numericPolicy ?: defaultNumericSequencePolicy(column)
        val seedValue = priorValue.ifBlank { rule.sequenceSeedValue.orEmpty() }
        val normalizedSeed = seedValue.replace(",", "").ifBlank { "0" }
        val seedNumber = normalizedSeed.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        val step = java.math.BigDecimal.valueOf(rule.stepAmount.toLong())
        val nextValue = if (priorValue.isBlank()) {
            seedNumber
        } else if (direction >= 0) {
            seedNumber.add(step)
        } else {
            seedNumber.subtract(step)
        }

        return if (numericPolicy.numericKind == Screen4NumericKind.INTEGER) {
            nextValue.toBigInteger().toString()
        } else {
            nextValue.stripTrailingZeros().toPlainString()
        }
    }

    private fun defaultNumericSequencePolicy(column: ActiveColumn): Screen4NumericFormatPolicy {
        val resolvedType = Screen4ColumnTypeRegistry.resolveByConstraintType(column.constraintType)
        return if (
            resolvedType.primitiveType == Screen4PrimitiveType.INTEGER ||
            resolvedType.validatorKey.equals("integer", ignoreCase = true) ||
            column.metadata?.dataType?.trim()?.equals("INTEGER", ignoreCase = true) == true
        ) {
            Screen4NumericFormatPolicy(
                numericKind = Screen4NumericKind.INTEGER,
                maxDigits = column.maxLength.coerceAtLeast(1),
                decimalPlaces = 0,
                useSeparators = false,
                allowNegative = false,
            )
        } else {
            val fallbackMaxDigits = column.maxLength.coerceAtLeast(2)
            Screen4NumericFormatPolicy(
                numericKind = Screen4NumericKind.DECIMAL,
                maxDigits = fallbackMaxDigits,
                decimalPlaces = 2.coerceAtMost((fallbackMaxDigits - 1).coerceAtLeast(0)),
                useSeparators = false,
                allowNegative = false,
            )
        }
    }

    private fun applyTemporalStep(
        calendar: java.util.Calendar,
        stepUnit: RapidEntryStepUnit,
        amount: Int,
    ) {
        when (stepUnit) {
            RapidEntryStepUnit.DAY -> calendar.add(java.util.Calendar.DAY_OF_MONTH, amount)
            RapidEntryStepUnit.WEEK -> calendar.add(java.util.Calendar.WEEK_OF_YEAR, amount)
            RapidEntryStepUnit.MONTH -> calendar.add(java.util.Calendar.MONTH, amount)
            RapidEntryStepUnit.YEAR -> calendar.add(java.util.Calendar.YEAR, amount)
            RapidEntryStepUnit.MINUTE -> calendar.add(java.util.Calendar.MINUTE, amount)
            RapidEntryStepUnit.HOUR -> calendar.add(java.util.Calendar.HOUR_OF_DAY, amount)
        }
    }

    private fun currentDateParts(calendar: java.util.Calendar) = Screen4TemporalInputPolicy.DateParts(
        year = calendar.get(java.util.Calendar.YEAR),
        monthOfYearZeroBased = calendar.get(java.util.Calendar.MONTH),
        dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH),
    )

    private fun currentTimeParts(calendar: java.util.Calendar) = Screen4TemporalInputPolicy.TimeParts(
        hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
        minute = calendar.get(java.util.Calendar.MINUTE),
    )

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
        RapidEntryFillMode.CURRENT_GPS -> isGpsAutoFillColumn(column, resolvedType)
        RapidEntryFillMode.SEQUENCE_INCREMENT,
        RapidEntryFillMode.SEQUENCE_DECREMENT -> {
            val allowedUnits = compatibleStepUnits(column, resolvedType)
            allowedUnits.isNotEmpty() && stepUnit in allowedUnits || isNumericSequenceColumn(column, resolvedType)
        }
    }
}

private fun RapidEntryFillMode.isAutoFill(): Boolean = this != RapidEntryFillMode.MANUAL

private fun isGpsAutoFillColumn(
    column: ActiveColumn,
    resolvedType: ColumnTypeDefinition,
): Boolean {
    return column.gpsColumnRole(resolvedType) != Screen4GpsColumnRole.NONE
}

private fun compatibleStepUnits(
    column: ActiveColumn,
    resolvedType: ColumnTypeDefinition,
): Set<RapidEntryStepUnit> {
    return when {
        Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType) -> setOf(
            RapidEntryStepUnit.MINUTE,
            RapidEntryStepUnit.HOUR,
            RapidEntryStepUnit.DAY,
            RapidEntryStepUnit.WEEK,
            RapidEntryStepUnit.MONTH,
            RapidEntryStepUnit.YEAR,
        )
        Screen4InputUiPolicy.usesDatePicker(column, resolvedType) -> setOf(
            RapidEntryStepUnit.DAY,
            RapidEntryStepUnit.WEEK,
            RapidEntryStepUnit.MONTH,
            RapidEntryStepUnit.YEAR,
        )
        Screen4InputUiPolicy.usesTimePicker(column, resolvedType) -> setOf(
            RapidEntryStepUnit.MINUTE,
            RapidEntryStepUnit.HOUR,
        )
        else -> emptySet()
    }
}

private fun isNumericSequenceColumn(
    column: ActiveColumn,
    resolvedType: ColumnTypeDefinition,
): Boolean {
    val metadataType = column.metadata?.dataType?.trim()?.uppercase()
    val label = column.label.trim().uppercase()
    return column.numericPolicy != null ||
        resolvedType.primitiveType == Screen4PrimitiveType.INTEGER ||
        resolvedType.primitiveType == Screen4PrimitiveType.DECIMAL ||
        resolvedType.name == "integer" ||
        resolvedType.name == "decimal" ||
        resolvedType.validatorKey.equals("integer", ignoreCase = true) ||
        resolvedType.validatorKey.equals("decimal", ignoreCase = true) ||
        metadataType == "INTEGER" ||
        metadataType == "DECIMAL" ||
        label == "ID" ||
        label.endsWith(" ID")
}
