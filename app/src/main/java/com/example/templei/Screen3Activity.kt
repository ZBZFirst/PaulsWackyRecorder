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
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.soundboard.SoundboardStateMachine
import com.example.templei.feature.soundboard.SoundboardConfig
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        selectFolderButton.setOnClickListener { pickFolderLauncher.launch(savedRootFolderUri()) }
        settingsButton.setOnClickListener { showSettingsDialog() }
        clearSelectedSlotButton.setOnClickListener { clearSelectedAssignmentSlot() }

        bindFavoritePadButtons()
        bindFolderBrowser()
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
        val soundId: Int? = null,
        var state: SoundboardStateMachine.ClipLoadState,
        var lastUsedMs: Long,
        var pinned: Boolean
    )

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