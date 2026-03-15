package com.example.templei

import android.app.AlertDialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.ActiveColumn
import com.example.templei.feature.screen4.DraftRow
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4FormatGroup
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.Screen4RapidEntryStore
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.Screen4FieldInputFormatter
import com.example.templei.feature.screen4.TableViewModel
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen 4 deterministic serial measurement engine host.
 */
class Screen4Activity : ComponentActivity() {
    private enum class EntryMode { MANUAL, RAPID }

    private lateinit var screen4Coordinator: Screen4Coordinator
    private lateinit var statusText: TextView
    private lateinit var rowDisplayLabel: TextView
    private lateinit var rowDisplaySlider: SeekBar
    private lateinit var manualEntryContainer: LinearLayout
    private lateinit var rapidEntryContainer: LinearLayout
    private lateinit var manualEntryCard: LinearLayout
    private lateinit var rapidEntryCard: LinearLayout
    private lateinit var tableLayout: TableLayout
    private lateinit var actionSectionBody: LinearLayout
    private lateinit var entrySectionBody: LinearLayout
    private lateinit var actionSectionToggleButton: Button
    private lateinit var entrySectionToggleButton: Button

    private var visibleRows: Int = Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS
    private var selectedRowId: Long? = null
    private var editMode: Boolean = false
    private var rapidEntryColumnIds: Set<Long> = emptySet()
    private var entryMode: EntryMode = EntryMode.MANUAL

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) {
            statusText.text = getString(R.string.screen4_status_export_cancelled)
            return@registerForActivityResult
        }
        exportTableToCsv(uri)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen4)
        TopNavigation.bind(activity = this, currentDestination = Screen4Activity::class.java)

        bindViews()
        bindButtons()
        initializeEngine()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        rowDisplayLabel = findViewById(R.id.rowDisplayLabel)
        rowDisplaySlider = findViewById(R.id.rowDisplaySlider)
        manualEntryContainer = findViewById(R.id.manualEntryContainer)
        rapidEntryContainer = findViewById(R.id.rapidEntryContainer)
        manualEntryCard = findViewById(R.id.manualEntryCard)
        rapidEntryCard = findViewById(R.id.rapidEntryCard)
        tableLayout = findViewById(R.id.tableLayout)
        actionSectionBody = findViewById(R.id.actionSectionBody)
        entrySectionBody = findViewById(R.id.entrySectionBody)
        actionSectionToggleButton = findViewById(R.id.actionSectionToggleButton)
        entrySectionToggleButton = findViewById(R.id.entrySectionToggleButton)

        bindCollapsibleSection(actionSectionBody, actionSectionToggleButton)
        bindCollapsibleSection(entrySectionBody, entrySectionToggleButton)

        rowDisplaySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                visibleRows = progress.coerceAtLeast(5)
                rowDisplayLabel.text = getString(R.string.screen4_rows_visible, visibleRows)
                if (fromUser) {
                    refreshTable(statusOverride = getString(R.string.screen4_status_refreshed_rows, visibleRows))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindButtons() {
        findViewById<Button>(R.id.selectRowButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            showRapidEntryColumnsDialog()
        }

        findViewById<Button>(R.id.editRowButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            setEntryMode(EntryMode.MANUAL)
            val rowId = selectedRowId
            if (rowId == null) {
                renderEntryForms(screen4Coordinator.currentDraft())
                statusText.text = getString(R.string.screen4_status_manual_entry)
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val draft = screen4Coordinator.beginEditFromRow(rowId)
                editMode = true
                renderEntryForms(draft)
                statusText.text = getString(R.string.screen4_status_editing_row, rowId)
            }
        }

        findViewById<Button>(R.id.addRowButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            lifecycleScope.launch {
                if (editMode && selectedRowId != null) {
                    val result = screen4Coordinator.applyDraftToRow(selectedRowId!!, visibleRows)
                    result.onSuccess { model ->
                        statusText.text = getString(R.string.screen4_status_measurement_updated, selectedRowId!!)
                        renderTable(model)
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                } else {
                    val result = screen4Coordinator.commitMeasurement(visibleRows, useRapidEntryConfig = entryMode == EntryMode.RAPID)
                    result.onSuccess { model ->
                        val insertedId = model.rows.firstOrNull()?.rowId ?: 0L
                        statusText.text = getString(R.string.screen4_status_measurement_saved, insertedId)
                        renderTable(model)
                        renderEntryForms(screen4Coordinator.currentDraft())
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                }
            }
        }

        findViewById<Button>(R.id.deleteRowButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            lifecycleScope.launch {
                val (deleted, model) = screen4Coordinator.deleteLatestMeasurement(visibleRows)
                statusText.text = if (deleted) getString(R.string.screen4_status_deleted_latest) else getString(R.string.screen4_status_delete_none)
                renderTable(model)
            }
        }

        findViewById<Button>(R.id.deleteSelectedRowButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            val rowId = selectedRowId
            if (rowId == null) {
                statusText.text = getString(R.string.screen4_status_select_row_first)
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val (deleted, model) = screen4Coordinator.deleteSelectedMeasurement(rowId, visibleRows)
                if (deleted) {
                    selectedRowId = null
                    editMode = false
                }
                statusText.text = if (deleted) getString(R.string.screen4_status_deleted_selected, rowId) else getString(R.string.screen4_status_delete_none)
                renderTable(model)
            }
        }

        findViewById<Button>(R.id.addColumnButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            showColumnManagementDialog()
        }

        findViewById<Button>(R.id.exportCsvButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            statusText.text = getString(R.string.screen4_status_export_started)
            exportCsvLauncher.launch(getString(R.string.screen4_export_default_filename))
        }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        screen4Coordinator = Screen4Coordinator(
            Screen4MeasurementEngine(
                Screen4Repository(
                    db,
                    Screen4DraftStore(this),
                    Screen4RapidEntryStore(this),
                )
            )
        )

        lifecycleScope.launch {
            val table = screen4Coordinator.initialize()
            rapidEntryColumnIds = screen4Coordinator.currentRapidEntryConfig().activeColumnIds
            statusText.text = getString(R.string.screen4_status_ready, table.columns.size)
            renderEntryForms(screen4Coordinator.beginRapidEntry())
            setEntryMode(EntryMode.MANUAL)
            renderTable(table)
        }
    }

    private fun setEntryMode(mode: EntryMode) {
        entryMode = mode
        manualEntryCard.visibility = View.VISIBLE
        rapidEntryCard.visibility = View.GONE
    }

    private fun renderEntryForms(draft: DraftRow) {
        renderFormIntoContainer(manualEntryContainer, draft, screen4Coordinator.activeColumns(), isRapid = false)
        rapidEntryContainer.removeAllViews()
    }

    private fun refreshTable(statusOverride: String) {
        if (!::screen4Coordinator.isInitialized) return
        lifecycleScope.launch {
            val table = screen4Coordinator.tableModel(visibleRows)
            renderTable(table)
            statusText.text = statusOverride
        }
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
                startRapidEntry(selectedColumnIds)
                statusText.text = getString(R.string.screen4_status_rapid_entry_columns_selected, selectedColumnIds.size, allColumns.size)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startRapidEntry(columnIds: Set<Long>) {
        val config = screen4Coordinator.configureRapidEntry(columnIds)
        rapidEntryColumnIds = config.activeColumnIds - config.autoColumns.keys
        editMode = false
        selectedRowId = null
        setEntryMode(EntryMode.RAPID)
        val draft = screen4Coordinator.startNewDraft()
        renderEntryForms(draft)
        showRapidEntryInputDialog()
    }


    private fun showRapidEntryInputDialog() {
        val rapidColumns = currentRapidEntryColumns()
        if (rapidColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_columns_unavailable)
            return
        }

        val draft = screen4Coordinator.currentDraft()
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 4)
        }
        val inputsByColumn = mutableMapOf<ActiveColumn, EditText>()

        rapidColumns.forEachIndexed { index, column ->
            val input = EditText(this).apply {
                hint = getString(
                    R.string.screen4_draft_hint,
                    column.label,
                    if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                    column.maxLength,
                )
                val columnType = screen4Coordinator.resolveColumnType(column)
                inputType = inputTypeForWidget(columnType.uiWidget)
                setText(draft.valuesByColumnId[column.columnId].orEmpty())
                var formattingInProgress = false
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (formattingInProgress) return
                        val currentValue = s?.toString().orEmpty()
                        val normalizedValue = if (Screen4FieldInputFormatter.supports(column.constraintType)) {
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
                        error = screen4Coordinator.validateField(column, normalizedValue)
                    }
                })

                if (index == rapidColumns.lastIndex) {
                    imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }
            inputsByColumn[column] = input
            inputContainer.addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_popup_title, rapidColumns.size))
            .setView(inputContainer)
            .setPositiveButton(getString(R.string.screen4_rapid_entry_commit_next), null)
            .setNeutralButton(getString(R.string.screen4_rapid_entry_reselect), null)
            .setNegativeButton(getString(R.string.screen4_switch_manual_entry), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                var hasErrors = false
                inputsByColumn.forEach { (column, input) ->
                    val value = input.text?.toString().orEmpty()
                    val validationError = screen4Coordinator.validateField(column, value)
                    input.error = validationError
                    if (validationError != null) hasErrors = true
                }
                if (hasErrors) return@setOnClickListener

                lifecycleScope.launch {
                    val result = screen4Coordinator.commitMeasurement(visibleRows, useRapidEntryConfig = true)
                    result.onSuccess { model ->
                        val insertedId = model.rows.firstOrNull()?.rowId ?: 0L
                        statusText.text = getString(R.string.screen4_status_measurement_saved, insertedId)
                        renderTable(model)
                        renderEntryForms(screen4Coordinator.currentDraft())
                        dialog.dismiss()
                        if (entryMode == EntryMode.RAPID) {
                            showRapidEntryInputDialog()
                        }
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showRapidEntryColumnsDialog()
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                setEntryMode(EntryMode.MANUAL)
                statusText.text = getString(R.string.screen4_status_manual_entry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showColumnManagementDialog() {
        val options = arrayOf(getString(R.string.screen4_column_management_add), getString(R.string.screen4_column_management_prune))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_column_management_title))
            .setItems(options) { _, which -> if (which == 0) showAddColumnDialog() else showPruneColumnsDialog() }
            .show()
    }

    private fun showAddColumnDialog(initialLabel: String = getString(R.string.screen4_default_new_column_label)) {
        val input = EditText(this).apply {
            hint = getString(R.string.screen4_add_column_hint)
            setText(initialLabel)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_column_title))
            .setView(input)
            .setPositiveButton(getString(R.string.screen4_add_column_confirm)) { _, _ ->
                val label = input.text?.toString().orEmpty()
                showColumnFormatGroupDialog(label)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showColumnFormatGroupDialog(label: String) {
        val groups = screen4Coordinator.columnFormatGroups()
        if (groups.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "no format groups")
            return
        }

        val labels = groups.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_column_group_title))
            .setItems(labels) { _, which ->
                showColumnFormatTypeDialog(label, groups[which])
            }
            .setNeutralButton(getString(R.string.screen4_dialog_back)) { _, _ ->
                showAddColumnDialog(initialLabel = label)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showColumnFormatTypeDialog(label: String, group: Screen4FormatGroup) {
        val options = screen4Coordinator.columnFormatOptions(group.key)
        if (options.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "no formats in group")
            return
        }
        val labels = options.map { "${it.label} • ${it.previewExample} • ${it.uiWidget}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_column_type_title, group.label))
            .setItems(labels) { _, which ->
                lifecycleScope.launch {
                    val selected = options[which]
                    screen4Coordinator.addColumn(label = label, constraintType = selected.typeName, visibleRows = visibleRows)
                        .onSuccess { model ->
                            rapidEntryColumnIds = screen4Coordinator.currentRapidEntryConfig().activeColumnIds - screen4Coordinator.currentRapidEntryConfig().autoColumns.keys
                            renderEntryForms(screen4Coordinator.currentDraft())
                            renderTable(model)
                            statusText.text = getString(R.string.screen4_status_column_added_with_type, model.columns.size, "${selected.label}: ${selected.previewExample}")
                        }
                        .onFailure {
                            statusText.text = getString(R.string.screen4_status_add_column_failed, it.message ?: "unknown")
                        }
                }
            }
            .setNeutralButton(getString(R.string.screen4_dialog_back)) { _, _ ->
                showColumnFormatGroupDialog(label)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPruneColumnsDialog() {
        val prunableColumns = screen4Coordinator.activeColumns().filterNot { it.required }
        if (prunableColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_prune_none_available)
            return
        }
        val labels = prunableColumns.map { it.label }.toTypedArray()
        val checked = BooleanArray(prunableColumns.size)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_prune_columns_title))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(getString(R.string.screen4_prune_columns_confirm)) { _, _ ->
                lifecycleScope.launch {
                    val toPrune = prunableColumns.mapIndexedNotNull { index, column -> if (checked[index]) column.columnId else null }
                    screen4Coordinator.pruneColumns(toPrune, visibleRows)
                        .onSuccess { model ->
                            val config = screen4Coordinator.currentRapidEntryConfig()
                            rapidEntryColumnIds = config.activeColumnIds - config.autoColumns.keys
                            renderEntryForms(screen4Coordinator.currentDraft())
                            renderTable(model)
                            statusText.text = getString(R.string.screen4_status_pruned_columns, toPrune.size)
                        }
                        .onFailure {
                            statusText.text = getString(R.string.screen4_status_prune_failed, it.message ?: "unknown")
                        }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }


    private fun bindCollapsibleSection(body: View, toggleButton: Button) {
        toggleButton.setOnClickListener {
            val isCollapsed = body.visibility == View.GONE
            body.visibility = if (isCollapsed) View.VISIBLE else View.GONE
            toggleButton.text = getString(if (isCollapsed) R.string.screen4_section_collapse else R.string.screen4_section_expand)
            toggleButton.alpha = if (isCollapsed) 1.0f else 0.75f
        }
    }

    private fun currentRapidEntryColumns(): List<ActiveColumn> {
        val activeColumns = screen4Coordinator.activeColumns()
        val config = screen4Coordinator.currentRapidEntryConfig()
        val autoIds = config.autoColumns.keys
        val configuredIds = if (config.activeColumnIds.isEmpty()) {
            activeColumns.map { it.columnId }.toSet()
        } else {
            config.activeColumnIds
        }
        val visibleInputIds = configuredIds - autoIds
        if (visibleInputIds.isEmpty()) return emptyList()
        return activeColumns.filter { it.columnId in visibleInputIds }
    }

    private fun renderFormIntoContainer(
        container: LinearLayout,
        draft: DraftRow,
        columns: List<ActiveColumn>,
        isRapid: Boolean,
    ) {
        container.removeAllViews()
        columns.forEachIndexed { index, column ->
            val input = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = 8 }
                hint = getString(
                    R.string.screen4_draft_hint,
                    column.label,
                    if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                    column.maxLength,
                )
                val columnType = screen4Coordinator.resolveColumnType(column)
                inputType = inputTypeForWidget(columnType.uiWidget)
                setText(draft.valuesByColumnId[column.columnId].orEmpty())
                val initialValue = text?.toString().orEmpty()
                val initialError = screen4Coordinator.validateField(column, initialValue)
                error = initialError
                var formattingInProgress = false
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (formattingInProgress) return

                        val currentValue = s?.toString().orEmpty()
                        val normalizedValue = if (Screen4FieldInputFormatter.supports(column.constraintType)) {
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
                        error = screen4Coordinator.validateField(column, normalizedValue)
                    }
                })

                if (isRapid && index == columns.lastIndex) {
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    setOnEditorActionListener { _, actionId, event ->
                        val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                        if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                            findViewById<Button>(R.id.addRowButton).performClick()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            container.addView(input)
        }
    }

    private fun renderTable(model: TableViewModel) {
        tableLayout.removeAllViews()
        if (model.rows.isEmpty()) {
            selectedRowId = null
            editMode = false
            val emptyRow = TableRow(this)
            emptyRow.addView(TextView(this).apply { text = getString(R.string.screen4_table_empty) })
            tableLayout.addView(emptyRow)
            return
        }

        val header = TableRow(this)
        header.addView(headerCell(getString(R.string.screen4_table_header_row_id)))
        header.addView(headerCell(getString(R.string.screen4_table_header_created)))
        model.columns.forEach { header.addView(headerCell(it.label)) }
        tableLayout.addView(header)

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        model.rows.forEach { row ->
            val rowView = TableRow(this).apply {
                setOnClickListener {
                    selectedRowId = row.rowId
                    editMode = false
                    renderTable(model)
                    statusText.text = getString(R.string.screen4_status_selected_row, row.rowId)
                }
                setBackgroundColor(if (selectedRowId == row.rowId) Color.parseColor("#1A73E8") else Color.TRANSPARENT)
            }
            rowView.addView(bodyCell(row.rowId.toString()))
            rowView.addView(bodyCell(formatter.format(Date(row.createdAtMillis))))
            model.columns.forEach { rowView.addView(bodyCell(row.valuesByColumnId[it.columnId].orEmpty())) }
            tableLayout.addView(rowView)
        }
    }

    private fun exportTableToCsv(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val table = screen4Coordinator.tableModel(visibleRows)
                contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { writer ->
                        val headers = buildList {
                            add(getString(R.string.screen4_table_header_row_id))
                            add(getString(R.string.screen4_table_header_created))
                            addAll(table.columns.map { it.label })
                        }
                        writer.appendLine(headers.toCsvLine())

                        table.rows.forEach { row ->
                            val values = buildList {
                                add(row.rowId.toString())
                                add(row.createdAtMillis.toString())
                                addAll(table.columns.map { column -> row.valuesByColumnId[column.columnId].orEmpty() })
                            }
                            writer.appendLine(values.toCsvLine())
                        }
                    }
                } ?: error("Unable to open output stream")
                table.rows.size
            }.onSuccess { count ->
                statusText.text = getString(R.string.screen4_status_export_success, count)
            }.onFailure { throwable ->
                statusText.text = getString(R.string.screen4_status_export_failed, throwable.message ?: "unknown")
            }
        }
    }

    private fun List<String>.toCsvLine(): String {
        return joinToString(",") { value ->
            val escaped = value.replace("\"", "\"\"")
            "\"$escaped\""
        }
    }

    private fun inputTypeForWidget(widget: String): Int {
        return when (widget) {
            "NumericInput" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            "DecimalInput" -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            "EmailInput" -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            "PhoneInput" -> InputType.TYPE_CLASS_PHONE
            "DateInput" -> InputType.TYPE_CLASS_DATETIME
            "TimeInput" -> InputType.TYPE_CLASS_DATETIME
            "TimestampInput" -> InputType.TYPE_CLASS_NUMBER
            else -> InputType.TYPE_CLASS_TEXT
        }
    }

    private fun headerCell(value: String): TextView = TextView(this).apply {
        text = value
        setPadding(12, 8, 12, 8)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun bodyCell(value: String): TextView = TextView(this).apply {
        text = value
        setPadding(12, 8, 12, 8)
    }
}
