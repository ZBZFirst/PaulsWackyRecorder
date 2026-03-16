package com.example.templei

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.Screen4ColumnFormatCatalog
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.Screen4RapidEntryStore
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.Screen4TableSessionStore
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

    private lateinit var screen4Coordinator: Screen4Coordinator

    private lateinit var statusText: TextView
    private lateinit var rowDisplayLabel: TextView
    private lateinit var rowDisplaySlider: SeekBar
    private lateinit var tableLayout: TableLayout
    private lateinit var actionSectionBody: View
    private lateinit var actionSectionToggleButton: Button
    private lateinit var longFormSectionBody: View
    private lateinit var longFormSectionToggleButton: Button
    private lateinit var longFormGrid: GridLayout

    private lateinit var deleteColumnsButton: Button
    private lateinit var shortFormButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var exportCsvButton: Button

    private var visibleRows: Int = Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS
    private var selectedRowId: Long? = null

    private val exportCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
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

        TopNavigation.bind(
            activity = this,
            currentDestination = Screen4Activity::class.java
        )

        bindViews()
        bindButtons()
        initializeEngine()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        rowDisplayLabel = findViewById(R.id.rowDisplayLabel)
        rowDisplaySlider = findViewById(R.id.rowDisplaySlider)
        tableLayout = findViewById(R.id.tableLayout)
        actionSectionBody = findViewById(R.id.actionSectionBody)
        actionSectionToggleButton = findViewById(R.id.actionSectionToggleButton)
        longFormSectionBody = findViewById(R.id.longFormSectionBody)
        longFormSectionToggleButton = findViewById(R.id.longFormSectionToggleButton)
        longFormGrid = findViewById(R.id.longFormGrid)

        deleteColumnsButton = findViewById(R.id.pruneColumnsButton)
        shortFormButton = findViewById(R.id.selectRowButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedRowButton)
        exportCsvButton = findViewById(R.id.exportCsvButton)

        bindCollapsibleSection(actionSectionBody, actionSectionToggleButton)
        bindCollapsibleSection(longFormSectionBody, longFormSectionToggleButton)

        rowDisplaySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                visibleRows = progress.coerceAtLeast(5)
                rowDisplayLabel.text = getString(R.string.screen4_rows_visible, visibleRows)
                if (fromUser) {
                    refreshTable(getString(R.string.screen4_status_refreshed_rows, visibleRows))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindButtons() {
        findViewById<Button>(R.id.newTableButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            confirmStartNewTable()
        }

        findViewById<Button>(R.id.openTableButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            showOpenTableDialog()
        }

        findViewById<Button>(R.id.archiveTableButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            confirmArchiveActiveTable()
        }

        findViewById<Button>(R.id.addColumnButton).setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            showAddColumnDialog()
        }

        deleteColumnsButton.setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            showPruneColumnsDialog()
        }

        deleteSelectedButton.setOnClickListener {
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
                    statusText.text = getString(R.string.screen4_status_deleted_selected, rowId)
                } else {
                    statusText.text = getString(R.string.screen4_status_delete_none)
                }
                renderScreen(model)
            }
        }

        shortFormButton.setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            startActivity(Intent(this, Screen4ShortFormActivity::class.java))
            statusText.text = getString(R.string.screen4_status_short_form_opened)
        }

        exportCsvButton.setOnClickListener {
            statusText.text = getString(R.string.screen4_status_export_started)
            exportCsvLauncher.launch(getString(R.string.screen4_export_default_filename))
        }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        screen4Coordinator =
            Screen4Coordinator(
                Screen4MeasurementEngine(
                    Screen4Repository(
                        db,
                        Screen4DraftStore(this),
                        Screen4RapidEntryStore(this),
                        Screen4TableSessionStore(this)
                    )
                )
            )

        lifecycleScope.launch {
            val table = screen4Coordinator.initialize()
            renderScreen(table)

            val workspaceName = screen4Coordinator.activeWorkspace()?.name
                ?: getString(R.string.screen4_unknown_table)
            statusText.text = getString(
                R.string.screen4_status_ready_workspace,
                table.columns.size,
                workspaceName
            )

            maybePromptBootstrapForEmptyTable(table)
        }
    }

    private fun maybePromptBootstrapForEmptyTable(table: TableViewModel) {
        if (table.columns.isNotEmpty() || table.rows.isNotEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_bootstrap_title)
            .setMessage(R.string.screen4_bootstrap_message)
            .setPositiveButton(R.string.screen4_bootstrap_default) { _, _ ->
                lifecycleScope.launch {
                    val model = screen4Coordinator.initializeColumnsForActiveWorkspace(
                        initializeDefaultColumns = true,
                        visibleRows = visibleRows
                    )
                    renderScreen(model)
                    statusText.text = getString(R.string.screen4_status_default_table_created)
                }
            }
            .setNegativeButton(R.string.screen4_bootstrap_empty) { _, _ ->
                statusText.text = getString(R.string.screen4_status_empty_table_selected)
            }
            .show()
    }

    private fun confirmStartNewTable() {
        val nameInput = EditText(this).apply {
            hint = getString(R.string.screen4_new_table_name_hint)
            setText(getString(R.string.screen4_new_table_default_name))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_new_table_confirm_title)
            .setMessage(R.string.screen4_new_table_confirm_message)
            .setView(nameInput)
            .setPositiveButton(R.string.screen4_new_table_confirm_action) { _, _ ->
                val tableName = nameInput.text.toString().trim()
                showNewTableSeedDialog(tableName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewTableSeedDialog(tableName: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_new_table_seed_title)
            .setMessage(R.string.screen4_new_table_seed_message)
            .setPositiveButton(R.string.screen4_bootstrap_default) { _, _ ->
                lifecycleScope.launch {
                    val model = screen4Coordinator.createAndSelectWorkspace(
                        tableName,
                        visibleRows,
                        initializeDefaultColumns = true
                    )
                    selectedRowId = null
                    renderScreen(model)
                    statusText.text = getString(
                        R.string.screen4_status_new_table_workspace_started,
                        tableName.ifBlank { getString(R.string.screen4_new_table_default_name) }
                    )
                }
            }
            .setNegativeButton(R.string.screen4_bootstrap_empty) { _, _ ->
                lifecycleScope.launch {
                    val model = screen4Coordinator.createAndSelectWorkspace(
                        tableName,
                        visibleRows,
                        initializeDefaultColumns = false
                    )
                    selectedRowId = null
                    renderScreen(model)
                    statusText.text = getString(R.string.screen4_status_empty_table_selected)
                }
            }
            .show()
    }

    private fun showOpenTableDialog() {
        lifecycleScope.launch {
            val workspaces = screen4Coordinator.listActiveWorkspaces()
            if (workspaces.isEmpty()) {
                statusText.text = getString(R.string.screen4_status_open_table_none)
                return@launch
            }

            val labels = workspaces.map { workspace -> workspace.name }.toTypedArray()
            AlertDialog.Builder(this@Screen4Activity)
                .setTitle(R.string.screen4_open_table_title)
                .setItems(labels) { _, which ->
                    val workspace = workspaces[which]
                    lifecycleScope.launch {
                        val switched = screen4Coordinator.selectWorkspace(workspace.id, visibleRows)
                        if (!switched) {
                            statusText.text = getString(R.string.screen4_status_open_table_switch_failed)
                            return@launch
                        }
                        selectedRowId = null
                        val table = screen4Coordinator.tableModel(visibleRows)
                        renderScreen(table)
                        statusText.text = getString(R.string.screen4_status_open_table_loaded_workspace, workspace.name)
                        maybePromptBootstrapForEmptyTable(table)
                    }
                }
                .setNeutralButton(R.string.screen4_restore_archived_table) { _, _ ->
                    showRestoreArchivedTablesDialog()
                }
                .show()
        }
    }

    private fun confirmArchiveActiveTable() {
        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_archive_table_confirm_title)
            .setMessage(R.string.screen4_archive_table_confirm_message)
            .setPositiveButton(R.string.screen4_archive_table_confirm_action) { _, _ ->
                lifecycleScope.launch {
                    val archived = screen4Coordinator.archiveActiveWorkspace(visibleRows)
                    if (!archived) {
                        statusText.text = getString(R.string.screen4_status_archive_table_failed)
                        return@launch
                    }
                    selectedRowId = null
                    val table = screen4Coordinator.tableModel(visibleRows)
                    renderScreen(table)
                    statusText.text = getString(R.string.screen4_status_archive_table_success)
                    maybePromptBootstrapForEmptyTable(table)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRestoreArchivedTablesDialog() {
        lifecycleScope.launch {
            val archived = screen4Coordinator.listArchivedWorkspaces()
            if (archived.isEmpty()) {
                statusText.text = getString(R.string.screen4_status_restore_table_none)
                return@launch
            }

            val labels = archived.map { it.name }.toTypedArray()
            AlertDialog.Builder(this@Screen4Activity)
                .setTitle(R.string.screen4_restore_archived_table_title)
                .setItems(labels) { _, which ->
                    val workspace = archived[which]
                    lifecycleScope.launch {
                        val restored = screen4Coordinator.restoreWorkspace(workspace.id, visibleRows)
                        if (!restored) {
                            statusText.text = getString(R.string.screen4_status_restore_table_failed)
                            return@launch
                        }
                        selectedRowId = null
                        val table = screen4Coordinator.tableModel(visibleRows)
                        renderScreen(table)
                        statusText.text = getString(R.string.screen4_status_restore_table_success, workspace.name)
                        maybePromptBootstrapForEmptyTable(table)
                    }
                }
                .show()
        }
    }

    private fun showAddColumnDialog() {
        val labelInput = EditText(this).apply {
            hint = getString(R.string.screen4_add_column_hint)
        }

        val groupOptions = screen4Coordinator.columnFormatGroups()
        if (groupOptions.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "No format groups available")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_add_column_group_title)
            .setItems(groupOptions.map { it.label }.toTypedArray()) { _, whichGroup ->
                val group = groupOptions[whichGroup]
                val formatOptions = screen4Coordinator.columnFormatOptions(group.key)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.screen4_add_column_type_title, group.label))
                    .setItems(formatOptions.map { "${it.label} (e.g., ${it.previewExample})" }.toTypedArray()) { _, whichFormat ->
                        val chosenFormat = formatOptions[whichFormat]
                        AlertDialog.Builder(this)
                            .setTitle(R.string.screen4_add_column_title)
                            .setView(labelInput)
                            .setPositiveButton(R.string.screen4_add_column_confirm) { _, _ ->
                                val requestedLabel = labelInput.text.toString().trim().ifBlank {
                                    getString(R.string.screen4_default_new_column_label)
                                }
                                lifecycleScope.launch {
                                    val result = screen4Coordinator.addColumn(requestedLabel, chosenFormat.typeName, visibleRows)
                                    result.onSuccess { table ->
                                        renderScreen(table)
                                        statusText.text = getString(
                                            R.string.screen4_status_column_added_with_type,
                                            table.columns.size,
                                            chosenFormat.label
                                        )
                                    }
                                    result.onFailure {
                                        statusText.text = getString(
                                            R.string.screen4_status_add_column_failed,
                                            it.message ?: "unknown"
                                        )
                                    }
                                }
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPruneColumnsDialog() {
        val activeColumns = screen4Coordinator.activeColumns()
        if (activeColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_prune_none_available)
            return
        }

        val labels = activeColumns.map { it.label }.toTypedArray()
        val checked = BooleanArray(activeColumns.size)

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_prune_columns_title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.screen4_prune_columns_confirm) { _, _ ->
                val selectedIds = activeColumns.mapIndexedNotNull { index, column ->
                    if (checked[index]) column.columnId else null
                }
                lifecycleScope.launch {
                    val result = screen4Coordinator.pruneColumns(selectedIds, visibleRows)
                    result.onSuccess { table ->
                        renderScreen(table)
                        statusText.text = getString(R.string.screen4_status_pruned_columns, selectedIds.size)
                    }
                    result.onFailure {
                        statusText.text = getString(
                            R.string.screen4_status_prune_failed,
                            it.message ?: "unknown"
                        )
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshTable(statusOverride: String) {
        lifecycleScope.launch {
            val table = screen4Coordinator.tableModel(visibleRows)
            renderScreen(table)
            statusText.text = statusOverride
        }
    }

    private fun renderScreen(model: TableViewModel) {
        renderLongFormGrid(model)
        renderTable(model)
        setActionButtonsEnabledForColumns(model.columns.isNotEmpty())
    }

    private fun renderLongFormGrid(model: TableViewModel) {
        longFormGrid.removeAllViews()

        if (model.columns.isEmpty()) {
            longFormGrid.addView(
                TextView(this).apply {
                    text = getString(R.string.screen4_long_form_empty)
                }
            )
            return
        }

        model.columns.forEach { column ->
            val fieldCell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
            }

            val label = TextView(this).apply {
                text = column.label
                textSize = 12f
            }

            val input = EditText(this).apply {
                val previewExample = Screen4ColumnFormatCatalog.previewForType(column.constraintType)
                hint = getString(
                    R.string.screen4_draft_hint_with_example,
                    column.label,
                    if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                    column.maxLength,
                    previewExample,
                )
                setText(screen4Coordinator.currentDraft().valuesByColumnId[column.columnId].orEmpty())
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        val value = s?.toString().orEmpty()
                        screen4Coordinator.userInputEvent(column.columnId, value)
                        error = screen4Coordinator.validateField(column, value)
                    }
                })
            }

            fieldCell.addView(label)
            fieldCell.addView(input)
            longFormGrid.addView(fieldCell)
        }
    }

    private fun setActionButtonsEnabledForColumns(hasColumns: Boolean) {
        deleteColumnsButton.isEnabled = hasColumns
        shortFormButton.isEnabled = hasColumns
        deleteSelectedButton.isEnabled = hasColumns
        exportCsvButton.isEnabled = hasColumns
        longFormGrid.alpha = if (hasColumns) 1f else 0.45f
    }

    private fun renderTable(model: TableViewModel) {
        tableLayout.removeAllViews()

        if (model.rows.isEmpty()) {
            val row = TableRow(this)
            row.addView(
                TextView(this).apply {
                    text = getString(R.string.screen4_table_empty)
                }
            )
            tableLayout.addView(row)
            return
        }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

        model.rows.forEach { row ->
            val rowView = TableRow(this)

            rowView.setOnClickListener {
                selectedRowId = row.rowId
                statusText.text = getString(R.string.screen4_status_selected_row, row.rowId)
            }

            rowView.addView(bodyCell(row.rowId.toString()))
            rowView.addView(bodyCell(formatter.format(Date(row.createdAtMillis))))

            model.columns.forEach {
                rowView.addView(bodyCell(row.valuesByColumnId[it.columnId].orEmpty()))
            }

            tableLayout.addView(rowView)
        }
    }

    private fun exportTableToCsv(uri: Uri) {
        lifecycleScope.launch {
            runCatching {
                val table = screen4Coordinator.tableModel(visibleRows)
                contentResolver
                    .openOutputStream(uri)
                    ?.use { stream ->
                        OutputStreamWriter(stream).use { writer ->
                            val headers = buildList {
                                add("row_id")
                                add("created")
                                addAll(table.columns.map { it.label })
                            }
                            writer.appendLine(headers.joinToString(","))

                            table.rows.forEach { row ->
                                val values = buildList {
                                    add(row.rowId.toString())
                                    add(row.createdAtMillis.toString())
                                    addAll(table.columns.map { row.valuesByColumnId[it.columnId].orEmpty() })
                                }
                                writer.appendLine(values.joinToString(","))
                            }
                        }
                    }
                table.rows.size
            }.onSuccess { rowCount ->
                statusText.text = getString(R.string.screen4_status_export_success, rowCount)
            }.onFailure {
                statusText.text = getString(R.string.screen4_status_export_failed, it.message ?: "unknown")
            }
        }
    }

    private fun bindCollapsibleSection(
        body: View,
        toggleButton: Button
    ) {
        toggleButton.setOnClickListener {
            val isCollapsed = body.visibility == View.GONE
            body.visibility = if (isCollapsed) View.VISIBLE else View.GONE
            toggleButton.text = if (isCollapsed) {
                getString(R.string.screen4_section_collapse)
            } else {
                getString(R.string.screen4_section_expand)
            }
        }
    }

    private fun bodyCell(value: String) =
        TextView(this).apply {
            text = value
            setPadding(12, 8, 12, 8)
        }
}
