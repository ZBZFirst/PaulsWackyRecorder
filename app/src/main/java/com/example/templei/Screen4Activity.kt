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
import android.widget.FrameLayout
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
    private enum class EntryMode { LONG_FORM, RAPID }

    /**
     * Phase 0 explicit rapid modal state vocabulary used for UI/flow observability.
     */
    private enum class RapidModalState { IDLE, EDITING, APPENDED, COMMITTED, ERROR }

    private data class RapidCommittedSnapshot(
        val valuesByColumnId: Map<Long, String>,
        val committedAtMillis: Long,
    )

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
    private var entryMode: EntryMode = EntryMode.LONG_FORM
    private var rapidModalState: RapidModalState = RapidModalState.IDLE
    private val rapidCommittedHistory: MutableList<RapidCommittedSnapshot> = mutableListOf()

    companion object {
        private const val MAX_RAPID_HISTORY = 5
    }

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
            setEntryMode(EntryMode.LONG_FORM)
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
                        rapidModalState = RapidModalState.ERROR
                        statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                    }
                } else {
                    val result = screen4Coordinator.commitMeasurement(visibleRows, useRapidEntryConfig = entryMode == EntryMode.RAPID)
                    result.onSuccess { model ->
                        val insertedId = model.rows.firstOrNull()?.rowId ?: 0L
                        rapidModalState = RapidModalState.COMMITTED
                        statusText.text = getString(R.string.screen4_status_measurement_saved, insertedId)
                        renderTable(model)
                        renderEntryForms(screen4Coordinator.currentDraft())
                    }.onFailure {
                        rapidModalState = RapidModalState.ERROR
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
            setEntryMode(EntryMode.LONG_FORM)
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
        rapidCommittedHistory.clear()
        val draft = screen4Coordinator.startNewDraft()
        renderEntryForms(draft)
        showRapidEntryInputDialog()
    }


    private fun showRapidEntryInputDialog() {
        val rapidColumns = currentRapidEntryColumns()
        if (rapidColumns.isEmpty()) {
            rapidModalState = RapidModalState.ERROR
            statusText.text = getString(R.string.screen4_status_columns_unavailable)
            return
        }

        rapidModalState = RapidModalState.EDITING
        val draft = screen4Coordinator.currentDraft()
        // Phase 5 placeholder contract: measurements-included region is UI-only and intentionally
        // non-interactive in this phase. It must not mutate draft/commit payload behavior.
        // See SCREEN4_RAPID_ENTRY_PHASE5_REVIEW.md for acceptance notes.
        val modalView = layoutInflater.inflate(R.layout.dialog_screen4_rapid_entry, null)
        val titleText = modalView.findViewById<TextView>(R.id.rapidModalTitle)
        val committedContainer = modalView.findViewById<FrameLayout>(R.id.rapidCommittedContainer)
        val newValuesContainer = modalView.findViewById<LinearLayout>(R.id.rapidNewValuesContainer)
        val modalStatus = modalView.findViewById<TextView>(R.id.rapidModalStatus)
        titleText.text = getString(R.string.screen4_rapid_entry_popup_title, rapidColumns.size)
        renderCommittedPreviewRows(committedContainer, rapidColumns)

        val inputsByColumn = mutableMapOf<ActiveColumn, EditText>()

        rapidColumns.forEachIndexed { index, column ->
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
            newValuesContainer.addView(input)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(modalView)
            .create()

        dialog.setOnShowListener {
            val appendButton = modalView.findViewById<Button>(R.id.rapidAppendButton)
            val commitButton = modalView.findViewById<Button>(R.id.rapidCommitNextButton)

            appendButton.setOnClickListener {
                if (hasRapidInputErrors(inputsByColumn)) return@setOnClickListener
                appendRapidCommitted(
                    inputsByColumn = inputsByColumn,
                    committedContainer = committedContainer,
                    rapidColumns = rapidColumns,
                    modalStatus = modalStatus,
                )
            }

            commitButton.setOnClickListener {
                if (hasRapidInputErrors(inputsByColumn)) return@setOnClickListener
                commitRapidMeasurement(
                    commitButton = commitButton,
                    appendButton = appendButton,
                    modalStatus = modalStatus,
                    dialog = dialog,
                    inputsByColumn = inputsByColumn,
                )
            }

            modalView.findViewById<Button>(R.id.rapidModalReselectButton).setOnClickListener {
                rapidModalState = RapidModalState.IDLE
                dialog.dismiss()
                showRapidEntryColumnsDialog()
            }

            modalView.findViewById<Button>(R.id.rapidModalCloseButton).setOnClickListener {
                rapidModalState = RapidModalState.IDLE
                setEntryMode(EntryMode.LONG_FORM)
                statusText.text = getString(R.string.screen4_status_manual_entry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun hasRapidInputErrors(inputsByColumn: Map<ActiveColumn, EditText>): Boolean {
        var hasErrors = false
        inputsByColumn.forEach { (column, input) ->
            val value = input.text?.toString().orEmpty()
            val validationError = screen4Coordinator.validateField(column, value)
            input.error = validationError
            if (validationError != null) hasErrors = true
        }
        return hasErrors
    }

    private fun appendRapidCommitted(
        inputsByColumn: Map<ActiveColumn, EditText>,
        committedContainer: FrameLayout,
        rapidColumns: List<ActiveColumn>,
        modalStatus: TextView,
    ) {
        val snapshot = buildRapidInputSnapshot(inputsByColumn)
        addRapidCommittedSnapshot(snapshot)
        clearRapidInputFields(inputsByColumn)
        renderCommittedPreviewRows(committedContainer, rapidColumns)
        rapidModalState = RapidModalState.APPENDED
        val populatedCount = snapshot.valuesByColumnId.values.count { it.isNotBlank() }
        modalStatus.text = getString(R.string.screen4_rapid_entry_append_success, populatedCount)
    }

    private fun clearRapidInputFields(inputsByColumn: Map<ActiveColumn, EditText>) {
        inputsByColumn.forEach { (column, input) ->
            input.setText("")
            input.error = null
            screen4Coordinator.userInputEvent(column.columnId, "")
        }
    }

    private fun commitRapidMeasurement(
        commitButton: Button,
        appendButton: Button,
        modalStatus: TextView,
        dialog: AlertDialog,
        inputsByColumn: Map<ActiveColumn, EditText>,
    ) {
        if (!commitButton.isEnabled) return

        commitButton.isEnabled = false
        appendButton.isEnabled = false
        modalStatus.text = getString(R.string.screen4_rapid_entry_commit_in_progress)

        lifecycleScope.launch {
            val result = screen4Coordinator.commitMeasurement(visibleRows, useRapidEntryConfig = true)
            result.onSuccess { model ->
                val insertedId = model.rows.firstOrNull()?.rowId ?: 0L
                addRapidCommittedSnapshot(buildRapidInputSnapshot(inputsByColumn))
                rapidModalState = RapidModalState.COMMITTED
                statusText.text = getString(R.string.screen4_status_measurement_saved, insertedId)
                renderTable(model)
                renderEntryForms(screen4Coordinator.currentDraft())
                dialog.dismiss()
                if (entryMode == EntryMode.RAPID) {
                    showRapidEntryInputDialog()
                }
            }.onFailure {
                rapidModalState = RapidModalState.ERROR
                statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                modalStatus.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                commitButton.isEnabled = true
                appendButton.isEnabled = true
            }
        }
    }

    private fun buildRapidInputSnapshot(inputsByColumn: Map<ActiveColumn, EditText>): RapidCommittedSnapshot {
        val values = inputsByColumn.entries.associate { (column, input) ->
            column.columnId to input.text?.toString().orEmpty().trim()
        }
        return RapidCommittedSnapshot(valuesByColumnId = values, committedAtMillis = System.currentTimeMillis())
    }

    private fun addRapidCommittedSnapshot(snapshot: RapidCommittedSnapshot) {
        rapidCommittedHistory.add(0, snapshot)
        while (rapidCommittedHistory.size > MAX_RAPID_HISTORY) {
            rapidCommittedHistory.removeAt(rapidCommittedHistory.lastIndex)
        }
    }

    private fun renderCommittedPreviewRows(container: FrameLayout, columns: List<ActiveColumn>) {
        container.removeAllViews()
        if (rapidCommittedHistory.isEmpty() || columns.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.screen4_rapid_entry_committed_empty)
                setPadding(8, 8, 8, 8)
            })
            return
        }

        val displayed = rapidCommittedHistory.take(MAX_RAPID_HISTORY).reversed()
        displayed.forEachIndexed { index, snapshot ->
            val isTopCard = index == displayed.lastIndex
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_surface)
                setPadding(12, 10, 12, 10)
                translationX = ((displayed.size - 1 - index) * 8).toFloat()
                translationY = ((displayed.size - 1 - index) * 10).toFloat()
                alpha = if (isTopCard) 1.0f else 0.72f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
            }

            val title = TextView(this).apply {
                text = if (isTopCard) {
                    getString(R.string.screen4_rapid_entry_latest_commit)
                } else {
                    getString(R.string.screen4_rapid_entry_previous_commit, displayed.size - index)
                }
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            card.addView(title)

            if (isTopCard) {
                columns.forEach { column ->
                    val value = snapshot.valuesByColumnId[column.columnId].orEmpty().ifBlank { "—" }
                    card.addView(TextView(this).apply {
                        text = getString(R.string.screen4_rapid_entry_committed_item, column.label, value)
                        setPadding(0, 4, 0, 0)
                    })
                }
            } else {
                val populatedCount = snapshot.valuesByColumnId.values.count { it.isNotBlank() }
                card.addView(TextView(this).apply {
                    text = getString(R.string.screen4_rapid_entry_previous_summary, populatedCount)
                    setPadding(0, 4, 0, 0)
                })
            }

            container.addView(card)
        }
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
