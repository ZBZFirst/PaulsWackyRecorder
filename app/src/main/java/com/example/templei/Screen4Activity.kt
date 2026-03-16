package com.example.templei

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import com.example.templei.feature.screen4.ActiveColumn
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import android.view.ViewGroup
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
import com.example.templei.feature.screen4.*
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Screen 4 deterministic serial measurement engine host.
 */
class Screen4Activity : ComponentActivity() {

    private lateinit var screen4Coordinator: Screen4Coordinator

    private lateinit var statusText: TextView
    private lateinit var rowDisplayLabel: TextView
    private lateinit var rowDisplaySlider: SeekBar
    private lateinit var manualEntryContainer: LinearLayout
    private lateinit var rapidEntryContainer: LinearLayout
    private lateinit var tableLayout: TableLayout
    private lateinit var actionSectionBody: LinearLayout
    private lateinit var entrySectionBody: LinearLayout
    private lateinit var actionSectionToggleButton: Button
    private lateinit var entrySectionToggleButton: Button

    private var visibleRows: Int = Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS
    private var selectedRowId: Long? = null
    private var editMode: Boolean = false

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
        manualEntryContainer = findViewById(R.id.manualEntryContainer)
        rapidEntryContainer = findViewById(R.id.rapidEntryContainer)
        tableLayout = findViewById(R.id.tableLayout)
        actionSectionBody = findViewById(R.id.actionSectionBody)
        entrySectionBody = findViewById(R.id.entrySectionBody)
        actionSectionToggleButton = findViewById(R.id.actionSectionToggleButton)
        entrySectionToggleButton = findViewById(R.id.entrySectionToggleButton)

        bindCollapsibleSection(actionSectionBody, actionSectionToggleButton)
        bindCollapsibleSection(entrySectionBody, entrySectionToggleButton)

        rowDisplaySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {

                visibleRows = progress.coerceAtLeast(5)

                rowDisplayLabel.text =
                    getString(R.string.screen4_rows_visible, visibleRows)

                if (fromUser) {
                    refreshTable(
                        getString(
                            R.string.screen4_status_refreshed_rows,
                            visibleRows
                        )
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindButtons() {

        findViewById<Button>(R.id.selectRowButton).setOnClickListener {

            if (!::screen4Coordinator.isInitialized) return@setOnClickListener

            startActivity(Intent(this, Screen4ShortFormActivity::class.java))

            statusText.text =
                getString(R.string.screen4_status_short_form_opened)
        }

        findViewById<Button>(R.id.editRowButton).setOnClickListener {

            if (!::screen4Coordinator.isInitialized) return@setOnClickListener

            val rowId = selectedRowId

            if (rowId == null) {

                renderEntryForms(screen4Coordinator.currentDraft())

                statusText.text =
                    getString(R.string.screen4_status_manual_entry)

                return@setOnClickListener
            }

            lifecycleScope.launch {

                val draft = screen4Coordinator.beginEditFromRow(rowId)

                editMode = true

                renderEntryForms(draft)

                statusText.text =
                    getString(
                        R.string.screen4_status_editing_row,
                        rowId
                    )
            }
        }

        findViewById<Button>(R.id.addRowButton).setOnClickListener {

            if (!::screen4Coordinator.isInitialized) return@setOnClickListener

            lifecycleScope.launch {

                if (editMode && selectedRowId != null) {

                    val result =
                        screen4Coordinator.applyDraftToRow(
                            selectedRowId!!,
                            visibleRows
                        )

                    result.onSuccess { model ->

                        statusText.text =
                            getString(
                                R.string.screen4_status_measurement_updated,
                                selectedRowId!!
                            )

                        renderTable(model)
                    }

                    result.onFailure {

                        statusText.text =
                            getString(
                                R.string.screen4_status_measurement_failed,
                                it.message ?: "unknown"
                            )
                    }

                } else {

                    val result =
                        screen4Coordinator.commitMeasurement(
                            visibleRows,
                            useRapidEntryConfig = false
                        )

                    result.onSuccess { model ->

                        val insertedId =
                            model.rows.firstOrNull()?.rowId ?: 0L

                        statusText.text =
                            getString(
                                R.string.screen4_status_measurement_saved,
                                insertedId
                            )

                        renderTable(model)

                        renderEntryForms(
                            screen4Coordinator.currentDraft()
                        )
                    }

                    result.onFailure {

                        statusText.text =
                            getString(
                                R.string.screen4_status_measurement_failed,
                                it.message ?: "unknown"
                            )
                    }
                }
            }
        }

        findViewById<Button>(R.id.deleteRowButton).setOnClickListener {

            lifecycleScope.launch {

                val (deleted, model) =
                    screen4Coordinator.deleteLatestMeasurement(visibleRows)

                statusText.text =
                    if (deleted)
                        getString(R.string.screen4_status_deleted_latest)
                    else
                        getString(R.string.screen4_status_delete_none)

                renderTable(model)
            }
        }

        findViewById<Button>(R.id.exportCsvButton).setOnClickListener {

            statusText.text =
                getString(R.string.screen4_status_export_started)

            exportCsvLauncher.launch(
                getString(R.string.screen4_export_default_filename)
            )
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
                        Screen4RapidEntryStore(this)
                    )
                )
            )

        lifecycleScope.launch {

            val table = screen4Coordinator.initialize()

            statusText.text =
                getString(
                    R.string.screen4_status_ready,
                    table.columns.size
                )

            renderEntryForms(
                screen4Coordinator.beginRapidEntry()
            )

            renderTable(table)
        }
    }

    private fun renderEntryForms(
        draft: DraftRow
    ) {

        renderFormIntoContainer(
            manualEntryContainer,
            draft,
            screen4Coordinator.activeColumns()
        )

        rapidEntryContainer.removeAllViews()
    }

    private fun renderFormIntoContainer(
        container: LinearLayout,
        draft: DraftRow,
        columns: List<ActiveColumn>
    ) {

        container.removeAllViews()

        columns.forEach { column ->

            val fieldInput =
                EditText(this).apply {

                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).also { lp ->
                            lp.topMargin = 8
                        }

                    hint =
                        getString(
                            R.string.screen4_draft_hint,
                            column.label,
                            if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                            column.maxLength
                        )

                    val columnType =
                        screen4Coordinator.resolveColumnType(column)

                    inputType =
                        inputTypeForWidget(columnType.uiWidget)

                    val initialValue =
                        draft.valuesByColumnId[column.columnId].orEmpty()

                    setText(initialValue)

                    error =
                        screen4Coordinator.validateField(column, initialValue)

                    addTextChangedListener(object : TextWatcher {

                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int
                        ) = Unit

                        override fun afterTextChanged(s: Editable?) {

                            val value =
                                s?.toString().orEmpty()

                            screen4Coordinator.userInputEvent(
                                column.columnId,
                                value
                            )

                            error =
                                screen4Coordinator.validateField(
                                    column,
                                    value
                                )
                        }
                    })
                }

            container.addView(fieldInput)
        }
    }

    private fun refreshTable(statusOverride: String) {

        lifecycleScope.launch {

            val table =
                screen4Coordinator.tableModel(visibleRows)

            renderTable(table)

            statusText.text = statusOverride
        }
    }

    private fun renderTable(model: TableViewModel) {

        tableLayout.removeAllViews()

        if (model.rows.isEmpty()) {

            val row = TableRow(this)

            row.addView(
                TextView(this).apply {
                    text =
                        getString(R.string.screen4_table_empty)
                }
            )

            tableLayout.addView(row)

            return
        }

        val formatter =
            SimpleDateFormat("HH:mm:ss", Locale.US)

        model.rows.forEach { row ->

            val rowView =
                TableRow(this)

            rowView.addView(
                bodyCell(row.rowId.toString())
            )

            rowView.addView(
                bodyCell(
                    formatter.format(
                        Date(row.createdAtMillis)
                    )
                )
            )

            model.columns.forEach {

                rowView.addView(
                    bodyCell(
                        row.valuesByColumnId[it.columnId]
                            .orEmpty()
                    )
                )
            }

            tableLayout.addView(rowView)
        }
    }

    private fun exportTableToCsv(uri: Uri) {

        lifecycleScope.launch {

            runCatching {

                val table =
                    screen4Coordinator.tableModel(
                        visibleRows
                    )

                contentResolver
                    .openOutputStream(uri)
                    ?.use { stream ->

                        OutputStreamWriter(stream)
                            .use { writer ->

                                val headers =
                                    buildList {

                                        add("row_id")

                                        add("created")

                                        addAll(
                                            table.columns.map {
                                                it.label
                                            }
                                        )
                                    }

                                writer.appendLine(
                                    headers.joinToString(",")
                                )

                                table.rows.forEach { row ->

                                    val values =
                                        buildList {

                                            add(
                                                row.rowId.toString()
                                            )

                                            add(
                                                row.createdAtMillis
                                                    .toString()
                                            )

                                            addAll(
                                                table.columns.map {
                                                    row.valuesByColumnId[it.columnId]
                                                        .orEmpty()
                                                }
                                            )
                                        }

                                    writer.appendLine(
                                        values.joinToString(",")
                                    )
                                }
                            }
                    }

                table.rows.size
            }
        }
    }

    private fun inputTypeForWidget(widget: String): Int {

        return when (widget) {

            "NumericInput" ->
                InputType.TYPE_CLASS_NUMBER

            "DecimalInput" ->
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL

            "EmailInput" ->
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

            "PhoneInput" ->
                InputType.TYPE_CLASS_PHONE

            else ->
                InputType.TYPE_CLASS_TEXT
        }
    }

    private fun bindCollapsibleSection(
        body: View,
        toggleButton: Button
    ) {

        toggleButton.setOnClickListener {

            val isCollapsed =
                body.visibility == View.GONE

            body.visibility =
                if (isCollapsed) View.VISIBLE else View.GONE
        }
    }

    private fun bodyCell(value: String) =
        TextView(this).apply {

            text = value

            setPadding(12, 8, 12, 8)
        }
}
