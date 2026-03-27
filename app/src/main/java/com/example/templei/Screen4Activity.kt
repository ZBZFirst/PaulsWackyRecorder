package com.example.templei

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.Screen4CompileResult
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4SampleLibraryRepository
import com.example.templei.feature.screen4.Screen4SamplePlaybackEngine
import com.example.templei.feature.screen4.Screen4SchedulerEngine
import com.example.templei.feature.screen4.Screen4SequenceStore
import com.example.templei.feature.screen4.Screen4SequenceBarUi
import com.example.templei.feature.screen4.Screen4UiState
import com.example.templei.feature.soundboard.ClipIndexRepository
import com.example.templei.feature.soundboard.Screen3SettingsStore
import com.example.templei.ui.navigation.TopNavigation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Screen 4 host for the multi-bar wav loop sequencer.
 */
class Screen4Activity : ComponentActivity() {
    private lateinit var screen4Coordinator: Screen4Coordinator
    private var latestUiState: Screen4UiState = Screen4UiState()
    private var hasRenderedState: Boolean = false
    private var hasCompletedInitialResume: Boolean = false

    private lateinit var bpmInput: EditText
    private lateinit var favoritesSection: LinearLayout
    private lateinit var favoritesPad: GridLayout
    private lateinit var favoritePagePrevButton: Button
    private lateinit var favoritePageStatusText: TextView
    private lateinit var favoritePageNextButton: Button
    private lateinit var playBarSection: LinearLayout
    private lateinit var playBarCountText: TextView
    private lateinit var addPlayBarButton: Button
    private lateinit var removePlayBarButton: Button
    private lateinit var saveSongButton: Button
    private lateinit var loadSongButton: Button
    private lateinit var playBarsContainer: LinearLayout
    private lateinit var transportStatusText: TextView
    private lateinit var cycleStatusText: TextView
    private lateinit var activePatternText: TextView
    private lateinit var pendingPatternText: TextView
    private lateinit var compileStatusText: TextView
    private lateinit var errorStatusText: TextView
    private lateinit var runtimeStatusText: TextView
    private lateinit var favoritesToggleButton: Button
    private lateinit var playBarToggleButton: Button
    private lateinit var playButton: Button
    private lateinit var stopButton: Button

