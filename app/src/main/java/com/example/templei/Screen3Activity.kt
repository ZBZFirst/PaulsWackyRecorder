package com.example.templei

import android.content.res.AssetFileDescriptor
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.templei.feature.soundboard.SoundboardStateMachine
import com.example.templei.ui.navigation.TopNavigation

/**
 * Screen 3: button-driven soundboard.
 *
 * Behavior contract:
 * - Files are discovered from `assets/soundboard`.
 * - A folder browser selects one sample folder at a time.
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

    private var sampleFolders: List<String> = emptyList()
    private var currentFolderIndex: Int = 0

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen3)
        TopNavigation.bind(activity = this, currentDestination = Screen3Activity::class.java)

        statusText = findViewById(R.id.soundboardStatusText)
        folderNameText = findViewById(R.id.soundboardFolderNameText)
        previousFolderButton = findViewById(R.id.soundboardFolderPrevButton)
        nextFolderButton = findViewById(R.id.soundboardFolderNextButton)

        bindFolderBrowser()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun bindFolderBrowser() {
        sampleFolders = discoverPlayableFolders(SOUNDBOARD_ASSET_DIR)
        currentFolderIndex = 0

        previousFolderButton.setOnClickListener {
            if (sampleFolders.isNotEmpty()) {
                currentFolderIndex = (currentFolderIndex - 1 + sampleFolders.size) % sampleFolders.size
                bindCurrentFolder()
            }
        }

        nextFolderButton.setOnClickListener {
            if (sampleFolders.isNotEmpty()) {
                currentFolderIndex = (currentFolderIndex + 1) % sampleFolders.size
                bindCurrentFolder()
            }
        }

        bindCurrentFolder()
    }

    private fun bindCurrentFolder() {
        val folderPath = sampleFolders.getOrNull(currentFolderIndex)
        if (folderPath == null) {
            folderNameText.text = getString(R.string.soundboard_folder_none)
            previousFolderButton.isEnabled = false
            nextFolderButton.isEnabled = false
            bindButtons(playableFiles = emptyList(), folderPath = null)
            stateMachine.onCatalogLoaded(emptyList())
            renderState(stateMachine.currentState())
            return
        }

        folderNameText.text = folderPath.removePrefix("$SOUNDBOARD_ASSET_DIR/")
        previousFolderButton.isEnabled = sampleFolders.size > 1
        nextFolderButton.isEnabled = sampleFolders.size > 1

        val playableFiles = loadPlayableFiles(folderPath)
        stateMachine.onCatalogLoaded(playableFiles)
        renderState(stateMachine.currentState())
        bindButtons(playableFiles = playableFiles, folderPath = folderPath)
    }

    private fun bindButtons(playableFiles: List<String>, folderPath: String?) {
        val buttons = buttonIds.mapNotNull { id -> findViewById<Button?>(id) }
        buttons.forEachIndexed { index, button ->
            val fileName = playableFiles.getOrNull(index)
            if (fileName == null || folderPath == null) {
                button.isEnabled = false
                button.text = getString(R.string.soundboard_button_empty)
                button.setOnClickListener(null)
            } else {
                button.isEnabled = true
                button.text = fileName
                button.setOnClickListener {
                    playAsset(
                        folderPath = folderPath,
                        fileName = fileName,
                        playableFiles = playableFiles
                    )
                }
            }
        }
    }

    private fun discoverPlayableFolders(basePath: String): List<String> {
        val result = mutableListOf<String>()

        fun scan(path: String) {
            val entries = assets.list(path)?.toList().orEmpty()
            if (entries.isEmpty()) {
                return
            }

            val files = entries.filter { entry ->
                entry.endsWith(".wav", ignoreCase = true) || entry.endsWith(".mp3", ignoreCase = true)
            }
            val subdirs = entries.filter { entry ->
                assets.list("$path/$entry")?.isNotEmpty() == true
            }

            if (files.isNotEmpty()) {
                val playableCount = files.count { fileName -> isSupportedDuration(path, fileName) }
                if (playableCount > 0) {
                    result += path
                }
            }

            subdirs.forEach { subdir ->
                scan("$path/$subdir")
            }
        }

        scan(basePath)
        return result.distinct().sorted()
    }

    private fun loadPlayableFiles(folderPath: String): List<String> {
        val candidates = assets.list(folderPath)?.toList().orEmpty()
        return candidates
            .filter { it.endsWith(".wav", ignoreCase = true) || it.endsWith(".mp3", ignoreCase = true) }
            .filter { isSupportedDuration(folderPath, it) }
            .sorted()
    }

    private fun isSupportedDuration(folderPath: String, fileName: String): Boolean {
        return runCatching {
            assets.openFd("$folderPath/$fileName").use { fd ->
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: Long.MAX_VALUE
                retriever.release()
                durationMs <= MAX_SOUND_DURATION_MS
            }
        }.getOrDefault(false)
    }

    private fun playAsset(folderPath: String, fileName: String, playableFiles: List<String>) {
        val path = "$folderPath/$fileName"
        val afd: AssetFileDescriptor = try {
            assets.openFd(path)
        } catch (_: Exception) {
            stateMachine.onError(getString(R.string.soundboard_state_error_missing, fileName))
            renderState(stateMachine.currentState())
            return
        }

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    stateMachine.onPlaybackCompleted(playableFiles)
                    renderState(stateMachine.currentState())
                }
                prepareAsync()
                stateMachine.onPlayPressed(fileName)
                renderState(stateMachine.currentState())
            } catch (_: Exception) {
                stateMachine.onError(getString(R.string.soundboard_state_error_playback, fileName))
                renderState(stateMachine.currentState())
                release()
                mediaPlayer = null
            } finally {
                afd.close()
            }
        }
    }

    private fun renderState(state: SoundboardStateMachine.State) {
        statusText.text = when (state) {
            SoundboardStateMachine.State.Loading -> getString(R.string.soundboard_state_loading)
            is SoundboardStateMachine.State.Ready -> getString(R.string.soundboard_state_ready, state.playableFiles.size)
            is SoundboardStateMachine.State.Playing -> getString(R.string.soundboard_state_playing, state.fileName)
            is SoundboardStateMachine.State.Error -> getString(R.string.soundboard_state_error, state.message)
        }
    }

    private companion object {
        const val SOUNDBOARD_ASSET_DIR = "soundboard"
        const val MAX_SOUND_DURATION_MS = 6_000L
    }
}
