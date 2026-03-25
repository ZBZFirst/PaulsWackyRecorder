package com.example.templei

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ScrollView
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.Screen4ColumnFormatCatalog
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4FieldInputFormatter
import com.example.templei.feature.screen4.Screen4InputUiPolicy
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.Screen4NumericFormatPolicy
import com.example.templei.feature.screen4.Screen4NumericKind
import com.example.templei.feature.screen4.Screen4RapidEntryStore
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.Screen4SimpleNumericCatalog
import com.example.templei.feature.screen4.Screen4TableSessionStore
import com.example.templei.feature.screen4.Screen4TemporalInputPolicy
import com.example.templei.feature.screen4.TableViewModel
import com.example.templei.feature.screen4.Screen4WorkbookCatalog
import com.example.templei.feature.screen4.Screen4WorkbookColumnDefinition
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Screen 4 deterministic serial measurement engine host.
 */
class Screen4Activity : ComponentActivity() {
    companion object {
        private val ADVANCED_NUMERIC_COLUMN_NAMES = setOf(
            "currency_usd",
            "currency_usd_plain",
            "percent",
            "percent_decimal",
            "number_scientific",
        )
    }

    private lateinit var screen4Coordinator: Screen4Coordinator

    private lateinit var statusText: TextView
    private lateinit var workspaceSummaryText: TextView
    private lateinit var tableStatsText: TextView
    private lateinit var rowDisplayLabel: TextView
    private lateinit var rowDisplaySlider: SeekBar
    private lateinit var tableLayout: TableLayout
    private lateinit var dataScreenScrollView: ScrollView
    private lateinit var actionSectionBody: View
    private lateinit var actionSectionToggleButton: Button
    private lateinit var longFormSectionBody: View
    private lateinit var longFormSectionToggleButton: Button
    private lateinit var longFormEditorCard: View
    private lateinit var longFormEditorLabel: TextView
    private lateinit var longFormEditorMeta: TextView
    private lateinit var longFormEditorValidation: TextView
    private lateinit var longFormEditorInputContainer: LinearLayout
    private lateinit var longFormFieldList: LinearLayout

    private lateinit var deleteColumnsButton: Button
    private lateinit var shortFormButton: Button
    private lateinit var deleteSelectedButton: Button
    private lateinit var exportCsvButton: Button
    private lateinit var saveDraftButton: Button
    private lateinit var clearDraftButton: Button
    private lateinit var loadSelectedRowButton: Button

    private var visibleRows: Int = Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS
    private var selectedRowId: Long? = null
    private var editingRowId: Long? = null
    private var activeWorkspaceName: String = ""
    private var activeLongFormColumnId: Long? = null
    private val longFormInputsByColumnId = mutableMapOf<Long, EditText>()

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
        workspaceSummaryText = findViewById(R.id.workspaceSummaryText)
        tableStatsText = findViewById(R.id.tableStatsText)
        rowDisplayLabel = findViewById(R.id.rowDisplayLabel)
        rowDisplaySlider = findViewById(R.id.rowDisplaySlider)
        tableLayout = findViewById(R.id.tableLayout)
        dataScreenScrollView = findViewById(R.id.dataScreenScrollView)
        actionSectionBody = findViewById(R.id.actionSectionBody)
        actionSectionToggleButton = findViewById(R.id.actionSectionToggleButton)
        longFormSectionBody = findViewById(R.id.longFormSectionBody)
        longFormSectionToggleButton = findViewById(R.id.longFormSectionToggleButton)
        longFormEditorCard = findViewById(R.id.longFormEditorCard)
        longFormEditorLabel = findViewById(R.id.longFormEditorLabel)
        longFormEditorMeta = findViewById(R.id.longFormEditorMeta)
        longFormEditorValidation = findViewById(R.id.longFormEditorValidation)
        longFormEditorInputContainer = findViewById(R.id.longFormEditorInputContainer)
        longFormFieldList = findViewById(R.id.longFormFieldList)

