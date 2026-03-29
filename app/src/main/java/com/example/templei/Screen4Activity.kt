package com.example.templei

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.screen4.Screen4BatchAssignTarget
import com.example.templei.feature.screen4.Screen4BarEffectState
import com.example.templei.feature.screen4.Screen4CompileResult
import com.example.templei.feature.screen4.Screen4Coordinator
import com.example.templei.feature.screen4.Screen4MusicRuntime
import com.example.templei.feature.screen4.Screen4SequenceBarUi
import com.example.templei.feature.screen4.Screen4UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Screen 4 host for the multi-bar wav loop sequencer.
 */
class Screen4Activity : ComponentActivity() {
    private lateinit var screen4Coordinator: Screen4Coordinator
    private var latestUiState: Screen4UiState = Screen4UiState()
    private var hasRenderedState: Boolean = false

    private lateinit var bpmValueText: TextView
    private lateinit var bpmSlider: SeekBar
    private lateinit var favoritesSection: LinearLayout
    private lateinit var favoritesPad: GridLayout
    private lateinit var favoritePagePrevButton: Button
    private lateinit var favoritePageStatusText: TextView
    private lateinit var favoritePageNextButton: Button
    private lateinit var selectionModeButton: Button
    private lateinit var selectAllButton: Button
    private lateinit var selectOddButton: Button
    private lateinit var selectEvenButton: Button
    private lateinit var assignSelectionButton: Button
    private lateinit var clearSelectionButton: Button
    private lateinit var groupSelectionStatusText: TextView
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
    private lateinit var headerBpmChipText: TextView
    private lateinit var channelRosterContainer: LinearLayout

