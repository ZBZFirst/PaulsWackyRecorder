package com.example.templei

import android.content.Intent
import android.graphics.Rect
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.ScrollView
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.soundboard.CachePolicy
import com.example.templei.feature.soundboard.SoundboardAudioEngine
import com.example.templei.feature.soundboard.SoundboardConfig
import com.example.templei.feature.soundboard.ClipIndexRepository
import com.example.templei.feature.soundboard.SoundboardStateMachine
import com.example.templei.feature.soundboard.Screen3UiRenderer
import com.example.templei.feature.soundboard.Screen3ClipBrowserRenderer
import com.example.templei.feature.soundboard.Screen3SettingsStore
import com.example.templei.feature.soundboard.Screen3SettingsDialogHelper
import com.example.templei.feature.soundboard.Screen3FavoritePadHelper
import com.example.templei.feature.soundboard.Screen3PlaybackPolicy
import com.example.templei.feature.soundboard.Screen3ClipCacheManager
import com.example.templei.feature.soundboard.Screen3FavoritesManager
import com.example.templei.feature.soundboard.Screen3FolderBrowserCoordinator
import com.example.templei.feature.soundboard.Screen3LibraryRefreshSignal
import com.example.templei.feature.soundboard.Screen3Coordinator
import com.example.templei.feature.soundboard.Screen3Intent
import com.example.templei.feature.tutorial.TutorialScreen
import com.example.templei.feature.tutorial.TutorialSpotlightOverlayView
import com.example.templei.feature.tutorial.TutorialStatus
import com.example.templei.feature.tutorial.TutorialStore
import com.example.templei.ui.navigation.AppShellInsets
import com.example.templei.ui.navigation.AppShellNavigation
import java.util.Locale
import kotlin.math.max

/**
 * Screen 3: bounded soundboard optimized for short clip triggering.
 */
class Screen3Activity : ComponentActivity() {
    private val stateMachine = SoundboardStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var statusText: TextView
    private lateinit var loadingDetailText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var folderSpinner: Spinner
    private lateinit var previousFolderButton: Button
    private lateinit var nextFolderButton: Button
    private lateinit var selectFolderButton: Button
    private lateinit var settingsButton: Button
    private lateinit var rescanLibraryButton: Button
    private lateinit var clearSelectedSlotButton: Button
    private lateinit var assignmentTargetText: TextView
    private lateinit var assignmentRow: LinearLayout
    private lateinit var favoritePageRow: LinearLayout
    private lateinit var favoritePagePrevButton: Button
    private lateinit var favoritePageNextButton: Button
    private lateinit var favoritePageAddButton: Button
    private lateinit var favoritePageRemoveButton: Button
    private lateinit var favoritePageSaveButton: Button
    private lateinit var favoritePageLoadButton: Button
    private lateinit var favoritePageStatusText: TextView
    private lateinit var favoritePageDotsContainer: LinearLayout
    private lateinit var clipBrowserContainer: LinearLayout
    private lateinit var clipBrowserScroll: ScrollView
    private lateinit var mainScrollView: ScrollView
    private lateinit var favoritesPad: android.widget.GridLayout
    private lateinit var browserToggleButton: Button
    private lateinit var favoritesToggleButton: Button
    private lateinit var tutorialCard: LinearLayout
    private lateinit var tutorialHandoffCard: LinearLayout
    private lateinit var tutorialProgressText: TextView
    private lateinit var tutorialPromptText: TextView
    private lateinit var tutorialInstructionText: TextView
    private lateinit var tutorialSkipButton: Button
    private lateinit var continueTutorialButton: Button
    private lateinit var closeTutorialButton: Button
    private lateinit var tutorialOverlay: TutorialSpotlightOverlayView

    private lateinit var soundPool: SoundPool
    private var config = SoundboardConfig()

    private val favoritePadButtonIds = listOf(
        R.id.favoritePadButton1, R.id.favoritePadButton2, R.id.favoritePadButton3,
        R.id.favoritePadButton4, R.id.favoritePadButton5, R.id.favoritePadButton6,
        R.id.favoritePadButton7, R.id.favoritePadButton8, R.id.favoritePadButton9
    )

    private var folderEntries: List<FolderEntry> = emptyList()
    private var currentFolderIndex: Int = 0
    private var activeFolderClips: List<ClipMetadata> = emptyList()

    private val clipById = linkedMapOf<String, ClipMetadata>()
    private val favoriteSlotClipIds = mutableMapOf<Int, String>()
    private var selectedAssignmentSlotIndex: Int = 0

    private val activeStreamIds = mutableSetOf<Int>()
    private val lastPlayByClipIdMs = mutableMapOf<String, Long>()
    private val rejectionCounts = mutableMapOf<SoundboardStateMachine.PlaybackRejectionReason, Int>()
    private var lastRejectionEvent: SoundboardStateMachine.LastRejection? = null
    private val audioEngine by lazy { SoundboardAudioEngine.getInstance(this) }
    private val clipIndexRepository by lazy { ClipIndexRepository(this) }
    private val libraryRefreshSignal by lazy { Screen3LibraryRefreshSignal(this) }
    private val tutorialStore by lazy { TutorialStore(this) }
    private lateinit var uiRenderer: Screen3UiRenderer
    private val settingsStore by lazy { Screen3SettingsStore(this) }
    private val settingsDialogHelper by lazy { Screen3SettingsDialogHelper(this) }
    private val playbackPolicy = Screen3PlaybackPolicy()
    private lateinit var clipCacheManager: Screen3ClipCacheManager
    private lateinit var favoritesManager: Screen3FavoritesManager
    private val folderBrowserCoordinator by lazy { Screen3FolderBrowserCoordinator(clipIndexRepository) }
    private val screen3Coordinator = Screen3Coordinator(stateMachine = stateMachine, favoriteSlotCount = FAVORITE_SLOT_COUNT)
    private lateinit var clipBrowserRenderer: Screen3ClipBrowserRenderer
    private val favoritePadHelper = Screen3FavoritePadHelper()
    private lateinit var favoritePadButtons: List<Button>
    private lateinit var controlsCard: View
    private lateinit var controlsGroup: View
    private lateinit var controlsToggleButton: Button
    private lateinit var folderSpinnerAdapter: ArrayAdapter<String>
    private var suppressFolderSpinnerSelection: Boolean = false
    private var lastObservedLibraryChangeMs: Long = 0L
    private var hasCompletedInitialResume: Boolean = false
    private var tutorialMode: Boolean = false
    private var favoritePadDeleteMode: Boolean = false

