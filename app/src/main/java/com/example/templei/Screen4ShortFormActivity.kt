package com.example.templei

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.ActiveColumn
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4ColumnFormatCatalog
import com.example.templei.feature.screen4.Screen4Database
import com.example.templei.feature.screen4.Screen4DraftStore
import com.example.templei.feature.screen4.Screen4FieldInputFormatter
import com.example.templei.feature.screen4.Screen4MeasurementEngine
import com.example.templei.feature.screen4.Screen4RapidEntryStore
import com.example.templei.feature.screen4.Screen4Repository
import com.example.templei.feature.screen4.Screen4TableSessionStore
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Dedicated host surface for Screen 4 Short Form entry loops (Phase B).
 */
class Screen4ShortFormActivity : ComponentActivity() {

    private enum class RapidModalState { IDLE, EDITING, APPENDED, COMMITTED, ERROR }

    private data class RapidCommittedSnapshot(
        val valuesByColumnId: Map<Long, String>,
        val committedAtMillis: Long,
    )

    private lateinit var screen4Coordinator: Screen4Coordinator
    private lateinit var statusText: TextView
    private lateinit var titleText: TextView
    private lateinit var committedContainer: FrameLayout
    private lateinit var newValuesContainer: LinearLayout
    private lateinit var modalStatus: TextView
    private lateinit var appendButton: Button
    private lateinit var commitButton: Button
    private lateinit var reselectButton: Button
    private lateinit var closeButton: Button

    private var rapidEntryColumnIds: Set<Long> = emptySet()
    private var rapidModalState: RapidModalState = RapidModalState.IDLE
    private val rapidCommittedHistory: MutableList<RapidCommittedSnapshot> = mutableListOf()

    companion object {
        private const val MAX_RAPID_HISTORY = 5
        private const val KEY_SHORT_FORM_COLUMN_IDS = "screen4.shortForm.columnIds"
        private const val KEY_SHORT_FORM_MODAL_STATE = "screen4.shortForm.modalState"
        private const val KEY_SHORT_FORM_COMMITTED_HISTORY = "screen4.shortForm.committedHistory"
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
        outState.putStringArrayList(
            KEY_SHORT_FORM_COMMITTED_HISTORY,
            ArrayList(rapidCommittedHistory.map(::encodeRapidSnapshot))
        )
    }

    private fun bindViews() {
        statusText = findViewById(R.id.shortFormStatusText)
        titleText = findViewById(R.id.rapidModalTitle)
        committedContainer = findViewById(R.id.rapidCommittedContainer)
        newValuesContainer = findViewById(R.id.rapidNewValuesContainer)
        modalStatus = findViewById(R.id.rapidModalStatus)
        appendButton = findViewById(R.id.rapidAppendButton)
        commitButton = findViewById(R.id.rapidCommitNextButton)
        reselectButton = findViewById(R.id.rapidModalReselectButton)
        closeButton = findViewById(R.id.rapidModalCloseButton)

        closeButton.setOnClickListener { finish() }
        reselectButton.setOnClickListener { showRapidEntryColumnsDialog() }
    }