    private val favoriteButtons = mutableListOf<Button>()
    private val playBarViewHolders = mutableListOf<PlayBarViewHolder>()
    private val assignFavoriteDialogSlotButtons = mutableListOf<Button>()
    private var isFavoritesCollapsed: Boolean = false
    private var isPlayBarCollapsed: Boolean = false
    private var assignFavoriteDialog: AlertDialog? = null
    private var assignFavoriteDialogPageStatusText: TextView? = null
    private var assignFavoriteDialogPrevButton: Button? = null
    private var assignFavoriteDialogNextButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen4)
        TopNavigation.bind(activity = this, currentDestination = Screen4Activity::class.java)

        bindViews()
        initializeCoordinator()
        buildFavoritePad()
        bindButtons()
        bindInputs()
        updateSectionVisibility()
        collectUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        assignFavoriteDialog?.dismiss()
        if (::screen4Coordinator.isInitialized) {
            screen4Coordinator.release()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasCompletedInitialResume && ::screen4Coordinator.isInitialized) {
            screen4Coordinator.refreshSharedPad()
        } else {
            hasCompletedInitialResume = true
        }
    }

    private fun bindViews() {
        bpmInput = findViewById(R.id.screen4MusicBpmInput)
        favoritesSection = findViewById(R.id.screen4MusicFavoritesSection)
        favoritesPad = findViewById(R.id.screen4MusicFavoritesPad)
        favoritePagePrevButton = findViewById(R.id.screen4MusicFavoritePagePrevButton)
        favoritePageStatusText = findViewById(R.id.screen4MusicFavoritePageStatusText)
        favoritePageNextButton = findViewById(R.id.screen4MusicFavoritePageNextButton)
        playBarSection = findViewById(R.id.screen4MusicPlayBarSection)
        playBarCountText = findViewById(R.id.screen4MusicPlayBarCountText)
        addPlayBarButton = findViewById(R.id.screen4MusicAddPlayBarButton)
        removePlayBarButton = findViewById(R.id.screen4MusicRemovePlayBarButton)
        saveSongButton = findViewById(R.id.screen4MusicSaveSongButton)
        loadSongButton = findViewById(R.id.screen4MusicLoadSongButton)
        playBarsContainer = findViewById(R.id.screen4MusicPlayBarsContainer)
        transportStatusText = findViewById(R.id.screen4MusicTransportStatus)
        cycleStatusText = findViewById(R.id.screen4MusicCycleStatus)
        activePatternText = findViewById(R.id.screen4MusicActivePatternStatus)
        pendingPatternText = findViewById(R.id.screen4MusicPendingPatternStatus)
        compileStatusText = findViewById(R.id.screen4MusicCompileStatus)
        errorStatusText = findViewById(R.id.screen4MusicErrorStatus)
        runtimeStatusText = findViewById(R.id.screen4MusicRuntimeStatus)
        favoritesToggleButton = findViewById(R.id.screen4MusicFavoritesToggleButton)
        playBarToggleButton = findViewById(R.id.screen4MusicPlayBarToggleButton)
        playButton = findViewById(R.id.screen4MusicPlayButton)
        stopButton = findViewById(R.id.screen4MusicStopButton)

        val root = findViewById<View>(R.id.screen4MusicScrollView)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom,
            )
            windowInsets
        }
    }

    private fun initializeCoordinator() {
        val sampleLibraryRepository = Screen4SampleLibraryRepository(ClipIndexRepository(this))
        val sharedFavoritesStore = Screen3SettingsStore(this)
        val sequenceStore = Screen4SequenceStore(this)
        val playbackEngine = Screen4SamplePlaybackEngine(this)
        val schedulerEngine = Screen4SchedulerEngine(
            playbackEngine = playbackEngine,
            onCycleWindow = { cycleWindow -> screen4Coordinator.onCycleWindow(cycleWindow) },
            onStepTick = { stepIndex -> screen4Coordinator.onStepTick(stepIndex) },
            onRuntimeError = { message -> screen4Coordinator.onRuntimeError(message) },
        )
        screen4Coordinator = Screen4Coordinator(
            sampleLibraryRepository = sampleLibraryRepository,
            sharedFavoritesStore = sharedFavoritesStore,
            sequenceStore = sequenceStore,
            schedulerEngine = schedulerEngine,
        )
        screen4Coordinator.initialize()
    }

    private fun buildFavoritePad() {
        favoriteButtons.clear()
        favoritesPad.removeAllViews()
        repeat(Screen4Coordinator.FAVORITE_SLOT_COUNT) { index ->
            val button = Button(this).apply {
                text = getString(R.string.screen4_music_favorite_button_placeholder, index + 1)
                minHeight = dp(84)
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_button_secondary)
                setOnClickListener { screen4Coordinator.previewFavoriteSlot(index) }
                setOnLongClickListener {
                    val payload = tag as? String ?: return@setOnLongClickListener false
                    startFavoriteReferenceDrag(this, payload)
                    true
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            favoritesPad.addView(button, params)
            favoriteButtons += button
        }
    }

    private fun bindButtons() {
        favoritesToggleButton.setOnClickListener {
            isFavoritesCollapsed = !isFavoritesCollapsed
            updateSectionVisibility()
        }
        favoritePagePrevButton.setOnClickListener { screen4Coordinator.showPreviousFavoritePage() }
        favoritePageNextButton.setOnClickListener { screen4Coordinator.showNextFavoritePage() }
        playBarToggleButton.setOnClickListener {
            isPlayBarCollapsed = !isPlayBarCollapsed
            updateSectionVisibility()
        }
        addPlayBarButton.setOnClickListener { screen4Coordinator.addPlayBar() }
        removePlayBarButton.setOnClickListener { screen4Coordinator.removePlayBar() }
        saveSongButton.setOnClickListener { showSaveSongDialog() }
        loadSongButton.setOnClickListener { showLoadSongDialog() }
        playButton.setOnClickListener { screen4Coordinator.play() }
        stopButton.setOnClickListener { screen4Coordinator.stop() }
    }

    private fun bindInputs() {
        bpmInput.setText(Screen4Coordinator.DEFAULT_BPM.toString())
        bpmInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                screen4Coordinator.updateBpm(s?.toString().orEmpty())
            }
        })
    }

    private fun collectUiState() {
        lifecycleScope.launch {
            screen4Coordinator.uiState.collectLatest { state ->
                val previousState = latestUiState
                latestUiState = state
                renderState(state, previousState)
            }
        }
    }

    private fun renderState(
        state: Screen4UiState,
        previousState: Screen4UiState,
    ) {
        transportStatusText.text = getString(
            R.string.screen4_music_transport_status,
            state.transportState.name.lowercase().replaceFirstChar { it.uppercase() },
        )
        cycleStatusText.text = getString(
            R.string.screen4_music_cycle_status,
            state.activeCycle + 1,
            state.bpm,
        )
        activePatternText.text = getString(
            R.string.screen4_music_active_pattern_status,
            state.activePatternSummary,
        )
        pendingPatternText.text = getString(
            R.string.screen4_music_pending_pattern_status,
            state.pendingPatternSummary,
        )
        compileStatusText.text = when (val compile = state.patternSource.compileResult) {
            Screen4CompileResult.Idle -> getString(R.string.screen4_music_compile_idle)
            Screen4CompileResult.Compiling -> getString(R.string.screen4_music_compile_compiling)
            is Screen4CompileResult.Success ->
                getString(R.string.screen4_music_compile_success, compile.pattern.summaryLabel())
            is Screen4CompileResult.Failure ->
                getString(R.string.screen4_music_compile_failure, compile.message)
        }
        errorStatusText.text = state.lastError?.let {
            getString(R.string.screen4_music_error_status, it)
        } ?: getString(R.string.screen4_music_error_clear)
        runtimeStatusText.text = getString(
            R.string.screen4_music_runtime_status,
            state.runtimeStatus,
        )
        playButton.isEnabled = !state.isPlaying
        stopButton.isEnabled = state.isPlaying

        val bpmText = state.bpm.toString()
        if (bpmInput.text?.toString() != bpmText) {
            bpmInput.setText(bpmText)
            bpmInput.setSelection(bpmText.length)
        }

        favoritePageStatusText.text = getString(
            R.string.screen4_music_favorite_page_status,
            state.currentFavoritePageIndex + 1,
            state.favoritePageCount,
        )
        favoritePagePrevButton.isEnabled = state.currentFavoritePageIndex > 0
        favoritePageNextButton.isEnabled = state.currentFavoritePageIndex < state.favoritePageCount - 1

        playBarCountText.text = getString(
            R.string.screen4_music_play_bar_count,
            state.sequenceBars.size,
        )
        addPlayBarButton.isEnabled = state.sequenceBars.size < Screen4Coordinator.MAX_PLAY_BAR_COUNT
        removePlayBarButton.isEnabled = state.sequenceBars.size > Screen4Coordinator.MIN_PLAY_BAR_COUNT

        if (
            !hasRenderedState ||
            state.favoriteSlots != previousState.favoriteSlots ||
            state.currentFavoritePageId != previousState.currentFavoritePageId
        ) {
            renderFavorites(state)
        }

        if (!hasRenderedState || state.sequenceBars.size != previousState.sequenceBars.size) {
            rebuildPlayBars(state.sequenceBars)
        }

        if (
            !hasRenderedState ||
            state.sequenceBars != previousState.sequenceBars ||
            state.activeStepIndex != previousState.activeStepIndex ||
            state.isPlaying != previousState.isPlaying
        ) {
            renderPlayBars(state.sequenceBars, state)
        }
        renderAssignFavoriteDialog(state)
        hasRenderedState = true
    }

    private fun renderFavorites(state: Screen4UiState) {
        state.favoriteSlots.forEachIndexed { index, slot ->
            favoriteButtons.getOrNull(index)?.let { button ->
                button.text = slot.label
                button.tag = if (slot.sampleId == null) {
                    null
                } else {
                    buildFavoriteReferencePayload(state.currentFavoritePageId, slot.index)
                }
                button.background = getDrawable(
                    if (slot.sampleId == null) R.drawable.bg_button_secondary else R.drawable.bg_button_primary
                )
            }
        }
    }

    private fun rebuildPlayBars(sequenceBars: List<Screen4SequenceBarUi>) {
        playBarViewHolders.clear()
        playBarsContainer.removeAllViews()
        sequenceBars.forEach { sequenceBar ->
            val barColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.bg_section_surface)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
            val containerParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }

            val title = TextView(this).apply {
                text = sequenceBar.label
                textSize = 16f
            }
            val titleParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            barColumn.addView(title, titleParams)

            val actionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val actionsParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
            val saveBarButton = Button(this).apply {
                text = getString(R.string.screen4_music_save_bar)
                setOnClickListener { showSaveBarDialog(sequenceBar.barIndex) }
            }
            val saveBarParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
            val loadBarButton = Button(this).apply {
                text = getString(R.string.screen4_music_load_bar)
                setOnClickListener { showLoadBarDialog(sequenceBar.barIndex) }
            }
            val loadBarParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
            }
            actionsRow.addView(saveBarButton, saveBarParams)
            actionsRow.addView(loadBarButton, loadBarParams)
            barColumn.addView(actionsRow, actionsParams)

            val grid = GridLayout(this).apply {
                columnCount = 4
                rowCount = 4
                alignmentMode = GridLayout.ALIGN_MARGINS
                useDefaultMargins = true
            }
            val gridParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }

            val buttons = mutableListOf<Button>()
            repeat(Screen4Coordinator.STEP_COUNT) { stepIndex ->
                val button = Button(this).apply {
                    text = getString(R.string.screen4_music_step_button_placeholder, stepIndex + 1)
                    minHeight = dp(72)
                    gravity = Gravity.CENTER
                    background = getDrawable(R.drawable.bg_button_secondary)
                    setOnClickListener { screen4Coordinator.clearStep(sequenceBar.barIndex, stepIndex) }
                    setOnLongClickListener {
                        val payload = tag as? String
                        if (payload != null) {
                            startFavoriteReferenceDrag(this, payload)
                        } else {
                            showAssignFavoriteDialog(
                                barIndex = sequenceBar.barIndex,
                                stepIndex = stepIndex,
                            )
                        }
                        true
                    }
                    setOnDragListener { _, event ->
                        handleFavoriteReferenceDrop(event) { favoritePageId, favoriteSlotIndex ->
                            screen4Coordinator.assignStepFromFavorite(
                                barIndex = sequenceBar.barIndex,
                                stepIndex = stepIndex,
                                favoritePageId = favoritePageId,
                                favoriteSlotIndex = favoriteSlotIndex,
                            )
                        }
                    }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                grid.addView(button, params)
                buttons += button
            }

            barColumn.addView(grid, gridParams)
            playBarsContainer.addView(barColumn, containerParams)
            playBarViewHolders += PlayBarViewHolder(
                title = title,
                buttons = buttons,
            )
        }
    }

    private fun renderPlayBars(
        sequenceBars: List<Screen4SequenceBarUi>,
        state: Screen4UiState,
    ) {
        sequenceBars.forEachIndexed { barIndex, sequenceBar ->
            val holder = playBarViewHolders.getOrNull(barIndex) ?: return@forEachIndexed
            holder.title.text = sequenceBar.label
            sequenceBar.steps.forEachIndexed { stepIndex, slot ->
                holder.buttons.getOrNull(stepIndex)?.let { button ->
                    button.text = slot.label
                    button.tag = if (slot.sourcePageId != null && slot.sourceSlotIndex != null) {
                        buildFavoriteReferencePayload(slot.sourcePageId, slot.sourceSlotIndex)
                    } else {
                        null
                    }
                    button.background = getDrawable(
                        when {
                            state.isPlaying && stepIndex == state.activeStepIndex -> R.drawable.bg_button_primary
                            slot.sampleId != null -> R.drawable.bg_button_pad
                            else -> R.drawable.bg_button_secondary
                        }
                    )
                }
            }
        }
    }

    private fun showSaveSongDialog() {
        showNameInputDialog(
            title = getString(R.string.screen4_music_save_song_dialog_title),
        ) { enteredName ->
            screen4Coordinator.saveSong(enteredName)
        }
    }

    private fun showLoadSongDialog() {
        showSnapshotPickerDialog(
            title = getString(R.string.screen4_music_load_song_dialog_title),
            emptyMessage = getString(R.string.screen4_music_saved_songs_empty),
            options = screen4Coordinator.savedSongSnapshots().map { it.name },
        ) { savedName ->
            screen4Coordinator.loadSavedSong(savedName)
        }
    }

    private fun showSaveBarDialog(barIndex: Int) {
        showNameInputDialog(
            title = getString(R.string.screen4_music_save_bar_dialog_title, barIndex + 1),
        ) { enteredName ->
            screen4Coordinator.savePlayBar(barIndex, enteredName)
        }
    }

    private fun showLoadBarDialog(barIndex: Int) {
        showSnapshotPickerDialog(
            title = getString(R.string.screen4_music_load_bar_dialog_title, barIndex + 1),
            emptyMessage = getString(R.string.screen4_music_saved_bars_empty),
            options = screen4Coordinator.savedBarSnapshots().map { it.name },
        ) { savedName ->
            screen4Coordinator.loadSavedPlayBar(barIndex, savedName)
        }
    }

    private fun showNameInputDialog(
        title: String,
        onSaveName: (String) -> Unit,
    ) {
        val input = EditText(this).apply {
            hint = getString(R.string.screen4_music_save_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSelection(text?.length ?: 0)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSaveName(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSnapshotPickerDialog(
        title: String,
        emptyMessage: String,
        options: List<String>,
        onSelect: (String) -> Unit,
    ) {
        if (options.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(emptyMessage)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                options.getOrNull(which)?.let(onSelect)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAssignFavoriteDialog(
        barIndex: Int,
        stepIndex: Int,
    ) {
        assignFavoriteDialog?.dismiss()
        assignFavoriteDialogSlotButtons.clear()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }

        val pageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val pageRowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        val prevButton = Button(this).apply {
            text = getString(R.string.screen4_music_favorite_page_prev)
            setOnClickListener { screen4Coordinator.showPreviousFavoritePage() }
        }
        val pageButtonParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply {
            marginEnd = dp(4)
        }

        val pageStatusText = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 16f
        }
        val pageStatusParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            2f,
        )

        val nextButton = Button(this).apply {
            text = getString(R.string.screen4_music_favorite_page_next)
            setOnClickListener { screen4Coordinator.showNextFavoritePage() }
        }
        val nextButtonParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply {
            marginStart = dp(4)
        }

        pageRow.addView(prevButton, pageButtonParams)
        pageRow.addView(pageStatusText, pageStatusParams)
        pageRow.addView(nextButton, nextButtonParams)
        content.addView(pageRow, pageRowParams)

        val favoritesGrid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 3
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }
        val favoritesGridParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(8)
        }

        repeat(Screen4Coordinator.FAVORITE_SLOT_COUNT) { slotIndex ->
            val button = Button(this).apply {
                minHeight = dp(72)
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_button_secondary)
                setOnClickListener {
                    screen4Coordinator.assignStepFromFavorite(
                        barIndex = barIndex,
                        stepIndex = stepIndex,
                        favoritePageId = latestUiState.currentFavoritePageId,
                        favoriteSlotIndex = slotIndex,
                    )
                    assignFavoriteDialog?.dismiss()
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            favoritesGrid.addView(button, params)
            assignFavoriteDialogSlotButtons += button
        }
        content.addView(favoritesGrid, favoritesGridParams)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen4_music_assign_dialog_title, barIndex + 1, stepIndex + 1))
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnDismissListener {
            assignFavoriteDialog = null
            assignFavoriteDialogPageStatusText = null
            assignFavoriteDialogPrevButton = null
            assignFavoriteDialogNextButton = null
            assignFavoriteDialogSlotButtons.clear()
        }

        assignFavoriteDialog = dialog
        assignFavoriteDialogPageStatusText = pageStatusText
        assignFavoriteDialogPrevButton = prevButton
        assignFavoriteDialogNextButton = nextButton

        dialog.show()
        renderAssignFavoriteDialog(latestUiState)
    }

    private fun renderAssignFavoriteDialog(state: Screen4UiState) {
        if (assignFavoriteDialog?.isShowing != true) return

        assignFavoriteDialogPageStatusText?.text = getString(
            R.string.screen4_music_favorite_page_status,
            state.currentFavoritePageIndex + 1,
            state.favoritePageCount,
        )
        assignFavoriteDialogPrevButton?.isEnabled = state.currentFavoritePageIndex > 0
        assignFavoriteDialogNextButton?.isEnabled = state.currentFavoritePageIndex < state.favoritePageCount - 1

        state.favoriteSlots.forEachIndexed { slotIndex, slot ->
            assignFavoriteDialogSlotButtons.getOrNull(slotIndex)?.let { button ->
                val isAssigned = slot.sampleId != null
                button.text = slot.label
                button.isEnabled = isAssigned
                button.alpha = if (isAssigned) 1f else 0.5f
                button.background = getDrawable(
                    if (isAssigned) R.drawable.bg_button_primary else R.drawable.bg_button_secondary
                )
            }
        }
    }

    private fun updateSectionVisibility() {
        favoritesSection.visibility = if (isFavoritesCollapsed) View.GONE else View.VISIBLE
        playBarSection.visibility = if (isPlayBarCollapsed) View.GONE else View.VISIBLE
        favoritesToggleButton.text = getString(
            if (isFavoritesCollapsed) R.string.screen4_section_expand else R.string.screen4_section_collapse
        )
        playBarToggleButton.text = getString(
            if (isPlayBarCollapsed) R.string.screen4_section_expand else R.string.screen4_section_collapse
        )
    }

    private fun startFavoriteReferenceDrag(view: View, payload: String) {
        val clipData = ClipData(
            "screen4FavoriteReference",
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
            ClipData.Item(payload),
        )
        view.startDragAndDrop(clipData, View.DragShadowBuilder(view), null, 0)
    }

    private fun handleFavoriteReferenceDrop(
        event: DragEvent,
        onDropFavoriteReference: (String, Int) -> Unit,
    ): Boolean {
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            DragEvent.ACTION_DROP -> {
                val payload = event.clipData?.getItemAt(0)?.text?.toString().orEmpty()
                val segments = payload.split("|")
                val favoritePageId = segments.getOrNull(1).orEmpty()
                val favoriteSlotIndex = segments.getOrNull(2)?.toIntOrNull()
                if (
                    segments.getOrNull(0) == FAVORITE_REFERENCE_PREFIX &&
                    favoritePageId.isNotBlank() &&
                    favoriteSlotIndex != null
                ) {
                    onDropFavoriteReference(favoritePageId, favoriteSlotIndex)
                    true
                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_ENDED,
            DragEvent.ACTION_DRAG_ENTERED,
            DragEvent.ACTION_DRAG_EXITED,
            DragEvent.ACTION_DRAG_LOCATION -> true
            else -> false
        }
    }

    private fun buildFavoriteReferencePayload(pageId: String, slotIndex: Int): String {
        return "$FAVORITE_REFERENCE_PREFIX|$pageId|$slotIndex"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class PlayBarViewHolder(
        val title: TextView,
        val buttons: List<Button>,
    )

    private companion object {
        private const val FAVORITE_REFERENCE_PREFIX = "favorite-ref"
    }
}
