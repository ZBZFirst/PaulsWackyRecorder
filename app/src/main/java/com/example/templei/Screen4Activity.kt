package com.example.templei

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.ActiveColumn
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
                    refreshTable(statusOverride = getString(R.string.screen4_status_refreshed))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun bindButtons() {
        findViewById<Button>(R.id.selectRowButton).setOnClickListener {
            statusText.text = getString(R.string.screen4_status_rapid_entry)
            renderRapidEntryForm(measurementEngine.beginRapidEntry(), measurementEngine.activeColumns())
        }

        findViewById<Button>(R.id.addRowButton).setOnClickListener {
            lifecycleScope.launch {
                val result = measurementEngine.commitMeasurement(visibleRows)
                result.onSuccess { model ->
                    statusText.text = getString(R.string.screen4_status_measurement_saved, model.rows.firstOrNull()?.rowId ?: 0L)
                    renderTable(model)
                    renderRapidEntryForm(measurementEngine.currentDraft(), measurementEngine.activeColumns())
                }.onFailure {
                    statusText.text = getString(R.string.screen4_status_measurement_failed, it.message ?: "unknown")
                }
            }
        }

        findViewById<Button>(R.id.deleteRowButton).setOnClickListener {
            lifecycleScope.launch {
                val (deleted, model) = measurementEngine.deleteLatestMeasurement(visibleRows)
                statusText.text = if (deleted) {
                    getString(R.string.screen4_status_deleted_latest)
                } else {
                    getString(R.string.screen4_status_delete_none)
                }
                renderTable(model)
            }
        }

        findViewById<Button>(R.id.editRowButton).setOnClickListener {
            refreshTable(statusOverride = getString(R.string.screen4_status_refreshed))
        }

        findViewById<Button>(R.id.deleteSelectedRowButton).setOnClickListener {
            statusText.text = getString(R.string.screen4_status_delete_selected_placeholder)
        }

        findViewById<Button>(R.id.addColumnButton).setOnClickListener {
            statusText.text = getString(R.string.screen4_status_add_column_placeholder)
        }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        val repository = Screen4Repository(db, Screen4DraftStore(this))
        measurementEngine = Screen4MeasurementEngine(repository)

        lifecycleScope.launch {
            val table = measurementEngine.initialize()
            statusText.text = getString(R.string.screen4_status_ready, table.columns.size)
            renderRapidEntryForm(measurementEngine.currentDraft(), measurementEngine.activeColumns())
            renderTable(table)
        }
    }

    private fun refreshTable(statusOverride: String) {
        lifecycleScope.launch {
            val table = measurementEngine.tableModel(visibleRows)
            renderTable(table)
            statusText.text = statusOverride
        }
    }

    private fun renderRapidEntryForm(draft: com.example.templei.feature.screen4.DraftRow, columns: List<ActiveColumn>) {
        rapidEntryContainer.removeAllViews()
        columns.forEach { column ->
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
            }
            rapidEntryContainer.addView(input)
        }
    }

    private fun renderTable(model: TableViewModel) {
        tableLayout.removeAllViews()

        if (model.rows.isEmpty()) {
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
            val rowView = TableRow(this)
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
