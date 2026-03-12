package com.example.templei

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.soundboard.SoundboardStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 3: folder-scoped soundboard with explicit favorites and folder clip browser.
 */
class Screen3Activity : ComponentActivity() {

    private val stateMachine = SoundboardStateMachine()
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var soundboardAudioEngine: SoundboardAudioEngine

    private lateinit var statusText: TextView
    private lateinit var loadingDetailText: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var folderNameText: TextView
    private lateinit var previousFolderButton: Button
    private lateinit var nextFolderButton: Button
    private lateinit var selectFolderButton: Button
    private lateinit var assignmentTargetText: TextView
    private lateinit var clearSelectedSlotButton: Button
    private lateinit var toggleFavoritesSectionButton: Button
    private lateinit var toggleBrowserSectionButton: Button
    private lateinit var favoritesSectionBody: LinearLayout
    private lateinit var browserSectionBody: LinearLayout
    private lateinit var clipBrowserContainer: LinearLayout
    private lateinit var browserCountText: TextView

    private lateinit var favoritePadButtons: List<Button>

    private var folderNames: List<String> = emptyList()
    private var currentFolderIndex: Int = 0
    private var clipsByFolder: Map<String, List<AudioClip>> = emptyMap()
    private var currentFolderClips: List<AudioClip> = emptyList()
    private var availableClipByUri: Map<String, AudioClip> = emptyMap()
    private var loadFolderJob: Job? = null
    private var loadedRootUriString: String? = null

    private var selectedFavoriteSlotIndex: Int = 0
    private var favoritesSectionExpanded: Boolean = true
    private var browserSectionExpanded: Boolean = true
    private val favoriteSlotUris = MutableList<String?>(FAVORITE_SLOT_COUNT) { null }

    private val durationSupportCache = mutableMapOf<String, Boolean>()

