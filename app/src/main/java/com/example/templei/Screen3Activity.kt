package com.example.templei

import android.content.Intent
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
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
import com.example.templei.ui.navigation.TopNavigation
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
    private lateinit var clipBrowserContainer: LinearLayout
    private lateinit var clipBrowserScroll: ScrollView
    private lateinit var favoritesPad: android.widget.GridLayout
    private lateinit var browserToggleButton: Button
    private lateinit var favoritesToggleButton: Button

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
    private var isBrowserCollapsed: Boolean = true
    private var isFavoritesCollapsed: Boolean = true
    private var isControlsCollapsed: Boolean = true
    private val clipIndexRepository by lazy { ClipIndexRepository(this) }
    private lateinit var uiRenderer: Screen3UiRenderer
    private val settingsStore by lazy { Screen3SettingsStore(this) }
    private val settingsDialogHelper by lazy { Screen3SettingsDialogHelper(this) }
    private val playbackPolicy = Screen3PlaybackPolicy()
    private lateinit var clipCacheManager: Screen3ClipCacheManager
    private lateinit var favoritesManager: Screen3FavoritesManager
    private val folderBrowserCoordinator by lazy { Screen3FolderBrowserCoordinator(clipIndexRepository) }
    private lateinit var clipBrowserRenderer: Screen3ClipBrowserRenderer
    private val favoritePadHelper = Screen3FavoritePadHelper()
    private lateinit var favoritePadButtons: List<Button>
    private lateinit var controlsGroup: LinearLayout
    private lateinit var controlsToggleButton: Button
    private lateinit var folderSpinnerAdapter: ArrayAdapter<String>
    private var suppressFolderSpinnerSelection: Boolean = false

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
            TopNavigation.bind(activity = this, currentDestination = Screen3Activity::class.java)

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
            clipBrowserContainer = findViewById(R.id.soundboardClipBrowserContainer)
            clipBrowserScroll = findViewById(R.id.soundboardClipBrowserScroll)
            favoritesPad = findViewById(R.id.soundboardFavoritesPad)
            browserToggleButton = findViewById(R.id.soundboardBrowserToggleButton)
            favoritesToggleButton = findViewById(R.id.soundboardFavoritesToggleButton)
            controlsGroup = findViewById(R.id.soundboardControlsGroup)
            controlsToggleButton = findViewById(R.id.soundboardControlsToggleButton)
            favoritePadButtons = favoritePadButtonIds.map { findViewById(it) }

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
                savedUnknownLabelForSlot = { slotNumber -> getString(R.string.soundboard_favorite_slot_label_saved, slotNumber) }
            )
            favoritesManager.initialize()
            selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
            syncFavoritesFromManager()

            folderSpinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf<String>())
            folderSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            folderSpinner.adapter = folderSpinnerAdapter

            previousFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    applyIndexedState(folderBrowserCoordinator.movePreviousFolder())
                }
            }

            nextFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    applyIndexedState(folderBrowserCoordinator.moveNextFolder())
                }
            }

            folderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (suppressFolderSpinnerSelection || folderEntries.isEmpty()) return
                    if (position == currentFolderIndex) return
                    applyIndexedState(folderBrowserCoordinator.selectFolder(position))
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
            clearSelectedSlotButton.setOnClickListener { showClearSlotDialog() }
            browserToggleButton.setOnClickListener {
                isBrowserCollapsed = !isBrowserCollapsed
                updateSectionVisibility()
            }
            favoritesToggleButton.setOnClickListener {
                isFavoritesCollapsed = !isFavoritesCollapsed
                updateSectionVisibility()
            }
            controlsToggleButton.setOnClickListener {
                isControlsCollapsed = !isControlsCollapsed
                updateSectionVisibility()
            }

            bindFavoritePadButtons()
            updateSectionVisibility()
            initializeFromPersistedIndexOrNoRoot()
        }.onFailure(::failToMainMenu)
    }

    override fun onResume() {
        super.onResume()
        updateSectionVisibility()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCacheAndPending()
        audioEngine.release()
        soundPool.release()
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
        if (!indexedState.hasRootSelection) {
            renderNoRootSelected()
            return
        }
        applyIndexedState(indexedState)
    }

    private fun rebuildIndexAndBind(rootUri: Uri) {
        stateMachine.onLoading()
        renderState(stateMachine.currentState())
        loadingProgressBar.isIndeterminate = true
        loadingProgressBar.progress = 0
        loadingDetailText.text = getString(R.string.soundboard_loading_detail_loading)

        Thread {
            val summary = folderBrowserCoordinator.setPickedRootAndRebuild(rootUri)
            mainHandler.post {
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

    private fun bindFolderBrowserFromIndex() {
        applyIndexedState(folderBrowserCoordinator.currentState())
    }

    private fun bindCurrentFolderFromIndex() {
        applyIndexedState(folderBrowserCoordinator.currentState())
    }

    private fun applyIndexedState(indexedState: Screen3FolderBrowserCoordinator.IndexedFolderState) {
        folderEntries = indexedState.folderNames.map { FolderEntry(it) }
        currentFolderIndex = indexedState.selectedFolderIndex.coerceAtMost(max(0, folderEntries.size - 1))
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
        activeFolderClips.forEach { clipById[it.id] = it }
        renderClipBrowser(activeFolderClips)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), Screen3ClipCacheManager.TrimReason.FOLDER_SWITCH)
        refreshReadyState()
        renderFavoritePadButtons()
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
        uiRenderer.renderSectionVisibility(isBrowserCollapsed = isBrowserCollapsed, isFavoritesCollapsed = isFavoritesCollapsed)
        controlsGroup.visibility = if (isControlsCollapsed) android.view.View.GONE else android.view.View.VISIBLE
        controlsToggleButton.text = getString(if (isControlsCollapsed) R.string.soundboard_section_expand else R.string.soundboard_section_collapse)
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
                favoritesManager.selectSlot(index)
                selectedAssignmentSlotIndex = favoritesManager.selectedSlotIndex()
                renderAssignmentTarget()
            }
        )
        renderFavoritePadButtons()
        renderAssignmentTarget()
    }

    private fun renderFavoritePadButtons() {
        val labels = favoritesManager
            .buildSlotItems(clipById.mapValues { it.value.displayName })
            .map { it.label }
        favoritePadHelper.renderLabels(favoritePadButtons, labels)
    }

    private fun renderAssignmentTarget() {
        uiRenderer.renderAssignmentTarget(selectedAssignmentSlotIndex)
    }

    private fun renderClipBrowser(clips: List<ClipMetadata>) {
        val models = clips.map {
            Screen3ClipBrowserRenderer.ClipButtonModel(
                displayName = it.displayName,
                playable = it.isPlayable,
                payloadId = it.id
            )
        }
        clipBrowserRenderer.render(
            clips = models,
            onTap = { clipId -> clipById[clipId]?.let(::attemptPlayback) },
            onLongPress = { clipId -> clipById[clipId]?.let(::showAssignClipDialog) }
        )
    }

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
        renderFavoritePadButtons()
        renderAssignmentTarget()
        refreshReadyState()
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
        renderFavoritePadButtons()
        refreshReadyState()
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

    private fun pinnedClipIds(): Set<String> = favoriteSlotClipIds.values.toSet()

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

    private fun renderState(state: SoundboardStateMachine.State) {
        uiRenderer.renderState(state)
    }

    private fun failToMainMenu(error: Throwable) {
        Log.e(TAG, "Screen3 startup failure", error)
        Toast.makeText(this, getString(R.string.screen3_startup_failed), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
    private companion object {
        private const val TAG = "Screen3Soundboard"
        private const val MAX_SOUND_DURATION_MS = 6_000L

        private const val FAVORITE_SLOT_COUNT = 9
        private const val STREAM_RELEASE_PADDING_MS = 120L

        private const val DEFAULT_MAX_STREAMS = 4
        private const val DEFAULT_COOLDOWN_MS = 120L
        private const val DEFAULT_MAX_CACHE_SIZE = 24
        private const val DEFAULT_UNLOAD_ON_FOLDER_CHANGE = true
        private val DEFAULT_CACHE_POLICY = CachePolicy.BALANCED

    }
}