    private val favoriteButtons = mutableListOf<Button>()
    private val assignFavoriteDialogSlotButtons = mutableListOf<Button>()
    private var assignFavoriteDialogBatchTarget: Screen4BatchAssignTarget? = null
    private var assignFavoriteDialogBarIndex: Int? = null
    private var assignFavoriteDialogStepIndex: Int? = null
    private var isFavoritesCollapsed: Boolean = false
    private var isPlayBarCollapsed: Boolean = false
    private val expandedBarIndices = mutableListOf(0)
    private var selectionTargetBarIndex: Int = 0
    private var activeFxDragCount: Int = 0
    private var hasDeferredPlayBarRender: Boolean = false
    private var assignFavoriteDialog: AlertDialog? = null
    private var assignFavoriteDialogPageStatusText: TextView? = null
    private var assignFavoriteDialogPrevButton: Button? = null
    private var assignFavoriteDialogNextButton: Button? = null
    private var assignFavoriteDialogBarIndices: List<Int> = emptyList()
    private val renderedExpandedStepButtons = mutableMapOf<Int, List<Button>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen4)

        bindViews()
        initializeCoordinator()
        screen4Coordinator.ensurePlayBarCount(Screen4Coordinator.MAX_PLAY_BAR_COUNT)
        buildFavoritePad()
        bindButtons()
        bindInputs()
        updateSectionVisibility()
        collectUiState()
    }

    override fun onDestroy() {
        super.onDestroy()
        assignFavoriteDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        if (::screen4Coordinator.isInitialized) {
            screen4Coordinator.refreshSharedPad()
        }
    }

    private fun bindViews() {
        bpmValueText = findViewById(R.id.screen4MusicBpmValueText)
        bpmSlider = findViewById(R.id.screen4MusicBpmSlider)
        favoritesSection = findViewById(R.id.screen4MusicFavoritesSection)
        favoritesPad = findViewById(R.id.screen4MusicFavoritesPad)
        favoritePagePrevButton = findViewById(R.id.screen4MusicFavoritePagePrevButton)
        favoritePageStatusText = findViewById(R.id.screen4MusicFavoritePageStatusText)
        favoritePageNextButton = findViewById(R.id.screen4MusicFavoritePageNextButton)
        selectionModeButton = findViewById(R.id.screen4MusicSelectionModeButton)
        selectAllButton = findViewById(R.id.screen4MusicSelectAllButton)
        selectOddButton = findViewById(R.id.screen4MusicSelectOddButton)
        selectEvenButton = findViewById(R.id.screen4MusicSelectEvenButton)
        assignSelectionButton = findViewById(R.id.screen4MusicAssignSelectionButton)
        clearSelectionButton = findViewById(R.id.screen4MusicClearSelectionButton)
        groupSelectionStatusText = findViewById(R.id.screen4MusicGroupSelectionStatusText)
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
        headerBpmChipText = findViewById(R.id.screen4HeaderBpmChip)
        channelRosterContainer = findViewById(R.id.screen4MusicChannelRosterContainer)

        val topBar = findViewById<View>(R.id.screen4TopBar)
        val bottomNav = findViewById<View>(R.id.screen4BottomNav)
        val scrollView = findViewById<View>(R.id.screen4MusicScrollView)
        val root = findViewById<View>(android.R.id.content)

        val topBarPaddingStart = topBar.paddingStart
        val topBarPaddingTop = topBar.paddingTop
        val topBarPaddingEnd = topBar.paddingEnd
        val topBarPaddingBottom = topBar.paddingBottom
        val bottomNavPaddingStart = bottomNav.paddingStart
        val bottomNavPaddingTop = bottomNav.paddingTop
        val bottomNavPaddingEnd = bottomNav.paddingEnd
        val bottomNavPaddingBottom = bottomNav.paddingBottom
        val scrollPaddingStart = scrollView.paddingStart
        val scrollPaddingTop = scrollView.paddingTop
        val scrollPaddingEnd = scrollView.paddingEnd
        val scrollPaddingBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPaddingRelative(
                topBarPaddingStart + systemBars.left,
                topBarPaddingTop + systemBars.top,
                topBarPaddingEnd + systemBars.right,
                topBarPaddingBottom,
            )
            bottomNav.setPaddingRelative(
                bottomNavPaddingStart + systemBars.left,
                bottomNavPaddingTop,
                bottomNavPaddingEnd + systemBars.right,
                bottomNavPaddingBottom + systemBars.bottom,
            )
            scrollView.setPaddingRelative(
                scrollPaddingStart + systemBars.left,
                scrollPaddingTop,
                scrollPaddingEnd + systemBars.right,
                scrollPaddingBottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun initializeCoordinator() {
        screen4Coordinator = Screen4MusicRuntime.coordinator(applicationContext)
    }

    private fun buildFavoritePad() {
        favoriteButtons.clear()
        favoritesPad.removeAllViews()
        favoritesPad.columnCount = Screen4Coordinator.FAVORITE_SLOT_COUNT
        favoritesPad.rowCount = 1
        repeat(Screen4Coordinator.FAVORITE_SLOT_COUNT) { index ->
            val button = Button(this).apply {
                text = getString(R.string.screen4_music_favorite_short_label, index + 1)
                minHeight = dp(76)
                minWidth = dp(76)
                gravity = Gravity.CENTER
                background = getDrawable(R.drawable.bg_screen4_pad_tile)
                setTextColor(getColor(R.color.screen4_text_secondary))
                textSize = 10f
                isAllCaps = false
                maxLines = 2
                setSingleLine(false)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    this,
                    7,
                    12,
                    1,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                )
                setOnClickListener { screen4Coordinator.previewFavoriteSlot(index) }
                setOnLongClickListener {
                    val payload = tag as? String ?: return@setOnLongClickListener false
                    startFavoriteReferenceDrag(this, payload)
                    true
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = dp(76)
                height = dp(76)
                columnSpec = GridLayout.spec(index)
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
        selectionModeButton.setOnClickListener { toggleExpandedBarSelectionMode() }
        selectAllButton.setOnClickListener { selectExpandedBars(Screen4BatchAssignTarget.ALL_SLOTS) }
        selectOddButton.setOnClickListener { selectExpandedBars(Screen4BatchAssignTarget.ODD_SLOTS) }
        selectEvenButton.setOnClickListener { selectExpandedBars(Screen4BatchAssignTarget.EVEN_SLOTS) }
        assignSelectionButton.setOnClickListener { showAssignFavoriteDialogForExpandedBars() }
        clearSelectionButton.setOnClickListener { clearExpandedBarSelections() }
        saveSongButton.setOnClickListener { showSaveSongDialog() }
        loadSongButton.setOnClickListener { showLoadSongDialog() }
        playButton.setOnClickListener { screen4Coordinator.play() }
        stopButton.setOnClickListener { screen4Coordinator.stop() }
        findViewById<View>(R.id.screen4MenuButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.screen4NavCapture).setOnClickListener {
            startActivity(Intent(this, Screen1Activity::class.java))
        }
        findViewById<View>(R.id.screen4NavRecorder).setOnClickListener {
            startActivity(Intent(this, Screen2Activity::class.java))
        }
        findViewById<View>(R.id.screen4NavBoard).setOnClickListener {
            startActivity(Intent(this, Screen3Activity::class.java))
        }
        findViewById<View>(R.id.screen4NavController).setOnClickListener {
            // Current destination.
        }
        findViewById<View>(R.id.screen4NavHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun bindInputs() {
        bpmSlider.max = Screen4Coordinator.MAX_BPM - Screen4Coordinator.MIN_BPM
        bpmSlider.progress = Screen4Coordinator.DEFAULT_BPM - Screen4Coordinator.MIN_BPM
        bpmSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val bpm = Screen4Coordinator.MIN_BPM + progress
                screen4Coordinator.updateBpm(bpm.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
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
        transportStatusText.text = formatTransportClock(state)
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

        val bpmProgress = state.bpm - Screen4Coordinator.MIN_BPM
        bpmValueText.text = getString(R.string.screen4_music_bpm_value, state.bpm)
        headerBpmChipText.text = getString(R.string.screen4_music_bpm_value, state.bpm)
        if (bpmSlider.progress != bpmProgress) {
            bpmSlider.progress = bpmProgress
        }

        favoritePageStatusText.text = getString(
            R.string.screen4_music_favorite_page_status,
            state.currentFavoritePageIndex + 1,
            state.favoritePageCount,
        )
        favoritePagePrevButton.isEnabled = state.currentFavoritePageIndex > 0
        favoritePageNextButton.isEnabled = state.currentFavoritePageIndex < state.favoritePageCount - 1
        val expandedBars = expandedSequenceBars(state)
        clampSelectionTargetBarIndex(expandedBars)
        val targetBar = expandedBars.firstOrNull { it.barIndex == selectionTargetBarIndex }
            ?: expandedBars.firstOrNull()
        val selectedStepCount = expandedBars.sumOf { it.selectedStepIndices.size }
        val selectionModeEnabled = expandedBars.any { it.isSelectionModeEnabled }
        selectionModeButton.text = getString(
            if (selectionModeEnabled) {
                R.string.screen4_music_group_select_toggle_on
            } else {
                R.string.screen4_music_group_select_toggle_off
            }
        )
        groupSelectionStatusText.text = getString(
            R.string.screen4_music_group_select_status,
            (targetBar?.barIndex ?: 0) + 1,
            selectedStepCount,
        )
        assignSelectionButton.isEnabled = selectedStepCount > 0
        clearSelectionButton.isEnabled = selectedStepCount > 0
        selectAllButton.isEnabled = targetBar != null
        selectOddButton.isEnabled = targetBar != null
        selectEvenButton.isEnabled = targetBar != null

        playBarCountText.text = getString(
            R.string.screen4_music_play_bar_count,
            state.sequenceBars.size,
        )
        addPlayBarButton.isEnabled = false
        removePlayBarButton.isEnabled = false

        if (
            !hasRenderedState ||
            state.favoriteSlots != previousState.favoriteSlots ||
            state.currentFavoritePageId != previousState.currentFavoritePageId
        ) {
            renderFavorites(state)
        }

        val needsPlayBarStructureRender =
            !hasRenderedState || state.sequenceBars != previousState.sequenceBars
        val needsPlaybackHighlightRefresh =
            hasRenderedState &&
                (
                    state.activeStepIndex != previousState.activeStepIndex ||
                        state.isPlaying != previousState.isPlaying
                    )
        if (needsPlayBarStructureRender) {
            if (activeFxDragCount > 0) {
                hasDeferredPlayBarRender = true
            } else {
                renderPlayBars(state.sequenceBars, state)
            }
        } else if (needsPlaybackHighlightRefresh) {
            refreshExpandedStepButtons(state)
        }
        renderAssignFavoriteDialog(state)
        hasRenderedState = true
    }

    private fun renderFavorites(state: Screen4UiState) {
        state.favoriteSlots.forEachIndexed { index, slot ->
            favoriteButtons.getOrNull(index)?.let { button ->
                button.text = buildFavoritePadLabel(index, slot)
                button.tag = if (slot.sampleId == null) {
                    null
                } else {
                    buildFavoriteReferencePayload(state.currentFavoritePageId, slot.index)
                }
                button.background = getDrawable(
                    if (slot.sampleId == null) {
                        R.drawable.bg_screen4_pad_tile
                    } else {
                        R.drawable.bg_screen4_pad_tile_active
                    }
                )
                button.alpha = if (slot.sampleId == null) 0.6f else 1f
                button.setTextColor(
                    getColor(
                        if (slot.sampleId == null) {
                            R.color.screen4_text_secondary
                        } else {
                            R.color.screen4_primary
                        }
                    )
                )
            }
        }
    }

    private fun buildFavoritePadLabel(
        index: Int,
        slot: com.example.templei.feature.screen4.Screen4VisualSlot,
    ): String {
        return if (slot.sampleId == null) {
            getString(R.string.screen4_music_favorite_short_label, index + 1)
        } else {
            val suffix = slot.label.substringAfter('\n', "").trim()
            getString(
                R.string.screen4_music_favorite_assigned_label,
                getString(R.string.screen4_music_favorite_short_label, index + 1),
                suffix,
            )
        }
    }

    private fun renderPlayBars(
        sequenceBars: List<Screen4SequenceBarUi>,
        state: Screen4UiState,
    ) {
        clampExpandedBarIndices(sequenceBars.size)
        renderedExpandedStepButtons.clear()
        channelRosterContainer.removeAllViews()
        playBarsContainer.removeAllViews()
        if (sequenceBars.isEmpty()) return

        sequenceBars.forEachIndexed { index, sequenceBar ->
            channelRosterContainer.addView(
                buildChannelRosterChip(
                    sequenceBar = sequenceBar,
                    rosterIndex = index,
                    barCount = sequenceBars.size,
                ),
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f,
                ).apply {
                    if (index > 0) {
                        marginStart = dp(8)
                    }
                }
            )
        }

        val expandedBars = expandedBarIndices
            .distinct()
            .mapNotNull(sequenceBars::getOrNull)
        val workspaceRow = LinearLayout(this).apply {
            orientation = if (expandedBars.size > 1) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        expandedBars.forEachIndexed { index, sequenceBar ->
            workspaceRow.addView(
                buildPrototypeWorkspacePanel(
                    sequenceBar = sequenceBar,
                    state = state,
                    accent = if (index == 0) WorkspaceAccent.PRIMARY else WorkspaceAccent.SECONDARY,
                ),
                LinearLayout.LayoutParams(
                    if (expandedBars.size > 1) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (expandedBars.size > 1) 1f else 0f,
                ).apply {
                    if (index > 0) {
                        marginStart = dp(12)
                    }
                }
            )
        }
        playBarsContainer.addView(
            workspaceRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
    }

    private fun buildChannelRosterChip(
        sequenceBar: Screen4SequenceBarUi,
        rosterIndex: Int,
        barCount: Int,
    ): View {
        val isPrimary = expandedBarIndices.firstOrNull() == rosterIndex
        val isSecondary = expandedBarIndices.getOrNull(1) == rosterIndex
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            val chip = TextView(this@Screen4Activity).apply {
                text = sequenceBar.label.uppercase()
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = getDrawable(
                    when {
                        isPrimary -> R.drawable.bg_screen4_roster_primary
                        isSecondary -> R.drawable.bg_screen4_roster_secondary
                        else -> R.drawable.bg_screen4_roster_idle
                    }
                )
                setTextColor(
                    getColor(
                        when {
                            isPrimary -> R.color.screen4_primary
                            isSecondary -> R.color.screen4_secondary
                            else -> R.color.screen4_text_secondary
                        }
                    )
                )
                textSize = 10f
                setOnClickListener {
                    selectionTargetBarIndex = sequenceBar.barIndex
                    toggleExpandedBar(sequenceBar.barIndex, barCount)
                    renderPlayBars(latestUiState.sequenceBars, latestUiState)
                }
            }
            addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
            if (isPrimary || isSecondary) {
                addView(
                    View(this@Screen4Activity).apply {
                        setBackgroundColor(
                            getColor(
                                if (isPrimary) {
                                    R.color.screen4_primary
                                } else {
                                    R.color.screen4_secondary
                                }
                            )
                        )
                    },
                    LinearLayout.LayoutParams(dp(2), dp(14)).apply {
                        topMargin = dp(2)
                    }
                )
            } else {
                addView(
                    View(this@Screen4Activity),
                    LinearLayout.LayoutParams(dp(2), dp(14)).apply {
                        topMargin = dp(2)
                    }
                )
            }
        }
    }

    private fun buildPrototypeWorkspacePanel(
        sequenceBar: Screen4SequenceBarUi,
        state: Screen4UiState,
        accent: WorkspaceAccent,
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(accent.workspaceDrawableRes)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            background = getDrawable(if (accent == WorkspaceAccent.PRIMARY) R.drawable.bg_screen4_step_primary else R.drawable.bg_screen4_step_secondary)
        }
        headerRow.addView(
            dot,
            LinearLayout.LayoutParams(dp(10), dp(10))
        )
        headerRow.addView(
            TextView(this).apply {
                text = getString(
                    R.string.screen4_music_channel_workspace_title,
                    sequenceBar.label.uppercase(),
                    sequenceBar.displayName.uppercase(),
                )
                setTextColor(getColor(accent.textColorRes))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
            }
        )
        headerRow.addView(
            TextView(this).apply {
                text = "• •"
                setTextColor(getColor(accent.textColorRes))
                textSize = 12f
            }
        )
        card.addView(headerRow)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actionRow.addView(
            Button(this).apply {
                text = getString(R.string.screen4_music_save_channel)
                background = getDrawable(R.drawable.bg_screen4_transport_button)
                setTextColor(getColor(R.color.screen4_text_primary))
                setOnClickListener { showSaveBarDialog(sequenceBar.barIndex) }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        actionRow.addView(
            Button(this).apply {
                text = getString(R.string.screen4_music_load_channel)
                background = getDrawable(R.drawable.bg_screen4_transport_button)
                setTextColor(getColor(R.color.screen4_text_primary))
                setOnClickListener { showLoadBarDialog(sequenceBar.barIndex) }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        )
        card.addView(
            actionRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )

        val contentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        contentRow.addView(
            buildWorkspaceGrid(sequenceBar, state, accent),
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        )
        contentRow.addView(
            buildFxRail(sequenceBar, accent),
            LinearLayout.LayoutParams(
                dp(78),
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                marginStart = dp(12)
            }
        )
        card.addView(
            contentRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        )

        return card
    }

    private fun buildWorkspaceGrid(
        sequenceBar: Screen4SequenceBarUi,
        state: Screen4UiState,
        accent: WorkspaceAccent,
    ): View {
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_screen4_section)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }
        val renderedButtons = mutableListOf<Button>()
        repeat(Screen4Coordinator.STEP_COUNT) { stepIndex ->
            val slot = sequenceBar.steps[stepIndex]
            val button = Button(this).apply {
                text = "%02d".format(stepIndex + 1)
                minHeight = dp(64)
                gravity = Gravity.CENTER
                tag = if (slot.sourcePageId != null && slot.sourceSlotIndex != null) {
                    buildFavoriteReferencePayload(slot.sourcePageId, slot.sourceSlotIndex)
                } else {
                    null
                }
                setOnClickListener {
                    if (latestUiState.sequenceBars.getOrNull(sequenceBar.barIndex)?.isSelectionModeEnabled == true) {
                        screen4Coordinator.toggleSelectedStep(sequenceBar.barIndex, stepIndex)
                    } else {
                        screen4Coordinator.clearStep(sequenceBar.barIndex, stepIndex)
                    }
                }
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
            applyStepButtonVisuals(
                button = button,
                sequenceBar = sequenceBar,
                stepIndex = stepIndex,
                slot = slot,
                state = state,
                accent = accent,
            )
            grid.addView(
                button,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
            )
            renderedButtons += button
        }
        renderedExpandedStepButtons[sequenceBar.barIndex] = renderedButtons
        frame.addView(grid)
        return frame
    }

    private fun buildFxRail(
        sequenceBar: Screen4SequenceBarUi,
        accent: WorkspaceAccent,
    ): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val effectState = sequenceBar.effectState
        listOf(
            FxRailItem(
                label = getString(R.string.screen4_music_fx_gain_tile),
                enabled = effectState.gainEnabled,
                level = effectState.gainLevel,
                onToggle = { screen4Coordinator.toggleGainEnabled(sequenceBar.barIndex) },
                onLevelChanged = { level -> screen4Coordinator.updateGainLevel(sequenceBar.barIndex, level) },
            ),
            FxRailItem(
                label = getString(R.string.screen4_music_fx_pitch_tile),
                enabled = effectState.pitchEnabled,
                level = effectState.pitchLevel,
                onToggle = { screen4Coordinator.togglePitchEnabled(sequenceBar.barIndex) },
                onLevelChanged = { level -> screen4Coordinator.updatePitchLevel(sequenceBar.barIndex, level) },
            ),
            FxRailItem(
                label = getString(R.string.screen4_music_fx_reverb),
                enabled = effectState.reverbEnabled,
                level = effectState.reverbLevel,
                onToggle = { screen4Coordinator.toggleReverbEnabled(sequenceBar.barIndex) },
                onLevelChanged = { level -> screen4Coordinator.updateReverbLevel(sequenceBar.barIndex, level) },
            ),
            FxRailItem(
                label = getString(R.string.screen4_music_fx_pan_tile),
                enabled = effectState.panEnabled,
                level = effectState.panLevel,
                onToggle = { screen4Coordinator.togglePanEnabled(sequenceBar.barIndex) },
                onLevelChanged = { level -> screen4Coordinator.updatePanLevel(sequenceBar.barIndex, level) },
            ),
        ).forEachIndexed { index, item ->
            column.addView(
                buildFxRailTile(item, accent),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f,
                ).apply {
                    if (index > 0) {
                        topMargin = dp(8)
                    }
                }
            )
        }
        return column
    }

    private fun buildFxRailTile(
        item: FxRailItem,
        accent: WorkspaceAccent,
    ): View {
        val wheelThresholdPx = dp(18).toFloat().coerceAtLeast(1f)
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val card = FrameLayout(this).apply {
            isClickable = true
            isLongClickable = true
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = getDrawable(
                when {
                    item.enabled && accent == WorkspaceAccent.PRIMARY -> R.drawable.bg_screen4_fx_tile_primary
                    item.enabled && accent == WorkspaceAccent.SECONDARY -> R.drawable.bg_screen4_fx_tile_secondary
                    else -> R.drawable.bg_screen4_fx_tile
                }
            )
        }
        val valueText = TextView(this).apply {
            text = if (item.enabled) {
                getString(R.string.screen4_music_fx_level, item.level)
            } else {
                getString(R.string.screen4_music_fx_off)
            }
            gravity = Gravity.CENTER
            setTextColor(
                getColor(
                    when {
                        item.enabled && accent == WorkspaceAccent.PRIMARY -> R.color.screen4_primary
                        item.enabled && accent == WorkspaceAccent.SECONDARY -> R.color.screen4_secondary
                        else -> R.color.screen4_text_secondary
                    }
                )
            )
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val labelText = TextView(this).apply {
            text = item.label.uppercase()
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.screen4_text_secondary))
            textSize = 8f
        }
        card.addView(
            valueText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            )
        )
        card.addView(
            labelText,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(6)
            }
        )

        fun syncFxTileVisuals(
            enabled: Boolean,
            level: Int,
            wheelRotation: Float = 0f,
        ) {
            valueText.text = if (enabled) {
                getString(R.string.screen4_music_fx_level, level)
            } else {
                getString(R.string.screen4_music_fx_off)
            }
            valueText.rotationY = wheelRotation
            valueText.setTextColor(
                getColor(
                    when {
                        enabled && accent == WorkspaceAccent.PRIMARY -> R.color.screen4_primary
                        enabled && accent == WorkspaceAccent.SECONDARY -> R.color.screen4_secondary
                        else -> R.color.screen4_text_secondary
                    }
                )
            )
            labelText.setTextColor(
                getColor(
                    if (enabled) {
                        R.color.screen4_text_primary
                    } else {
                        R.color.screen4_text_secondary
                    }
                )
            )
            card.background = getDrawable(
                when {
                    enabled && accent == WorkspaceAccent.PRIMARY -> R.drawable.bg_screen4_fx_tile_primary
                    enabled && accent == WorkspaceAccent.SECONDARY -> R.drawable.bg_screen4_fx_tile_secondary
                    else -> R.drawable.bg_screen4_fx_tile
                }
            )
            card.alpha = if (enabled) 1f else 0.78f
        }

        var currentEnabled = item.enabled
        var currentLevel = item.level.coerceIn(1, 10)
        var committedLevel = currentLevel
        var downX = 0f
        var downY = 0f
        var isDragging = false
        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            currentEnabled = !currentEnabled
            card.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            syncFxTileVisuals(currentEnabled, currentLevel)
            item.onToggle()
        }

        syncFxTileVisuals(currentEnabled, currentLevel)
        card.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    isDragging = false
                    longPressTriggered = false
                    committedLevel = currentLevel
                    activeFxDragCount += 1
                    card.removeCallbacks(longPressRunnable)
                    card.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    if (!isDragging && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        card.removeCallbacks(longPressRunnable)
                        isDragging = kotlin.math.abs(deltaX) >= kotlin.math.abs(deltaY)
                    }
                    if (!currentEnabled || !isDragging) {
                        return@setOnTouchListener true
                    }
                    val stepDelta = (deltaX / wheelThresholdPx).toInt()
                    currentLevel = (committedLevel + stepDelta).coerceIn(1, 10)
                    val wheelRotation = (deltaX / wheelThresholdPx) * 12f
                    syncFxTileVisuals(
                        enabled = true,
                        level = currentLevel,
                        wheelRotation = wheelRotation.coerceIn(-45f, 45f),
                    )
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    card.removeCallbacks(longPressRunnable)
                    valueText.animate().rotationY(0f).setDuration(120L).start()
                    activeFxDragCount = (activeFxDragCount - 1).coerceAtLeast(0)
                    if (currentEnabled && isDragging && currentLevel != committedLevel) {
                        item.onLevelChanged(currentLevel)
                    }
                    if (activeFxDragCount == 0 && hasDeferredPlayBarRender) {
                        hasDeferredPlayBarRender = false
                        renderPlayBars(latestUiState.sequenceBars, latestUiState)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
        return card
    }

    private fun formatTransportClock(state: Screen4UiState): String {
        val stepDurationMs = ((60_000f / state.bpm.coerceAtLeast(1)) / 4f).toLong()
        val elapsedMs = (state.activeCycle * (stepDurationMs * Screen4Coordinator.STEP_COUNT)) +
            (state.activeStepIndex * stepDurationMs)
        val hours = elapsedMs / 3_600_000L
        val minutes = (elapsedMs % 3_600_000L) / 60_000L
        val seconds = (elapsedMs % 60_000L) / 1_000L
        val millis = elapsedMs % 1_000L
        return String.format("%02d:%02d:%02d:%03d", hours, minutes, seconds, millis)
    }

    private fun buildExpandedPlayBarPanel(
        sequenceBar: Screen4SequenceBarUi,
        state: Screen4UiState,
        dualExpanded: Boolean,
    ): View {
        val barColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_card_surface)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val title = TextView(this).apply {
            text = getString(R.string.screen4_music_balloon_title, sequenceBar.label)
            setTextColor(getColor(R.color.sb_text_primary))
            textSize = 18f
        }
        val subtitle = TextView(this).apply {
            text = getString(
                R.string.screen4_music_balloon_summary,
                sequenceBar.steps.count { it.sampleId != null },
                sequenceBar.selectedStepIndices.size,
            )
            setTextColor(getColor(R.color.sb_text_secondary))
            textSize = 13f
        }
        barColumn.addView(title)
        barColumn.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
            }
        )

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleLabel = TextView(this).apply {
            text = sequenceBar.label
            setTextColor(getColor(R.color.sb_text_primary))
            textSize = 16f
        }
        val removeBarButton = Button(this).apply {
            text = getString(R.string.screen4_music_remove_play_bar_inline)
            setOnClickListener { screen4Coordinator.removePlayBarAt(sequenceBar.barIndex) }
        }
        titleRow.addView(
            titleLabel,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        )
        titleRow.addView(
            removeBarButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(8)
            }
        )
        barColumn.addView(
            titleRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        )

        val actionsDock = buildCollapsibleDock(
            title = getString(R.string.screen4_music_assignment_dock_title),
            initiallyCollapsed = false,
        )
        val actionsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val saveBarButton = Button(this).apply {
            text = getString(R.string.screen4_music_save_bar)
            setOnClickListener { showSaveBarDialog(sequenceBar.barIndex) }
        }
        val loadBarButton = Button(this).apply {
            text = getString(R.string.screen4_music_load_bar)
            setOnClickListener { showLoadBarDialog(sequenceBar.barIndex) }
        }
        actionsRow.addView(saveBarButton, barActionLayoutParams())
        actionsRow.addView(loadBarButton, barActionLayoutParams(withStartMargin = true))
        actionsColumn.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(2)
            }
        )

        val batchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val allButton = Button(this).apply {
            text = getString(R.string.screen4_music_assign_all)
            setOnClickListener {
                showAssignFavoriteDialog(
                    barIndices = listOf(sequenceBar.barIndex),
                    batchTarget = Screen4BatchAssignTarget.ALL_SLOTS,
                )
            }
        }
        val oddButton = Button(this).apply {
            text = getString(R.string.screen4_music_assign_odd)
            setOnClickListener {
                showAssignFavoriteDialog(
                    barIndices = listOf(sequenceBar.barIndex),
                    batchTarget = Screen4BatchAssignTarget.ODD_SLOTS,
                )
            }
        }
        val evenButton = Button(this).apply {
            text = getString(R.string.screen4_music_assign_even)
            setOnClickListener {
                showAssignFavoriteDialog(
                    barIndices = listOf(sequenceBar.barIndex),
                    batchTarget = Screen4BatchAssignTarget.EVEN_SLOTS,
                )
            }
        }
        batchRow.addView(allButton, barActionLayoutParams())
        batchRow.addView(oddButton, barActionLayoutParams(withStartMargin = true))
        batchRow.addView(evenButton, barActionLayoutParams(withStartMargin = true))
        actionsColumn.addView(
            batchRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )

        val selectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val selectToggleButton = Button(this).apply {
            text = getString(
                if (sequenceBar.isSelectionModeEnabled) {
                    R.string.screen4_music_select_slots_done
                } else {
                    R.string.screen4_music_select_slots
                }
            )
            setOnClickListener { screen4Coordinator.toggleBarSelectionMode(sequenceBar.barIndex) }
        }
        val assignSelectedButton = Button(this).apply {
            text = getString(R.string.screen4_music_assign_selected)
            isEnabled = sequenceBar.selectedStepIndices.isNotEmpty()
            setOnClickListener {
                showAssignFavoriteDialog(
                    barIndices = listOf(sequenceBar.barIndex),
                    batchTarget = Screen4BatchAssignTarget.SELECTED_SLOTS,
                )
            }
        }
        val selectionSummaryText = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            text = getString(
                R.string.screen4_music_selected_count,
                sequenceBar.selectedStepIndices.size,
            )
            setTextColor(getColor(R.color.sb_text_secondary))
        }
        selectRow.addView(selectToggleButton, barActionLayoutParams())
        selectRow.addView(assignSelectedButton, barActionLayoutParams(withStartMargin = true))
        selectRow.addView(
            selectionSummaryText,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply {
                marginStart = dp(8)
            }
        )
        actionsColumn.addView(
            selectRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )
        actionsDock.content.addView(actionsColumn)
        barColumn.addView(
            actionsDock.container,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )

        val renderedButtons = mutableListOf<Button>()
        val grid = GridLayout(this).apply {
            columnCount = 4
            rowCount = 4
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }
        repeat(Screen4Coordinator.STEP_COUNT) { stepIndex ->
            val slot = sequenceBar.steps[stepIndex]
            val button = Button(this).apply {
                text = slot.label
                minHeight = dp(72)
                gravity = Gravity.CENTER
                tag = if (slot.sourcePageId != null && slot.sourceSlotIndex != null) {
                    buildFavoriteReferencePayload(slot.sourcePageId, slot.sourceSlotIndex)
                } else {
                    null
                }
                background = stepButtonBackground(
                    state = state,
                    sequenceBar = sequenceBar,
                    stepIndex = stepIndex,
                    slot = slot,
                )
                setOnClickListener {
                    if (latestUiState.sequenceBars.getOrNull(sequenceBar.barIndex)?.isSelectionModeEnabled == true) {
                        screen4Coordinator.toggleSelectedStep(sequenceBar.barIndex, stepIndex)
                    } else {
                        screen4Coordinator.clearStep(sequenceBar.barIndex, stepIndex)
                    }
                }
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
            renderedButtons += button
        }
        renderedExpandedStepButtons[sequenceBar.barIndex] = renderedButtons

        val padDock = buildCollapsibleDock(
            title = getString(R.string.screen4_music_step_pad_dock_title),
            initiallyCollapsed = false,
        )
        padDock.content.addView(grid)
        barColumn.addView(
            padDock.container,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )

        barColumn.addView(
            buildFxSection(
                barIndex = sequenceBar.barIndex,
                effectState = sequenceBar.effectState,
                dualExpanded = dualExpanded,
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(10)
            }
        )

        return barColumn
    }

    private fun refreshExpandedStepButtons(state: Screen4UiState) {
        renderedExpandedStepButtons.forEach { (barIndex, buttons) ->
            val sequenceBar = state.sequenceBars.getOrNull(barIndex) ?: return@forEach
            val accent = accentForExpandedBar(barIndex)
            buttons.forEachIndexed { stepIndex, button ->
                val slot = sequenceBar.steps.getOrNull(stepIndex) ?: return@forEachIndexed
                applyStepButtonVisuals(
                    button = button,
                    sequenceBar = sequenceBar,
                    stepIndex = stepIndex,
                    slot = slot,
                    state = state,
                    accent = accent,
                )
            }
        }
    }

    private fun applyStepButtonVisuals(
        button: Button,
        sequenceBar: Screen4SequenceBarUi,
        stepIndex: Int,
        slot: com.example.templei.feature.screen4.Screen4VisualSlot,
        state: Screen4UiState,
        accent: WorkspaceAccent,
    ) {
        button.setTextColor(
            getColor(
                when {
                    state.isPlaying && stepIndex == state.activeStepIndex -> R.color.black
                    sequenceBar.selectedStepIndices.contains(stepIndex) -> R.color.black
                    slot.sampleId != null -> R.color.black
                    else -> R.color.screen4_text_secondary
                }
            )
        )
        button.setBackgroundResource(
            when {
                state.isPlaying && stepIndex == state.activeStepIndex -> R.drawable.bg_screen4_step_selected
                sequenceBar.selectedStepIndices.contains(stepIndex) -> R.drawable.bg_screen4_step_selected
                slot.sampleId != null && accent == WorkspaceAccent.PRIMARY -> R.drawable.bg_screen4_step_primary
                slot.sampleId != null && accent == WorkspaceAccent.SECONDARY -> R.drawable.bg_screen4_step_secondary
                else -> R.drawable.bg_screen4_step_empty
            }
        )
    }

    private fun accentForExpandedBar(barIndex: Int): WorkspaceAccent {
        return if (expandedBarIndices.firstOrNull() == barIndex) {
            WorkspaceAccent.PRIMARY
        } else {
            WorkspaceAccent.SECONDARY
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
        showAssignFavoriteDialog(
            barIndices = listOf(barIndex),
            stepIndex = stepIndex,
            batchTarget = null,
        )
    }

    private fun showAssignFavoriteDialog(
        barIndices: List<Int>,
        stepIndex: Int? = null,
        batchTarget: Screen4BatchAssignTarget?,
    ) {
        val normalizedBarIndices = barIndices.distinct()
        val primaryBarIndex = normalizedBarIndices.firstOrNull() ?: return
        assignFavoriteDialog?.dismiss()
        assignFavoriteDialogSlotButtons.clear()
        assignFavoriteDialogBarIndices = normalizedBarIndices
        assignFavoriteDialogBarIndex = primaryBarIndex
        assignFavoriteDialogStepIndex = stepIndex
        assignFavoriteDialogBatchTarget = batchTarget

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
                    assignFavoriteFromDialog(slotIndex)
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
            .setTitle(
                if (batchTarget == null && stepIndex != null) {
                    getString(R.string.screen4_music_assign_dialog_title, primaryBarIndex + 1, stepIndex + 1)
                } else if (normalizedBarIndices.size > 1) {
                    getString(R.string.screen4_music_assign_group_dialog_title)
                } else {
                    getString(R.string.screen4_music_assign_batch_dialog_title, primaryBarIndex + 1)
                }
            )
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnDismissListener {
            assignFavoriteDialog = null
            assignFavoriteDialogPageStatusText = null
            assignFavoriteDialogPrevButton = null
            assignFavoriteDialogNextButton = null
            assignFavoriteDialogSlotButtons.clear()
            assignFavoriteDialogBatchTarget = null
            assignFavoriteDialogBarIndices = emptyList()
            assignFavoriteDialogBarIndex = null
            assignFavoriteDialogStepIndex = null
        }

        assignFavoriteDialog = dialog
        assignFavoriteDialogPageStatusText = pageStatusText
        assignFavoriteDialogPrevButton = prevButton
        assignFavoriteDialogNextButton = nextButton

        dialog.show()
        renderAssignFavoriteDialog(latestUiState)
    }

    private fun assignFavoriteFromDialog(slotIndex: Int) {
        val barIndices = assignFavoriteDialogBarIndices.ifEmpty {
            listOfNotNull(assignFavoriteDialogBarIndex)
        }
        if (barIndices.isEmpty()) return
        val batchTarget = assignFavoriteDialogBatchTarget
        if (batchTarget == null) {
            val barIndex = barIndices.first()
            val stepIndex = assignFavoriteDialogStepIndex ?: return
            screen4Coordinator.assignStepFromFavorite(
                barIndex = barIndex,
                stepIndex = stepIndex,
                favoritePageId = latestUiState.currentFavoritePageId,
                favoriteSlotIndex = slotIndex,
            )
        } else {
            barIndices.forEach { barIndex ->
                screen4Coordinator.assignFavoriteToBar(
                    barIndex = barIndex,
                    favoritePageId = latestUiState.currentFavoritePageId,
                    favoriteSlotIndex = slotIndex,
                    target = batchTarget,
                )
            }
        }
    }

    private fun toggleExpandedBarSelectionMode() {
        val expandedBars = expandedSequenceBars(latestUiState)
        if (expandedBars.isEmpty()) return
        val enableSelection = expandedBars.any { !it.isSelectionModeEnabled }
        expandedBars.forEach { sequenceBar ->
            screen4Coordinator.setBarSelectionMode(sequenceBar.barIndex, enableSelection)
        }
    }

    private fun selectExpandedBars(target: Screen4BatchAssignTarget) {
        val targetBarIndex = targetedExpandedBarIndex() ?: return
        screen4Coordinator.selectBatchTarget(targetBarIndex, target)
    }

    private fun showAssignFavoriteDialogForExpandedBars() {
        val expandedBars = expandedSequenceBars(latestUiState)
        val hasSelection = expandedBars.any { it.selectedStepIndices.isNotEmpty() }
        if (!hasSelection) return
        showAssignFavoriteDialog(
            barIndices = expandedBars.map { it.barIndex },
            batchTarget = Screen4BatchAssignTarget.SELECTED_SLOTS,
        )
    }

    private fun clearExpandedBarSelections() {
        expandedSequenceBars(latestUiState).forEach { sequenceBar ->
            if (sequenceBar.selectedStepIndices.isNotEmpty()) {
                screen4Coordinator.clearSelectedAssignments(sequenceBar.barIndex)
            }
        }
    }

    private fun expandedSequenceBars(state: Screen4UiState): List<Screen4SequenceBarUi> {
        return expandedBarIndices
            .distinct()
            .mapNotNull(state.sequenceBars::getOrNull)
    }

    private fun targetedExpandedBarIndex(): Int? {
        val expandedBars = expandedSequenceBars(latestUiState)
        clampSelectionTargetBarIndex(expandedBars)
        return expandedBars.firstOrNull { it.barIndex == selectionTargetBarIndex }?.barIndex
            ?: expandedBars.firstOrNull()?.barIndex
    }

    private fun clampSelectionTargetBarIndex(expandedBars: List<Screen4SequenceBarUi>) {
        if (expandedBars.isEmpty()) {
            selectionTargetBarIndex = 0
            return
        }
        if (expandedBars.none { it.barIndex == selectionTargetBarIndex }) {
            selectionTargetBarIndex = expandedBars.first().barIndex
        }
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
        favoritesSection.visibility = View.VISIBLE
        playBarSection.visibility = View.VISIBLE
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

    private fun toggleExpandedBar(barIndex: Int, barCount: Int) {
        if (barIndex !in 0 until barCount) return
        val current = expandedBarIndices.distinct().filter { it in 0 until barCount }.toMutableList()
        if (current.contains(barIndex)) {
            if (current.size > 1) {
                expandedBarIndices.clear()
                expandedBarIndices += barIndex
            }
            return
        }
        if (current.isEmpty()) {
            expandedBarIndices.clear()
            expandedBarIndices += 0
            return
        }
        if (current.size == 1) {
            expandedBarIndices.clear()
            expandedBarIndices += current.first()
            expandedBarIndices += barIndex
            return
        }
        expandedBarIndices.clear()
        expandedBarIndices += current.first()
        expandedBarIndices += barIndex
    }

    private fun buildStripFxSummary(stripDensity: StripDensity): View {
        return LinearLayout(this).apply {
            orientation = if (stripDensity.isUltraDense) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            addView(buildMiniFxBadge(getString(R.string.screen4_music_fx_badge_gain)))
            addView(
                buildMiniFxBadge(getString(R.string.screen4_music_fx_badge_pitch)),
                miniFxLayoutParams(stripDensity)
            )
            addView(
                buildMiniFxBadge(getString(R.string.screen4_music_fx_badge_space)),
                miniFxLayoutParams(stripDensity)
            )
        }
    }

    private fun buildMiniFxBadge(label: String): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(getColor(R.color.sb_text_secondary))
            textSize = 10f
            background = getDrawable(R.drawable.bg_section_surface)
            setPadding(dp(6), dp(3), dp(6), dp(3))
        }
    }

    private fun miniFxLayoutParams(stripDensity: StripDensity): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (stripDensity.isUltraDense) {
                topMargin = dp(4)
            } else {
                marginStart = dp(4)
            }
        }
    }

    private fun buildFxSection(
        barIndex: Int,
        effectState: Screen4BarEffectState,
        dualExpanded: Boolean,
    ): View {
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_section_surface)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val title = TextView(this).apply {
            text = getString(R.string.screen4_music_fx_title)
            setTextColor(getColor(R.color.sb_text_primary))
            textSize = 15f
        }
        val subtitle = TextView(this).apply {
            text = getString(
                if (dualExpanded) {
                    R.string.screen4_music_fx_subtitle_dual
                } else {
                    R.string.screen4_music_fx_subtitle_single
                }
            )
            setTextColor(getColor(R.color.sb_text_secondary))
            textSize = 12f
        }
        section.addView(title)
        section.addView(
            subtitle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
            }
        )

        val knobs = listOf(
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_gain),
                valueText = { progress -> formatPercent(progress / 100f) },
                progress = (effectState.gain * 100f).toInt(),
                max = 100,
                onChanged = { progress ->
                    screen4Coordinator.updateBarGain(barIndex, progress / 100f)
                },
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_pitch),
                valueText = { progress -> formatSigned(progress - 12f) },
                progress = (effectState.pitchSemitones + 12f).toInt(),
                max = 24,
                onChanged = { progress ->
                    screen4Coordinator.updateBarPitchSemitones(barIndex, progress - 12f)
                },
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_pan),
                valueText = { progress -> formatPan((progress / 50f) - 1f) },
                progress = ((effectState.pan + 1f) * 50f).toInt(),
                max = 100,
                onChanged = { progress ->
                    screen4Coordinator.updateBarPan(barIndex, (progress / 50f) - 1f)
                },
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_reverb),
                valueText = { progress -> formatPercent(progress / 100f) },
                progress = (effectState.reverbSend * 100f).toInt(),
                max = 100,
                onChanged = { progress ->
                    screen4Coordinator.updateBarReverbSend(barIndex, progress / 100f)
                },
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_delay),
                valueText = { progress -> formatPercent(progress / 100f) },
                progress = (effectState.delaySend * 100f).toInt(),
                max = 100,
                onChanged = { progress ->
                    screen4Coordinator.updateBarDelaySend(barIndex, progress / 100f)
                },
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_low_pass),
                valueText = { _ -> getString(R.string.screen4_music_fx_coming_soon) },
                progress = 0,
                max = 100,
                enabled = false,
                onChanged = {},
            ),
            FxSliderConfig(
                label = getString(R.string.screen4_music_fx_high_pass),
                valueText = { _ -> getString(R.string.screen4_music_fx_coming_soon) },
                progress = 0,
                max = 100,
                enabled = false,
                onChanged = {},
            ),
        )
        val grid = GridLayout(this).apply {
            columnCount = if (dualExpanded) 2 else 4
            rowCount = if (dualExpanded) 4 else 2
            alignmentMode = GridLayout.ALIGN_MARGINS
            useDefaultMargins = true
        }
        knobs.forEach { config ->
            val knobCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = getDrawable(R.drawable.bg_card_surface)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                alpha = if (config.enabled) 1f else 0.55f
            }
            val knobLabel = TextView(this).apply {
                text = config.label
                setTextColor(getColor(R.color.sb_text_primary))
                textSize = 12f
                gravity = Gravity.CENTER
            }
            val valueLabel = TextView(this).apply {
                text = config.valueText(config.progress)
                setTextColor(getColor(R.color.sb_text_secondary))
                textSize = 11f
                gravity = Gravity.CENTER
            }
            val sliderFrame = FrameLayout(this).apply {
                minimumHeight = dp(132)
            }
            val slider = SeekBar(this).apply {
                max = config.max
                progress = config.progress.coerceIn(0, config.max)
                isEnabled = config.enabled
                rotation = -90f
                var pendingProgress = progress
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        pendingProgress = progress
                        valueLabel.text = config.valueText(progress)
                        if (!fromUser || !config.enabled) {
                            return
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        if (config.enabled) {
                            activeFxDragCount += 1
                        }
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        if (!config.enabled) return
                        activeFxDragCount = (activeFxDragCount - 1).coerceAtLeast(0)
                        config.onChanged(pendingProgress)
                        if (activeFxDragCount == 0 && hasDeferredPlayBarRender) {
                            hasDeferredPlayBarRender = false
                            renderPlayBars(latestUiState.sequenceBars, latestUiState)
                        }
                    }
                })
            }
            knobCard.addView(knobLabel)
            knobCard.addView(
                valueLabel,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = dp(4)
                }
            )
            sliderFrame.addView(
                slider,
                FrameLayout.LayoutParams(
                    dp(112),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ).apply {
                    gravity = Gravity.CENTER
                }
            )
            knobCard.addView(
                sliderFrame,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(132),
                ).apply {
                    topMargin = dp(6)
                }
            )
            grid.addView(
                knobCard,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            )
        }
        section.addView(
            grid,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )
        return section
    }

    private fun buildBarDockContainer(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_section_surface)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
    }

    private fun buildCollapsibleDock(
        title: String,
        initiallyCollapsed: Boolean,
    ): DockSection {
        val container = buildBarDockContainer()
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.sb_text_primary))
            textSize = 13f
        }
        val toggleButton = Button(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun syncDockVisibility(isCollapsed: Boolean) {
            content.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            toggleButton.text = getString(
                if (isCollapsed) {
                    R.string.screen4_section_expand
                } else {
                    R.string.screen4_section_collapse
                }
            )
        }

        var isCollapsed = initiallyCollapsed
        toggleButton.setOnClickListener {
            isCollapsed = !isCollapsed
            syncDockVisibility(isCollapsed)
        }

        headerRow.addView(
            titleView,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        )
        headerRow.addView(
            toggleButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = dp(8)
            }
        )
        container.addView(headerRow)
        container.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        )
        syncDockVisibility(isCollapsed)
        return DockSection(container = container, content = content)
    }

    private fun stepButtonBackground(
        state: Screen4UiState,
        sequenceBar: Screen4SequenceBarUi,
        stepIndex: Int,
        slot: com.example.templei.feature.screen4.Screen4VisualSlot,
    ) = getDrawable(
        when {
            state.isPlaying && stepIndex == state.activeStepIndex -> R.drawable.bg_button_primary
            sequenceBar.selectedStepIndices.contains(stepIndex) -> R.drawable.bg_button_primary
            slot.sampleId != null -> R.drawable.bg_button_pad
            else -> R.drawable.bg_button_secondary
        }
    )

    private fun formatPercent(value: Float): String {
        return "${(value * 100f).toInt()}%"
    }

    private fun formatSigned(value: Float): String {
        val rounded = value.toInt()
        return if (rounded > 0) "+$rounded" else rounded.toString()
    }

    private fun formatPan(value: Float): String {
        val rounded = (kotlin.math.abs(value) * 100f).toInt()
        return when {
            value > 0.05f -> "${rounded}R"
            value < -0.05f -> "${rounded}L"
            else -> "C"
        }
    }

    private fun stripDensity(barCount: Int): StripDensity {
        return StripDensity(
            isDense = barCount >= 4,
            isUltraDense = barCount >= 5,
            showMiniFxColumn = barCount >= 4,
        )
    }

    private fun clampExpandedBarIndices(barCount: Int) {
        val normalized = expandedBarIndices
            .distinct()
            .filter { it in 0 until barCount }
            .take(2)
            .toMutableList()
        if (barCount > 0 && normalized.isEmpty()) {
            normalized += 0
        }
        expandedBarIndices.clear()
        expandedBarIndices += normalized
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun barActionLayoutParams(withStartMargin: Boolean = false): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply {
            if (withStartMargin) {
                marginStart = dp(8)
            }
        }
    }

    private companion object {
        private const val FAVORITE_REFERENCE_PREFIX = "favorite-ref"
    }

    private data class StripDensity(
        val isDense: Boolean,
        val isUltraDense: Boolean,
        val showMiniFxColumn: Boolean,
    )

    private data class FxSliderConfig(
        val label: String,
        val valueText: (Int) -> String,
        val progress: Int,
        val max: Int,
        val enabled: Boolean = true,
        val onChanged: (Int) -> Unit,
    )

    private data class DockSection(
        val container: LinearLayout,
        val content: LinearLayout,
    )

    private enum class WorkspaceAccent(
        val workspaceDrawableRes: Int,
        val textColorRes: Int,
    ) {
        PRIMARY(
            workspaceDrawableRes = R.drawable.bg_screen4_workspace_primary,
            textColorRes = R.color.screen4_primary,
        ),
        SECONDARY(
            workspaceDrawableRes = R.drawable.bg_screen4_workspace_secondary,
            textColorRes = R.color.screen4_secondary,
        ),
    }

    private data class FxRailItem(
        val label: String,
        val enabled: Boolean,
        val level: Int,
        val onToggle: () -> Unit,
        val onLevelChanged: (Int) -> Unit,
    )
}