    private val pickFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                Log.i(TAG, "Folder picked: $uri")
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }.onFailure {
                    Log.w(TAG, "Persistable URI permission failed for $uri", it)
                }

                saveRootFolderUri(uri)
                loadedRootUriString = null

                runCatching { bindFolderBrowser() }
                    .onFailure(::failToMainMenu)
            } else {
                Log.d(TAG, "Folder picker canceled by user")
            }
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
            assignmentTargetText = findViewById(R.id.soundboardAssignmentTargetText)
            clearSelectedSlotButton = findViewById(R.id.soundboardClearSelectedSlotButton)
            toggleFavoritesSectionButton = findViewById(R.id.soundboardToggleFavoritesSectionButton)
            toggleBrowserSectionButton = findViewById(R.id.soundboardToggleBrowserSectionButton)
            favoritesSectionBody = findViewById(R.id.soundboardFavoritesSectionBody)
            browserSectionBody = findViewById(R.id.soundboardBrowserSectionBody)
            clipBrowserContainer = findViewById(R.id.soundboardClipBrowserContainer)
            browserCountText = findViewById(R.id.soundboardBrowserCountText)

            favoritePadButtons = listOf(
                findViewById(R.id.favoritePadButton1),
                findViewById(R.id.favoritePadButton2),
                findViewById(R.id.favoritePadButton3),
                findViewById(R.id.favoritePadButton4),
                findViewById(R.id.favoritePadButton5),
                findViewById(R.id.favoritePadButton6),
                findViewById(R.id.favoritePadButton7),
                findViewById(R.id.favoritePadButton8),
                findViewById(R.id.favoritePadButton9)
            )

            selectFolderButton.setOnClickListener {
                pickFolderLauncher.launch(savedRootFolderUri())
            }
            previousFolderButton.setOnClickListener { moveFolderSelection(-1) }
            nextFolderButton.setOnClickListener { moveFolderSelection(1) }

            toggleFavoritesSectionButton.setOnClickListener {
                favoritesSectionExpanded = !favoritesSectionExpanded
                renderSectionVisibility()
            }
            toggleBrowserSectionButton.setOnClickListener {
                browserSectionExpanded = !browserSectionExpanded
                renderSectionVisibility()
            }

            clearSelectedSlotButton.setOnClickListener {
                favoriteSlotUris[selectedFavoriteSlotIndex] = null
                saveFavoriteSlots()
                renderFavoriteSlots()
                Toast.makeText(
                    this,
                    getString(R.string.soundboard_assignment_cleared, selectedFavoriteSlotIndex + 1),
                    Toast.LENGTH_SHORT
                ).show()
            }

            soundboardAudioEngine = SoundboardAudioEngine.getInstance(this)
            restoreFavoriteSlots()
            renderFavoriteSlots()
            renderSectionVisibility()

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
        loadFolderJob?.cancel()
        activityScope.cancel()
        soundboardAudioEngine.release()
    }

    private fun moveFolderSelection(direction: Int) {
        if (folderNames.isEmpty()) return
        currentFolderIndex = (currentFolderIndex + direction + folderNames.size) % folderNames.size
        bindCurrentFolder()
    }

    private fun bindFolderBrowser() {
        val rootUri = savedRootFolderUri() ?: run {
            renderFolderSelectionRequired()
            return
        }

        val rootFolder = DocumentFile.fromTreeUri(this, rootUri)
        if (rootFolder == null || !rootFolder.canRead()) {
            renderFolderSelectionRequired()
            return
        }

        val rootUriString = rootUri.toString()
        if (loadedRootUriString == rootUriString && clipsByFolder.isNotEmpty()) {
            bindCurrentFolder()
            renderFavoriteSlots()
            return
        }

        loadFolderJob?.cancel()
        loadFolderJob = activityScope.launch {
            stateMachine.onLoadingProgress(
                stage = SoundboardStateMachine.LoadingStage.Discovering,
                processedFiles = 0,
                totalFiles = 0,
                playableFiles = 0,
                discoveredFolders = 0,
                discoveredFiles = 0
            )
            renderState(stateMachine.currentState())

            val loaded = withContext(Dispatchers.IO) {
                loadClipsByFolderAtSingleLevel(rootFolder) { snapshot ->
                    withContext(Dispatchers.Main) {
                        stateMachine.onLoadingProgress(
                            stage = snapshot.stage,
                            processedFiles = snapshot.processedFiles,
                            totalFiles = snapshot.totalFiles,
                            playableFiles = snapshot.playableFiles,
                            discoveredFolders = snapshot.discoveredFolders,
                            discoveredFiles = snapshot.discoveredFiles
                        )
                        renderState(stateMachine.currentState())
                    }
                }
            }

            loadedRootUriString = rootUriString
            clipsByFolder = loaded
            folderNames = clipsByFolder.keys.sorted()
            availableClipByUri = clipsByFolder.values.flatten().associateBy { it.uri.toString() }
            currentFolderIndex = currentFolderIndex.coerceIn(
                minimumValue = 0,
                maximumValue = (folderNames.size - 1).coerceAtLeast(0)
            )

            bindCurrentFolder()
            renderFavoriteSlots()
        }
    }

    private fun renderSectionVisibility() {
        favoritesSectionBody.visibility = if (favoritesSectionExpanded) View.VISIBLE else View.GONE
        browserSectionBody.visibility = if (browserSectionExpanded) View.VISIBLE else View.GONE

        toggleFavoritesSectionButton.text = if (favoritesSectionExpanded) {
            getString(R.string.soundboard_section_collapse)
        } else {
            getString(R.string.soundboard_section_expand)
        }
        toggleBrowserSectionButton.text = if (browserSectionExpanded) {
            getString(R.string.soundboard_section_collapse)
        } else {
            getString(R.string.soundboard_section_expand)
        }
    }

    private fun renderFolderSelectionRequired() {
        folderNames = emptyList()
        clipsByFolder = emptyMap()
        currentFolderClips = emptyList()
        availableClipByUri = emptyMap()
        currentFolderIndex = 0

        folderNameText.text = getString(R.string.soundboard_folder_none)
        previousFolderButton.isEnabled = false
        nextFolderButton.isEnabled = false

        browserCountText.text = getString(R.string.soundboard_browser_count_value, 0, 0, 0)
        renderClipBrowser(emptyList())
        renderFavoriteSlots()

        stateMachine.onError(getString(R.string.soundboard_state_error_select_folder))
        renderState(stateMachine.currentState())
    }

    private fun bindCurrentFolder() {
        val folderName = folderNames.getOrNull(currentFolderIndex)
        if (folderName == null) {
            folderNameText.text = getString(R.string.soundboard_folder_none)
            previousFolderButton.isEnabled = false
            nextFolderButton.isEnabled = false
            currentFolderClips = emptyList()
            browserCountText.text = getString(R.string.soundboard_browser_count_value, 0, 0, 0)
            renderClipBrowser(emptyList())

            stateMachine.onCatalogLoaded(emptyList())
            renderState(stateMachine.currentState())
            return
        }

        val clips = clipsByFolder[folderName].orEmpty().sortedBy { it.displayName }
        currentFolderClips = clips

        folderNameText.text = folderName
        previousFolderButton.isEnabled = folderNames.size > 1
        nextFolderButton.isEnabled = folderNames.size > 1

        val wavClips = clips.filter { it.displayName.endsWith(".wav", ignoreCase = true) }
        val wavCount = wavClips.size
        val mp3Count = clips.count { it.displayName.endsWith(".mp3", ignoreCase = true) }
        browserCountText.text = getString(
            R.string.soundboard_browser_count_value,
            clips.size,
            wavCount,
            mp3Count
        )

        stateMachine.onCatalogLoaded(clips.map { it.displayName })
        renderState(stateMachine.currentState())
        renderClipBrowser(wavClips)
    }

    private fun renderFavoriteSlots() {
        assignmentTargetText.text = getString(
            R.string.soundboard_assignment_target,
            selectedFavoriteSlotIndex + 1
        )

        favoritePadButtons.forEachIndexed { index, button ->
            val assignedUri = favoriteSlotUris[index]
            val clip = assignedUri?.let(availableClipByUri::get)

            button.text = when {
                assignedUri == null -> getString(R.string.soundboard_favorite_slot_empty)
                clip != null -> clip.displayName
                else -> getString(R.string.soundboard_favorite_slot_missing)
            }

            button.isEnabled = true
            button.isSelected = index == selectedFavoriteSlotIndex
            button.setOnClickListener {
                selectedFavoriteSlotIndex = index
                renderFavoriteSlots()
                playFavoriteSlot(index)
            }
            button.setOnLongClickListener {
                selectedFavoriteSlotIndex = index
                favoriteSlotUris[index] = null
                saveFavoriteSlots()
                renderFavoriteSlots()
                Toast.makeText(
                    this,
                    getString(R.string.soundboard_assignment_cleared, index + 1),
                    Toast.LENGTH_SHORT
                ).show()
                true
            }
        }
    }

    private fun renderClipBrowser(clips: List<AudioClip>) {
        clipBrowserContainer.removeAllViews()

        if (clips.isEmpty()) {
            clipBrowserContainer.addView(TextView(this).apply {
                text = getString(R.string.soundboard_browser_empty)
            })
            return
        }

        clips.forEach { clip ->
            val clipButton = Button(this).apply {
                text = clip.displayName
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }

                setOnClickListener {
                    playClip(clip, currentFolderClips.map { it.displayName })
                }
                setOnLongClickListener {
                    assignClipToSelectedFavoriteSlot(clip)
                    true
                }
            }
            clipBrowserContainer.addView(clipButton)
        }
    }

    private fun assignClipToSelectedFavoriteSlot(clip: AudioClip) {
        favoriteSlotUris[selectedFavoriteSlotIndex] = clip.uri.toString()
        saveFavoriteSlots()
        renderFavoriteSlots()
        Toast.makeText(
            this,
            getString(R.string.soundboard_assignment_saved, selectedFavoriteSlotIndex + 1, clip.displayName),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun playFavoriteSlot(slotIndex: Int) {
        val assignedUri = favoriteSlotUris[slotIndex]
        if (assignedUri == null) {
            Toast.makeText(
                this,
                getString(R.string.soundboard_reject_not_assigned, slotIndex + 1),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val clip = availableClipByUri[assignedUri]
        if (clip == null) {
            Toast.makeText(this, getString(R.string.soundboard_reject_missing), Toast.LENGTH_SHORT).show()
            return
        }

        playClip(clip, currentFolderClips.map { it.displayName })
    }

    private fun playClip(clip: AudioClip, playableFiles: List<String>) {
        runCatching {
            val queued = soundboardAudioEngine.playClipUri(this, clip.uri)
            if (!queued) error("Clip queue/play failed")

            stateMachine.onPlayPressed(clip.displayName)
            renderState(stateMachine.currentState())
        }.onFailure { error ->
            Log.e(TAG, "Playback failed for ${clip.displayName}", error)
            stateMachine.onError(getString(R.string.soundboard_state_error_playback, clip.displayName))
            renderState(stateMachine.currentState())
            stateMachine.onPlaybackCompleted(playableFiles)
        }
    }

    private suspend fun loadClipsByFolderAtSingleLevel(
        rootFolder: DocumentFile,
        onProgress: suspend (LoadingSnapshot) -> Unit
    ): Map<String, List<AudioClip>> {
        val byFolder = linkedMapOf<String, MutableList<AudioClip>>()
        val candidateFiles = mutableListOf<CandidateAudioFile>()

        var discoveredFolders = 0
        var discoveredFiles = 0

        rootFolder.listFiles()
            .filter { it.isDirectory }
            .forEach { childFolder ->
                discoveredFolders += 1
                val folderName = childFolder.name ?: getString(R.string.soundboard_folder_unknown)

                childFolder.listFiles()
                    .filter { it.isFile }
                    .forEach { file ->
                        candidateFiles.add(CandidateAudioFile(folderName, file))
                        discoveredFiles += 1
                    }

                onProgress(
                    LoadingSnapshot(
                        stage = SoundboardStateMachine.LoadingStage.Discovering,
                        processedFiles = discoveredFiles,
                        totalFiles = 0,
                        playableFiles = 0,
                        discoveredFolders = discoveredFolders,
                        discoveredFiles = discoveredFiles
                    )
                )
            }

        val currentFolderName = getString(R.string.soundboard_folder_current)
        rootFolder.listFiles()
            .filter { it.isFile }
            .forEach { file ->
                candidateFiles.add(CandidateAudioFile(currentFolderName, file))
                discoveredFiles += 1
            }

        onProgress(
            LoadingSnapshot(
                stage = SoundboardStateMachine.LoadingStage.Discovering,
                processedFiles = discoveredFiles,
                totalFiles = 0,
                playableFiles = 0,
                discoveredFolders = discoveredFolders,
                discoveredFiles = discoveredFiles
            )
        )

        val totalFiles = candidateFiles.size
        var processedFiles = 0
        var playableFiles = 0

        onProgress(
            LoadingSnapshot(
                stage = SoundboardStateMachine.LoadingStage.Validating,
                processedFiles = processedFiles,
                totalFiles = totalFiles,
                playableFiles = playableFiles,
                discoveredFolders = discoveredFolders,
                discoveredFiles = discoveredFiles
            )
        )

        candidateFiles.forEach { candidate ->
            val fileName = candidate.file.name
            if (fileName != null && isSupportedExtension(fileName) && isSupportedDuration(candidate.file.uri)) {
                byFolder.getOrPut(candidate.folderName) { mutableListOf() }
                    .add(AudioClip(fileName, candidate.file.uri, candidate.folderName))
                playableFiles += 1
            }

            processedFiles += 1
            onProgress(
                LoadingSnapshot(
                    stage = SoundboardStateMachine.LoadingStage.Validating,
                    processedFiles = processedFiles,
                    totalFiles = totalFiles,
                    playableFiles = playableFiles,
                    discoveredFolders = discoveredFolders,
                    discoveredFiles = discoveredFiles
                )
            )
        }

        return byFolder
            .mapValues { entry -> entry.value.sortedBy { it.displayName } }
            .toSortedMap()
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) ||
            displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun isSupportedDuration(uri: Uri): Boolean {
        val key = uri.toString()
        val cached = durationSupportCache[key]
        if (cached != null) return cached

        val supported = runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: Long.MAX_VALUE
            retriever.release()
            durationMs in 1..MAX_SOUND_DURATION_MS
        }.onFailure {
            Log.w(TAG, "Duration check failed for $uri", it)
        }.getOrDefault(false)

        durationSupportCache[key] = supported
        return supported
    }

    private fun saveRootFolderUri(uri: Uri) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_FOLDER_URI, uri.toString())
            .apply()
    }

    private fun savedRootFolderUri(): Uri? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_ROOT_FOLDER_URI, null)
        return raw?.let(Uri::parse)
    }

    private fun saveFavoriteSlots() {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        favoriteSlotUris.forEachIndexed { index, uri ->
            editor.putString("$KEY_FAVORITE_SLOT_PREFIX$index", uri)
        }
        editor.apply()
    }

    private fun restoreFavoriteSlots() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        favoriteSlotUris.indices.forEach { index ->
            favoriteSlotUris[index] = prefs.getString("$KEY_FAVORITE_SLOT_PREFIX$index", null)
        }
    }

    private fun renderState(state: SoundboardStateMachine.State) {
        when (state) {
            is SoundboardStateMachine.State.Loading -> {
                loadingProgressBar.visibility = View.VISIBLE
                statusText.text = getString(R.string.soundboard_state_loading)
                loadingDetailText.visibility = View.VISIBLE

                when (state.stage) {
                    SoundboardStateMachine.LoadingStage.Discovering -> {
                        loadingProgressBar.isIndeterminate = true
                        loadingDetailText.text = getString(
                            R.string.soundboard_state_loading_discovery,
                            state.discoveredFolders,
                            state.discoveredFiles
                        )
                    }

                    SoundboardStateMachine.LoadingStage.Validating -> {
                        loadingProgressBar.isIndeterminate = false
                        loadingProgressBar.max = state.totalFiles.coerceAtLeast(1)
                        loadingProgressBar.progress = state.processedFiles.coerceAtMost(loadingProgressBar.max)
                        loadingDetailText.text = getString(
                            R.string.soundboard_state_loading_progress,
                            state.processedFiles,
                            state.totalFiles,
                            state.playableFiles
                        )
                    }
                }
            }

            is SoundboardStateMachine.State.Ready -> {
                loadingProgressBar.visibility = View.GONE
                loadingDetailText.visibility = View.GONE
                statusText.text = getString(R.string.soundboard_state_ready, state.playableFiles.size)
            }

            is SoundboardStateMachine.State.Playing -> {
                loadingProgressBar.visibility = View.GONE
                loadingDetailText.visibility = View.GONE
                statusText.text = getString(R.string.soundboard_state_playing, state.fileName)
            }

            is SoundboardStateMachine.State.Error -> {
                loadingProgressBar.visibility = View.GONE
                loadingDetailText.visibility = View.GONE
                statusText.text = getString(R.string.soundboard_state_error, state.message)
            }
        }
    }

    private fun failToMainMenu(error: Throwable) {
        Log.e(TAG, "Screen3 startup failure", error)
        Toast.makeText(this, getString(R.string.screen3_startup_failed), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private data class AudioClip(
        val displayName: String,
        val uri: Uri,
        val folderName: String
    )

    private data class CandidateAudioFile(
        val folderName: String,
        val file: DocumentFile
    )

    private data class LoadingSnapshot(
        val stage: SoundboardStateMachine.LoadingStage,
        val processedFiles: Int,
        val totalFiles: Int,
        val playableFiles: Int,
        val discoveredFolders: Int,
        val discoveredFiles: Int
    )

    private companion object {
        private const val TAG = "Screen3Soundboard"
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val KEY_FAVORITE_SLOT_PREFIX = "favorite_slot_"
        private const val MAX_SOUND_DURATION_MS = 6_000L
        private const val FAVORITE_SLOT_COUNT = 9
    }
}
