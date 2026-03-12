package com.example.templei

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.soundboard.SoundboardStateMachine
import com.example.templei.ui.navigation.TopNavigation

/**
 * Screen 3: button-driven soundboard with user-selected folder scope.
 *
 * Behavior contract:
 * - User selects a root folder (via system file picker).
 * - Browser navigates laterally only across immediate child folders at one level.
 * - Only `.wav` and `.mp3` files with duration <= 6 seconds are playable.
 * - Audio starts only when a button is explicitly pressed.
 */
class Screen3Activity : ComponentActivity() {
    private val stateMachine = SoundboardStateMachine()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var statusText: TextView
    private lateinit var folderNameText: TextView
    private lateinit var previousFolderButton: Button
    private lateinit var nextFolderButton: Button
    private lateinit var selectFolderButton: Button

    private var folderNames: List<String> = emptyList()
    private var currentFolderIndex: Int = 0
    private var clipsByFolder: Map<String, List<AudioClip>> = emptyMap()

    private val buttonIds = listOf(
        R.id.gridButton01, R.id.gridButton02, R.id.gridButton03,
        R.id.gridButton04, R.id.gridButton05, R.id.gridButton06,
        R.id.gridButton07, R.id.gridButton08, R.id.gridButton09,
        R.id.gridButton10, R.id.gridButton11, R.id.gridButton12,
        R.id.gridButton13, R.id.gridButton14, R.id.gridButton15,
        R.id.gridButton16, R.id.gridButton17, R.id.gridButton18,
        R.id.gridButton19, R.id.gridButton20, R.id.gridButton21,
        R.id.gridButton22, R.id.gridButton23, R.id.gridButton24,
        R.id.gridButton25, R.id.gridButton26, R.id.gridButton27,
        R.id.gridButton28, R.id.gridButton29, R.id.gridButton30,
        R.id.gridButton31, R.id.gridButton32, R.id.gridButton33,
        R.id.gridButton34, R.id.gridButton35, R.id.gridButton36
    )

