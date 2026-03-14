package com.example.templei

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
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
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.ActiveColumn
import com.example.templei.feature.screen4.DraftRow
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.TableViewModel
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen 4 deterministic serial measurement engine host.
 *
 * Handles schema bootstrapping, rapid entry draft lifecycle, and atomic commit workflow.
 */
class Screen4Activity : ComponentActivity() {
    private lateinit var measurementEngine: Screen4MeasurementEngine
    private lateinit var statusText: TextView
    private lateinit var rowDisplayLabel: TextView
    private lateinit var rowDisplaySlider: SeekBar
    private lateinit var rapidEntryContainer: LinearLayout
    private lateinit var tableLayout: TableLayout

    private var visibleRows: Int = Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS
    private var selectedRowId: Long? = null
    private var editMode: Boolean = false
    private var rapidEntryColumnIds: Set<Long> = emptySet()

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
        rapidEntryContainer = findViewById(R.id.rapidEntryContainer)
        tableLayout = findViewById(R.id.tableLayout)

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
            showRapidEntryColumnsDialog()
        }

        findViewById<Button>(R.id.addRowButton).setOnClickListener {
            lifecycleScope.launch {
                if (editMode && selectedRowId != null) {
                    val result = measurementEngine.applyDraftToRow(selectedRowId!!, visibleRows)
                    result.onSuccess { model ->
                        statusText.text = getString(R.string.screen4_status_measurement_updated, selectedRowId!!)
                        renderTable(model)
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                } else {
                    val result = measurementEngine.commitMeasurement(visibleRows)
                    result.onSuccess { model ->
                        val insertedId = model.rows.firstOrNull()?.rowId ?: 0L
                        statusText.text = getString(R.string.screen4_status_measurement_saved, insertedId)
                        renderTable(model)
                        renderRapidEntryForm(measurementEngine.currentDraft(), currentRapidEntryColumns())
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                }
            }
        }

        findViewById<Button>(R.id.deleteRowButton).setOnClickListener {
            lifecycleScope.launch {
                val (deleted, model) = measurementEngine.deleteLatestMeasurement(visibleRows)
                if (deleted && selectedRowId == model.rows.firstOrNull()?.rowId) {
                    selectedRowId = null
                    editMode = false
                }
                statusText.text = if (deleted) {
                    getString(R.string.screen4_status_deleted_latest)
                } else {
                    getString(R.string.screen4_status_delete_none)
                }
                renderTable(model)
            }
        }

        findViewById<Button>(R.id.editRowButton).setOnClickListener {
            val rowId = selectedRowId
            if (rowId == null) {
                refreshTable(statusOverride = getString(R.string.screen4_status_refreshed_rows, visibleRows))
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val draft = measurementEngine.beginEditFromRow(rowId)
                editMode = true
                renderRapidEntryForm(draft, currentRapidEntryColumns())
                statusText.text = getString(R.string.screen4_status_editing_row, rowId)
            }
        }

        findViewById<Button>(R.id.deleteSelectedRowButton).setOnClickListener {
            val rowId = selectedRowId
            if (rowId == null) {
                statusText.text = getString(R.string.screen4_status_select_row_first)
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val (deleted, model) = measurementEngine.deleteSelectedMeasurement(rowId, visibleRows)
                if (deleted) {
                    selectedRowId = null
                    editMode = false
                }
                statusText.text = if (deleted) {
                    getString(R.string.screen4_status_deleted_selected, rowId)
                } else {
                    getString(R.string.screen4_status_delete_none)
                }
                renderTable(model)
            }
        }

        findViewById<Button>(R.id.addColumnButton).setOnClickListener { showColumnManagementDialog() }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        val repository = Screen4Repository(db, Screen4DraftStore(this))
        measurementEngine = Screen4MeasurementEngine(repository)

        lifecycleScope.launch {
            val table = measurementEngine.initialize()
            rapidEntryColumnIds = table.columns.map { it.columnId }.toSet()
            statusText.text = getString(R.string.screen4_status_ready, table.columns.size)
            renderRapidEntryForm(measurementEngine.beginRapidEntry(), currentRapidEntryColumns())
            renderTable(table)
        }
    }

    private fun showRapidEntryColumnsDialog() {
        val allColumns = measurementEngine.activeColumns()
        val requiredColumns = allColumns.filter { it.required }
        val optionalColumns = allColumns.filterNot { it.required }

        if (optionalColumns.isEmpty()) {
            editMode = false
            selectedRowId = null
            val draft = measurementEngine.startNewDraft()
            rapidEntryColumnIds = requiredColumns.map { it.columnId }.toSet()
            renderRapidEntryForm(draft, currentRapidEntryColumns())
            statusText.text = getString(R.string.screen4_status_rapid_entry_required_only)
            return
        }

        val labels = optionalColumns.map { it.label }.toTypedArray()
        val checked = optionalColumns.map { rapidEntryColumnIds.contains(it.columnId) }.toBooleanArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_rapid_entry_dialog_title))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.screen4_rapid_entry_apply)) { _, _ ->
                val selectedOptionalIds = optionalColumns.mapIndexedNotNull { index, column ->
                    if (checked[index]) column.columnId else null
                }.toSet()
                rapidEntryColumnIds = requiredColumns.map { it.columnId }.toSet() + selectedOptionalIds

                editMode = false
                selectedRowId = null
                val draft = measurementEngine.startNewDraft()
                renderRapidEntryForm(draft, currentRapidEntryColumns())
                statusText.text = getString(
                    R.string.screen4_status_rapid_entry_columns_selected,
                    rapidEntryColumnIds.size,
                    allColumns.size,
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshTable(statusOverride: String) {
        lifecycleScope.launch {
            val table = measurementEngine.tableModel(visibleRows)
            renderTable(table)
            statusText.text = statusOverride
        }
    }

    private fun showColumnManagementDialog() {
        val options = arrayOf(
            getString(R.string.screen4_column_management_add),
            getString(R.string.screen4_column_management_prune),
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_column_management_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddColumnDialog()
                    1 -> showPruneColumnsDialog()
                }
            }
            .show()
    }

    private fun showAddColumnDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.screen4_add_column_hint)
            setText(getString(R.string.screen4_default_new_column_label))
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_column_title))
            .setView(input)
            .setPositiveButton(getString(R.string.screen4_add_column_confirm)) { _, _ ->
                lifecycleScope.launch {
                    val result = measurementEngine.addColumn(input.text?.toString().orEmpty(), visibleRows)
                    result.onSuccess { model ->
                        rapidEntryColumnIds = model.columns.map { it.columnId }.toSet()
                        renderRapidEntryForm(measurementEngine.currentDraft(), currentRapidEntryColumns())
                        renderTable(model)
                        statusText.text = getString(R.string.screen4_status_column_added, model.columns.size)
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_add_column_failed, it.message ?: "unknown")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPruneColumnsDialog() {
        val prunableColumns = measurementEngine.activeColumns().filterNot { it.required }
        if (prunableColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_prune_none_available)
            return
        }

        val labels = prunableColumns.map { it.label }.toTypedArray()
        val checked = BooleanArray(prunableColumns.size)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_prune_columns_title))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.screen4_prune_columns_confirm)) { _, _ ->
                lifecycleScope.launch {
                    val toPrune = prunableColumns.mapIndexedNotNull { index, column ->
                        if (checked[index]) column.columnId else null
                    }
                    val result = measurementEngine.pruneColumns(toPrune, visibleRows)
                    result.onSuccess { model ->
                        rapidEntryColumnIds = rapidEntryColumnIds.intersect(model.columns.map { it.columnId }.toSet())
                        if (rapidEntryColumnIds.isEmpty()) {
                            rapidEntryColumnIds = model.columns.filter { it.required }.map { it.columnId }.toSet()
                        }
                        renderRapidEntryForm(measurementEngine.currentDraft(), currentRapidEntryColumns())
                        renderTable(model)
                        statusText.text = getString(R.string.screen4_status_pruned_columns, toPrune.size)
                    }.onFailure {
                        statusText.text = getString(R.string.screen4_status_prune_failed, it.message ?: "unknown")
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun currentRapidEntryColumns(): List<ActiveColumn> {
        val activeColumns = measurementEngine.activeColumns()
        if (rapidEntryColumnIds.isEmpty()) return activeColumns
        val requiredIds = activeColumns.filter { it.required }.map { it.columnId }.toSet()
        val effectiveIds = rapidEntryColumnIds + requiredIds
        return activeColumns.filter { it.columnId in effectiveIds }
    }

    private fun renderRapidEntryForm(draft: DraftRow, columns: List<ActiveColumn>) {
        rapidEntryContainer.removeAllViews()
        columns.forEachIndexed { index, column ->
            val input = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = 8 }
                hint = getString(
                    R.string.screen4_draft_hint,
                    column.label,
                    if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                    column.maxLength,
                )
                inputType = when {
                    column.fakerKey.contains("number") -> InputType.TYPE_CLASS_NUMBER
                    column.fakerKey.contains("science") -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    else -> InputType.TYPE_CLASS_TEXT
                }
                setText(draft.valuesByColumnId[column.columnId].orEmpty())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        measurementEngine.userInputEvent(column.columnId, s?.toString().orEmpty())
                    }
                })

                val isLastField = index == columns.lastIndex
                if (isLastField) {
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
            rapidEntryContainer.addView(input)
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
        model.columns.forEach { column ->
            header.addView(headerCell(column.label))
        }
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
                setBackgroundColor(
                    if (selectedRowId == row.rowId) Color.parseColor("#1A73E8") else Color.TRANSPARENT
                )
            }
            rowView.addView(bodyCell(row.rowId.toString()))
            rowView.addView(bodyCell(formatter.format(Date(row.createdAtMillis))))
            model.columns.forEach { column ->
                rowView.addView(bodyCell(row.valuesByColumnId[column.columnId].orEmpty()))
            }
            tableLayout.addView(rowView)
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
