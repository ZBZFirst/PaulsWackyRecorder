package com.example.templei

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.ActiveColumn
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4FieldInputFormatter
import com.example.templei.feature.screen4.Screen4InputUiPolicy
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.RapidEntryFillMode
import com.example.templei.feature.screen4.RapidEntryFillRule
import com.example.templei.feature.screen4.Screen4RapidEntryStore
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.Screen4TableSessionStore
import com.example.templei.feature.screen4.Screen4TemporalInputPolicy
import com.example.templei.feature.screen4.Screen4WorkbookCatalog
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Dedicated host surface for Screen 4 short-form row append loops.
 */
class Screen4ShortFormActivity : ComponentActivity() {

    private enum class RapidModalState { IDLE, EDITING, APPENDING, ERROR }

    private lateinit var screen4Coordinator: Screen4Coordinator
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var shortFormScroll: ScrollView
    private lateinit var newValuesContainer: LinearLayout
    private lateinit var rapidEditorLabel: TextView
    private lateinit var rapidEditorMeta: TextView
    private lateinit var rapidEditorValidation: TextView
    private lateinit var rapidEditorInputContainer: LinearLayout
    private lateinit var modalStatus: TextView
    private lateinit var appendButton: Button
    private lateinit var reselectButton: Button
    private lateinit var closeButton: Button

    private var rapidEntryColumnIds: Set<Long> = emptySet()
    private var rapidModalState: RapidModalState = RapidModalState.IDLE
    private var activeRapidColumnId: Long? = null
    private val rapidInputsByColumnId = mutableMapOf<Long, EditText>()
    private var rapidFillRulesByColumnId: Map<Long, RapidEntryFillRule> = emptyMap()

    companion object {
        private const val KEY_SHORT_FORM_COLUMN_IDS = "screen4.shortForm.columnIds"
        private const val KEY_SHORT_FORM_MODAL_STATE = "screen4.shortForm.modalState"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen4_short_form)
        TopNavigation.bind(activity = this, currentDestination = Screen4Activity::class.java)

