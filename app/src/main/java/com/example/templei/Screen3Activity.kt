package com.example.templei

import android.Manifest
import android.content.ContentUris
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.example.templei.feature.soundboard.SoundboardStateMachine
import com.example.templei.ui.navigation.TopNavigation

/**
 * Screen 3: button-driven soundboard backed by device Music storage.
 *
 * Behavior contract:
 * - Clips are discovered from shared media storage (Music directory / media index).
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

    override fun onResume() {
        super.onResume()
        bindFolderBrowser()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun bindFolderBrowser() {
        if (!canReadMusic()) {
            folderNames = emptyList()
            clipsByFolder = emptyMap()
            currentFolderIndex = 0
            folderNameText.text = getString(R.string.soundboard_folder_none)
            previousFolderButton.isEnabled = false
            nextFolderButton.isEnabled = false
            bindButtons(emptyList())
            stateMachine.onError(getString(R.string.soundboard_state_error_permission_required))
            renderState(stateMachine.currentState())
            return
        }

        clipsByFolder = loadClipsByFolder()
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

    private fun loadClipsByFolder(): Map<String, List<AudioClip>> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATA
        )

        val clips = mutableListOf<AudioClip>()
        contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val relativePathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(nameColumn) ?: continue
                val durationMs = cursor.getLong(durationColumn)
                if (durationMs <= 0 || durationMs > MAX_SOUND_DURATION_MS) {
                    continue
                }
                if (!isSupportedExtension(displayName)) {
                    continue
                }

                val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) else null
                val folderName = deriveFolderName(relativePath, dataPath)
                if (folderName == null) {
                    continue
                }

                clips += AudioClip(
                    displayName = displayName,
                    uri = ContentUris.withAppendedId(collection, id),
                    folderName = folderName
                )
            }
        }

        return clips.groupBy { it.folderName }
    }

    private fun deriveFolderName(relativePath: String?, dataPath: String?): String? {
        relativePath?.let {
            val normalized = it.trim('/').replace('\\', '/')
            if (normalized.equals("Music", ignoreCase = true)) {
                return "Music"
            }
            if (normalized.startsWith("Music/", ignoreCase = true)) {
                return normalized.removePrefix("Music/")
            }
        }

        dataPath?.let {
            val normalized = it.replace('\\', '/')
            val marker = "/Music/"
            val idx = normalized.indexOf(marker, ignoreCase = true)
            if (idx >= 0) {
                val remainder = normalized.substring(idx + marker.length)
                val slash = remainder.lastIndexOf('/')
                return if (slash > 0) remainder.substring(0, slash) else "Music"
            }
        }

        return null
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

    private fun canReadMusic(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        return ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun renderState(state: SoundboardStateMachine.State) {
        statusText.text = when (state) {
            SoundboardStateMachine.State.Loading -> getString(R.string.soundboard_state_loading)
            is SoundboardStateMachine.State.Ready -> getString(R.string.soundboard_state_ready, state.playableFiles.size)
            is SoundboardStateMachine.State.Playing -> getString(R.string.soundboard_state_playing, state.fileName)
            is SoundboardStateMachine.State.Error -> getString(R.string.soundboard_state_error, state.message)
        }
    }

    private data class AudioClip(
        val displayName: String,
        val uri: android.net.Uri,
        val folderName: String
    )

    private companion object {
        const val MAX_SOUND_DURATION_MS = 6_000L
    }
}