    private val tutorialAssignments = listOf(
        Screen3TutorialAssignment(slotIndex = 0, spokenPrompt = "so", clipBaseName = "tutorial_01_so"),
        Screen3TutorialAssignment(slotIndex = 1, spokenPrompt = "re", clipBaseName = "tutorial_02_re"),
        Screen3TutorialAssignment(slotIndex = 2, spokenPrompt = "mi", clipBaseName = "tutorial_03_mi"),
        Screen3TutorialAssignment(slotIndex = 3, spokenPrompt = "do", clipBaseName = "tutorial_04_do"),
        Screen3TutorialAssignment(slotIndex = 4, spokenPrompt = "la", clipBaseName = "tutorial_05_la"),
    )

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Log.i(TAG, "Folder picked: $uri")
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                Log.w(TAG, "Persistable URI permission failed for $uri", it)
            }
            clipIndexRepository.setPersistedRootUri(uri)
            runCatching { rebuildIndexAndBind(uri) }.onFailure(::failToMainMenu)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            setContentView(R.layout.activity_screen3)
            tutorialMode = shouldRunTutorial()
            AppShellNavigation.bind(
                activity = this,
                currentDestination = Screen3Activity::class.java,
                title = getString(R.string.screen3TitleText),
                chipText = getString(R.string.screen3_header_chip),
            )
            AppShellInsets.apply(
                activity = this,
                rootId = R.id.screen3Root,
                scrollViewId = R.id.screen3ScrollView,
            )

            statusText = findViewById(R.id.soundboardStatusText)
            loadingDetailText = findViewById(R.id.soundboardLoadingDetailText)
            loadingProgressBar = findViewById(R.id.soundboardLoadingProgressBar)
            folderSpinner = findViewById(R.id.soundboardFolderSpinner)
            previousFolderButton = findViewById(R.id.soundboardFolderPrevButton)
            nextFolderButton = findViewById(R.id.soundboardFolderNextButton)
            selectFolderButton = findViewById(R.id.soundboardSelectFolderButton)
            settingsButton = findViewById(R.id.soundboardSettingsButton)
            rescanLibraryButton = findViewById(R.id.soundboardRescanLibraryButton)
            clearSelectedSlotButton = findViewById(R.id.soundboardClearSelectedSlotButton)
            assignmentTargetText = findViewById(R.id.soundboardAssignmentTargetText)
            assignmentRow = findViewById(R.id.soundboardAssignmentRow)
            favoritePageRow = findViewById(R.id.soundboardFavoritePageRow)
            favoritePagePrevButton = findViewById(R.id.soundboardFavoritePagePrevButton)
            favoritePageNextButton = findViewById(R.id.soundboardFavoritePageNextButton)
            favoritePageAddButton = findViewById(R.id.soundboardFavoritePageAddButton)
            favoritePageRemoveButton = findViewById(R.id.soundboardFavoritePageRemoveButton)
            favoritePageSaveButton = findViewById(R.id.soundboardFavoritePageSaveButton)
            favoritePageLoadButton = findViewById(R.id.soundboardFavoritePageLoadButton)
            favoritePageStatusText = findViewById(R.id.soundboardFavoritePageStatusText)
            favoritePageDotsContainer = findViewById(R.id.soundboardFavoritePageDotsContainer)
            clipBrowserContainer = findViewById(R.id.soundboardClipBrowserContainer)
            clipBrowserScroll = findViewById(R.id.soundboardClipBrowserScroll)
            mainScrollView = findViewById(R.id.screen3ScrollView)
            favoritesPad = findViewById(R.id.soundboardFavoritesPad)
            browserToggleButton = findViewById(R.id.soundboardBrowserToggleButton)
            favoritesToggleButton = findViewById(R.id.soundboardFavoritesToggleButton)
            tutorialCard = findViewById(R.id.screen3TutorialCard)
            tutorialHandoffCard = findViewById(R.id.screen3TutorialHandoffCard)
            tutorialProgressText = findViewById(R.id.screen3TutorialProgressText)
            tutorialPromptText = findViewById(R.id.screen3TutorialPromptText)
            tutorialInstructionText = findViewById(R.id.screen3TutorialInstructionText)
            tutorialSkipButton = findViewById(R.id.screen3TutorialSkipButton)
            continueTutorialButton = findViewById(R.id.screen3ContinueTutorialButton)
            closeTutorialButton = findViewById(R.id.screen3CloseTutorialButton)
            tutorialOverlay = findViewById(R.id.screen3TutorialOverlay)
            controlsCard = findViewById(R.id.soundboardControlsCard)
            controlsGroup = findViewById(R.id.soundboardControlsGroup)
            controlsToggleButton = findViewById(R.id.soundboardControlsToggleButton)
            favoritePadButtons = favoritePadButtonIds.map { findViewById(it) }
            findViewById<TextView>(R.id.appHeaderChip)?.setOnClickListener {
                toggleControlsCard(scrollIntoView = true)
            }
            mainScrollView.setOnScrollChangeListener { _, _, _, _, _ ->
                if (tutorialMode) {
                    tutorialOverlay.invalidate()
                }
            }
            clipBrowserScroll.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            clipBrowserScroll.setOnScrollChangeListener { _, _, _, _, _ ->
                if (tutorialMode) {
                    tutorialOverlay.invalidate()
                }
            }

            uiRenderer = Screen3UiRenderer(
                context = this,
                statusText = statusText,
                loadingDetailText = loadingDetailText,
                loadingProgressBar = loadingProgressBar,
                folderSpinner = folderSpinner,
                previousFolderButton = previousFolderButton,
                nextFolderButton = nextFolderButton,
                assignmentTargetText = assignmentTargetText,
                assignmentRow = assignmentRow,
                favoritePageRow = favoritePageRow,
                clipBrowserScroll = clipBrowserScroll,
                favoritesPad = favoritesPad,
                browserToggleButton = browserToggleButton,
                favoritesToggleButton = favoritesToggleButton
            )
            clipBrowserRenderer = Screen3ClipBrowserRenderer(
                context = this,
                clipBrowserContainer = clipBrowserContainer
            )

            val defaults = Screen3SettingsStore.Defaults(
                defaultMaxStreams = DEFAULT_MAX_STREAMS,
                defaultCooldownMs = DEFAULT_COOLDOWN_MS,
                defaultMaxCacheSize = DEFAULT_MAX_CACHE_SIZE,
                defaultUnloadOnFolderChange = DEFAULT_UNLOAD_ON_FOLDER_CHANGE,
                defaultCachePolicy = DEFAULT_CACHE_POLICY
            )
            config = settingsStore.loadConfig(defaults)
            buildSoundPool(config.maxStreams)
            clipCacheManager = Screen3ClipCacheManager(soundPool = soundPool, contentResolver = contentResolver)
            favoritesManager = Screen3FavoritesManager(
                settingsStore = settingsStore,
                favoriteSlotCount = FAVORITE_SLOT_COUNT,
                emptyLabelForSlot = { slotNumber -> getString(R.string.soundboard_favorite_slot_label_empty, slotNumber) },
                assignedLabelForSlot = { slotNumber, clipDisplayName ->
                    getString(R.string.soundboard_favorite_slot_label_assigned, slotNumber, clipDisplayName)
                },
                savedUnknownLabelForSlot = { slotNumber -> getString(R.string.soundboard_favorite_slot_label_missing, slotNumber) }
            )
            favoritesManager.initialize()
            selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
            syncFavoritesFromManager()
            prepareTutorialFavoritePadIfNeeded()
            screen3Coordinator.dispatch(Screen3Intent.Initialize)

            folderSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
            folderSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            folderSpinner.adapter = folderSpinnerAdapter

            previousFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    screen3Coordinator.dispatch(Screen3Intent.PreviousFolder)
                    applyIndexedState(folderBrowserCoordinator.movePreviousFolder())
                    refreshSelectedFolderFromDisk()
                }
            }

            nextFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    screen3Coordinator.dispatch(Screen3Intent.NextFolder)
                    applyIndexedState(folderBrowserCoordinator.moveNextFolder())
                    refreshSelectedFolderFromDisk()
                }
            }

            folderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (suppressFolderSpinnerSelection || folderEntries.isEmpty()) return
                    if (position == currentFolderIndex) return
                    screen3Coordinator.dispatch(Screen3Intent.SelectFolderIndex(position))
                    applyIndexedState(folderBrowserCoordinator.selectFolder(position))
                    refreshSelectedFolderFromDisk()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

            selectFolderButton.setOnClickListener { pickFolderLauncher.launch(clipIndexRepository.getPersistedRootUri()) }
            settingsButton.setOnClickListener { showSettingsDialog() }
            rescanLibraryButton.setOnClickListener {
                val rootUri = clipIndexRepository.getPersistedRootUri()
                if (rootUri == null) {
                    renderNoRootSelected()
                } else {
                    runCatching { rebuildIndexAndBind(rootUri) }.onFailure(::failToMainMenu)
                }
            }
            tutorialSkipButton.setOnClickListener {
                closeTutorialAndKeepExploring()
            }
            continueTutorialButton.setOnClickListener {
                continueToSequencer()
            }
            closeTutorialButton.setOnClickListener {
                closeTutorialAndKeepExploring()
            }
            clearSelectedSlotButton.setOnClickListener { showClearSlotDialog() }
            favoritePagePrevButton.setOnClickListener {
                if (favoritesManager.moveToPreviousPage()) {
                    syncFavoritesFromManager()
                    renderFavoritePadState()
                }
            }
            favoritePageNextButton.setOnClickListener {
                if (favoritesManager.moveToNextPage()) {
                    syncFavoritesFromManager()
                    renderFavoritePadState()
                }
            }
            favoritePageAddButton.setOnClickListener {
                favoritesManager.addPage()
                syncFavoritesFromManager()
                renderFavoritePadState()
                Toast.makeText(
                    this,
                    getString(R.string.soundboard_favorite_page_added, favoritesManager.currentPageIndex() + 1),
                    Toast.LENGTH_SHORT
                ).show()
            }
            favoritePageRemoveButton.setOnClickListener {
                if (favoritesManager.removeCurrentPage()) {
                    syncFavoritesFromManager()
                    renderFavoritePadState()
                    Toast.makeText(
                        this,
                        getString(R.string.soundboard_favorite_page_removed, favoritesManager.currentPageIndex() + 1),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.soundboard_favorite_page_remove_blocked),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            favoritePageSaveButton.setOnClickListener {
                if (tutorialMode && currentTutorialPhase() == Screen3TutorialPhase.SAVE_PAGE) {
                    saveTutorialFavoritePage()
                } else {
                    showSaveFavoritePageDialog()
                }
            }
            favoritePageLoadButton.setOnClickListener {
                showLoadFavoritePageDialog()
            }
            browserToggleButton.setOnClickListener {
                val next = !screen3Coordinator.currentViewState().isBrowserCollapsed
                screen3Coordinator.dispatch(Screen3Intent.SetBrowserCollapsed(next))
                updateSectionVisibility()
            }
            favoritesToggleButton.setOnClickListener {
                toggleFavoritePadDeleteMode()
            }
            controlsToggleButton.setOnClickListener {
                toggleControlsCard(scrollIntoView = false)
            }

            bindFavoritePadButtons()
            updateSectionVisibility()
            initializeFromLaunchContext()
        }.onFailure(::failToMainMenu)
    }

    override fun onResume() {
        super.onResume()
        if (hasCompletedInitialResume) {
            if (!maybeRefreshIndexedLibrary()) {
                refreshSelectedFolderFromDisk()
            }
        } else {
            hasCompletedInitialResume = true
            maybeRefreshIndexedLibrary()
        }
        updateSectionVisibility()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && tutorialMode) {
            renderTutorialChrome()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCacheAndPending()
        audioEngine.release()
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
    }

    private fun buildSoundPool(maxStreams: Int) {
        soundPool = SoundPool.Builder().setMaxStreams(maxStreams).build()
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            if (::clipCacheManager.isInitialized) {
                clipCacheManager.onSoundPoolLoadComplete(soundId, status)
            }
        }
    }

    private fun rebuildSoundPoolIfNeeded(newConfig: SoundboardConfig) {
        val streamChanged = newConfig.maxStreams != config.maxStreams
        config = newConfig
        settingsStore.saveConfig(config)
        if (streamChanged) {
            clearCacheAndPending()
            soundPool.release()
            buildSoundPool(config.maxStreams)
            clipCacheManager = Screen3ClipCacheManager(soundPool = soundPool, contentResolver = contentResolver)
            refreshReadyState()
        }
    }

    private fun showSettingsDialog() {
        settingsDialogHelper.show(
            config = config,
            onReset = {
                rebuildSoundPoolIfNeeded(SoundboardConfig())
                trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.SETTINGS_APPLY)
                refreshReadyState()
            },
            onApply = { updated ->
                rebuildSoundPoolIfNeeded(updated)
                trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.SETTINGS_APPLY)
                refreshReadyState()
            }
        )
    }

    private fun initializeFromPersistedIndexOrNoRoot() {
        val indexedState = folderBrowserCoordinator.initializeFromPersistedRoot()
        lastObservedLibraryChangeMs = clipIndexRepository.latestIndexedTimestampMs()
        if (!indexedState.hasRootSelection) {
            screen3Coordinator.dispatch(Screen3Intent.FolderPicked(uri = null))
            renderNoRootSelected()
            return
        }
        screen3Coordinator.dispatch(Screen3Intent.FolderPicked(indexedState.rootUri))
        if (indexedState.folderNames.isEmpty()) {
            val rootUri = indexedState.rootUri
            if (rootUri == null) {
                renderNoRootSelected()
            } else {
                runCatching { rebuildIndexAndBind(rootUri) }.onFailure(::failToMainMenu)
            }
            return
        }
        applyIndexedState(indexedState)
    }

    private fun initializeFromLaunchContext() {
        val tutorialRootUri = intent?.getStringExtra(EXTRA_TUTORIAL_ROOT_URI)?.let(Uri::parse)
        if (tutorialRootUri != null) {
            clipIndexRepository.setPersistedRootUri(tutorialRootUri)
            val selectedFolderName = DocumentFile.fromTreeUri(this, tutorialRootUri)?.name
            clipIndexRepository.setPersistedSelectedFolderName(selectedFolderName)
            runCatching { rebuildIndexAndBind(tutorialRootUri) }.onFailure(::failToMainMenu)
            return
        }
        val forceRebuildOnStart = intent?.getBooleanExtra(EXTRA_FORCE_REBUILD_ON_START, false) == true
        val persistedRootUri = clipIndexRepository.getPersistedRootUri()
        if (forceRebuildOnStart && persistedRootUri != null) {
            runCatching { rebuildIndexAndBind(persistedRootUri) }.onFailure(::failToMainMenu)
            return
        }
        initializeFromPersistedIndexOrNoRoot()
    }

    private fun maybeRefreshIndexedLibrary(): Boolean {
        val latestChangeMs = libraryRefreshSignal.lastChangedAtMs()
        if (latestChangeMs <= lastObservedLibraryChangeMs) return false
        lastObservedLibraryChangeMs = latestChangeMs
        refreshSelectedFolderFromDisk()
        return true
    }

    private fun refreshSelectedFolderFromDisk(showLoading: Boolean = false) {
        val indexedState = folderBrowserCoordinator.initializeFromPersistedRoot()
        if (!indexedState.hasRootSelection) return

        if (showLoading) {
            stateMachine.onLoading()
            renderState(stateMachine.currentState())
            loadingProgressBar.isIndeterminate = true
            loadingProgressBar.progress = 0
            loadingDetailText.text = getString(R.string.soundboard_loading_detail_loading)
        }

        Thread {
            runCatching { folderBrowserCoordinator.refreshCurrentFolder() }
                .onSuccess { summary ->
                    lastObservedLibraryChangeMs = clipIndexRepository.latestIndexedTimestampMs()
                    mainHandler.post {
                        if (showLoading) {
                            loadingProgressBar.isIndeterminate = false
                            loadingProgressBar.max = 100
                            loadingProgressBar.progress = 100
                            loadingDetailText.text = getString(
                                R.string.soundboard_loading_detail_index_ready,
                                summary.playableCount,
                                summary.clipCount,
                                summary.folderCount
                            )
                        }
                        applyIndexedState(folderBrowserCoordinator.currentState())
                    }
                }
                .onFailure {
                    mainHandler.post {
                        Log.w(TAG, "Screen3 library refresh failed", it)
                        if (showLoading) {
                            renderState(stateMachine.currentState())
                        }
                    }
                }
        }.start()
    }

    private fun rebuildIndexAndBind(rootUri: Uri) {
        screen3Coordinator.dispatch(Screen3Intent.FolderPicked(rootUri))
        stateMachine.onLoading()
        renderState(stateMachine.currentState())
        loadingProgressBar.isIndeterminate = true
        loadingProgressBar.progress = 0
        loadingDetailText.text = getString(R.string.soundboard_loading_detail_loading)

        Thread {
            val summary = folderBrowserCoordinator.setPickedRootAndRebuild(rootUri)
            mainHandler.post {
                lastObservedLibraryChangeMs = clipIndexRepository.latestIndexedTimestampMs()
                loadingProgressBar.isIndeterminate = false
                loadingProgressBar.max = 100
                loadingProgressBar.progress = 100
                loadingDetailText.text = getString(
                    R.string.soundboard_loading_detail_index_ready,
                    summary.playableCount,
                    summary.clipCount,
                    summary.folderCount
                )
                applyIndexedState(folderBrowserCoordinator.currentState())
            }
        }.start()
    }

    private fun applyIndexedState(indexedState: Screen3FolderBrowserCoordinator.IndexedFolderState) {
        folderEntries = indexedState.folderNames.map { FolderEntry(it) }
        currentFolderIndex = indexedState.selectedFolderIndex.coerceAtMost(max(0, folderEntries.size - 1))
        screen3Coordinator.setFolders(indexedState.folderNames, currentFolderIndex)
        if (folderEntries.isEmpty()) {
            renderIndexedEmptyState()
            return
        }

        uiRenderer.renderFolderHeader(
            hasMultipleFolders = folderEntries.size > 1,
            folderCount = folderEntries.size
        )
        syncFolderSpinnerOptions()

        val clips: List<ClipMetadata> = indexedState.clips.map { indexed ->
            ClipMetadata(
                id = indexed.clipId,
                displayName = indexed.fileName,
                uri = indexed.clipUri,
                folderName = indexed.folderName,
                durationMs = indexed.durationMs,
                isPlayable = indexed.playable
            )
        }

        activeFolderClips = clips
        syncKnownClipsFromIndex()
        activeFolderClips.forEach { clipById[it.id] = it }
        screen3Coordinator.setClipItems(
            clips = activeFolderClips.map {
                com.example.templei.feature.soundboard.Screen3ClipItem(
                    id = it.id,
                    label = it.displayName,
                    playable = it.isPlayable
                )
            }
        )
        renderClipBrowser(activeFolderClips)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.FOLDER_SWITCH)
        refreshReadyState()
        renderFavoritePadState()
    }

    private fun renderNoRootSelected() {
        folderEntries = emptyList()
        activeFolderClips = emptyList()
        currentFolderIndex = 0
        renderClipBrowser(emptyList())
        stateMachine.markNoRootSelected()
        renderState(stateMachine.currentState())
        uiRenderer.renderNoRootSelectedVisuals()
    }

    private fun renderIndexedEmptyState() {
        activeFolderClips = emptyList()
        renderClipBrowser(emptyList())
        refreshReadyState()
        uiRenderer.renderIndexedEmptyVisuals()
    }

    private fun updateSectionVisibility() {
        val viewState = screen3Coordinator.currentViewState()
        uiRenderer.renderSectionVisibility(
            isBrowserCollapsed = viewState.isBrowserCollapsed,
            isFavoritesCollapsed = viewState.isFavoritesCollapsed
        )
        controlsCard.visibility = if (viewState.isControlsCollapsed) View.GONE else View.VISIBLE
        controlsGroup.visibility = View.VISIBLE
        controlsToggleButton.text = getString(
            if (viewState.isControlsCollapsed) R.string.soundboard_section_expand else R.string.soundboard_section_collapse
        )
        renderFavoritePadDeleteToggle()
    }

    private fun toggleControlsCard(scrollIntoView: Boolean) {
        val nextCollapsed = !screen3Coordinator.currentViewState().isControlsCollapsed
        screen3Coordinator.dispatch(Screen3Intent.SetControlsCollapsed(nextCollapsed))
        updateSectionVisibility()
        if (!nextCollapsed && scrollIntoView) {
            scrollToControlsCard()
        }
    }

    private fun scrollToControlsCard() {
        val scrollView = findViewById<ScrollView>(R.id.screen3ScrollView) ?: return
        scrollView.post {
            scrollView.smoothScrollTo(0, controlsCard.top)
        }
    }


    private fun syncFolderSpinnerOptions() {
        val folderNames = folderEntries.map { it.name }
        suppressFolderSpinnerSelection = true
        folderSpinnerAdapter.clear()
        folderSpinnerAdapter.addAll(folderNames)
        folderSpinnerAdapter.notifyDataSetChanged()

        if (folderNames.isNotEmpty()) {
            val safeIndex = currentFolderIndex.coerceIn(0, folderNames.lastIndex)
            if (folderSpinner.selectedItemPosition != safeIndex) {
                folderSpinner.setSelection(safeIndex, false)
            }
        }
        suppressFolderSpinnerSelection = false
    }

    private fun bindFavoritePadButtons() {
        favoritePadHelper.bind(
            buttons = favoritePadButtons,
            onTapSlot = { index ->
                if (tutorialMode && handleTutorialFavoritePadTap(index)) {
                    return@bind
                }
                if (handleFavoritePadDeleteTap(index)) {
                    return@bind
                }
                val clipId = favoriteSlotClipIds[index]
                if (clipId == null) {
                    reject(
                        SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_ASSIGNED,
                        getString(R.string.soundboard_reject_not_assigned, index + 1)
                    )
                } else {
                    val clip = clipById[clipId]
                    if (clip == null) {
                        reject(
                            SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE,
                            getString(R.string.soundboard_reject_missing)
                        )
                    } else {
                        attemptPlayback(clip)
                    }
                }
            },
            onLongPressSlot = { index ->
                if (tutorialMode && handleTutorialFavoritePadTap(index)) {
                    return@bind
                }
                favoritesManager.selectSlot(index)
                selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
                renderAssignmentTarget()
                renderTutorialChrome()
            }
        )
        renderFavoritePadState()
    }

    private fun renderFavoritePadButtons() {
        val items = favoritesManager.buildSlotItems(clipById.mapValues { it.value.displayName })
        favoritePadHelper.renderSlots(
            buttons = favoritePadButtons,
            items = items,
            selectedSlotIndex = selectedAssignmentSlotIndex,
        )
    }

    private fun renderFavoritePadState() {
        renderFavoritePadButtons()
        renderAssignmentTarget()
        renderFavoritePageControls()
        refreshReadyState()
        renderTutorialChrome()
    }

    private fun renderAssignmentTarget() {
        uiRenderer.renderAssignmentTarget(
            currentPageNumber = favoritesManager.currentPageIndex() + 1,
            pageCount = favoritesManager.pageCount(),
            selectedAssignmentSlotIndex = selectedAssignmentSlotIndex,
        )
    }

    private fun renderFavoritePageControls() {
        val tutorialPhase = currentTutorialPhase()
        favoritePageStatusText.text = getString(
            R.string.soundboard_favorite_page_status_value,
            favoritesManager.currentPageIndex() + 1,
            favoritesManager.pageCount(),
        )
        favoritePagePrevButton.isEnabled = favoritesManager.currentPageIndex() > 0
        favoritePageNextButton.isEnabled = favoritesManager.currentPageIndex() < favoritesManager.pageCount() - 1
        favoritePageRemoveButton.isEnabled = favoritesManager.pageCount() > 1
        favoritePageSaveButton.isEnabled = !tutorialMode || tutorialPhase == Screen3TutorialPhase.SAVE_PAGE
        favoritePageLoadButton.isEnabled = favoritesManager.savedPagePresets().isNotEmpty()
        favoritePageLoadButton.isVisible = !tutorialMode || tutorialPhase == Screen3TutorialPhase.SCREEN_COMPLETE
        renderFavoritePageDots()
    }

    private fun showSaveFavoritePageDialog() {
        val suggestedName = getString(
            R.string.soundboard_favorite_page_save_default_name,
            favoritesManager.currentPageIndex() + 1,
        )
        val input = EditText(this).apply {
            hint = suggestedName
            setText(suggestedName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.soundboard_favorite_page_save_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.soundboard_favorite_page_save) { _, _ ->
                val preset = favoritesManager.saveCurrentPagePreset(
                    input.text?.toString()?.trim().orEmpty().ifBlank { suggestedName }
                )
                renderFavoritePadState()
                Toast.makeText(
                    this,
                    getString(R.string.soundboard_favorite_page_saved, preset.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLoadFavoritePageDialog() {
        val presets = favoritesManager.savedPagePresets()
        if (presets.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.soundboard_favorite_page_load_empty),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.soundboard_favorite_page_load_dialog_title)
            .setItems(
                arrayOf(
                    getString(R.string.soundboard_favorite_page_load_item),
                    getString(R.string.soundboard_favorite_page_delete_item),
                )
            ) { _, which ->
                when (which) {
                    0 -> showFavoritePagePresetPicker(
                        title = R.string.soundboard_favorite_page_load_dialog_title,
                        presets = presets,
                        onSelect = { preset ->
                            val loaded = favoritesManager.loadPagePreset(preset.presetId) ?: return@showFavoritePagePresetPicker
                            syncFavoritesFromManager()
                            renderFavoritePadState()
                            Toast.makeText(
                                this,
                                getString(R.string.soundboard_favorite_page_loaded, loaded.name),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    1 -> showFavoritePagePresetPicker(
                        title = R.string.soundboard_favorite_page_delete_dialog_title,
                        presets = presets,
                        onSelect = ::confirmFavoritePagePresetDelete,
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFavoritePagePresetPicker(
        title: Int,
        presets: List<Screen3SettingsStore.SavedFavoritePagePreset>,
        onSelect: (Screen3SettingsStore.SavedFavoritePagePreset) -> Unit,
    ) {
        val labels = presets.map { preset -> preset.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which ->
                presets.getOrNull(which)?.let(onSelect)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmFavoritePagePresetDelete(preset: Screen3SettingsStore.SavedFavoritePagePreset) {
        AlertDialog.Builder(this)
            .setTitle(R.string.soundboard_favorite_page_delete_dialog_title)
            .setMessage(getString(R.string.soundboard_favorite_page_delete_dialog_message, preset.name))
            .setPositiveButton(R.string.soundboard_favorite_page_delete) { _, _ ->
                val deleted = favoritesManager.deletePagePreset(preset.presetId) ?: return@setPositiveButton
                renderFavoritePadState()
                Toast.makeText(
                    this,
                    getString(R.string.soundboard_favorite_page_deleted, deleted.name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderClipBrowser(clips: List<ClipMetadata>) {
        val models = clips.map {
            Screen3ClipBrowserRenderer.ClipButtonModel(
                displayName = it.displayName,
                metaLabel = clipMetaLabel(it),
                playable = it.isPlayable,
                payloadId = it.id
            )
        }
        clipBrowserRenderer.render(
            clips = models,
            onPlay = { clipId -> clipById[clipId]?.let(::attemptPlayback) },
            onAssign = { clipId -> clipById[clipId]?.let(::handleClipAssignRequest) }
        )
        renderTutorialChrome()
    }

    private fun renderFavoritePageDots() {
        favoritePageDotsContainer.removeAllViews()
        repeat(favoritesManager.pageCount()) { index ->
            favoritePageDotsContainer.addView(
                View(this).apply {
                    background = getDrawable(
                        if (index == favoritesManager.currentPageIndex()) {
                            R.drawable.bg_screen3_page_dot_secondary
                        } else {
                            R.drawable.bg_screen3_page_dot_inactive
                        }
                    )
                },
                LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    if (index > 0) {
                        marginStart = dp(6)
                    }
                }
            )
        }
    }

    private fun clipMetaLabel(clip: ClipMetadata): String {
        val extension = clip.displayName.substringAfterLast('.', "wav").uppercase(Locale.US)
        val durationSeconds = String.format(Locale.US, "%.1fs", clip.durationMs / 1000f)
        return "$extension / $durationSeconds"
    }

    private fun shouldRunTutorial(): Boolean {
        val progress = tutorialStore.loadProgress()
        return progress.status == TutorialStatus.IN_PROGRESS && progress.currentScreen == TutorialScreen.SCREEN3
    }

    private fun handleClipAssignRequest(clip: ClipMetadata) {
        if (tutorialMode) {
            val expectedAssignment = currentTutorialAssignment() ?: return
            if (currentTutorialPhase() == Screen3TutorialPhase.ASSIGN_CLIP &&
                clipMatchesTutorialAssignment(clip, expectedAssignment)
            ) {
                assignFavoriteClip(expectedAssignment.slotIndex, clip)
                onTutorialClipAssigned()
                return
            }
        }

        showAssignClipDialog(clip)
    }

    private fun handleTutorialFavoritePadTap(slotIndex: Int): Boolean {
        if (!tutorialMode || currentTutorialPhase() != Screen3TutorialPhase.SELECT_SLOT) {
            return false
        }

        val expectedAssignment = currentTutorialAssignment() ?: return true
        if (slotIndex != expectedAssignment.slotIndex) {
            return true
        }

        favoritesManager.selectSlot(slotIndex)
        selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
        renderFavoritePadState()
        tutorialStore.setCurrentScreen(TutorialScreen.SCREEN3, STEP_ASSIGN_CLIP)
        renderTutorialChrome()
        return true
    }

    private fun onTutorialClipAssigned() {
        val completedCount = completedTutorialAssignmentCount()
        if (completedCount >= tutorialAssignments.size) {
            tutorialStore.setCurrentScreen(TutorialScreen.SCREEN3, STEP_SAVE_PAGE)
        } else {
            tutorialStore.setCurrentScreen(TutorialScreen.SCREEN3, STEP_SELECT_SLOT)
        }
        renderTutorialChrome()
    }

    private fun currentTutorialPhase(): Screen3TutorialPhase {
        if (!tutorialMode) return Screen3TutorialPhase.INACTIVE
        if (completedTutorialAssignmentCount() >= tutorialAssignments.size) {
            return when (tutorialStore.loadProgress().currentStepIndex) {
                STEP_SCREEN_COMPLETE -> Screen3TutorialPhase.SCREEN_COMPLETE
                else -> Screen3TutorialPhase.SAVE_PAGE
            }
        }
        return when (tutorialStore.loadProgress().currentStepIndex) {
            STEP_ASSIGN_CLIP -> {
                if (currentTutorialAssignment()?.let(::tutorialClipForAssignment) == null) {
                    Screen3TutorialPhase.WAITING_FOR_CLIP
                } else {
                    Screen3TutorialPhase.ASSIGN_CLIP
                }
            }
            STEP_SCREEN_COMPLETE -> Screen3TutorialPhase.SCREEN_COMPLETE
            else -> Screen3TutorialPhase.SELECT_SLOT
        }
    }

    private fun currentTutorialAssignment(): Screen3TutorialAssignment? {
        val completedCount = completedTutorialAssignmentCount()
        return tutorialAssignments.getOrNull(completedCount)
    }

    private fun completedTutorialAssignmentCount(): Int {
        var count = 0
        tutorialAssignments.forEach { assignment ->
            val clipId = favoriteSlotClipIds[assignment.slotIndex] ?: return count
            val clip = clipById[clipId] ?: return count
            if (!clipMatchesTutorialAssignment(clip, assignment)) {
                return count
            }
            count += 1
        }
        return count
    }

    private fun clipMatchesTutorialAssignment(
        clip: ClipMetadata,
        assignment: Screen3TutorialAssignment,
    ): Boolean {
        return clip.displayName.startsWith(assignment.clipBaseName, ignoreCase = true)
    }

    private fun tutorialClipForAssignment(assignment: Screen3TutorialAssignment): ClipMetadata? {
        return activeFolderClips.firstOrNull { clipMatchesTutorialAssignment(it, assignment) }
    }

    private fun renderTutorialChrome() {
        if (!::tutorialCard.isInitialized) return
        if (!tutorialMode) {
            tutorialCard.isVisible = false
            tutorialHandoffCard.isVisible = false
            tutorialOverlay.clearTargets()
            return
        }

        val phase = currentTutorialPhase()
        ensureTutorialRecoveryUiVisible(phase)
        val assignment = currentTutorialAssignment()
        val visibleProgress = (completedTutorialAssignmentCount() + 1).coerceAtMost(tutorialAssignments.size)
        tutorialCard.isVisible = phase != Screen3TutorialPhase.SCREEN_COMPLETE
        tutorialHandoffCard.isVisible = phase == Screen3TutorialPhase.SCREEN_COMPLETE

        if (phase != Screen3TutorialPhase.SCREEN_COMPLETE) {
            tutorialProgressText.text = getString(
                R.string.screen3_tutorial_progress,
                if (phase == Screen3TutorialPhase.SAVE_PAGE) tutorialAssignments.size else visibleProgress,
                tutorialAssignments.size,
            )
            tutorialPromptText.text = when (phase) {
                Screen3TutorialPhase.SELECT_SLOT ->
                    assignment?.let {
                        getString(R.string.screen3_tutorial_select_pad_title, it.slotIndex + 1)
                    }.orEmpty()
                Screen3TutorialPhase.ASSIGN_CLIP -> {
                    val clip = assignment?.let(::tutorialClipForAssignment)
                    getString(
                        R.string.screen3_tutorial_assign_clip_title,
                        clip?.displayName ?: "${assignment?.clipBaseName}.wav",
                    )
                }
                Screen3TutorialPhase.WAITING_FOR_CLIP ->
                    getString(R.string.screen3_tutorial_clip_missing_title, "${assignment?.clipBaseName}.wav")
                Screen3TutorialPhase.SAVE_PAGE ->
                    getString(R.string.screen3_tutorial_save_title)
                else -> ""
            }
            tutorialInstructionText.text = when (phase) {
                Screen3TutorialPhase.SELECT_SLOT ->
                    assignment?.let {
                        getString(R.string.screen3_tutorial_select_pad_instruction, it.slotIndex + 1)
                    }.orEmpty()
                Screen3TutorialPhase.ASSIGN_CLIP -> {
                    val clip = assignment?.let(::tutorialClipForAssignment)
                    getString(
                        R.string.screen3_tutorial_assign_clip_instruction,
                        clip?.displayName ?: "${assignment?.clipBaseName}.wav",
                        (assignment?.slotIndex ?: 0) + 1,
                    )
                }
                Screen3TutorialPhase.WAITING_FOR_CLIP ->
                    getString(R.string.screen3_tutorial_clip_missing_instruction)
                Screen3TutorialPhase.SAVE_PAGE ->
                    getString(R.string.screen3_tutorial_save_instruction)
                else -> ""
            }
        }

        renderTutorialCardEmphasis(phase)
        renderTutorialSpotlight(phase, assignment)
    }

    private fun ensureTutorialRecoveryUiVisible(phase: Screen3TutorialPhase) {
        var updated = false
        val viewState = screen3Coordinator.currentViewState()
        if ((phase == Screen3TutorialPhase.ASSIGN_CLIP || phase == Screen3TutorialPhase.WAITING_FOR_CLIP) &&
            viewState.isBrowserCollapsed
        ) {
            screen3Coordinator.dispatch(Screen3Intent.SetBrowserCollapsed(false))
            updated = true
        }
        if (phase == Screen3TutorialPhase.WAITING_FOR_CLIP && viewState.isControlsCollapsed) {
            screen3Coordinator.dispatch(Screen3Intent.SetControlsCollapsed(false))
            updated = true
        }
        if (updated) {
            updateSectionVisibility()
        }
    }

    private fun renderTutorialCardEmphasis(phase: Screen3TutorialPhase) {
        val emphasized = tutorialMode && phase != Screen3TutorialPhase.SCREEN_COMPLETE
        tutorialCard.animate()
            .scaleX(if (emphasized) 1.04f else 1f)
            .scaleY(if (emphasized) 1.04f else 1f)
            .setDuration(180L)
            .start()
    }

    private fun renderTutorialSpotlight(
        phase: Screen3TutorialPhase,
        assignment: Screen3TutorialAssignment?,
    ) {
        if (!tutorialMode) {
            tutorialOverlay.clearTargets()
            return
        }

        val targets = tutorialSpotlightTargets(phase, assignment)
        if (targets.isEmpty()) {
            tutorialOverlay.clearTargets()
            return
        }

        val primaryTarget = tutorialPrimaryTarget(phase, assignment) ?: targets.first()
        tutorialOverlay.post {
            scrollTutorialTargetIntoView(primaryTarget)
            tutorialOverlay.post {
                tutorialOverlay.showTargets(targets)
            }
        }
    }

    private fun tutorialSpotlightTargets(
        phase: Screen3TutorialPhase,
        assignment: Screen3TutorialAssignment?,
    ): List<View> {
        return when (phase) {
            Screen3TutorialPhase.SELECT_SLOT -> {
                val targetPad = assignment?.let { favoritePadButtons.getOrNull(it.slotIndex) }
                listOfNotNull(tutorialCard, targetPad)
            }
            Screen3TutorialPhase.ASSIGN_CLIP -> {
                val targetClip = assignment?.let(::tutorialClipForAssignment)
                listOfNotNull(
                    tutorialCard,
                    targetClip?.let { clipBrowserRenderer.clipCardViewFor(it.id) },
                    targetClip?.let { clipBrowserRenderer.clipAssignButtonViewFor(it.id) },
                )
            }
            Screen3TutorialPhase.WAITING_FOR_CLIP -> listOfNotNull(
                tutorialCard,
                soundboardBrowserHeaderRow(),
                selectFolderButton,
                rescanLibraryButton,
            )
            Screen3TutorialPhase.SAVE_PAGE -> listOfNotNull(tutorialCard, favoritePageSaveButton)
            Screen3TutorialPhase.SCREEN_COMPLETE -> listOfNotNull(
                tutorialHandoffCard,
                favoritePageLoadButton,
                continueTutorialButton,
                closeTutorialButton,
            )
            Screen3TutorialPhase.INACTIVE -> emptyList()
        }.filter { it.isShown }
    }

    private fun tutorialPrimaryTarget(
        phase: Screen3TutorialPhase,
        assignment: Screen3TutorialAssignment?,
    ): View? {
        return when (phase) {
            Screen3TutorialPhase.SELECT_SLOT -> assignment?.let { favoritePadButtons.getOrNull(it.slotIndex) }
            Screen3TutorialPhase.ASSIGN_CLIP -> assignment
                ?.let(::tutorialClipForAssignment)
                ?.let { clipBrowserRenderer.clipAssignButtonViewFor(it.id) }
            Screen3TutorialPhase.WAITING_FOR_CLIP -> selectFolderButton
            Screen3TutorialPhase.SAVE_PAGE -> favoritePageSaveButton
            Screen3TutorialPhase.SCREEN_COMPLETE -> continueTutorialButton
            Screen3TutorialPhase.INACTIVE -> null
        }
    }

    private fun scrollTutorialTargetIntoView(target: View) {
        val focusRect = Rect(0, -resources.displayMetrics.density.times(16).toInt(), target.width, target.height)
        target.requestRectangleOnScreen(focusRect, true)
    }

    private fun closeTutorialAndKeepExploring() {
        tutorialStore.skipTutorial()
        tutorialMode = false
        renderTutorialChrome()
    }

    private fun handleFavoritePadDeleteTap(slotIndex: Int): Boolean {
        if (!favoritePadDeleteMode) return false
        if (favoriteSlotClipIds[slotIndex] == null) return true

        favoritePadDeleteMode = false
        favoritesManager.selectSlot(slotIndex)
        selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
        clearSelectedAssignmentSlot()
        renderFavoritePadDeleteToggle()
        return true
    }

    private fun toggleFavoritePadDeleteMode() {
        if (!canUseFavoritePadDeleteMode()) return
        favoritePadDeleteMode = !favoritePadDeleteMode
        renderFavoritePadDeleteToggle()
        Toast.makeText(
            this,
            getString(
                if (favoritePadDeleteMode) {
                    R.string.soundboard_favorite_pad_delete_mode_on
                } else {
                    R.string.soundboard_favorite_pad_delete_mode_off
                }
            ),
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun renderFavoritePadDeleteToggle() {
        if (!::favoritesToggleButton.isInitialized) return
        if (!canUseFavoritePadDeleteMode() && favoritePadDeleteMode) {
            favoritePadDeleteMode = false
        }
        favoritesToggleButton.isEnabled = canUseFavoritePadDeleteMode()
        favoritesToggleButton.text = getString(
            if (favoritePadDeleteMode) {
                R.string.soundboard_favorite_pad_delete_mode_on_button
            } else {
                R.string.soundboard_favorite_pad_delete_mode_off_button
            }
        )
        favoritesToggleButton.setTextColor(
            getColor(
                if (favoritePadDeleteMode) {
                    R.color.screen4_error_dim
                } else {
                    R.color.app_text_primary
                }
            )
        )
        favoritesToggleButton.alpha = if (favoritesToggleButton.isEnabled) 1f else 0.55f
    }

    private fun canUseFavoritePadDeleteMode(): Boolean {
        return !tutorialMode || currentTutorialPhase() == Screen3TutorialPhase.SCREEN_COMPLETE
    }

    private fun prepareTutorialFavoritePadIfNeeded() {
        if (!tutorialMode) return
        if (tutorialStore.loadProgress().currentStepIndex != STEP_SELECT_SLOT) return
        if (favoritesManager.exportAssignments().isEmpty()) return
        favoritesManager.clearCurrentPageAssignments()
        favoritesManager.selectSlot(0)
        selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
        syncFavoritesFromManager()
    }

    private fun saveTutorialFavoritePage() {
        val preset = favoritesManager.saveCurrentPagePreset(getString(R.string.screen3_tutorial_saved_page_name))
        renderFavoritePadState()
        Toast.makeText(
            this,
            getString(R.string.soundboard_favorite_page_saved, preset.name),
            Toast.LENGTH_SHORT,
        ).show()
        tutorialStore.setCurrentScreen(TutorialScreen.SCREEN3, STEP_SCREEN_COMPLETE)
        renderTutorialChrome()
    }

    private fun continueToSequencer() {
        tutorialStore.markScreenCompleted(TutorialScreen.SCREEN3, TutorialScreen.SCREEN4)
        startActivity(Intent(this, Screen4Activity::class.java))
    }

    private fun soundboardBrowserHeaderRow(): View = findViewById(R.id.soundboardBrowserHeaderRow)

    private fun soundboardBrowserContainer(): View = findViewById(R.id.soundboardBrowserCard)

    private fun showAssignClipDialog(clip: ClipMetadata) {
        val labels = (1..FAVORITE_SLOT_COUNT).map { slotNumber ->
            val slotIndex = slotNumber - 1
            val existing = favoriteSlotClipIds[slotIndex]?.let { clipById[it]?.displayName }
            val base = if (existing == null) {
                getString(R.string.soundboard_assign_slot_empty, slotNumber)
            } else {
                getString(R.string.soundboard_assign_slot_filled, slotNumber, existing)
            }
            if (slotIndex == selectedAssignmentSlotIndex) "$base ✓" else base
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.soundboard_assign_dialog_title, clip.displayName))
            .setItems(labels) { _, which -> assignFavoriteClip(which, clip) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun assignFavoriteClip(slotIndex: Int, clip: ClipMetadata) {
        favoritesManager.assignClip(slotIndex, clip.id)
        selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
        syncFavoritesFromManager()
        clipCacheManager.pinClip(clip.id)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.SETTINGS_APPLY)
        renderFavoritePadState()
    }

    private fun clearSelectedAssignmentSlot() {
        favoritesManager.selectSlot(selectedAssignmentSlotIndex)
        favoritesManager.clearSelectedSlot()
        selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
        syncFavoritesFromManager()
        Toast.makeText(
            this,
            getString(R.string.soundboard_assignment_cleared, selectedAssignmentSlotIndex + 1),
            Toast.LENGTH_SHORT
        ).show()
        renderFavoritePadState()
    }

    private fun showClearSlotDialog() {
        val labels = (1..FAVORITE_SLOT_COUNT).map { slotNumber ->
            val slotIndex = slotNumber - 1
            val existing = favoriteSlotClipIds[slotIndex]?.let { clipById[it]?.displayName }
            if (existing == null) {
                getString(R.string.soundboard_assign_slot_empty, slotNumber)
            } else {
                getString(R.string.soundboard_assign_slot_filled, slotNumber, existing)
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.soundboard_clear_dialog_title)
            .setItems(labels) { _, which ->
                favoritesManager.selectSlot(which)
                selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
                renderAssignmentTarget()
                clearSelectedAssignmentSlot()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun attemptPlayback(clip: ClipMetadata) {
        val now = System.currentTimeMillis()
        when (val decision = playbackPolicy.evaluate(
            Screen3PlaybackPolicy.Input(
                clipId = clip.id,
                isPlayable = clip.isPlayable,
                nowMs = now,
                lastPlayedAtMs = lastPlayByClipIdMs[clip.id],
                activeStreams = activeStreamIds.size,
                config = config
            )
        )) {
            Screen3PlaybackPolicy.Decision.Accept -> Unit
            is Screen3PlaybackPolicy.Decision.Reject -> {
                val message = when (decision.reason) {
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE ->
                        getString(R.string.soundboard_state_error_missing, clip.displayName)

                    SoundboardStateMachine.PlaybackRejectionReason.COOLDOWN_ACTIVE ->
                        getString(R.string.soundboard_reject_cooldown, config.cooldownMs.toInt())

                    SoundboardStateMachine.PlaybackRejectionReason.MAX_STREAMS_REACHED ->
                        getString(R.string.soundboard_reject_max_streams, config.maxStreams)

                    else -> getString(R.string.soundboard_reject_engine)
                }
                reject(decision.reason, message)
                return
            }
        }

        ensureClipLoaded(clip) { loaded ->
            if (!loaded) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_LOAD_FAILED,
                    getString(R.string.soundboard_reject_load_failed, clip.displayName)
                )
                return@ensureClipLoaded
            }

            val soundId = clipCacheManager.getSoundId(clip.id)
            if (soundId == null) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_LOAD_FAILED,
                    getString(R.string.soundboard_reject_load_failed, clip.displayName)
                )
                return@ensureClipLoaded
            }

            val played = audioEngine.playClipUri(this, clip.uri)
            if (!played) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.ENGINE_ERROR,
                    getString(R.string.soundboard_reject_engine)
                )
                return@ensureClipLoaded
            }

            val pseudoStreamId = System.currentTimeMillis().toInt()
            activeStreamIds += pseudoStreamId
            lastPlayByClipIdMs[clip.id] = System.currentTimeMillis()
            clipCacheManager.markClipUsed(clip.id)
            stateMachine.onPlayAccepted(
                fileName = clip.displayName,
                activeStreams = activeStreamIds.size,
                cacheState = cacheState(),
                constraintSnapshot = constraintSnapshot(),
                clipLoadSnapshot = clipLoadSnapshot(),
                lastRejection = lastRejectionEvent
            )
            renderState(stateMachine.currentState())

            mainHandler.postDelayed({
                activeStreamIds.remove(pseudoStreamId)
                refreshReadyState()
            }, clip.durationMs + STREAM_RELEASE_PADDING_MS)
        }
    }

    private fun reject(reason: SoundboardStateMachine.PlaybackRejectionReason, message: String) {
        rejectionCounts[reason] = (rejectionCounts[reason] ?: 0) + 1
        lastRejectionEvent = SoundboardStateMachine.LastRejection(
            reason = reason.name,
            detail = message,
            atEpochMs = System.currentTimeMillis()
        )
        stateMachine.onRejected(reason, message, rejectionCounters(), lastRejectionEvent)
        renderState(stateMachine.currentState())
    }

    private fun ensureClipLoaded(clip: ClipMetadata, onResult: (Boolean) -> Unit) {
        clipCacheManager.ensureClipLoaded(
            request = Screen3ClipCacheManager.ClipLoadRequest(
                clipId = clip.id,
                clipUri = clip.uri,
                pinned = clip.id in pinnedClipIds()
            ),
            onResult = onResult
        )
        trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.MEMORY_PRESSURE)
    }

    private fun trimClipCache(activeFolderClipIds: Set<String>, reason: Screen3ClipCacheManager.TrimReason) {
        clipCacheManager.trimCache(
            activeFolderClipIds = activeFolderClipIds,
            pinnedClipIds = pinnedClipIds(),
            reason = reason,
            config = config
        )
    }

    private fun clearCacheAndPending() {
        activeStreamIds.clear()
        if (::clipCacheManager.isInitialized) {
            clipCacheManager.clearAndRelease()
        }
    }

    private fun pinnedClipIds(): Set<String> = favoritesManager.assignedClipIds()

    private fun refreshReadyState() {
        val currentFolderName = folderEntries.getOrNull(currentFolderIndex)?.name
        val favorites = (0 until FAVORITE_SLOT_COUNT).map { slotIndex ->
            val clipId = favoriteSlotClipIds[slotIndex]
            val label = clipId?.let { clipById[it]?.displayName } ?: getString(R.string.soundboard_favorite_slot_empty)
            SoundboardStateMachine.FavoriteSlotAssignment(slotIndex = slotIndex, clipId = clipId, label = label)
        }

        stateMachine.onReady(
            folderName = currentFolderName,
            playableCount = activeFolderClips.count { it.isPlayable },
            activeStreams = activeStreamIds.size,
            cachedCount = clipCacheManager.cachedLoadedCount(),
            favorites = favorites,
            cacheState = cacheState(),
            constraintSnapshot = constraintSnapshot(),
            rejectionCounters = rejectionCounters(),
            clipLoadSnapshot = clipLoadSnapshot(),
            lastRejection = lastRejectionEvent
        )
        renderState(stateMachine.currentState())
    }

    private fun clipLoadSnapshot(): SoundboardStateMachine.ClipLoadSnapshot {
        return clipCacheManager.snapshotClipLoad(activeFolderClips.map { it.id }.toSet())
    }

    private fun cacheState(): SoundboardStateMachine.FolderCacheState {
        return clipCacheManager.snapshotCacheState(
            activeFolder = folderEntries.getOrNull(currentFolderIndex)?.name,
            pinnedClipIds = pinnedClipIds()
        )
    }

    private fun syncFavoritesFromManager() {
        favoriteSlotClipIds.clear()
        favoriteSlotClipIds.putAll(favoritesManager.exportAssignments())
    }

    private fun syncKnownClipsFromIndex() {
        clipIndexRepository.getAllIndexedClips().forEach { indexed ->
            clipById[indexed.clipId] = ClipMetadata(
                id = indexed.clipId,
                displayName = indexed.fileName,
                uri = Uri.parse(indexed.clipUri),
                folderName = indexed.folderName,
                durationMs = indexed.durationMs,
                isPlayable = indexed.playable
            )
        }
    }

    private fun constraintSnapshot(): SoundboardStateMachine.ConstraintSnapshot {
        return SoundboardStateMachine.ConstraintSnapshot(
            maxStreams = config.maxStreams,
            cooldownMs = config.cooldownMs,
            maxCacheSize = config.maxCacheSize,
            cachePolicy = config.cachePolicy.name
        )
    }

    private fun rejectionCounters(): SoundboardStateMachine.RejectionCounters {
        fun count(reason: SoundboardStateMachine.PlaybackRejectionReason) = rejectionCounts[reason] ?: 0
        return SoundboardStateMachine.RejectionCounters(
            cooldownActive = count(SoundboardStateMachine.PlaybackRejectionReason.COOLDOWN_ACTIVE),
            maxStreamsReached = count(SoundboardStateMachine.PlaybackRejectionReason.MAX_STREAMS_REACHED),
            clipLoadFailed = count(SoundboardStateMachine.PlaybackRejectionReason.CLIP_LOAD_FAILED),
            clipNotAssigned = count(SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_ASSIGNED),
            clipNotPlayable = count(SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE),
            engineError = count(SoundboardStateMachine.PlaybackRejectionReason.ENGINE_ERROR)
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun renderState(state: SoundboardStateMachine.State) {
        uiRenderer.renderState(state)
    }

    private fun failToMainMenu(error: Throwable) {
        Log.e(TAG, "Screen3 startup failure", error)
        Toast.makeText(this, getString(R.string.screen3_startup_failed), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private data class Screen3TutorialAssignment(
        val slotIndex: Int,
        val spokenPrompt: String,
        val clipBaseName: String,
    )

    private enum class Screen3TutorialPhase {
        INACTIVE,
        SELECT_SLOT,
        ASSIGN_CLIP,
        WAITING_FOR_CLIP,
        SAVE_PAGE,
        SCREEN_COMPLETE,
    }

    private data class FolderEntry(val name: String)
    private data class ClipMetadata(
        val id: String,
        val displayName: String,
        val uri: Uri,
        val folderName: String,
        val durationMs: Long,
        val isPlayable: Boolean
    )
    companion object {
        const val EXTRA_FORCE_REBUILD_ON_START = "com.example.templei.screen3.extra.FORCE_REBUILD_ON_START"
        const val EXTRA_TUTORIAL_ROOT_URI = "com.example.templei.screen3.extra.TUTORIAL_ROOT_URI"

        private const val TAG = "Screen3Soundboard"
        private const val MAX_SOUND_DURATION_MS = 6_000L

        private const val FAVORITE_SLOT_COUNT = 9
        private const val STREAM_RELEASE_PADDING_MS = 120L

        private const val DEFAULT_MAX_STREAMS = 4
        private const val DEFAULT_COOLDOWN_MS = 120L
        private const val DEFAULT_MAX_CACHE_SIZE = 24
        private const val DEFAULT_UNLOAD_ON_FOLDER_CHANGE = true
        private val DEFAULT_CACHE_POLICY = CachePolicy.BALANCED
        private const val STEP_SELECT_SLOT = 0
        private const val STEP_ASSIGN_CLIP = 1
        private const val STEP_SAVE_PAGE = 2
        private const val STEP_SCREEN_COMPLETE = 3

    }
}
