package com.example.templei

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.templei.feature.screen2.Screen2ClipRepository
import com.example.templei.feature.screen2.Screen2WavRecorder
import com.example.templei.feature.soundboard.Screen3LibraryRefreshSignal
import com.example.templei.ui.navigation.AppShellInsets
import com.example.templei.ui.navigation.AppShellNavigation
import java.io.File
import java.util.Locale

/**
 * Shell host for Screen 2 bounded WAV recording flow.
 */
class Screen2Activity : ComponentActivity() {
    private lateinit var statusText: TextView
    private lateinit var folderValueText: TextView
    private lateinit var countdownText: TextView
    private lateinit var lastSavedText: TextView
    private lateinit var fileNameInput: EditText
    private lateinit var chooseFolderButton: Button
    private lateinit var deleteWavButton: Button
    private lateinit var startRecordingButton: Button
    private lateinit var stopRecordingButton: Button

    private lateinit var clipRepository: Screen2ClipRepository
    private lateinit var libraryRefreshSignal: Screen3LibraryRefreshSignal
    private val wavRecorder = Screen2WavRecorder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recordingStartedAtMs: Long? = null
    private var pendingOutputFileName: String? = null

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                statusText.text = getString(R.string.screen2_status_folder_pick_cancelled)
                return@registerForActivityResult
            }

            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }

            clipRepository.saveSelectedFolderUri(uri)
            bindSelectedFolder()
            statusText.text = getString(R.string.screen2_status_folder_selected)
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                val outputFileName = resolveOutputFileName() ?: return@registerForActivityResult
                beginRecording(outputFileName)
            } else {
                statusText.text = getString(R.string.screen2_status_permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen2)
        AppShellNavigation.bind(
            activity = this,
            currentDestination = Screen2Activity::class.java,
            title = getString(R.string.screen2TitleText),
            chipText = getString(R.string.screen2_header_chip),
        )
        AppShellInsets.apply(
            activity = this,
            rootId = R.id.screen2Root,
            scrollViewId = R.id.screen2ScrollView,
        )

        clipRepository = Screen2ClipRepository(this)
        libraryRefreshSignal = Screen3LibraryRefreshSignal(this)

        bindViews()
        bindButtons()
        bindSelectedFolder()
        resetSuggestedFileName()
        renderIdleState(getString(R.string.screen2_status_idle))
    }

    override fun onPause() {
        super.onPause()
        if (wavRecorder.isRecording()) {
            finishRecording(wasAutoStopped = false)
        }
    }

    private fun bindViews() {
        statusText = findViewById(R.id.screen2StatusText)
        folderValueText = findViewById(R.id.screen2FolderValue)
        countdownText = findViewById(R.id.screen2CountdownText)
        lastSavedText = findViewById(R.id.screen2LastSavedText)
        fileNameInput = findViewById(R.id.screen2FileNameInput)
        chooseFolderButton = findViewById(R.id.screen2ChooseFolderButton)
        deleteWavButton = findViewById(R.id.screen2DeleteWavButton)
        startRecordingButton = findViewById(R.id.screen2StartRecordingButton)
        stopRecordingButton = findViewById(R.id.screen2StopRecordingButton)
    }

    private fun bindButtons() {
        chooseFolderButton.setOnClickListener {
            folderPickerLauncher.launch(clipRepository.selectedFolderUri())
        }
        deleteWavButton.setOnClickListener {
            showDeleteWavDialog()
        }
        startRecordingButton.setOnClickListener {
            ensureMicrophonePermissionAndStart()
        }
        stopRecordingButton.setOnClickListener {
            finishRecording(wasAutoStopped = false)
        }
    }

    private fun bindSelectedFolder() {
        folderValueText.text = clipRepository.selectedFolderLabel()
            ?: getString(R.string.screen2_folder_unselected)
        deleteWavButton.isEnabled = clipRepository.selectedFolderUri() != null
    }

    private fun ensureMicrophonePermissionAndStart() {
        if (clipRepository.selectedFolderUri() == null) {
            statusText.text = getString(R.string.screen2_status_select_folder_first)
            return
        }

        val outputFileName = resolveOutputFileName() ?: return

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            beginRecording(outputFileName)
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun beginRecording(outputFileName: String) {
        if (wavRecorder.isRecording()) return

        pendingOutputFileName = outputFileName
        val tempFile = File(cacheDir, clipRepository.buildClipFileName())
        wavRecorder.start(tempFile) {
            finishRecording(wasAutoStopped = true)
        }.onSuccess {
            recordingStartedAtMs = System.currentTimeMillis()
            statusText.text = getString(R.string.screen2_status_recording)
            startRecordingButton.isEnabled = false
            stopRecordingButton.isEnabled = true
            chooseFolderButton.isEnabled = false
            deleteWavButton.isEnabled = false
            fileNameInput.isEnabled = false
            startCountdownUpdates()
        }.onFailure { error ->
            pendingOutputFileName = null
            renderIdleState(
                getString(
                    R.string.screen2_status_recording_failed,
                    error.message ?: getString(R.string.screen2_unknown_error),
                ),
            )
        }
    }

    private fun finishRecording(wasAutoStopped: Boolean) {
        if (!wavRecorder.isRecording() && recordingStartedAtMs == null) return

        wavRecorder.stop()
            .onSuccess { result ->
                mainHandler.removeCallbacks(countdownRunnable)
                countdownText.text = formatDurationLabel(result.durationMs)
                saveRecordingResult(result, wasAutoStopped)
            }
            .onFailure { error ->
                renderIdleState(
                    getString(
                        R.string.screen2_status_recording_failed,
                        error.message ?: getString(R.string.screen2_unknown_error),
                    ),
                )
            }
    }

    private fun saveRecordingResult(
        result: Screen2WavRecorder.RecordingResult,
        wasAutoStopped: Boolean,
    ) {
        val fileName = pendingOutputFileName ?: result.outputFile.name
        runCatching {
            clipRepository.saveRecording(result.outputFile, fileName)
        }.onSuccess { savedClip ->
            libraryRefreshSignal.markChanged()
            lastSavedText.text = getString(
                R.string.screen2_last_saved_value,
                savedClip.fileName,
                savedClip.folderLabel,
            )
            val statusId = if (wasAutoStopped || result.reachedMaxDuration) {
                R.string.screen2_status_recording_saved_max
            } else {
                R.string.screen2_status_recording_saved
            }
            renderIdleState(getString(statusId, savedClip.fileName))
            resetSuggestedFileName()
        }.onFailure { error ->
            renderIdleState(
                getString(
                    R.string.screen2_status_save_failed,
                    error.message ?: getString(R.string.screen2_unknown_error),
                ),
            )
        }

        pendingOutputFileName = null
        result.outputFile.delete()
    }

    private fun renderIdleState(status: String) {
        mainHandler.removeCallbacks(countdownRunnable)
        recordingStartedAtMs = null
        statusText.text = status
        countdownText.text = getString(R.string.screen2_duration_initial)
        startRecordingButton.isEnabled = true
        stopRecordingButton.isEnabled = false
        chooseFolderButton.isEnabled = true
        deleteWavButton.isEnabled = clipRepository.selectedFolderUri() != null
        fileNameInput.isEnabled = true
    }

    private fun resolveOutputFileName(): String? {
        val rawValue = fileNameInput.text?.toString().orEmpty().trim()
        if (rawValue.isBlank()) {
            statusText.text = getString(R.string.screen2_status_enter_file_name)
            return null
        }
        val sanitized = rawValue.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
        if (sanitized.isBlank()) {
            statusText.text = getString(R.string.screen2_status_enter_file_name)
            return null
        }
        return if (sanitized.endsWith(".wav", ignoreCase = true)) sanitized else "$sanitized.wav"
    }

    private fun resetSuggestedFileName() {
        fileNameInput.setText(clipRepository.suggestedClipBaseName())
    }

    private fun showDeleteWavDialog() {
        val clips = clipRepository.listWavFiles()
        if (clips.isEmpty()) {
            statusText.text = getString(R.string.screen2_status_no_wavs_to_delete)
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.screen2_delete_wav_title)
            .setItems(clips.map { it.displayName }.toTypedArray()) { _, which ->
                val clip = clips[which]
                val deleted = clipRepository.deleteWav(clip.document)
                if (deleted) {
                    libraryRefreshSignal.markChanged()
                    lastSavedText.text = getString(R.string.screen2_last_deleted_value, clip.displayName)
                    statusText.text = getString(R.string.screen2_status_deleted_wav, clip.displayName)
                } else {
                    statusText.text = getString(R.string.screen2_status_delete_failed, clip.displayName)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startCountdownUpdates() {
        countdownRunnable.run()
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            val startedAt = recordingStartedAtMs ?: return
            val elapsed = System.currentTimeMillis() - startedAt
            countdownText.text = formatDurationLabel(elapsed)
            if (wavRecorder.isRecording()) {
                mainHandler.postDelayed(this, 100L)
            }
        }
    }

    private fun formatDurationLabel(elapsedMs: Long): String {
        val remainingMs = (MAX_DURATION_MS - elapsedMs).coerceAtLeast(0L)
        return getString(
            R.string.screen2_duration_format,
            formatSeconds(elapsedMs),
            formatSeconds(remainingMs),
        )
    }

    private fun formatSeconds(durationMs: Long): String {
        return String.format(Locale.US, "%.1fs", durationMs / 1000f)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        private const val MAX_DURATION_MS = 6_000L
    }
}