        deleteColumnsButton = findViewById(R.id.pruneColumnsButton)
        shortFormButton = findViewById(R.id.selectRowButton)
        deleteSelectedButton = findViewById(R.id.deleteSelectedRowButton)
        exportCsvButton = findViewById(R.id.exportCsvButton)
        saveDraftButton = findViewById(R.id.saveDraftButton)
        clearDraftButton = findViewById(R.id.clearDraftButton)
        loadSelectedRowButton = findViewById(R.id.loadSelectedRowButton)

        bindCollapsibleSection(actionSectionBody, actionSectionToggleButton)
        bindCollapsibleSection(longFormSectionBody, longFormSectionToggleButton)
        bindImeSafeScroll(dataScreenScrollView)
        rowDisplayLabel.text = getString(R.string.screen4_rows_visible, visibleRows)

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
                    editingRowId = if (editingRowId == rowId) null else editingRowId
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

        saveDraftButton.setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            commitLongFormDraft()
        }

        clearDraftButton.setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            screen4Coordinator.startNewDraft()
            editingRowId = null
            lifecycleScope.launch {
                renderScreen(screen4Coordinator.tableModel(visibleRows))
                statusText.text = getString(R.string.screen4_status_draft_cleared)
            }
        }

        loadSelectedRowButton.setOnClickListener {
            if (!::screen4Coordinator.isInitialized) return@setOnClickListener
            loadSelectedRowIntoDraft()
        }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        val workbookCatalog = Screen4WorkbookCatalog.getInstance(this)
        screen4Coordinator =
            Screen4Coordinator(
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
            renderScreen(table)

            activeWorkspaceName = screen4Coordinator.activeWorkspace()?.name
                ?: getString(R.string.screen4_unknown_table)
            statusText.text = getString(
                R.string.screen4_status_ready_workspace,
                table.columns.size,
                activeWorkspaceName
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
                    activeWorkspaceName = tableName.ifBlank { getString(R.string.screen4_new_table_default_name) }
                    selectedRowId = null
                    editingRowId = null
                    renderScreen(model)
                    statusText.text = getString(
                        R.string.screen4_status_new_table_workspace_started,
                        activeWorkspaceName
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
                    activeWorkspaceName = tableName.ifBlank { getString(R.string.screen4_new_table_default_name) }
                    selectedRowId = null
                    editingRowId = null
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
                        activeWorkspaceName = workspace.name
                        selectedRowId = null
                        editingRowId = null
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
                    editingRowId = null
                    activeWorkspaceName = screen4Coordinator.activeWorkspace()?.name
                        ?: getString(R.string.screen4_unknown_table)
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
                        activeWorkspaceName = workspace.name
                        selectedRowId = null
                        editingRowId = null
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
        val groupOptions = screen4Coordinator.workbookColumnGroups()
        if (groupOptions.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "No workbook column groups available")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_add_column_group_title)
            .setItems(groupOptions.map { "${it.label} (${it.itemCount})" }.toTypedArray()) { _, whichGroup ->
                val group = groupOptions[whichGroup]
                if (group.key.equals("NUMBERS", ignoreCase = true)) {
                    showSimpleNumericFamilyDialog()
                } else {
                    showAddColumnSubgroupDialog(group.key, group.label)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddColumnSubgroupDialog(groupKey: String, groupLabel: String) {
        val subgroupOptions = screen4Coordinator.workbookColumnSubgroups(groupKey)
        if (subgroupOptions.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "No workbook subgroups available")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_column_subgroup_title, groupLabel))
            .setItems(subgroupOptions.map { "${it.label} (${it.itemCount})" }.toTypedArray()) { _, whichSubgroup ->
                val subgroup = subgroupOptions[whichSubgroup]
                val workbookColumns = screen4Coordinator.workbookColumnsForGroup(groupKey, subgroup.key)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.screen4_add_column_type_title, subgroup.label))
                    .setItems(workbookColumns.map { "${it.displayName} (e.g., ${it.uiExampleValue})" }.toTypedArray()) { _, whichColumn ->
                        val workbookColumn = workbookColumns[whichColumn]
                        showAddWorkbookColumnMetadataDialog(workbookColumn)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSimpleNumericFamilyDialog() {
        val options = Screen4SimpleNumericCatalog.options
        val labels = buildList {
            addAll(options.map { it.label })
            add(getString(R.string.screen4_add_numeric_family_advanced))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_add_numeric_family_title)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < options.size) {
                    val option = options[which]
                    showNumericPolicyDialog(option.label, option.numericKind)
                } else {
                    showAdvancedNumericColumnDialog()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAdvancedNumericColumnDialog() {
        val advancedColumns = screen4Coordinator
            .workbookColumnSubgroups("NUMBERS")
            .flatMap { subgroup -> screen4Coordinator.workbookColumnsForGroup("NUMBERS", subgroup.key) }
            .filter { workbookColumn ->
                workbookColumn.columnName in ADVANCED_NUMERIC_COLUMN_NAMES
            }
            .sortedBy { it.displayName }

        if (advancedColumns.isEmpty()) {
            statusText.text = getString(R.string.screen4_status_add_column_failed, "No advanced numeric columns available")
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_add_numeric_advanced_title)
            .setItems(advancedColumns.map { "${it.displayName} (e.g., ${it.uiExampleValue})" }.toTypedArray()) { _, which ->
                showAddWorkbookColumnMetadataDialog(advancedColumns[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNumericPolicyDialog(baseLabel: String, numericKind: Screen4NumericKind) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val labelInput = EditText(this).apply {
            hint = getString(R.string.screen4_add_column_hint)
            setText(baseLabel)
        }
        val maxDigitsInput = EditText(this).apply {
            hint = getString(R.string.screen4_numeric_max_digits_hint)
            setText("10")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val decimalPlacesInput = EditText(this).apply {
            hint = getString(R.string.screen4_numeric_decimal_places_hint)
            setText(if (numericKind == Screen4NumericKind.INTEGER) "0" else "2")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            isEnabled = numericKind == Screen4NumericKind.DECIMAL
            alpha = if (numericKind == Screen4NumericKind.DECIMAL) 1f else 0.5f
        }
        val separatorsCheck = CheckBox(this).apply {
            text = getString(R.string.screen4_numeric_use_separators)
            isChecked = true
        }
        val negativeCheck = CheckBox(this).apply {
            text = getString(R.string.screen4_numeric_allow_negative)
            isChecked = false
        }

        container.addView(labelInput)
        container.addView(maxDigitsInput)
        container.addView(decimalPlacesInput)
        container.addView(separatorsCheck)
        container.addView(negativeCheck)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_add_numeric_policy_title, baseLabel))
            .setMessage(
                if (numericKind == Screen4NumericKind.INTEGER) {
                    getString(R.string.screen4_numeric_policy_integer_message)
                } else {
                    getString(R.string.screen4_numeric_policy_decimal_message)
                }
            )
            .setView(container)
            .setPositiveButton(R.string.screen4_add_column_confirm) { _, _ ->
                val maxDigits = maxDigitsInput.text.toString().trim().toIntOrNull() ?: 10
                val decimalPlaces = if (numericKind == Screen4NumericKind.INTEGER) {
                    0
                } else {
                    decimalPlacesInput.text.toString().trim().toIntOrNull() ?: 2
                }
                val policy = Screen4NumericFormatPolicy(
                    numericKind = numericKind,
                    maxDigits = maxDigits.coerceAtLeast(1),
                    decimalPlaces = decimalPlaces.coerceAtLeast(0),
                    useSeparators = separatorsCheck.isChecked,
                    allowNegative = negativeCheck.isChecked,
                )
                val requestedLabel = labelInput.text.toString().trim().ifBlank { baseLabel }
                lifecycleScope.launch {
                    val result = screen4Coordinator.addNumericPolicyColumn(
                        requestedLabel,
                        policy,
                        visibleRows,
                    )
                    result.onSuccess { table ->
                        renderScreen(table)
                        statusText.text = getString(
                            R.string.screen4_status_numeric_column_added,
                            requestedLabel,
                            policy.summary(),
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

    private fun showAddWorkbookColumnMetadataDialog(workbookColumn: Screen4WorkbookColumnDefinition) {
        val labelInput = EditText(this).apply {
            hint = getString(R.string.screen4_add_column_hint)
            setText(workbookColumn.displayName)
        }

        val message = getString(
            R.string.screen4_add_column_metadata_message,
            workbookColumn.notes.ifBlank { workbookColumn.displayName },
            workbookColumn.uiExampleValue.ifBlank { workbookColumn.columnName },
            workbookColumn.uiInputType ?: "FULL_KEYBOARD",
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.screen4_add_column_metadata_title)
            .setMessage(message)
            .setView(labelInput)
            .setPositiveButton(R.string.screen4_add_column_confirm) { _, _ ->
                val requestedLabel = labelInput.text.toString().trim().ifBlank { workbookColumn.displayName }
                lifecycleScope.launch {
                    val result = screen4Coordinator.addWorkbookColumn(
                        workbookColumn.columnName,
                        requestedLabel,
                        visibleRows,
                    )
                    result.onSuccess { table ->
                        renderScreen(table)
                        statusText.text = getString(
                            R.string.screen4_status_column_added_with_metadata,
                            workbookColumn.displayName,
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
        renderLongFormSection(model)
        renderTable(model)
        updateWorkspaceSummary(model)
        updateLongFormActionButtons(model.columns.isNotEmpty())
        setActionButtonsEnabledForColumns(model.columns.isNotEmpty())
    }

    private fun renderLongFormSection(model: TableViewModel) {
        val activeColumn = resolveActiveLongFormColumn(model)
        renderLongFormFieldList(model, activeColumn?.columnId)
        renderLongFormEditor(model, activeColumn)
    }

    private fun resolveActiveLongFormColumn(model: TableViewModel): com.example.templei.feature.screen4.ActiveColumn? {
        val resolved = model.columns.firstOrNull { it.columnId == activeLongFormColumnId } ?: model.columns.firstOrNull()
        activeLongFormColumnId = resolved?.columnId
        return resolved
    }

    private fun renderLongFormFieldList(
        model: TableViewModel,
        activeColumnId: Long?,
    ) {
        longFormFieldList.removeAllViews()

        if (model.columns.isEmpty()) {
            longFormFieldList.addView(
                TextView(this).apply {
                    text = getString(R.string.screen4_long_form_empty)
                }
            )
            return
        }

        val draft = screen4Coordinator.currentDraft()
        model.columns.forEach { column ->
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
                    if (column.columnId == activeColumnId) R.drawable.bg_card_surface
                    else R.drawable.bg_section_surface
                )
                setOnClickListener {
                    activeLongFormColumnId = column.columnId
                    renderLongFormSection(model)
                    dataScreenScrollView.post {
                        dataScreenScrollView.smoothScrollTo(0, (longFormEditorCard.top - 24).coerceAtLeast(0))
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
                text = if (currentValue.isBlank()) {
                    getString(R.string.screen4_field_value_empty)
                } else {
                    currentValue
                }
                textSize = 16f
            })

            row.addView(TextView(this).apply {
                text = validation ?: getString(R.string.screen4_field_ready)
                textSize = 12f
            })

            longFormFieldList.addView(row)
        }
    }

    private fun renderLongFormEditor(
        model: TableViewModel,
        activeColumn: com.example.templei.feature.screen4.ActiveColumn?,
    ) {
        longFormEditorInputContainer.removeAllViews()

        if (activeColumn == null) {
            longFormEditorCard.alpha = 0.45f
            longFormEditorLabel.text = getString(R.string.screen4_editor_none_selected)
            longFormEditorMeta.text = getString(R.string.screen4_long_form_empty)
            longFormEditorValidation.text = ""
            return
        }

        longFormEditorCard.alpha = 1f
        longFormEditorLabel.text = activeColumn.label
        longFormEditorMeta.text = buildString {
            append(describeInputMode(activeColumn))
            append(" • ")
            append(Screen4InputUiPolicy.buildHint(this@Screen4Activity, activeColumn))
        }
        val currentValue = screen4Coordinator.currentDraft().valuesByColumnId[activeColumn.columnId].orEmpty()
        val input = longFormInputsByColumnId[activeColumn.columnId] ?: buildLongFormInput(activeColumn).also {
            longFormInputsByColumnId[activeColumn.columnId] = it
        }
        if (input.text?.toString().orEmpty() != currentValue) {
            input.setText(currentValue)
            input.setSelection(input.text?.length ?: 0)
        }
        attachInputToContainer(input, longFormEditorInputContainer)
        longFormEditorValidation.text = screen4Coordinator.validateField(activeColumn, currentValue)
            ?: getString(R.string.screen4_field_ready)
    }

    private fun setActionButtonsEnabledForColumns(hasColumns: Boolean) {
        deleteColumnsButton.isEnabled = hasColumns
        shortFormButton.isEnabled = hasColumns
        deleteSelectedButton.isEnabled = hasColumns && selectedRowId != null
        exportCsvButton.isEnabled = hasColumns
        longFormEditorCard.alpha = if (hasColumns) 1f else 0.45f
        longFormFieldList.alpha = if (hasColumns) 1f else 0.45f
    }

    private fun updateLongFormActionButtons(hasColumns: Boolean) {
        saveDraftButton.isEnabled = hasColumns
        clearDraftButton.isEnabled = hasColumns
        loadSelectedRowButton.isEnabled = hasColumns && selectedRowId != null
        saveDraftButton.text = if (editingRowId != null) {
            getString(R.string.screen4_update_selected_row)
        } else {
            getString(R.string.screen4_save_new_row)
        }
    }

    private fun renderTable(model: TableViewModel) {
        tableLayout.removeAllViews()

        if (model.columns.isNotEmpty()) {
            val headerRow = TableRow(this)
            headerRow.addView(headerCell(getString(R.string.screen4_table_header_row_id)))
            headerRow.addView(headerCell(getString(R.string.screen4_table_header_created)))
            model.columns.forEach { column ->
                headerRow.addView(headerCell(column.label))
            }
            tableLayout.addView(headerRow)
        }

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
                statusText.text = getString(R.string.screen4_status_selected_row_ready, row.rowId)
                updateLongFormActionButtons(model.columns.isNotEmpty())
                updateWorkspaceSummary(model)
                renderTable(model)
            }

            if (selectedRowId == row.rowId) {
                rowView.setBackgroundColor(0x1F000000)
            }

            rowView.addView(bodyCell(row.rowId.toString()))
            rowView.addView(bodyCell(formatter.format(Date(row.createdAtMillis))))

            model.columns.forEach {
                rowView.addView(bodyCell(row.valuesByColumnId[it.columnId].orEmpty()))
            }

            tableLayout.addView(rowView)
        }
    }

    private fun commitLongFormDraft() {
        lifecycleScope.launch {
            val editingId = editingRowId
            if (editingId != null) {
                val result = screen4Coordinator.applyDraftToRow(editingId, visibleRows)
                result.onSuccess { table ->
                    editingRowId = null
                    renderScreen(table)
                    statusText.text = getString(R.string.screen4_status_measurement_updated, editingId)
                }.onFailure {
                    statusText.text = getString(
                        R.string.screen4_status_measurement_failed,
                        it.message ?: "unknown"
                    )
                }
            } else {
                val result = screen4Coordinator.commitMeasurement(visibleRows)
                result.onSuccess { table ->
                    selectedRowId = table.rows.firstOrNull()?.rowId
                    renderScreen(table)
                    statusText.text = getString(
                        R.string.screen4_status_measurement_saved,
                        table.rows.firstOrNull()?.rowId ?: 0L
                    )
                }.onFailure {
                    statusText.text = getString(
                        R.string.screen4_status_measurement_failed,
                        it.message ?: "unknown"
                    )
                }
            }
        }
    }

    private fun loadSelectedRowIntoDraft() {
        val rowId = selectedRowId
        if (rowId == null) {
            statusText.text = getString(R.string.screen4_status_select_row_first)
            return
        }

        lifecycleScope.launch {
            screen4Coordinator.beginEditFromRow(rowId)
            editingRowId = rowId
            val table = screen4Coordinator.tableModel(visibleRows)
            renderScreen(table)
            statusText.text = getString(R.string.screen4_status_loaded_row_for_edit, rowId)
        }
    }

    private fun updateWorkspaceSummary(model: TableViewModel) {
        workspaceSummaryText.text = getString(
            R.string.screen4_workspace_summary,
            activeWorkspaceName.ifBlank { getString(R.string.screen4_unknown_table) }
        )
        tableStatsText.text = getString(
            R.string.screen4_table_stats,
            model.rows.size,
            model.columns.size,
            selectedRowId?.toString() ?: getString(R.string.screen4_selected_row_none),
        )
    }

    private fun createInputView(column: com.example.templei.feature.screen4.ActiveColumn): EditText {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        val input = if (Screen4InputUiPolicy.usesAllowedValuePicker(column)) {
            AutoCompleteTextView(this).apply {
                setAdapter(
                    ArrayAdapter(
                        this@Screen4Activity,
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
        bindInputFocusScroll(input, dataScreenScrollView)
        return input
    }

    private fun buildLongFormInput(column: com.example.templei.feature.screen4.ActiveColumn): EditText {
        val resolvedType = screen4Coordinator.resolveColumnType(column)
        return createInputView(column).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            hint = Screen4InputUiPolicy.buildHint(this@Screen4Activity, column)
            var formattingInProgress = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (formattingInProgress) return

                    val currentValue = s?.toString().orEmpty()
                    val formattedValue = if (
                        Screen4InputUiPolicy.shouldApplyLiveFormatter(column, resolvedType) &&
                        Screen4FieldInputFormatter.supports(column.constraintType)
                    ) {
                        Screen4FieldInputFormatter.format(column.constraintType, currentValue)
                    } else {
                        currentValue
                    }
                    val normalizedValue = screen4Coordinator.normalizeField(column, formattedValue)

                    if (normalizedValue != currentValue) {
                        formattingInProgress = true
                        setText(normalizedValue)
                        setSelection(normalizedValue.length)
                        formattingInProgress = false
                    }

                    screen4Coordinator.userInputEvent(column.columnId, normalizedValue)
                    val validation = screen4Coordinator.validateField(column, normalizedValue)
                    error = validation
                    if (activeLongFormColumnId == column.columnId) {
                        longFormEditorValidation.text = validation ?: getString(R.string.screen4_field_ready)
                    }
                    renderLongFormFieldList(
                        TableViewModel(
                            columns = screen4Coordinator.activeColumns(),
                            rows = emptyList(),
                        ),
                        activeLongFormColumnId,
                    )
                }
            })
        }
    }

    private fun describeInputMode(column: com.example.templei.feature.screen4.ActiveColumn): String {
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

    private fun configureDatePickerInput(input: EditText, column: com.example.templei.feature.screen4.ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.isFocusable = true
        input.isClickable = true
        input.setOnClickListener {
            showDatePickerDialog(
                input = input,
                column = column,
                initialDate = Screen4TemporalInputPolicy.parseExistingDate(input.text?.toString().orEmpty()),
            )
        }
    }

    private fun configureTimePickerInput(input: EditText, column: com.example.templei.feature.screen4.ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.isFocusable = true
        input.isClickable = true
        input.setOnClickListener {
            showTimePickerDialog(
                input = input,
                column = column,
                initialTime = Screen4TemporalInputPolicy.parseExistingTime(input.text?.toString().orEmpty()),
            )
        }
    }

    private fun configureTimestampPickerInput(input: EditText, column: com.example.templei.feature.screen4.ActiveColumn) {
        Screen4InputUiPolicy.applyPickerFieldBehavior(input)
        input.isFocusable = true
        input.isClickable = true
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
        column: com.example.templei.feature.screen4.ActiveColumn,
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
        column: com.example.templei.feature.screen4.ActiveColumn,
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
        column: com.example.templei.feature.screen4.ActiveColumn,
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
        column: com.example.templei.feature.screen4.ActiveColumn,
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
        val initialPaddingLeft = scrollView.paddingLeft
        val initialPaddingTop = scrollView.paddingTop
        val initialPaddingRight = scrollView.paddingRight
        val initialPaddingBottom = scrollView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(systemBars.bottom, imeInsets.bottom)
            view.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + bottomInset,
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

    private fun headerCell(value: String) =
        TextView(this).apply {
            text = value
            setPadding(12, 10, 12, 10)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
}