        bindViews()
        restoreTransientSessionState(savedInstanceState)
        initializeEngine()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(KEY_SHORT_FORM_COLUMN_IDS, rapidEntryColumnIds.toLongArray())
        outState.putString(KEY_SHORT_FORM_MODAL_STATE, rapidModalState.name)
    }

    private fun bindViews() {
        statusText = findViewById(R.id.shortFormStatusText)
        titleText = findViewById(R.id.rapidModalTitle)
        shortFormScroll = findViewById(R.id.shortFormScroll)
        newValuesContainer = findViewById(R.id.rapidNewValuesContainer)
        rapidEditorLabel = findViewById(R.id.rapidEditorLabel)
        rapidEditorMeta = findViewById(R.id.rapidEditorMeta)
        rapidEditorValidation = findViewById(R.id.rapidEditorValidation)
        rapidEditorInputContainer = findViewById(R.id.rapidEditorInputContainer)
        modalStatus = findViewById(R.id.rapidModalStatus)
        appendButton = findViewById(R.id.rapidAppendButton)
        reselectButton = findViewById(R.id.rapidModalReselectButton)
        closeButton = findViewById(R.id.rapidModalCloseButton)
        bindImeSafeScroll(shortFormScroll)

        closeButton.setOnClickListener { finish() }
        reselectButton.setOnClickListener { showRapidEntryColumnsDialog() }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        val workbookCatalog = Screen4WorkbookCatalog.getInstance(this)
        screen4Coordinator = Screen4Coordinator(
            Screen4MeasurementEngine(
                Screen4Repository(
                    db,
                    Screen4DraftStore(this),
                    Screen4RapidEntryStore(this),
                    Screen4TableSessionStore(this),
                    workbookCatalog,
                )
            ),
            workbookCatalog,
        )

        lifecycleScope.launch {
            val table = screen4Coordinator.initialize()
            if (rapidEntryColumnIds.isEmpty()) {
                rapidEntryColumnIds = screen4Coordinator.currentRapidEntryConfig().activeColumnIds
            }
            rapidFillRulesByColumnId = screen4Coordinator.currentRapidEntryConfig().fillRulesByColumnId
            statusText.text = getString(R.string.screen4_status_ready, table.columns.size)

            if (rapidEntryColumnIds.isNotEmpty()) {
                screen4Coordinator.configureRapidEntry(rapidEntryColumnIds, rapidFillRulesByColumnId)
                renderRapidEntrySurface()
                statusText.text = getString(R.string.screen4_status_short_form_resumed)
            } else {
                showRapidEntryColumnsDialog()
            }
        }
    }

    private fun restoreTransientSessionState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        rapidEntryColumnIds = savedInstanceState
            .getLongArray(KEY_SHORT_FORM_COLUMN_IDS)
            ?.toSet()
            .orEmpty()

        rapidModalState = savedInstanceState
            .getString(KEY_SHORT_FORM_MODAL_STATE)
            ?.let { stateName ->
                runCatching { RapidModalState.valueOf(stateName) }
                    .getOrDefault(RapidModalState.IDLE)
            }
            ?: RapidModalState.IDLE
    }

    private fun showRapidEntryColumnsDialog() {
        val allColumns = screen4Coordinator.activeColumns()
        if (allColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_columns_unavailable)
            return
        }

        val labels = allColumns.map { column ->
            if (column.required) {
                getString(R.string.screen4_rapid_entry_required_item, column.label)
            } else {
                column.label
            }
        }.toTypedArray()

        val checked = allColumns.map { column ->
            column.required || rapidEntryColumnIds.contains(column.columnId)
        }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_dialog_title))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val column = allColumns[which]
                if (column.required && !isChecked) {
                    checked[which] = true
                    statusText.text = getString(R.string.screen4_status_required_columns_enforced)
                } else {
                    checked[which] = isChecked
                }
            }
            .setPositiveButton(getString(R.string.screen4_rapid_entry_apply)) { _, _ ->
                val selectedColumnIds = allColumns.mapIndexedNotNull { index, column ->
                    if (checked[index] || column.required) column.columnId else null
                }.toSet()
                showFillModeDialog(allColumns.filter { it.columnId in selectedColumnIds }, allColumns.size)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun startRapidEntry(columnIds: Set<Long>, fillRulesByColumnId: Map<Long, RapidEntryFillRule>) {
        val config = screen4Coordinator.configureRapidEntry(columnIds, fillRulesByColumnId)
        rapidEntryColumnIds = config.activeColumnIds
        rapidFillRulesByColumnId = config.fillRulesByColumnId
        screen4Coordinator.startNewDraft()
        renderRapidEntrySurface()
    }

    private fun renderRapidEntrySurface() {
        val rapidColumns = currentRapidEntryColumns()
        val selectedColumns = currentSelectedRapidColumns()
        if (selectedColumns.isEmpty()) {
            rapidModalState = RapidModalState.ERROR
            statusText.text = getString(R.string.screen4_status_columns_unavailable)
            return
        }

        rapidModalState = RapidModalState.EDITING
        titleText.text = getString(R.string.screen4_rapid_entry_popup_title, selectedColumns.size)

        val draft = screen4Coordinator.currentDraft()
        newValuesContainer.removeAllViews()
        val activeColumn = rapidColumns.firstOrNull { it.columnId == activeRapidColumnId } ?: rapidColumns.firstOrNull()
        activeRapidColumnId = activeColumn?.columnId

        rapidColumns.forEach { column ->
            val currentValue = draft.valuesByColumnId[column.columnId].orEmpty()
            val validation = screen4Coordinator.validateField(column, currentValue)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 14, 16, 14)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = 8 }
                background = getDrawable(
                    if (column.columnId == activeRapidColumnId) R.drawable.bg_section_surface
                    else R.drawable.bg_card_surface
                )
                setOnClickListener {
                    activeRapidColumnId = column.columnId
                    renderRapidEntrySurface()
                    shortFormScroll.post {
                        shortFormScroll.smoothScrollTo(0, 0)
                    }
                }
            }
            row.addView(TextView(this).apply {
                text = column.label
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            row.addView(TextView(this).apply {
                text = buildString {
                    if (column.required) {
                        append(getString(R.string.screen4_required))
                        append(" • ")
                    }
                    append(describeInputMode(column))
                }
                textSize = 12f
            })
            row.addView(TextView(this).apply {
                text = if (currentValue.isBlank()) getString(R.string.screen4_field_value_empty) else currentValue
                textSize = 16f
            })
            row.addView(TextView(this).apply {
                text = validation ?: getString(R.string.screen4_field_ready)
                textSize = 12f
            })
            newValuesContainer.addView(row)
        }

        if (activeColumn != null) {
            renderRapidEntryEditor(activeColumn, draft.valuesByColumnId[activeColumn.columnId].orEmpty())
        } else {
            rapidEditorLabel.text = getString(R.string.screen4_rapid_entry_fill_mode_title)
            rapidEditorMeta.text = autoFillSummary(selectedColumns)
            rapidEditorValidation.text = getString(R.string.screen4_field_ready)
            rapidEditorInputContainer.removeAllViews()
        }

        modalStatus.text = getString(R.string.screen4_status_rapid_entry)
        appendButton.isEnabled = true
        appendButton.setOnClickListener {
            if (rapidColumns.isNotEmpty() && hasRapidInputErrors(rapidColumns)) return@setOnClickListener
            appendRapidMeasurement()
        }
    }

    private fun renderRapidEntryEditor(column: ActiveColumn, currentValue: String) {
        rapidEditorLabel.text = column.label
        rapidEditorMeta.text = buildString {
            append(describeInputMode(column))
            append(" • ")
            append(Screen4InputUiPolicy.buildHint(this@Screen4ShortFormActivity, column))
        }
        rapidEditorInputContainer.removeAllViews()
        val input = rapidInputsByColumnId[column.columnId] ?: buildRapidInput(column).also {
            rapidInputsByColumnId[column.columnId] = it
        }
        if (input.text?.toString().orEmpty() != currentValue) {
            input.setText(currentValue)
            input.setSelection(input.text?.length ?: 0)
        }
        attachInputToContainer(input, rapidEditorInputContainer)
        rapidEditorValidation.text = screen4Coordinator.validateField(column, currentValue)
            ?: getString(R.string.screen4_field_ready)
    }

    private fun hasRapidInputErrors(columns: List<ActiveColumn>): Boolean {
        var hasErrors = false
        columns.forEach { column ->
            val input = rapidInputsByColumnId[column.columnId] ?: return@forEach
            val value = input.text?.toString().orEmpty().trim()
            val errorMessage = screen4Coordinator.validateField(column, value)
            input.error = errorMessage
            if (errorMessage != null) {
                hasErrors = true
            }
        }
        return hasErrors
    }

    private fun appendRapidMeasurement() {
        if (!appendButton.isEnabled) return

        rapidModalState = RapidModalState.APPENDING
        appendButton.isEnabled = false
        modalStatus.text = getString(R.string.screen4_rapid_entry_commit_in_progress)

        lifecycleScope.launch {
            val result = screen4Coordinator.commitMeasurement(
                Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS,
                useRapidEntryConfig = true,
            )
            result.onSuccess { table ->
                rapidModalState = RapidModalState.EDITING
                statusText.text = getString(
                    R.string.screen4_status_measurement_saved,
                    table.rows.firstOrNull()?.rowId ?: 0L
                )
                modalStatus.text = getString(R.string.screen4_rapid_entry_append_success)
                renderRapidEntrySurface()
            }.onFailure {
                rapidModalState = RapidModalState.ERROR
                statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                modalStatus.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                appendButton.isEnabled = true
            }
        }
    }

    private fun currentRapidEntryColumns(): List<ActiveColumn> {
        val activeColumns = currentSelectedRapidColumns()
        val config = screen4Coordinator.currentRapidEntryConfig()
        val visibleInputIds = config.activeColumnIds.filterTo(mutableSetOf()) { columnId ->
            config.fillRulesByColumnId[columnId]?.mode == null ||
                config.fillRulesByColumnId[columnId]?.mode == RapidEntryFillMode.MANUAL
        }
        if (visibleInputIds.isEmpty()) return emptyList()
        return activeColumns.filter { it.columnId in visibleInputIds }
    }

    private fun currentSelectedRapidColumns(): List<ActiveColumn> {
        val activeColumns = screen4Coordinator.activeColumns()
        val config = screen4Coordinator.currentRapidEntryConfig()
        val configuredIds = if (config.activeColumnIds.isEmpty()) {
            activeColumns.map { it.columnId }.toSet()
        } else {
            config.activeColumnIds
        }
        return activeColumns.filter { it.columnId in configuredIds }
    }

    private fun showFillModeDialog(selectedColumns: List<ActiveColumn>, totalColumnCount: Int) {
        if (selectedColumns.isEmpty()) {
            startRapidEntry(emptySet(), emptyMap())
            return
        }

        val workingRules = selectedColumns.associate { column ->
            column.columnId to (
                rapidFillRulesByColumnId[column.columnId] ?: defaultFillRuleFor(column)
            )
        }.toMutableMap()

        fun buildLabels(): Array<String> {
            return selectedColumns.map { column ->
                getString(
                    R.string.screen4_rapid_entry_fill_mode_summary,
                    column.label,
                    fillModeLabel(workingRules.getValue(column.columnId)),
                )
            }.toTypedArray()
        }

        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_fill_mode_title))
            .setItems(buildLabels()) { _, which ->
                val column = selectedColumns[which]
                showFillModeChoiceDialog(column, workingRules[column.columnId] ?: defaultFillRuleFor(column)) { updatedRule ->
                    workingRules[column.columnId] = updatedRule
                    dialog.dismiss()
                    showFillModeDialog(selectedColumns, totalColumnCount)
                }
            }
            .setPositiveButton(getString(R.string.screen4_rapid_entry_apply)) { _, _ ->
                startRapidEntry(selectedColumns.map { it.columnId }.toSet(), workingRules)
                statusText.text = getString(
                    R.string.screen4_status_rapid_entry_columns_selected,
                    selectedColumns.size,
                    totalColumnCount,
                )
                modalStatus.text = getString(R.string.screen4_status_rapid_entry_modes_saved)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun showFillModeChoiceDialog(
        column: ActiveColumn,
        currentRule: RapidEntryFillRule,
        onSelected: (RapidEntryFillRule) -> Unit,
    ) {
        val modes = availableFillModesFor(column)
        val labels = modes.map { mode -> fillModeLabel(RapidEntryFillRule(mode)) }.toTypedArray()
        val checkedIndex = modes.indexOf(currentRule.mode).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_fill_mode_choice_title, column.label))
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val mode = modes[which]
                dialog.dismiss()
                if (mode == RapidEntryFillMode.FIXED_VALUE) {
                    showFixedValueDialog(column) { fixedValue ->
                        onSelected(RapidEntryFillRule(mode = RapidEntryFillMode.FIXED_VALUE, fixedValue = fixedValue))
                    }
                } else {
                    onSelected(RapidEntryFillRule(mode = mode))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFixedValueDialog(column: ActiveColumn, onSelected: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.screen4_rapid_entry_fixed_value_hint)
            setText(rapidFillRulesByColumnId[column.columnId]?.fixedValue.orEmpty())
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_fixed_value_title, column.label))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelected(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun defaultFillRuleFor(column: ActiveColumn): RapidEntryFillRule {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        return when {
            Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_TIMESTAMP)
            Screen4InputUiPolicy.usesDatePicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_DATE)
            Screen4InputUiPolicy.usesTimePicker(column, resolvedType) -> RapidEntryFillRule(RapidEntryFillMode.CURRENT_TIME)
            else -> RapidEntryFillRule(RapidEntryFillMode.MANUAL)
        }
    }

    private fun availableFillModesFor(column: ActiveColumn): List<RapidEntryFillMode> {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        return buildList {
            add(RapidEntryFillMode.MANUAL)
            if (Screen4InputUiPolicy.usesDatePicker(column, resolvedType)) add(RapidEntryFillMode.CURRENT_DATE)
            if (Screen4InputUiPolicy.usesTimePicker(column, resolvedType)) add(RapidEntryFillMode.CURRENT_TIME)
            if (Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType)) add(RapidEntryFillMode.CURRENT_TIMESTAMP)
            add(RapidEntryFillMode.FIXED_VALUE)
        }
    }

    private fun fillModeLabel(rule: RapidEntryFillRule): String {
        return when (rule.mode) {
            RapidEntryFillMode.MANUAL -> getString(R.string.screen4_rapid_entry_fill_mode_manual)
            RapidEntryFillMode.CURRENT_DATE -> getString(R.string.screen4_rapid_entry_fill_mode_current_date)
            RapidEntryFillMode.CURRENT_TIME -> getString(R.string.screen4_rapid_entry_fill_mode_current_time)
            RapidEntryFillMode.CURRENT_TIMESTAMP -> getString(R.string.screen4_rapid_entry_fill_mode_current_timestamp)
            RapidEntryFillMode.FIXED_VALUE -> {
                val fixed = rule.fixedValue?.takeIf { it.isNotBlank() } ?: getString(R.string.screen4_field_value_empty)
                "${getString(R.string.screen4_rapid_entry_fill_mode_fixed)}: $fixed"
            }
        }
    }

    private fun autoFillSummary(columns: List<ActiveColumn>): String {
        val config = screen4Coordinator.currentRapidEntryConfig()
        return columns.joinToString(" • ") { column ->
            val rule = config.fillRulesByColumnId[column.columnId] ?: RapidEntryFillRule(RapidEntryFillMode.MANUAL)
            "${column.label}: ${fillModeLabel(rule)}"
        }
    }

    private fun createInputView(column: ActiveColumn): EditText {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        val input = if (Screen4InputUiPolicy.usesAllowedValuePicker(column)) {
            AutoCompleteTextView(this).apply {
                setAdapter(
                    ArrayAdapter(
                        this@Screen4ShortFormActivity,
                        android.R.layout.simple_dropdown_item_1line,
                        Screen4InputUiPolicy.allowedValues(column),
                    )
                )
                threshold = 0
                Screen4InputUiPolicy.applyAllowedValuePickerBehavior(this)
            }
        } else {
            EditText(this)
        }
        Screen4InputUiPolicy.applyToInput(input, column, resolvedType)
        if (Screen4InputUiPolicy.usesDatePicker(column, resolvedType)) {
            configureDatePickerInput(input, column)
        } else if (Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType)) {
            configureTimestampPickerInput(input, column)
        } else if (Screen4InputUiPolicy.usesTimePicker(column, resolvedType)) {
            configureTimePickerInput(input, column)
        }
        bindInputFocusScroll(input, shortFormScroll)
        return input
    }

    private fun buildRapidInput(column: ActiveColumn): EditText {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        return createInputView(column).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            hint = Screen4InputUiPolicy.buildHint(this@Screen4ShortFormActivity, column)
            var formattingInProgress = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (formattingInProgress) return

                    val currentValue = s?.toString().orEmpty()
                    val normalizedValue = if (
                        Screen4InputUiPolicy.shouldApplyLiveFormatter(column, resolvedType) &&
                        Screen4FieldInputFormatter.supports(column.constraintType)
                    ) {
                        Screen4FieldInputFormatter.format(column.constraintType, currentValue)
                    } else {
                        currentValue
                    }

                    if (normalizedValue != currentValue) {
                        formattingInProgress = true
                        setText(normalizedValue)
                        setSelection(normalizedValue.length)
                        formattingInProgress = false
                    }

                    screen4Coordinator.userInputEvent(column.columnId, normalizedValue)
                    val validation = screen4Coordinator.validateField(column, normalizedValue)
                    error = validation
                    if (activeRapidColumnId == column.columnId) {
                        rapidEditorValidation.text = validation ?: getString(R.string.screen4_field_ready)
                    }
                    renderRapidEntrySurface()
                }
            })
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, event ->
                val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                    appendButton.performClick()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun describeInputMode(column: ActiveColumn): String {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        return when {
            Screen4InputUiPolicy.usesAllowedValuePicker(column) -> getString(R.string.screen4_field_mode_list)
            Screen4InputUiPolicy.usesTimestampPicker(column, resolvedType) -> getString(R.string.screen4_field_mode_timestamp)
            Screen4InputUiPolicy.usesDatePicker(column, resolvedType) -> getString(R.string.screen4_field_mode_date)
            Screen4InputUiPolicy.usesTimePicker(column, resolvedType) -> getString(R.string.screen4_field_mode_time)
            else -> getString(R.string.screen4_field_mode_text)
        }
    }

    private fun attachInputToContainer(input: EditText, container: LinearLayout) {
        (input.parent as? ViewGroup)?.removeView(input)
        container.addView(input)
        input.requestFocus()
    }

    private fun configureDatePickerInput(input: EditText, column: ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.setOnClickListener {
            showDatePickerDialog(
                input = input,
                column = column,
                initialDate = Screen4TemporalInputPolicy.parseExistingDate(input.text?.toString().orEmpty()),
            )
        }
    }

    private fun configureTimePickerInput(input: EditText, column: ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.setOnClickListener {
            showTimePickerDialog(
                input = input,
                column = column,
                initialTime = Screen4TemporalInputPolicy.parseExistingTime(input.text?.toString().orEmpty()),
            )
        }
    }

    private fun configureTimestampPickerInput(input: EditText, column: ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.setOnClickListener {
            val existing = Screen4TemporalInputPolicy.parseExistingTimestamp(input.text?.toString().orEmpty())
            showTimestampDatePickerDialog(
                input = input,
                column = column,
                initialDate = existing?.first,
                initialTime = existing?.second,
            )
        }
    }

    private fun showDatePickerDialog(
        input: EditText,
        column: ActiveColumn,
        initialDate: Screen4TemporalInputPolicy.DateParts?,
    ) {
        val calendar = Calendar.getInstance()
        val seed = initialDate ?: Screen4TemporalInputPolicy.DateParts(
            year = calendar.get(Calendar.YEAR),
            monthOfYearZeroBased = calendar.get(Calendar.MONTH),
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
        )
        val dialog = android.app.DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                input.setText(Screen4TemporalInputPolicy.formatDateForColumn(column, year, monthOfYear, dayOfMonth))
            },
            seed.year,
            seed.monthOfYearZeroBased,
            seed.dayOfMonth,
        )
        dialog.setButton(android.app.DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.screen4_picker_today)) { _, _ -> }
        dialog.setButton(android.app.DatePickerDialog.BUTTON_NEGATIVE, getString(R.string.screen4_picker_clear)) { _, _ ->
            input.text?.clear()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.DatePickerDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val now = Calendar.getInstance()
                dialog.datePicker.updateDate(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH),
                )
            }
        }
        dialog.show()
    }

    private fun showTimePickerDialog(
        input: EditText,
        column: ActiveColumn,
        initialTime: Screen4TemporalInputPolicy.TimeParts?,
    ) {
        val calendar = Calendar.getInstance()
        val seed = initialTime ?: Screen4TemporalInputPolicy.TimeParts(
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
        )
        val dialog = android.app.TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                input.setText(Screen4TemporalInputPolicy.formatTime(column.constraintType, hourOfDay, minute))
            },
            seed.hourOfDay,
            seed.minute,
            !column.constraintType.contains("am_pm"),
        )
        dialog.setButton(android.app.TimePickerDialog.BUTTON_NEUTRAL, getString(R.string.screen4_picker_now)) { _, _ -> }
        dialog.setButton(android.app.TimePickerDialog.BUTTON_NEGATIVE, getString(R.string.screen4_picker_clear)) { _, _ ->
            input.text?.clear()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.TimePickerDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val now = Calendar.getInstance()
                dialog.updateTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            }
        }
        dialog.show()
    }

    private fun showTimestampDatePickerDialog(
        input: EditText,
        column: ActiveColumn,
        initialDate: Screen4TemporalInputPolicy.DateParts?,
        initialTime: Screen4TemporalInputPolicy.TimeParts?,
    ) {
        val calendar = Calendar.getInstance()
        val seed = initialDate ?: Screen4TemporalInputPolicy.DateParts(
            year = calendar.get(Calendar.YEAR),
            monthOfYearZeroBased = calendar.get(Calendar.MONTH),
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
        )
        val dialog = android.app.DatePickerDialog(
            this,
            { _, year, monthOfYear, dayOfMonth ->
                showTimestampTimePickerDialog(
                    input = input,
                    column = column,
                    year = year,
                    monthOfYearZeroBased = monthOfYear,
                    dayOfMonth = dayOfMonth,
                    initialTime = initialTime,
                )
            },
            seed.year,
            seed.monthOfYearZeroBased,
            seed.dayOfMonth,
        )
        dialog.setButton(android.app.DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.screen4_picker_today)) { _, _ -> }
        dialog.setButton(android.app.DatePickerDialog.BUTTON_NEGATIVE, getString(R.string.screen4_picker_clear)) { _, _ ->
            input.text?.clear()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.DatePickerDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val now = Calendar.getInstance()
                dialog.datePicker.updateDate(
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH),
                )
            }
        }
        dialog.show()
    }

    private fun showTimestampTimePickerDialog(
        input: EditText,
        column: ActiveColumn,
        year: Int,
        monthOfYearZeroBased: Int,
        dayOfMonth: Int,
        initialTime: Screen4TemporalInputPolicy.TimeParts?,
    ) {
        val calendar = Calendar.getInstance()
        val seed = initialTime ?: Screen4TemporalInputPolicy.TimeParts(
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
        )
        val dialog = android.app.TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                input.setText(
                    Screen4TemporalInputPolicy.formatTimestamp(
                        column.constraintType,
                        year,
                        monthOfYearZeroBased,
                        dayOfMonth,
                        hourOfDay,
                        minute,
                    )
                )
            },
            seed.hourOfDay,
            seed.minute,
            true,
        )
        dialog.setButton(android.app.TimePickerDialog.BUTTON_NEUTRAL, getString(R.string.screen4_picker_now)) { _, _ -> }
        dialog.setButton(android.app.TimePickerDialog.BUTTON_NEGATIVE, getString(R.string.screen4_picker_clear)) { _, _ ->
            input.text?.clear()
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.TimePickerDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                val now = Calendar.getInstance()
                dialog.updateTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            }
        }
        dialog.show()
    }

    private fun bindImeSafeScroll(scrollView: ScrollView) {
        val left = scrollView.paddingLeft
        val top = scrollView.paddingTop
        val right = scrollView.paddingRight
        val bottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(
                left + systemBars.left,
                top + systemBars.top,
                right + systemBars.right,
                bottom + maxOf(systemBars.bottom, imeInsets.bottom),
            )
            windowInsets
        }
    }

    private fun bindInputFocusScroll(input: EditText, scrollView: ScrollView) {
        input.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                scrollView.post {
                    scrollView.smoothScrollTo(0, (view.top - 48).coerceAtLeast(0))
                }
            }
        }
    }
}
