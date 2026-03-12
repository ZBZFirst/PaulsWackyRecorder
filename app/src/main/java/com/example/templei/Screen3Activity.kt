package com.example.templei

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
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
 * Screen 3: button-driven soundboard with user-selected root folder.
 *
 * Behavior contract:
 * - User selects a folder (via system file picker) that contains sample subfolders.
 * - Folder browser selects one discovered subfolder at a time.
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
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            saveRootFolderUri(uri)
            bindFolderBrowser()
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
                pickFolderLauncher.launch(savedRootFolderUri())
            }

            bindFolderBrowser()
        }.onFailure { error ->
            failToMainMenu(error)
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { bindFolderBrowser() }.onFailure { error ->
            failToMainMenu(error)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
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

        clipsByFolder = loadClipsByFolder(root)
        folderNames = clipsByFolder.keys.sorted()
        currentFolderIndex = currentFolderIndex.coerceAtMost((folderNames.size - 1).coerceAtLeast(0))

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

    private fun loadClipsByFolder(rootFolder: DocumentFile): Map<String, List<AudioClip>> {
        val clips = mutableListOf<AudioClip>()

        fun scan(folder: DocumentFile, relativePath: String) {
            folder.listFiles().forEach { entry ->
                if (entry.isDirectory) {
                    val nextPath = if (relativePath.isBlank()) {
                        entry.name ?: getString(R.string.soundboard_folder_unknown)
                    } else {
                        "$relativePath/${entry.name ?: getString(R.string.soundboard_folder_unknown)}"
                    }
                    scan(entry, nextPath)
                } else if (entry.isFile) {
                    val name = entry.name ?: return@forEach
                    if (!isSupportedExtension(name)) {
                        return@forEach
                    }
                    if (!isSupportedDuration(entry.uri)) {
                        return@forEach
                    }

                    val folderName = if (relativePath.isBlank()) {
                        getString(R.string.soundboard_folder_unknown)
                    } else {
                        relativePath
                    }

                    clips += AudioClip(
                        displayName = name,
                        uri = entry.uri,
                        folderName = folderName
                    )
                }
            }
        }

        scan(rootFolder, relativePath = "")
        return clips.groupBy { it.folderName }
    }

    private fun isSupportedDuration(uri: Uri): Boolean {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: Long.MAX_VALUE
            retriever.release()
            durationMs in 1..MAX_SOUND_DURATION_MS
        }.getOrDefault(false)
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) || displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun playClip(clip: AudioClip, playableFiles: List<String>) {
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
            } catch (_: Exception) {
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
        Toast.makeText(this, getString(R.string.screen3_startup_failed), Toast.LENGTH_LONG).show()
        stateMachine.onError(getString(R.string.soundboard_state_error, error.message ?: "unknown"))
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private data class AudioClip(
        val displayName: String,
        val uri: Uri,
        val folderName: String
    )

    private companion object {
        const val MAX_SOUND_DURATION_MS = 6_000L
        const val PREFS_NAME = "screen3_soundboard"
        const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
    }
}
