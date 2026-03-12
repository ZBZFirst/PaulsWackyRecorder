package com.example.templei

import android.app.AlertDialog
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.soundboard.SoundboardStateMachine
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
    private lateinit var folderNameText: TextView
    private lateinit var previousFolderButton: Button
    private lateinit var nextFolderButton: Button
    private lateinit var selectFolderButton: Button
    private lateinit var settingsButton: Button
    private lateinit var clearSelectedSlotButton: Button
    private lateinit var assignmentTargetText: TextView
    private lateinit var clipBrowserContainer: LinearLayout

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

    private val clipCache = linkedMapOf<String, CacheEntry>()
    private val pendingLoadCallbacks = mutableMapOf<Int, MutableList<(Boolean) -> Unit>>()
    private val soundIdToClipId = mutableMapOf<Int, String>()
    private val activeStreamIds = mutableSetOf<Int>()
    private val lastPlayByClipIdMs = mutableMapOf<String, Long>()
    private val rejectionCounts = mutableMapOf<SoundboardStateMachine.PlaybackRejectionReason, Int>()
    private var lastRejectionEvent: SoundboardStateMachine.LastRejection? = null

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            Log.i(TAG, "Folder picked: $uri")
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure {
                Log.w(TAG, "Persistable URI permission failed for $uri", it)
            }
            saveRootFolderUri(uri)
            runCatching { bindFolderBrowser() }.onFailure(::failToMainMenu)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            setContentView(R.layout.activity_screen3)

            statusText = findViewById(R.id.soundboardStatusText)
            loadingDetailText = findViewById(R.id.soundboardLoadingDetailText)
            loadingProgressBar = findViewById(R.id.soundboardLoadingProgressBar)
            folderNameText = findViewById(R.id.soundboardFolderNameText)
            previousFolderButton = findViewById(R.id.soundboardFolderPrevButton)
            nextFolderButton = findViewById(R.id.soundboardFolderNextButton)
            selectFolderButton = findViewById(R.id.soundboardSelectFolderButton)
            settingsButton = findViewById(R.id.soundboardSettingsButton)
            clearSelectedSlotButton = findViewById(R.id.soundboardClearSelectedSlotButton)
            assignmentTargetText = findViewById(R.id.soundboardAssignmentTargetText)
            clipBrowserContainer = findViewById(R.id.soundboardClipBrowserContainer)

            config = loadConfig()
            selectedAssignmentSlotIndex = loadSelectedAssignmentSlotIndex()
            favoriteSlotClipIds.putAll(loadFavoriteAssignments())
            buildSoundPool(config.maxStreams)

            previousFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    currentFolderIndex = (currentFolderIndex - 1 + folderEntries.size) % folderEntries.size
                    bindCurrentFolder()
                }
            }

            nextFolderButton.setOnClickListener {
                if (folderEntries.isNotEmpty()) {
                    currentFolderIndex = (currentFolderIndex + 1) % folderEntries.size
                    bindCurrentFolder()
                }
            }
            previousFolderButton.setOnClickListener { moveFolderSelection(-1) }
            nextFolderButton.setOnClickListener { moveFolderSelection(1) }

            selectFolderButton.setOnClickListener { pickFolderLauncher.launch(savedRootFolderUri()) }
            settingsButton.setOnClickListener { showSettingsDialog() }
            clearSelectedSlotButton.setOnClickListener { clearSelectedAssignmentSlot() }

            bindFavoritePadButtons()
            bindFolderBrowser()
        }.onFailure(::failToMainMenu)
    }

    override fun onResume() {
        super.onResume()
        runCatching { bindFolderBrowser() }
            .onFailure(::failToMainMenu)
    }

    override fun onDestroy() {
        super.onDestroy()
        clearCacheAndPending()
        soundPool.release()
    }

    private fun buildSoundPool(maxStreams: Int) {
        soundPool = SoundPool.Builder().setMaxStreams(maxStreams).build()
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            val clipId = soundIdToClipId[soundId]
            val entry = clipId?.let { clipCache[it] }
            val succeeded = status == 0 && clipId != null && entry != null
            if (succeeded) {
                entry?.state = SoundboardStateMachine.ClipLoadState.LOADED
            } else if (clipId != null) {
                clipCache[clipId]?.state = SoundboardStateMachine.ClipLoadState.FAILED
            }
            pendingLoadCallbacks.remove(soundId).orEmpty().forEach { it(succeeded) }
        }
    }

    private fun rebuildSoundPoolIfNeeded(newConfig: SoundboardConfig) {
        val streamChanged = newConfig.maxStreams != config.maxStreams
        config = newConfig
        saveConfig(config)
        if (streamChanged) {
            clearCacheAndPending()
            soundPool.release()
            buildSoundPool(config.maxStreams)
            refreshReadyState()
        }
    }

    private fun showSettingsDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        fun rowLabel(text: String): TextView = TextView(this).apply { this.text = text }

        val maxStreamsLabel = rowLabel(getString(R.string.soundboard_settings_max_streams, config.maxStreams))
        val maxStreamsSeek = SeekBar(this).apply {
            max = 7
            progress = (config.maxStreams - 1).coerceIn(0, 7)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                maxStreamsLabel.text = getString(R.string.soundboard_settings_max_streams, value + 1)
            })
        }

        val cooldownLabel = rowLabel(getString(R.string.soundboard_settings_cooldown_ms, config.cooldownMs.toInt()))
        val cooldownSeek = SeekBar(this).apply {
            max = 10
            progress = (config.cooldownMs / 50L).toInt().coerceIn(0, 10)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                cooldownLabel.text = getString(R.string.soundboard_settings_cooldown_ms, value * 50)
            })
        }

        val cacheLabel = rowLabel(getString(R.string.soundboard_settings_cache_size, config.maxCacheSize))
        val cacheSeek = SeekBar(this).apply {
            max = 28
            progress = (config.maxCacheSize - 8).coerceIn(0, 28)
            setOnSeekBarChangeListener(simpleSeekListener { _, value ->
                cacheLabel.text = getString(R.string.soundboard_settings_cache_size, value + 8)
            })
        }

        val unloadSwitch = CheckBox(this).apply {
            text = getString(R.string.soundboard_settings_unload_on_folder_change)
            isChecked = config.unloadOnFolderChange
        }

        val cachePolicyLabel = rowLabel(getString(R.string.soundboard_settings_cache_policy))
        val cachePolicyGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val aggressiveRadio = RadioButton(this).apply {
            id = CACHE_POLICY_AGGRESSIVE_ID
            text = getString(R.string.soundboard_settings_cache_policy_aggressive)
        }
        val balancedRadio = RadioButton(this).apply {
            id = CACHE_POLICY_BALANCED_ID
            text = getString(R.string.soundboard_settings_cache_policy_balanced)
        }
        val stickyRadio = RadioButton(this).apply {
            id = CACHE_POLICY_STICKY_ID
            text = getString(R.string.soundboard_settings_cache_policy_sticky)
        }
        cachePolicyGroup.addView(aggressiveRadio)
        cachePolicyGroup.addView(balancedRadio)
        cachePolicyGroup.addView(stickyRadio)
        cachePolicyGroup.check(
            when (config.cachePolicy) {
                CachePolicy.AGGRESSIVE -> CACHE_POLICY_AGGRESSIVE_ID
                CachePolicy.BALANCED -> CACHE_POLICY_BALANCED_ID
                CachePolicy.STICKY -> CACHE_POLICY_STICKY_ID
            }
        )

        root.addView(maxStreamsLabel)
        root.addView(maxStreamsSeek)
        root.addView(cooldownLabel)
        root.addView(cooldownSeek)
        root.addView(cacheLabel)
        root.addView(cacheSeek)
        root.addView(unloadSwitch)
        root.addView(cachePolicyLabel)
        root.addView(cachePolicyGroup)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.soundboard_settings_title))
            .setView(root)
            .setNeutralButton(getString(R.string.soundboard_settings_reset)) { _, _ ->
                rebuildSoundPoolIfNeeded(SoundboardConfig())
                trimClipCache(activeFolderClips.map { it.id }.toSet(), TrimReason.SETTINGS_APPLY)
                refreshReadyState()
            }
            .setPositiveButton(getString(R.string.soundboard_settings_apply)) { _, _ ->
                val policy = when (cachePolicyGroup.checkedRadioButtonId) {
                    CACHE_POLICY_AGGRESSIVE_ID -> CachePolicy.AGGRESSIVE
                    CACHE_POLICY_STICKY_ID -> CachePolicy.STICKY
                    else -> CachePolicy.BALANCED
                }
                val updated = SoundboardConfig(
                    maxStreams = (maxStreamsSeek.progress + 1).coerceIn(1, 8),
                    cooldownMs = (cooldownSeek.progress * 50L).coerceIn(0L, 500L),
                    maxCacheSize = (cacheSeek.progress + 8).coerceIn(8, 36),
                    unloadOnFolderChange = unloadSwitch.isChecked,
                    cachePolicy = policy
                )
                rebuildSoundPoolIfNeeded(updated)
                trimClipCache(activeFolderClips.map { it.id }.toSet(), TrimReason.SETTINGS_APPLY)
                refreshReadyState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun simpleSeekListener(onChange: (SeekBar, Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                onChange(seekBar, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        }
    }

    private fun bindFolderBrowser() {
        val rootUri = savedRootFolderUri()
        if (rootUri == null) {
            renderFolderSelectionRequired()
            return
        }

        val root = DocumentFile.fromTreeUri(this, rootUri)
        if (root == null || !root.canRead()) {
            renderFolderSelectionRequired()
            return
        }

        folderEntries = discoverFolderEntries(root)
        currentFolderIndex = currentFolderIndex.coerceAtMost(max(0, folderEntries.size - 1))
        bindCurrentFolder()
    }

    private fun discoverFolderEntries(rootFolder: DocumentFile): List<FolderEntry> {
        val entries = mutableListOf<FolderEntry>()
        rootFolder.listFiles()
            .filter { it.isDirectory && it.canRead() }
            .sortedBy { it.name ?: "" }
            .forEach { directory ->
                entries += FolderEntry(
                    name = directory.name ?: getString(R.string.soundboard_folder_unknown),
                    folderUri = directory.uri
                )
            }

        if (rootFolder.listFiles().any { it.isFile }) {
            entries.add(0, FolderEntry(getString(R.string.soundboard_folder_current), rootFolder.uri))
        }
        return entries
    }

    private fun bindCurrentFolder() {
        val folder = folderEntries.getOrNull(currentFolderIndex)
        if (folder == null) {
            folderNameText.text = getString(R.string.soundboard_folder_none)
            previousFolderButton.isEnabled = false
            nextFolderButton.isEnabled = false
            activeFolderClips = emptyList()
            renderClipBrowser(emptyList())
            stateMachine.onError(getString(R.string.soundboard_state_error_select_folder), rejectionCounters(), lastRejectionEvent)
            renderState(stateMachine.currentState())
            return
        }
    }

        stateMachine.onLoading()
        renderState(stateMachine.currentState())

        folderNameText.text = folder.name
        previousFolderButton.isEnabled = folderEntries.size > 1
        nextFolderButton.isEnabled = folderEntries.size > 1

        val folderDocument = DocumentFile.fromTreeUri(this, folder.folderUri)
        activeFolderClips = folderDocument?.let { scanFolderClips(it, folder.name) }.orEmpty()
        activeFolderClips.forEach { clipById[it.id] = it }

        renderClipBrowser(activeFolderClips)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), TrimReason.FOLDER_SWITCH)
        refreshReadyState()
        renderFavoritePadButtons()
    }

    private fun scanFolderClips(folder: DocumentFile, folderName: String): List<ClipMetadata> {
        return folder.listFiles().filter { it.isFile }.mapNotNull { file ->
            val fileName = file.name ?: return@mapNotNull null
            if (!isSupportedExtension(fileName)) return@mapNotNull null
            val durationMs = readDurationMs(file.uri) ?: 0L
            val playable = durationMs in 1..MAX_SOUND_DURATION_MS
            ClipMetadata(file.uri.toString(), fileName, file.uri, folderName, durationMs, playable)
        }.sortedBy { it.displayName }
    }

    private fun readDurationMs(uri: Uri): Long? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            duration
        }.getOrNull()
    }

    private fun renderFolderSelectionRequired() {
        folderEntries = emptyList()
        activeFolderClips = emptyList()
        currentFolderIndex = 0

        folderNameText.text = getString(R.string.soundboard_folder_none)
        previousFolderButton.isEnabled = false
        nextFolderButton.isEnabled = false
        renderClipBrowser(emptyList())
        stateMachine.onError(getString(R.string.soundboard_state_error_select_folder), rejectionCounters(), lastRejectionEvent)
        renderState(stateMachine.currentState())
    }

    private fun bindFavoritePadButtons() {
        favoritePadButtonIds.forEachIndexed { index, id ->
            val button = findViewById<Button>(id)
            button.setOnClickListener {
                val clipId = favoriteSlotClipIds[index]
                if (clipId == null) return@setOnClickListener reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_ASSIGNED,
                    getString(R.string.soundboard_reject_not_assigned, index + 1)
                )

                val clip = clipById[clipId]
                if (clip == null) return@setOnClickListener reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE,
                    getString(R.string.soundboard_reject_missing)
                )

                attemptPlayback(clip)
            }

            button.setOnLongClickListener {
                selectedAssignmentSlotIndex = index
                saveSelectedAssignmentSlotIndex(index)
                renderAssignmentTarget()
                true
            }
        }
        renderFavoritePadButtons()
        renderAssignmentTarget()
    }

    private fun renderFavoritePadButtons() {
        favoritePadButtonIds.forEachIndexed { index, id ->
            val button = findViewById<Button>(id)
            val clipId = favoriteSlotClipIds[index]
            val clip = clipId?.let { clipById[it] }
            button.text = when {
                clip == null && clipId == null -> getString(R.string.soundboard_favorite_slot_label_empty, index + 1)
                clip != null -> {
                getString(R.string.soundboard_favorite_slot_label_assigned, index + 1, clip.displayName)
                }
                else -> getString(R.string.soundboard_favorite_slot_label_saved, index + 1)
            }
            clipBrowserContainer.addView(clipButton)
        }
    }

    private fun renderAssignmentTarget() {
        assignmentTargetText.text = getString(R.string.soundboard_assignment_target, selectedAssignmentSlotIndex + 1)
    }

    private fun renderClipBrowser(clips: List<ClipMetadata>) {
        clipBrowserContainer.removeAllViews()
        if (clips.isEmpty()) {
            clipBrowserContainer.addView(TextView(this).apply { text = getString(R.string.soundboard_browser_empty) })
            return
        }

        clips.chunked(3).forEach { rowClips ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 8 }
            }

            repeat(3) { index ->
                val clip = rowClips.getOrNull(index)
                val button = Button(this).apply {
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also {
                        it.marginEnd = if (index < 2) 8 else 0
                    }
                }

                if (clip == null) {
                    button.isEnabled = false
                    button.text = ""
                    button.visibility = Button.INVISIBLE
                } else {
                    button.text = if (clip.isPlayable) {
                        clip.displayName
                    } else {
                        "${clip.displayName} (${getString(R.string.soundboard_unplayable_suffix)})"
                    }
                    button.setOnClickListener { attemptPlayback(clip) }
                    button.setOnLongClickListener {
                        showAssignClipDialog(clip)
                        true
                    }
                }
                row.addView(button)
            }
            clipBrowserContainer.addView(row)
        }
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
        favoriteSlotClipIds[slotIndex] = clip.id
        selectedAssignmentSlotIndex = slotIndex
        pinClip(clip.id)
        saveFavoriteAssignments()
        saveSelectedAssignmentSlotIndex(slotIndex)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), TrimReason.SETTINGS_APPLY)
        renderFavoritePadButtons()
        renderAssignmentTarget()
        refreshReadyState()
    }

    private fun clearSelectedAssignmentSlot() {
        favoriteSlotClipIds.remove(selectedAssignmentSlotIndex)
        saveFavoriteAssignments()
        Toast.makeText(
            this,
            getString(R.string.soundboard_assignment_cleared, selectedAssignmentSlotIndex + 1),
            Toast.LENGTH_SHORT
        ).show()
        renderFavoritePadButtons()
        refreshReadyState()
    }

    private fun attemptPlayback(clip: ClipMetadata) {
        if (!clip.isPlayable) {
            reject(
                SoundboardStateMachine.PlaybackRejectionReason.CLIP_NOT_PLAYABLE,
                getString(R.string.soundboard_state_error_missing, clip.displayName)
            )
            return
        }

        val now = System.currentTimeMillis()
        val lastPlayed = lastPlayByClipIdMs[clip.id] ?: 0L
        if (now - lastPlayed < config.cooldownMs) {
            reject(
                SoundboardStateMachine.PlaybackRejectionReason.COOLDOWN_ACTIVE,
                getString(R.string.soundboard_reject_cooldown, config.cooldownMs.toInt())
            )
            return
        }

        if (activeStreamIds.size >= config.maxStreams) {
            reject(
                SoundboardStateMachine.PlaybackRejectionReason.MAX_STREAMS_REACHED,
                getString(R.string.soundboard_reject_max_streams, config.maxStreams)
            )
            return
        }

        ensureClipLoaded(clip) { loaded ->
            if (!loaded) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_LOAD_FAILED,
                    getString(R.string.soundboard_reject_load_failed, clip.displayName)
                )
                return@ensureClipLoaded
            }

            val soundId = clipCache[clip.id]?.soundId
            if (soundId == null) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.CLIP_LOAD_FAILED,
                    getString(R.string.soundboard_reject_load_failed, clip.displayName)
                )
                return@ensureClipLoaded
            }

            val streamId = soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            if (streamId == 0) {
                reject(
                    SoundboardStateMachine.PlaybackRejectionReason.ENGINE_ERROR,
                    getString(R.string.soundboard_reject_engine)
                )
                return@ensureClipLoaded
            }

            activeStreamIds += streamId
            lastPlayByClipIdMs[clip.id] = System.currentTimeMillis()
            clipCache[clip.id]?.lastUsedMs = System.currentTimeMillis()
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
                activeStreamIds.remove(streamId)
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
        val existing = clipCache[clip.id]
        when (existing?.state) {
            SoundboardStateMachine.ClipLoadState.LOADED -> {
                existing.lastUsedMs = System.currentTimeMillis()
                onResult(true)
                return
            }

            SoundboardStateMachine.ClipLoadState.LOADING -> {
                val soundId = existing.soundId ?: run {
                    onResult(false)
                    return
                }
                pendingLoadCallbacks.getOrPut(soundId) { mutableListOf() }.add(onResult)
                return
            }

            SoundboardStateMachine.ClipLoadState.FAILED -> {
                onResult(false)
                return
            }

            else -> Unit
        }

        val soundId = runCatching {
            contentResolver.openAssetFileDescriptor(clip.uri, "r")?.use { afd ->
                soundPool.load(afd, 1)
            }
        }.getOrNull() ?: 0

        if (soundId == 0) {
            onResult(false)
            return
        }

        clipCache[clip.id] = CacheEntry(
            soundId = soundId,
            state = SoundboardStateMachine.ClipLoadState.LOADING,
            lastUsedMs = System.currentTimeMillis(),
            pinned = isClipPinned(clip.id)
        )
        soundIdToClipId[soundId] = clip.id
        pendingLoadCallbacks.getOrPut(soundId) { mutableListOf() }.add(onResult)
        trimClipCache(activeFolderClips.map { it.id }.toSet(), TrimReason.MEMORY_PRESSURE)
    }

    private fun trimClipCache(activeFolderClipIds: Set<String>, reason: TrimReason) {
        val pinned = pinnedClipIds()
        clipCache.keys.toList().forEach { clipId -> clipCache[clipId]?.pinned = clipId in pinned }

        val nonPinnedLoaded = clipCache
            .filter { (_, entry) -> !entry.pinned && entry.state != SoundboardStateMachine.ClipLoadState.LOADING }
            .toList()
            .sortedBy { (_, entry) -> entry.lastUsedMs }

        val toRemove = linkedMapOf<String, CacheEntry>()

        if (reason == TrimReason.FOLDER_SWITCH && config.unloadOnFolderChange) {
            when (config.cachePolicy) {
                CachePolicy.AGGRESSIVE,
                CachePolicy.BALANCED -> {
                    nonPinnedLoaded
                        .filter { (clipId, _) -> clipId !in activeFolderClipIds }
                        .forEach { (clipId, entry) -> toRemove[clipId] = entry }
                }

                CachePolicy.STICKY -> Unit
            }
        }

        if (config.cachePolicy == CachePolicy.AGGRESSIVE && reason == TrimReason.MEMORY_PRESSURE) {
            nonPinnedLoaded
                .filter { (clipId, _) -> clipId !in activeFolderClipIds }
                .forEach { (clipId, entry) -> toRemove[clipId] = entry }
        }

        val projectedSize = clipCache.size - toRemove.size
        val overflow = (projectedSize - config.maxCacheSize).coerceAtLeast(0)
        if (overflow > 0) {
            nonPinnedLoaded
                .filter { (clipId, _) -> clipId !in toRemove }
                .take(overflow)
                .forEach { (clipId, entry) -> toRemove[clipId] = entry }
        }

        toRemove.forEach { (clipId, entry) ->
            entry.soundId?.let { soundPool.unload(it) }
            clipCache.remove(clipId)
        }
    }

    private fun clearCacheAndPending() {
        activeStreamIds.clear()
        pendingLoadCallbacks.clear()
        clipCache.values.forEach { it.soundId?.let(soundPool::unload) }
        clipCache.clear()
        soundIdToClipId.clear()
    }

    private fun pinClip(clipId: String) {
        clipCache[clipId]?.pinned = true
    }

    private fun isClipPinned(clipId: String): Boolean = clipId in pinnedClipIds()

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
            cachedCount = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADED },
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
        val loading = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADING }
        val loaded = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADED }
        val failed = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.FAILED }
        val activeKnown = activeFolderClips.count { it.id in clipCache }
        val unloaded = (activeFolderClips.size - activeKnown).coerceAtLeast(0)
        return SoundboardStateMachine.ClipLoadSnapshot(
            unloaded = unloaded,
            loading = loading,
            loaded = loaded,
            failed = failed
        )
    }

    private fun cacheState(): SoundboardStateMachine.FolderCacheState {
        return SoundboardStateMachine.FolderCacheState(
            activeFolder = folderEntries.getOrNull(currentFolderIndex)?.name,
            cachedClipIds = clipCache.keys,
            pinnedClipIds = pinnedClipIds()
        )
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
        statusText.text = when (state) {
            SoundboardStateMachine.State.Loading -> getString(R.string.soundboard_state_loading)
            is SoundboardStateMachine.State.Ready -> buildString {
                append(
                    getString(
                        R.string.soundboard_state_ready,
                        state.playableCount,
                        state.activeStreams,
                        state.cachedCount,
                        state.favorites.count { it.clipId != null }
                    )
                )
                append(" cfg(streams=${state.constraintSnapshot.maxStreams}, cd=${state.constraintSnapshot.cooldownMs}, cache=${state.constraintSnapshot.maxCacheSize}, policy=${state.constraintSnapshot.cachePolicy})")
                append(" load(u=${state.clipLoadSnapshot.unloaded},l=${state.clipLoadSnapshot.loading},ok=${state.clipLoadSnapshot.loaded},f=${state.clipLoadSnapshot.failed})")
                state.lastRejection?.let { append(" lastReject=${it.reason}") }
            }

            is SoundboardStateMachine.State.Playing -> buildString {
                append(
                    getString(
                        R.string.soundboard_state_playing,
                        state.fileName,
                        state.activeStreams
                    )
                )
                append(" load(ok=${state.clipLoadSnapshot.loaded},l=${state.clipLoadSnapshot.loading})")
            }

            is SoundboardStateMachine.State.Error -> getString(
                R.string.soundboard_state_error,
                state.message,
                state.rejectionCounters.cooldownActive,
                state.rejectionCounters.maxStreamsReached,
                state.rejectionCounters.clipLoadFailed
            ) + (state.lastRejection?.let { " at=${it.atEpochMs}" } ?: "")
        }
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) || displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun saveRootFolderUri(uri: Uri) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(KEY_ROOT_FOLDER_URI, uri.toString()).apply()
    }

    private fun savedRootFolderUri(): Uri? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROOT_FOLDER_URI, null)
        return raw?.let(Uri::parse)
    }

    private fun saveFavoriteAssignments() {
        val packed = favoriteSlotClipIds.entries
            .sortedBy { it.key }
            .joinToString(separator = "||") { "${it.key}::${it.value}" }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_FAVORITE_ASSIGNMENTS, packed)
            .apply()
    }

    private fun loadFavoriteAssignments(): Map<Int, String> {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_FAVORITE_ASSIGNMENTS, null)
            .orEmpty()
        if (raw.isBlank()) return emptyMap()

        return raw.split("||")
            .mapNotNull { chunk ->
                val parts = chunk.split("::", limit = 2)
                val index = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val clipId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (index !in 0 until FAVORITE_SLOT_COUNT) return@mapNotNull null
                index to clipId
            }
            .toMap()
    }

    private fun saveSelectedAssignmentSlotIndex(index: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_ASSIGNMENT_SLOT, index.coerceIn(0, FAVORITE_SLOT_COUNT - 1))
            .apply()
    }

    private fun loadSelectedAssignmentSlotIndex(): Int {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(KEY_SELECTED_ASSIGNMENT_SLOT, 0)
            .coerceIn(0, FAVORITE_SLOT_COUNT - 1)
    }

    private fun loadConfig(): SoundboardConfig {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val policyName = prefs.getString(KEY_CACHE_POLICY, DEFAULT_CACHE_POLICY.name) ?: DEFAULT_CACHE_POLICY.name
        val cachePolicy = runCatching { CachePolicy.valueOf(policyName) }.getOrDefault(DEFAULT_CACHE_POLICY)
        return SoundboardConfig(
            maxStreams = prefs.getInt(KEY_MAX_STREAMS, DEFAULT_MAX_STREAMS).coerceIn(1, 8),
            cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS).coerceIn(0L, 500L),
            maxCacheSize = prefs.getInt(KEY_MAX_CACHE_SIZE, DEFAULT_MAX_CACHE_SIZE).coerceIn(8, 36),
            unloadOnFolderChange = prefs.getBoolean(KEY_UNLOAD_ON_FOLDER_CHANGE, DEFAULT_UNLOAD_ON_FOLDER_CHANGE),
            cachePolicy = cachePolicy
        )
    }

    private fun saveConfig(config: SoundboardConfig) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_MAX_STREAMS, config.maxStreams)
            .putLong(KEY_COOLDOWN_MS, config.cooldownMs)
            .putInt(KEY_MAX_CACHE_SIZE, config.maxCacheSize)
            .putBoolean(KEY_UNLOAD_ON_FOLDER_CHANGE, config.unloadOnFolderChange)
            .putString(KEY_CACHE_POLICY, config.cachePolicy.name)
            .apply()
    }

    private fun failToMainMenu(error: Throwable) {
        Log.e(TAG, "Screen3 startup failure", error)
        Toast.makeText(this, getString(R.string.screen3_startup_failed), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private data class FolderEntry(val name: String, val folderUri: Uri)
    private data class ClipMetadata(
        val id: String,
        val displayName: String,
        val uri: Uri,
        val folderName: String,
        val durationMs: Long,
        val isPlayable: Boolean
    )
    private data class CacheEntry(
        val soundId: Int?,
        var state: SoundboardStateMachine.ClipLoadState,
        var lastUsedMs: Long,
        var pinned: Boolean
    )

    private data class SoundboardConfig(
        val maxStreams: Int = DEFAULT_MAX_STREAMS,
        val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE,
        val unloadOnFolderChange: Boolean = DEFAULT_UNLOAD_ON_FOLDER_CHANGE,
        val cachePolicy: CachePolicy = DEFAULT_CACHE_POLICY
    )
    private data class CacheEntry(
        val soundId: Int?,
        var state: SoundboardStateMachine.ClipLoadState,
        var lastUsedMs: Long,
        var pinned: Boolean
    )

    private data class SoundboardConfig(
        val maxStreams: Int = DEFAULT_MAX_STREAMS,
        val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
        val maxCacheSize: Int = DEFAULT_MAX_CACHE_SIZE,
        val unloadOnFolderChange: Boolean = DEFAULT_UNLOAD_ON_FOLDER_CHANGE,
        val cachePolicy: CachePolicy = DEFAULT_CACHE_POLICY
    )

    private enum class CachePolicy { AGGRESSIVE, BALANCED, STICKY }

    private enum class TrimReason { FOLDER_SWITCH, MEMORY_PRESSURE, SETTINGS_APPLY }

    private enum class CachePolicy { AGGRESSIVE, BALANCED, STICKY }

    private enum class TrimReason { FOLDER_SWITCH, MEMORY_PRESSURE, SETTINGS_APPLY }

    private enum class CachePolicy { AGGRESSIVE, BALANCED, STICKY }

    private enum class TrimReason { FOLDER_SWITCH, MEMORY_PRESSURE, SETTINGS_APPLY }

    private companion object {
        private const val TAG = "Screen3Soundboard"
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_MAX_STREAMS = "max_streams"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_MAX_CACHE_SIZE = "max_cache_size"
        private const val KEY_UNLOAD_ON_FOLDER_CHANGE = "unload_on_folder_change"
        private const val KEY_CACHE_POLICY = "cache_policy"
        private const val KEY_FAVORITE_ASSIGNMENTS = "favorite_assignments"
        private const val KEY_SELECTED_ASSIGNMENT_SLOT = "selected_assignment_slot"

        private const val FAVORITE_SLOT_COUNT = 9
        private const val STREAM_RELEASE_PADDING_MS = 120L

        private const val DEFAULT_MAX_STREAMS = 4
        private const val DEFAULT_COOLDOWN_MS = 120L
        private const val DEFAULT_MAX_CACHE_SIZE = 24
        private const val DEFAULT_UNLOAD_ON_FOLDER_CHANGE = true
        private val DEFAULT_CACHE_POLICY = CachePolicy.BALANCED

        private const val CACHE_POLICY_AGGRESSIVE_ID = 1001
        private const val CACHE_POLICY_BALANCED_ID = 1002
        private const val CACHE_POLICY_STICKY_ID = 1003
    }
}
