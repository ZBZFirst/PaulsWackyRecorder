package com.example.templei

import android.content.Intent
import android.media.MediaMetadataRetriever
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
import kotlinx.coroutines.*

/**
 * Screen 3: folder-scoped soundboard with a 3x3 favorite pad.
 *
 * Behavioral contract:
 * - User selects a root folder through the system picker.
 * - Immediate child folders are browsed laterally.
 * - Direct audio files in the selected root are also supported.
 * - Only .wav and .mp3 files with duration <= 6 seconds are accepted.
 * - The visible 3x3 pad is rebound to the current folder contents.
 */
class Screen3Activity : ComponentActivity() {

    private val stateMachine = SoundboardStateMachine()

    private lateinit var soundboardAudioEngine: SoundboardAudioEngine

    private lateinit var statusText: TextView
    private lateinit var folderNameText: TextView
    private lateinit var previousFolderButton: Button
    private lateinit var nextFolderButton: Button
    private lateinit var selectFolderButton: Button

    private lateinit var favoritePadButtons: List<Button>

    private var folderNames: List<String> = emptyList()
    private var currentFolderIndex: Int = 0
    private var clipsByFolder: Map<String, List<AudioClip>> = emptyMap()

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
            folderNameText = findViewById(R.id.soundboardFolderNameText)
            previousFolderButton = findViewById(R.id.soundboardFolderPrevButton)
            nextFolderButton = findViewById(R.id.soundboardFolderNextButton)
            selectFolderButton = findViewById(R.id.soundboardSelectFolderButton)

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
                Log.d(TAG, "Launching folder picker")
                pickFolderLauncher.launch(savedRootFolderUri())
            }

            soundboardAudioEngine = SoundboardAudioEngine.getInstance(this)

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
        soundboardAudioEngine.release()
    }

    private fun bindFolderBrowser() {
        val rootUri = savedRootFolderUri()
        if (rootUri == null) {
            Log.d(TAG, "No root folder selected")
            renderFolderSelectionRequired()
            return
        }

        val rootFolder = DocumentFile.fromTreeUri(this, rootUri)
        if (rootFolder == null || !rootFolder.canRead()) {
            Log.w(TAG, "Root folder cannot be read: $rootUri")
            renderFolderSelectionRequired()
            return
        }

        // Load clips in a coroutine to offload this work from the main thread
        CoroutineScope(Dispatchers.IO).launch {
            clipsByFolder = loadClipsByFolderAtSingleLevel(rootFolder)

            // Update UI on the main thread after loading is complete
            withContext(Dispatchers.Main) {
                folderNames = clipsByFolder.keys.sorted()
                currentFolderIndex = currentFolderIndex.coerceIn(
                    minimumValue = 0,
                    maximumValue = (folderNames.size - 1).coerceAtLeast(0)
                )

                Log.i(TAG, "Loaded ${folderNames.size} playable folder buckets")

                bindCurrentFolder()
            }
        }
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
        favoritePadButtons.forEachIndexed { index, button ->
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

    private fun playClip(clip: AudioClip, playableFiles: List<String>) {
        Log.d(TAG, "Play pressed: ${clip.displayName} (${clip.folderName})")

        runCatching {
            // Pass the URI as a String instead of a Uri object
            val soundId = soundboardAudioEngine.loadClip(this, clip.uri.toString())  // Convert URI to String
            soundboardAudioEngine.playClip(soundId)

            stateMachine.onPlayPressed(clip.displayName)
            renderState(stateMachine.currentState())
        }.onFailure { error ->
            Log.e(TAG, "Playback failed for ${clip.displayName}", error)
            stateMachine.onError(
                getString(R.string.soundboard_state_error_playback, clip.displayName)
            )
            renderState(stateMachine.currentState())
            stateMachine.onPlaybackCompleted(playableFiles)
        }
    }

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

                        AudioClip(
                            displayName = fileName,
                            uri = file.uri,
                            folderName = folderName
                        )
                    }
                    .sortedBy { it.displayName }

                if (clips.isNotEmpty()) {
                    byFolder.getOrPut(folderName) { mutableListOf() }
                        .addAll(clips)
                }
            }

        val rootFiles = rootFolder.listFiles()
            .filter { it.isFile }
            .mapNotNull { file ->
                val fileName = file.name ?: return@mapNotNull null
                if (!isSupportedExtension(fileName)) return@mapNotNull null
                if (!isSupportedDuration(file.uri)) return@mapNotNull null

                AudioClip(
                    displayName = fileName,
                    uri = file.uri,
                    folderName = getString(R.string.soundboard_folder_current)
                )
            }
            .sortedBy { it.displayName }

        if (rootFiles.isNotEmpty()) {
            byFolder.getOrPut(getString(R.string.soundboard_folder_current)) { mutableListOf() }
                .addAll(rootFiles)
        }

        return byFolder.mapValues { entry -> entry.value.toList() }
    }

    private fun isSupportedExtension(displayName: String): Boolean {
        return displayName.endsWith(".wav", ignoreCase = true) ||
                displayName.endsWith(".mp3", ignoreCase = true)
    }

    private fun isSupportedDuration(uri: Uri): Boolean {
        return runCatching {
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
        private const val PREFS_NAME = "screen3_soundboard"
        private const val KEY_ROOT_FOLDER_URI = "root_folder_uri"
        private const val MAX_SOUND_DURATION_MS = 6_000L
    }
}