    private val pickFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
            runCatching { bindFolderBrowser() }
                .onFailure { failToMainMenu(it) }
        } else {
            Log.d(TAG, "Folder picker canceled by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            setContentView(R.layout.activity_screen3)
            TopNavigation.bind(activity = this, currentDestination = Screen3Activity::class.java)

            statusText = findViewById(R.id.soundboardStatusText)
            folderNameText = findViewById(R.id.soundboardFolderNameText)
            previousFolderButton = findViewById(R.id.soundboardFolderPrevButton)
            nextFolderButton = findViewById(R.id.soundboardFolderNextButton)
            selectFolderButton = findViewById(R.id.soundboardSelectFolderButton)

            selectFolderButton.setOnClickListener {
                Log.d(TAG, "Launching folder picker")
                pickFolderLauncher.launch(savedRootFolderUri())
            }

            bindFolderBrowser()
        }.onFailure(::failToMainMenu)
    }

    override fun onResume() {
        super.onResume()
        runCatching { bindFolderBrowser() }.onFailure(::failToMainMenu)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun bindFolderBrowser() {
        val rootUri = savedRootFolderUri()
        if (rootUri == null) {
            Log.d(TAG, "No root folder selected")
            renderFolderSelectionRequired()
            return
        }

        val root = DocumentFile.fromTreeUri(this, rootUri)
        if (root == null || !root.canRead()) {
            Log.w(TAG, "Root folder cannot be read: $rootUri")
            renderFolderSelectionRequired()
            return
        }

        clipsByFolder = loadClipsByFolderAtSingleLevel(root)
        folderNames = clipsByFolder.keys.sorted()
        currentFolderIndex = currentFolderIndex.coerceAtMost((folderNames.size - 1).coerceAtLeast(0))

        Log.i(TAG, "Loaded ${folderNames.size} sibling folders at selected level")

        previousFolderButton.setOnClickListener {
            if (folderNames.isNotEmpty()) {
                currentFolderIndex = (currentFolderIndex - 1 + folderNames.size) % folderNames.size
                bindCurrentFolder()
            }
        }

        nextFolderButton.setOnClickListener {
            if (folderNames.isNotEmpty()) {
                currentFolderIndex = (currentFolderIndex + 1) % folderNames.size
                bindCurrentFolder()
            }
        }

        bindCurrentFolder()
    }

    private fun renderFolderSelectionRequired() {
        folderNames = emptyList()
        clipsByFolder = emptyMap()
        currentFolderIndex = 0
        folderNameText.text = getString(R.string.soundboard_folder_none)
        previousFolderButton.isEnabled = false
        nextFolderButton.isEnabled = false
        bindButtons(emptyList())
        stateMachine.onError(getString(R.string.soundboard_state_error_select_folder))
        renderState(stateMachine.currentState())
    }

    private fun bindCurrentFolder() {
        val folderName = folderNames.getOrNull(currentFolderIndex)
        if (folderName == null) {
            folderNameText.text = getString(R.string.soundboard_folder_none)
            previousFolderButton.isEnabled = false
            nextFolderButton.isEnabled = false
            bindButtons(emptyList())
            stateMachine.onCatalogLoaded(emptyList())
            renderState(stateMachine.currentState())
            return
        }

        val clips = clipsByFolder[folderName].orEmpty().sortedBy { it.displayName }
        folderNameText.text = folderName
        previousFolderButton.isEnabled = folderNames.size > 1
        nextFolderButton.isEnabled = folderNames.size > 1

        Log.d(TAG, "Binding folder '$folderName' with ${clips.size} clips")

        stateMachine.onCatalogLoaded(clips.map { it.displayName })
        renderState(stateMachine.currentState())
        bindButtons(clips)
    }

    private fun bindButtons(clips: List<AudioClip>) {
        val buttons = buttonIds.mapNotNull { id -> findViewById<Button?>(id) }
        buttons.forEachIndexed { index, button ->
            val clip = clips.getOrNull(index)
            if (clip == null) {
                button.isEnabled = false
                button.text = getString(R.string.soundboard_button_empty)
                button.setOnClickListener(null)
            } else {
                button.isEnabled = true
                button.text = clip.displayName
                button.setOnClickListener {
                    playClip(clip, clips.map { it.displayName })
                }
            }
        }
    }

    /**
     * Load only one folder level: direct child folders of selected root.
     */
    private fun loadClipsByFolderAtSingleLevel(rootFolder: DocumentFile): Map<String, List<AudioClip>> {
        val byFolder = linkedMapOf<String, MutableList<AudioClip>>()

        rootFolder.listFiles()
            .filter { it.isDirectory }
            .forEach { childFolder ->
                val folderName = childFolder.name ?: getString(R.string.soundboard_folder_unknown)
                val clips = childFolder.listFiles()
                    .filter { it.isFile }
                    .mapNotNull { file ->
                        val fileName = file.name ?: return@mapNotNull null
                        if (!isSupportedExtension(fileName)) return@mapNotNull null
                        if (!isSupportedDuration(file.uri)) return@mapNotNull null
                        AudioClip(displayName = fileName, uri = file.uri, folderName = folderName)
                    }

                if (clips.isNotEmpty()) {
                    byFolder.getOrPut(folderName) { mutableListOf() }.addAll(clips)
                }
            }

        // If root itself directly contains audio, expose it as one addressable bucket.
        val rootFiles = rootFolder.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->
                val fileName = file.name ?: return@mapNotNull null
                if (!isSupportedExtension(fileName)) return@mapNotNull null
                if (!isSupportedDuration(file.uri)) return@mapNotNull null
                AudioClip(displayName = fileName, uri = file.uri, folderName = getString(R.string.soundboard_folder_current))
            }
        if (rootFiles.isNotEmpty()) {
            byFolder.getOrPut(getString(R.string.soundboard_folder_current)) { mutableListOf() }.addAll(rootFiles)
        }

        return byFolder.mapValues { it.value.sortedBy { clip -> clip.displayName } }
    }

    private fun isSupportedDuration(uri: Uri): Boolean {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: Long.MAX_VALUE
            retriever.release()
            durationMs in 1..MAX_SOUND_DURATION_MS
        }.onFailure {
            Log.w(TAG, "Failed duration check for $uri", it)
        }.getOrDefault(false)
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) || displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun playClip(clip: AudioClip, playableFiles: List<String>) {
        Log.d(TAG, "Play pressed: ${clip.displayName} (${clip.folderName})")
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(this@Screen3Activity, clip.uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    stateMachine.onPlaybackCompleted(playableFiles)
                    renderState(stateMachine.currentState())
                }
                prepareAsync()
                stateMachine.onPlayPressed(clip.displayName)
                renderState(stateMachine.currentState())
            } catch (error: Exception) {
                Log.e(TAG, "Playback failed for ${clip.displayName}", error)
                stateMachine.onError(getString(R.string.soundboard_state_error_playback, clip.displayName))
                renderState(stateMachine.currentState())
                release()
                mediaPlayer = null
            }
        }
    }

    private fun saveRootFolderUri(uri: Uri) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_FOLDER_URI, uri.toString())
            .apply()
    }

    private fun savedRootFolderUri(): Uri? {
        val raw = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ROOT_FOLDER_URI, null)
        return raw?.let(Uri::parse)
    }

    private fun renderState(state: SoundboardStateMachine.State) {
        statusText.text = when (state) {
            SoundboardStateMachine.State.Loading -> getString(R.string.soundboard_state_loading)
            is SoundboardStateMachine.State.Ready -> getString(R.string.soundboard_state_ready, state.playableFiles.size)
            is SoundboardStateMachine.State.Playing -> getString(R.string.soundboard_state_playing, state.fileName)
            is SoundboardStateMachine.State.Error -> getString(R.string.soundboard_state_error, state.message)
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

    private companion object {
        private const val TAG = "Screen3Soundboard"
        private const val MAX_SOUND_DURATION_MS = 6_000L
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
    }
}