    private fun initializeEngine() {
        val db = Screen4Database.getInstance(this)
        screen4Coordinator = Screen4Coordinator(
            Screen4MeasurementEngine(
                Screen4Repository(
                    db,
                    Screen4DraftStore(this),
                    Screen4RapidEntryStore(this),
                    Screen4TableSessionStore(this),
                )
            )
        )

        lifecycleScope.launch {
            val table = screen4Coordinator.initialize()
            if (rapidEntryColumnIds.isEmpty()) {
                rapidEntryColumnIds = screen4Coordinator.currentRapidEntryConfig().activeColumnIds
            }
            statusText.text = getString(R.string.screen4_status_ready, table.columns.size)

            if (rapidEntryColumnIds.isNotEmpty()) {
                screen4Coordinator.configureRapidEntry(rapidEntryColumnIds)
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

        val restoredHistory = savedInstanceState
            .getStringArrayList(KEY_SHORT_FORM_COMMITTED_HISTORY)
            .orEmpty()
            .mapNotNull(::decodeRapidSnapshot)
            .take(MAX_RAPID_HISTORY)

        rapidCommittedHistory.clear()
        rapidCommittedHistory.addAll(restoredHistory)
    }

    private fun encodeRapidSnapshot(snapshot: RapidCommittedSnapshot): String {
        val valueObject = JSONObject().apply {
            snapshot.valuesByColumnId.forEach { (columnId, value) ->
                put(columnId.toString(), value)
            }
        }
        return JSONObject()
            .put("committedAtMillis", snapshot.committedAtMillis)
            .put("values", valueObject)
            .toString()
    }

    private fun decodeRapidSnapshot(raw: String): RapidCommittedSnapshot? {
        return runCatching {
            val root = JSONObject(raw)
            val committedAtMillis = root.optLong("committedAtMillis", 0L)
            val valuesObject = root.optJSONObject("values") ?: JSONObject()
            val valuesMap = mutableMapOf<Long, String>()
            val keys = valuesObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val id = key.toLongOrNull() ?: continue
                valuesMap[id] = valuesObject.optString(key, "")
            }
            RapidCommittedSnapshot(valuesByColumnId = valuesMap, committedAtMillis = committedAtMillis)
        }.getOrNull()
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
                statusText.text = getString(
                    R.string.screen4_status_rapid_entry_columns_selected,
                    selectedColumnIds.size,
                    allColumns.size,
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .show()
    }

    private fun startRapidEntry(columnIds: Set<Long>) {
        val config = screen4Coordinator.configureRapidEntry(columnIds)
        rapidEntryColumnIds = config.activeColumnIds - config.autoColumns.keys
        rapidCommittedHistory.clear()
        screen4Coordinator.startNewDraft()
        renderRapidEntrySurface()
    }

    private fun renderRapidEntrySurface() {
        val rapidColumns = currentRapidEntryColumns()
        if (rapidColumns.isEmpty()) {
            rapidModalState = RapidModalState.ERROR
            statusText.text = getString(R.string.screen4_status_columns_unavailable)
            return
        }

        rapidModalState = RapidModalState.EDITING
        titleText.text = getString(R.string.screen4_rapid_entry_popup_title, rapidColumns.size)
        renderCommittedPreviewRows(committedContainer, rapidColumns)

        val draft = screen4Coordinator.currentDraft()
        val inputsByColumn = mutableMapOf<ActiveColumn, EditText>()
        newValuesContainer.removeAllViews()

        rapidColumns.forEachIndexed { index, column ->
            val input = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = 8 }
                val previewExample = Screen4ColumnFormatCatalog.previewForType(column.constraintType)
                hint = getString(
                    R.string.screen4_draft_hint_with_example,
                    column.label,
                    if (column.required) getString(R.string.screen4_required) else getString(R.string.screen4_optional),
                    column.maxLength,
                    previewExample,
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
                    setOnEditorActionListener { _, actionId, event ->
                        val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
                        if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                            commitButton.performClick()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
            newValuesContainer.addView(input)
            inputsByColumn[column] = input
        }

        modalStatus.text = getString(R.string.screen4_status_rapid_entry)

        appendButton.isEnabled = true
        commitButton.isEnabled = true
        appendButton.setOnClickListener {
            if (hasRapidInputErrors(inputsByColumn)) return@setOnClickListener
            appendRapidCommitted(inputsByColumn, committedContainer, rapidColumns)
            modalStatus.text = getString(R.string.screen4_rapid_entry_append_success, inputsByColumn.size)
        }

        commitButton.setOnClickListener {
            if (hasRapidInputErrors(inputsByColumn)) return@setOnClickListener
            commitRapidMeasurement(commitButton, appendButton, modalStatus, inputsByColumn)
        }
    }

    private fun hasRapidInputErrors(inputsByColumn: Map<ActiveColumn, EditText>): Boolean {
        var hasErrors = false
        inputsByColumn.forEach { (column, input) ->
            val value = input.text?.toString().orEmpty().trim()
            val errorMessage = screen4Coordinator.validateField(column, value)
            input.error = errorMessage
            if (errorMessage != null) {
                hasErrors = true
            }
        }
        return hasErrors
    }

    private fun appendRapidCommitted(
        inputsByColumn: Map<ActiveColumn, EditText>,
        committedContainer: FrameLayout,
        columns: List<ActiveColumn>,
    ) {
        addRapidCommittedSnapshot(buildRapidInputSnapshot(inputsByColumn))
        rapidModalState = RapidModalState.APPENDED
        renderCommittedPreviewRows(committedContainer, columns)
        inputsByColumn.values.forEach { input ->
            input.setText("")
            input.error = null
        }
        modalStatus.text = getString(R.string.screen4_rapid_entry_append_success, columns.size)
    }

    private fun commitRapidMeasurement(
        commitButton: Button,
        appendButton: Button,
        modalStatus: TextView,
        inputsByColumn: Map<ActiveColumn, EditText>,
    ) {
        if (!commitButton.isEnabled) return

        commitButton.isEnabled = false
        appendButton.isEnabled = false
        modalStatus.text = getString(R.string.screen4_rapid_entry_commit_in_progress)

        lifecycleScope.launch {
            val result = screen4Coordinator.commitMeasurement(
                Screen4MeasurementEngine.DEFAULT_VISIBLE_ROWS,
                useRapidEntryConfig = true,
            )
            result.onSuccess {
                addRapidCommittedSnapshot(buildRapidInputSnapshot(inputsByColumn))
                rapidModalState = RapidModalState.COMMITTED
                statusText.text = getString(R.string.screen4_status_measurement_saved, it.rows.firstOrNull()?.rowId ?: 0L)
                renderRapidEntrySurface()
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

        val stack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val displayed = rapidCommittedHistory.take(MAX_RAPID_HISTORY).reversed()
        displayed.forEachIndexed { index, snapshot ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(10, 10, 10, 10)
                background = getDrawable(R.drawable.bg_card_surface)
                alpha = if (index == displayed.lastIndex) 1.0f else 0.86f
            }
            val title = if (index == displayed.lastIndex) {
                getString(R.string.screen4_rapid_entry_latest_commit)
            } else {
                getString(R.string.screen4_rapid_entry_previous_commit, displayed.size - index)
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 14f
            })
            if (index == displayed.lastIndex) {
                columns.forEach { column ->
                    val value = snapshot.valuesByColumnId[column.columnId].orEmpty().ifEmpty { "—" }
                    card.addView(TextView(this).apply {
                        text = getString(R.string.screen4_rapid_entry_committed_item, column.label, value)
                        textSize = 12f
                    })
                }
            } else {
                val populated = snapshot.valuesByColumnId.values.count { it.isNotBlank() }
                card.addView(TextView(this).apply {
                    text = getString(R.string.screen4_rapid_entry_previous_summary, populated)
                    textSize = 12f
                })
            }
            card.addView(TextView(this).apply {
                text = android.text.format.DateFormat.format("HH:mm:ss", snapshot.committedAtMillis)
                textSize = 11f
                alpha = 0.8f
            })

            stack.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).also { it.topMargin = if (index == 0) 0 else 8 })
        }

        container.addView(stack)
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
}
