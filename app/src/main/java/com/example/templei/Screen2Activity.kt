package com.example.templei

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.documentfile.provider.DocumentFile
import com.example.templei.feature.screen2.Screen2ClipRepository
import com.example.templei.feature.screen2.Screen2WavRecorder
import com.example.templei.feature.soundboard.ClipIndexRepository
import com.example.templei.feature.soundboard.Screen3LibraryRefreshSignal
import com.example.templei.feature.tutorial.TutorialScreen
import com.example.templei.feature.tutorial.TutorialSpotlightOverlayView
import com.example.templei.feature.tutorial.TutorialStatus
import com.example.templei.feature.tutorial.TutorialStore
import com.example.templei.ui.navigation.AppShellInsets
import com.example.templei.ui.navigation.AppShellNavigation
import java.io.File
import java.util.Locale

/**
 * Screen 2 recorder host with draft recording support and tutorial onboarding.
 *
 * Drafts are recorded into cache first, then exported only when the user saves
 * the take. Tutorial mode layers a deterministic five-clip flow on top.
 */
class Screen2Activity : ComponentActivity() {
    private lateinit var tutorialProgressText: TextView
    private lateinit var tutorialPromptText: TextView
    private lateinit var tutorialInstructionText: TextView
    private lateinit var statusText: TextView
    private lateinit var folderValueText: TextView
    private lateinit var countdownText: TextView
    private lateinit var lastSavedText: TextView
    private lateinit var fileNameInput: EditText
    private lateinit var chooseFolderButton: Button
    private lateinit var deleteWavButton: Button
    private lateinit var tutorialSkipButton: Button
    private lateinit var recordPadButton: Button
    private lateinit var saveClipButton: Button
    private lateinit var nextPromptButton: Button
    private lateinit var continueTutorialButton: Button
    private lateinit var closeTutorialButton: Button
    private lateinit var tutorialCard: LinearLayout
    private lateinit var tutorialHandoffCard: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tutorialOverlay: TutorialSpotlightOverlayView

    private lateinit var clipRepository: Screen2ClipRepository
    private lateinit var clipIndexRepository: ClipIndexRepository
    private lateinit var libraryRefreshSignal: Screen3LibraryRefreshSignal
    private lateinit var tutorialStore: TutorialStore
    private val wavRecorder = Screen2WavRecorder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tutorialMode: Boolean = false
    private var tutorialPromptIndex: Int = 0
    private var uiState: Screen2UiState = Screen2UiState.NEEDS_FOLDER
    private var recordingStartedAtMs: Long? = null
    private var draftOutputFile: File? = null
    private var draftDurationMs: Long? = null
    private var previewPlayer: MediaPlayer? = null
    private var recordPadLongPressConsumed: Boolean = false

    private val tutorialPrompts = listOf(
        Screen2TutorialPrompt(spokenPrompt = "so", suggestedBaseName = "tutorial_01_so"),
        Screen2TutorialPrompt(spokenPrompt = "re", suggestedBaseName = "tutorial_02_re"),
        Screen2TutorialPrompt(spokenPrompt = "mi", suggestedBaseName = "tutorial_03_mi"),
        Screen2TutorialPrompt(spokenPrompt = "do", suggestedBaseName = "tutorial_04_do"),
        Screen2TutorialPrompt(spokenPrompt = "la", suggestedBaseName = "tutorial_05_la"),
    )

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                renderStatus(getString(R.string.screen2_status_folder_pick_cancelled))
                return@registerForActivityResult
            }

            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }

            if (tutorialMode) {
                tutorialStore.setScreen2FolderConfirmed(true)
            }
            clipRepository.saveSelectedFolderUri(uri)
            bindSelectedFolder()
            if (tutorialMode) {
                syncTutorialFolderToSoundboard(uri)
            }
            transitionTo(determineInputDrivenState(), getString(R.string.screen2_status_folder_selected))
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                transitionTo(determineInputDrivenState(), getString(R.string.screen2_status_press_and_hold))
            } else {
                renderStatus(getString(R.string.screen2_status_permission_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen2)

        tutorialStore = TutorialStore(this)
        tutorialMode = shouldRunTutorial()
        tutorialPromptIndex = currentTutorialPromptIndex()

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
        clipIndexRepository = ClipIndexRepository(this)
        libraryRefreshSignal = Screen3LibraryRefreshSignal(this)

        bindViews()
        bindButtons()
        bindSelectedFolder()
        initializeFileName()
        transitionTo(determineInitialState(), defaultStatusFor(determineInitialState()))
    }

    override fun onPause() {
        if (wavRecorder.isRecording()) {
            finishDraftRecording(wasAutoStopped = false)
        }
        stopPreviewPlayback()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && tutorialMode) {
            renderTutorialSpotlight()
        }
    }

    override fun onDestroy() {
        stopPreviewPlayback()
        deleteDraftIfPresent()
        super.onDestroy()
    }

    private fun bindViews() {
        tutorialCard = findViewById(R.id.screen2TutorialCard)
        tutorialHandoffCard = findViewById(R.id.screen2TutorialHandoffCard)
        tutorialProgressText = findViewById(R.id.screen2TutorialProgressText)
        tutorialPromptText = findViewById(R.id.screen2TutorialPromptText)
        tutorialInstructionText = findViewById(R.id.screen2TutorialInstructionText)
        tutorialSkipButton = findViewById(R.id.screen2TutorialSkipButton)
        statusText = findViewById(R.id.screen2StatusText)
        folderValueText = findViewById(R.id.screen2FolderValue)
        countdownText = findViewById(R.id.screen2CountdownText)
        lastSavedText = findViewById(R.id.screen2LastSavedText)
        fileNameInput = findViewById(R.id.screen2FileNameInput)
        chooseFolderButton = findViewById(R.id.screen2ChooseFolderButton)
        deleteWavButton = findViewById(R.id.screen2DeleteWavButton)
        recordPadButton = findViewById(R.id.screen2RecordPadButton)
        saveClipButton = findViewById(R.id.screen2SaveClipButton)
        nextPromptButton = findViewById(R.id.screen2NextPromptButton)
        continueTutorialButton = findViewById(R.id.screen2ContinueTutorialButton)
        closeTutorialButton = findViewById(R.id.screen2CloseTutorialButton)
        scrollView = findViewById(R.id.screen2ScrollView)
        tutorialOverlay = findViewById(R.id.screen2TutorialOverlay)
    }

    private fun bindButtons() {
        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            if (tutorialMode) {
                tutorialOverlay.invalidate()
            }
        }
        chooseFolderButton.setOnClickListener {
            folderPickerLauncher.launch(clipRepository.selectedFolderUri())
        }
        deleteWavButton.setOnClickListener {
            showDeleteWavDialog()
        }
        tutorialSkipButton.setOnClickListener {
            closeTutorialAndKeepRecording()
        }
        saveClipButton.setOnClickListener {
            saveDraftClip()
        }
        nextPromptButton.setOnClickListener {
            advanceTutorialPrompt()
        }
        continueTutorialButton.setOnClickListener {
            continueToFavoritePads()
        }
        closeTutorialButton.setOnClickListener {
            closeTutorialAndKeepRecording()
        }
        fileNameInput.doAfterTextChanged {
            if (uiState == Screen2UiState.RECORDING || uiState == Screen2UiState.SAVING || uiState == Screen2UiState.SCREEN_COMPLETE) {
                return@doAfterTextChanged
            }
            if (hasDraft()) {
                renderState(statusText.text?.toString().orEmpty())
            } else {
                transitionTo(determineInputDrivenState(), defaultStatusFor(determineInputDrivenState()))
            }
        }
        recordPadButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    recordPadLongPressConsumed = false
                    mainHandler.postDelayed(recordPadLongPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(recordPadLongPressRunnable)
                    if (wavRecorder.isRecording()) {
                        finishDraftRecording(wasAutoStopped = false)
                    } else if (!recordPadLongPressConsumed) {
                        handleRecordPadTap()
                    }
                    recordPadLongPressConsumed = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(recordPadLongPressRunnable)
                    if (wavRecorder.isRecording()) {
                        finishDraftRecording(wasAutoStopped = false)
                    }
                    recordPadLongPressConsumed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun bindSelectedFolder() {
        folderValueText.text = clipRepository.selectedFolderLabel()
            ?: getString(R.string.screen2_folder_unselected)
    }

    private fun initializeFileName() {
        applySuggestedFileName(force = true)
    }

    private fun shouldRunTutorial(): Boolean {
        val progress = tutorialStore.loadProgress()
        return progress.status == TutorialStatus.IN_PROGRESS && progress.currentScreen == TutorialScreen.SCREEN2
    }

    private fun currentTutorialPromptIndex(): Int {
        val progress = tutorialStore.loadProgress()
        return progress.screen2PromptIndex.coerceIn(0, tutorialPrompts.lastIndex)
    }

    private fun determineInitialState(): Screen2UiState {
        val progress = tutorialStore.loadProgress()
        if (tutorialMode && progress.completedScreens.contains(TutorialScreen.SCREEN2)) {
            return Screen2UiState.SCREEN_COMPLETE
        }
        if (tutorialMode && progress.currentStepIndex == tutorialStepIndexFor(Screen2UiState.PROMPT_SAVED)) {
            return Screen2UiState.PROMPT_SAVED
        }
        return determineInputDrivenState()
    }

    private fun determineInputDrivenState(): Screen2UiState {
        if (tutorialMode && !tutorialStore.loadProgress().screen2FolderConfirmed) {
            return Screen2UiState.NEEDS_FOLDER
        }
        return when {
            clipRepository.selectedFolderUri() == null -> Screen2UiState.NEEDS_FOLDER
            !hasValidOutputFileName() -> Screen2UiState.NEEDS_FILE_NAME
            hasDraft() -> Screen2UiState.DRAFT_READY
            else -> Screen2UiState.READY_TO_RECORD
        }
    }

    private fun transitionTo(nextState: Screen2UiState, status: String) {
        uiState = nextState
        if (tutorialMode && nextState != Screen2UiState.SCREEN_COMPLETE) {
            tutorialStore.setScreen2PromptIndex(
                promptIndex = tutorialPromptIndex,
                stepIndex = tutorialStepIndexFor(nextState),
            )
        }
        renderState(status)
    }

    private fun renderState(status: String) {
        renderStatus(status)
        tutorialCard.isVisible = tutorialMode && uiState != Screen2UiState.SCREEN_COMPLETE
        tutorialHandoffCard.isVisible = tutorialMode && uiState == Screen2UiState.SCREEN_COMPLETE
        deleteWavButton.isVisible = !tutorialMode
        nextPromptButton.isVisible = tutorialMode && uiState == Screen2UiState.PROMPT_SAVED

        chooseFolderButton.isEnabled = uiState !in setOf(
            Screen2UiState.RECORDING,
            Screen2UiState.SAVING,
            Screen2UiState.PROMPT_SAVED,
            Screen2UiState.SCREEN_COMPLETE,
        )
        fileNameInput.isEnabled = uiState !in setOf(
            Screen2UiState.RECORDING,
            Screen2UiState.SAVING,
            Screen2UiState.PROMPT_SAVED,
            Screen2UiState.SCREEN_COMPLETE,
        )
        recordPadButton.isEnabled = uiState in setOf(Screen2UiState.READY_TO_RECORD, Screen2UiState.DRAFT_READY, Screen2UiState.RECORDING)
        saveClipButton.isEnabled = uiState == Screen2UiState.DRAFT_READY
        nextPromptButton.isEnabled = uiState == Screen2UiState.PROMPT_SAVED

        recordPadButton.text = when {
            uiState == Screen2UiState.RECORDING -> getString(R.string.screen2_record_pad_recording)
            hasDraft() -> getString(R.string.screen2_record_pad_draft)
            else -> getString(R.string.screen2_record_pad_ready)
        }

        countdownText.text = when {
            uiState == Screen2UiState.RECORDING -> countdownText.text
            draftDurationMs != null -> formatDurationLabel(draftDurationMs ?: 0L)
            else -> getString(R.string.screen2_duration_initial)
        }

        if (tutorialMode) {
            val prompt = tutorialPrompts[tutorialPromptIndex]
            tutorialProgressText.text = getString(
                R.string.screen2_tutorial_progress,
                tutorialPromptIndex + 1,
                tutorialPrompts.size,
            )
            tutorialPromptText.text = getString(R.string.screen2_tutorial_prompt_label, prompt.spokenPrompt)
            tutorialInstructionText.text = when (uiState) {
                Screen2UiState.NEEDS_FOLDER -> getString(R.string.screen2_tutorial_folder_instruction)
                Screen2UiState.NEEDS_FILE_NAME -> getString(R.string.screen2_tutorial_name_instruction)
                Screen2UiState.READY_TO_RECORD,
                Screen2UiState.RECORDING -> getString(R.string.screen2_tutorial_record_instruction)
                Screen2UiState.DRAFT_READY,
                Screen2UiState.SAVING,
                Screen2UiState.PROMPT_SAVED -> getString(R.string.screen2_tutorial_save_instruction)
                Screen2UiState.SCREEN_COMPLETE -> getString(R.string.screen2_tutorial_complete_instruction)
            }
        }
        renderTutorialCardEmphasis()
        renderTutorialSpotlight()
    }

    private fun renderStatus(status: String) {
        statusText.text = status
    }

    private fun handleRecordPadTap() {
        when {
            hasDraft() -> previewDraft()
            uiState == Screen2UiState.NEEDS_FOLDER -> renderStatus(getString(R.string.screen2_status_select_folder_first))
            uiState == Screen2UiState.NEEDS_FILE_NAME -> renderStatus(getString(R.string.screen2_status_enter_file_name))
            else -> renderStatus(getString(R.string.screen2_status_press_and_hold))
        }
    }

    private fun handleRecordPadLongPress() {
        recordPadLongPressConsumed = true

        if (uiState == Screen2UiState.SCREEN_COMPLETE || uiState == Screen2UiState.PROMPT_SAVED || uiState == Screen2UiState.SAVING) {
            return
        }
        if (clipRepository.selectedFolderUri() == null) {
            transitionTo(Screen2UiState.NEEDS_FOLDER, getString(R.string.screen2_status_select_folder_first))
            return
        }
        if (!hasValidOutputFileName()) {
            transitionTo(Screen2UiState.NEEDS_FILE_NAME, getString(R.string.screen2_status_enter_file_name))
            return
        }
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        beginDraftRecording()
    }

    private fun beginDraftRecording() {
        if (wavRecorder.isRecording()) return

        stopPreviewPlayback()
        val candidateFile = File(cacheDir, "screen2_draft_${System.currentTimeMillis()}.wav")
        wavRecorder.start(candidateFile) {
            finishDraftRecording(wasAutoStopped = true)
        }.onSuccess {
            recordingStartedAtMs = System.currentTimeMillis()
            transitionTo(Screen2UiState.RECORDING, getString(R.string.screen2_status_release_to_stop))
            startCountdownUpdates()
        }.onFailure { error ->
            transitionTo(
                determineInputDrivenState(),
                getString(
                    R.string.screen2_status_recording_failed,
                    error.message ?: getString(R.string.screen2_unknown_error),
                ),
            )
        }
    }

    private fun finishDraftRecording(wasAutoStopped: Boolean) {
        if (!wavRecorder.isRecording() && recordingStartedAtMs == null) return

        wavRecorder.stop()
            .onSuccess { result ->
                mainHandler.removeCallbacks(countdownRunnable)
                val previousDraft = draftOutputFile
                draftOutputFile = result.outputFile
                draftDurationMs = result.durationMs
                recordingStartedAtMs = null
                if (previousDraft != null && previousDraft.absolutePath != result.outputFile.absolutePath) {
                    previousDraft.delete()
                }
                val status = if (wasAutoStopped || result.reachedMaxDuration) {
                    getString(R.string.screen2_status_draft_ready)
                } else {
                    getString(R.string.screen2_status_draft_ready)
                }
                transitionTo(Screen2UiState.DRAFT_READY, status)
            }
            .onFailure { error ->
                recordingStartedAtMs = null
                transitionTo(
                    determineInputDrivenState(),
                    getString(
                        R.string.screen2_status_recording_failed,
                        error.message ?: getString(R.string.screen2_unknown_error),
                    ),
                )
            }
    }

    private fun saveDraftClip() {
        val outputFileName = resolveOutputFileName() ?: run {
            transitionTo(Screen2UiState.NEEDS_FILE_NAME, getString(R.string.screen2_status_enter_file_name))
            return
        }
        val draftFile = draftOutputFile ?: run {
            renderStatus(getString(R.string.screen2_status_draft_missing))
            return
        }

        stopPreviewPlayback()
        transitionTo(Screen2UiState.SAVING, getString(R.string.screen2_status_saving))

        runCatching {
            clipRepository.saveRecording(draftFile, outputFileName)
        }.onSuccess { savedClip ->
            libraryRefreshSignal.markChanged()
            lastSavedText.text = getString(
                R.string.screen2_last_saved_value,
                savedClip.fileName,
                savedClip.folderLabel,
            )
            deleteDraftIfPresent()

            if (tutorialMode) {
                if (tutorialPromptIndex >= tutorialPrompts.lastIndex) {
                    tutorialStore.markScreenCompleted(TutorialScreen.SCREEN2, TutorialScreen.SCREEN3)
                    transitionTo(Screen2UiState.SCREEN_COMPLETE, getString(R.string.screen2_status_tutorial_complete))
                } else {
                    transitionTo(Screen2UiState.PROMPT_SAVED, getString(R.string.screen2_status_prompt_saved))
                }
            } else {
                applySuggestedFileName(force = true)
                transitionTo(Screen2UiState.READY_TO_RECORD, getString(R.string.screen2_status_recording_saved, savedClip.fileName))
            }
        }.onFailure { error ->
            transitionTo(
                Screen2UiState.DRAFT_READY,
                getString(
                    R.string.screen2_status_save_failed,
                    error.message ?: getString(R.string.screen2_unknown_error),
                ),
            )
        }
    }

    private fun advanceTutorialPrompt() {
        if (!tutorialMode || tutorialPromptIndex >= tutorialPrompts.lastIndex) return
        tutorialPromptIndex += 1
        applySuggestedFileName(force = true)
        transitionTo(Screen2UiState.READY_TO_RECORD, getString(R.string.screen2_status_press_and_hold))
    }

    private fun continueToFavoritePads() {
        tutorialStore.setCurrentScreen(TutorialScreen.SCREEN3)
        val selectedFolderUri = clipRepository.selectedFolderUri()
        startActivity(
            Intent(this, Screen3Activity::class.java)
                .putExtra(Screen3Activity.EXTRA_FORCE_REBUILD_ON_START, true)
                .apply {
                    selectedFolderUri?.let { putExtra(Screen3Activity.EXTRA_TUTORIAL_ROOT_URI, it.toString()) }
                }
        )
    }

    private fun closeTutorialAndKeepRecording() {
        tutorialStore.skipTutorial()
        tutorialMode = false
        applySuggestedFileName(force = true)
        transitionTo(determineInputDrivenState(), defaultStatusFor(determineInputDrivenState()))
    }

    private fun previewDraft() {
        val draftFile = draftOutputFile ?: run {
            renderStatus(getString(R.string.screen2_status_draft_missing))
            return
        }

        runCatching {
            stopPreviewPlayback()
            MediaPlayer().apply {
                setDataSource(draftFile.absolutePath)
                setOnCompletionListener {
                    stopPreviewPlayback()
                    if (uiState == Screen2UiState.DRAFT_READY) {
                        renderStatus(getString(R.string.screen2_status_draft_ready))
                    }
                }
                prepare()
                start()
            }.also { previewPlayer = it }
        }.onSuccess {
            renderStatus(getString(R.string.screen2_status_previewing))
        }.onFailure { error ->
            renderStatus(
                getString(
                    R.string.screen2_status_preview_failed,
                    error.message ?: getString(R.string.screen2_unknown_error),
                ),
            )
        }
    }

    private fun stopPreviewPlayback() {
        previewPlayer?.runCatching {
            stop()
        }
        previewPlayer?.release()
        previewPlayer = null
    }

    private fun resolveOutputFileName(): String? {
        val rawValue = fileNameInput.text?.toString().orEmpty().trim()
        if (rawValue.isBlank()) {
            return null
        }
        val sanitized = rawValue.replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
        if (sanitized.isBlank()) {
            return null
        }
        return if (sanitized.endsWith(".wav", ignoreCase = true)) sanitized else "$sanitized.wav"
    }

    private fun hasValidOutputFileName(): Boolean = resolveOutputFileName() != null

    private fun applySuggestedFileName(force: Boolean) {
        if (!force && fileNameInput.text?.isNotBlank() == true) return
        val suggestedValue = if (tutorialMode) {
            tutorialPrompts[tutorialPromptIndex].suggestedBaseName
        } else {
            clipRepository.suggestedClipBaseName()
        }
        fileNameInput.setText(suggestedValue)
    }

    private fun showDeleteWavDialog() {
        val clips = clipRepository.listWavFiles()
        if (clips.isEmpty()) {
            renderStatus(getString(R.string.screen2_status_no_wavs_to_delete))
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
                    renderStatus(getString(R.string.screen2_status_deleted_wav, clip.displayName))
                } else {
                    renderStatus(getString(R.string.screen2_status_delete_failed, clip.displayName))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun syncTutorialFolderToSoundboard(uri: android.net.Uri) {
        runCatching {
            clipIndexRepository.setPersistedRootUri(uri)
            clipIndexRepository.rebuildIndex(uri)
            val folderName = DocumentFile.fromTreeUri(this, uri)?.name
            clipIndexRepository.setPersistedSelectedFolderName(folderName)
        }
    }

    private fun renderTutorialSpotlight() {
        if (!tutorialMode) {
            tutorialOverlay.clearTargets()
            return
        }

        val targets = spotlightTargetsFor(uiState)
        if (targets.isEmpty()) {
            tutorialOverlay.clearTargets()
            return
        }

        val primaryTarget = primarySpotlightTargetFor(uiState)
        tutorialOverlay.post {
            scrollPrimaryTargetIntoView(primaryTarget)
            tutorialOverlay.post {
                tutorialOverlay.showTargets(targets)
            }
        }
    }

    private fun spotlightTargetsFor(state: Screen2UiState): List<View> = when (state) {
        Screen2UiState.NEEDS_FOLDER -> listOf(tutorialCard, chooseFolderButton)
        Screen2UiState.NEEDS_FILE_NAME -> listOf(tutorialCard, fileNameInput)
        Screen2UiState.READY_TO_RECORD,
        Screen2UiState.RECORDING -> listOf(tutorialCard, recordPadButton, fileNameInput)
        Screen2UiState.DRAFT_READY -> listOf(tutorialCard, saveClipButton, recordPadButton, fileNameInput)
        Screen2UiState.SAVING -> emptyList()
        Screen2UiState.PROMPT_SAVED -> listOf(tutorialCard, nextPromptButton)
        Screen2UiState.SCREEN_COMPLETE -> listOf(tutorialHandoffCard, continueTutorialButton, closeTutorialButton)
    }.filter { it.isShown }

    private fun primarySpotlightTargetFor(state: Screen2UiState): View = when (state) {
        Screen2UiState.NEEDS_FOLDER -> chooseFolderButton
        Screen2UiState.NEEDS_FILE_NAME -> fileNameInput
        Screen2UiState.READY_TO_RECORD,
        Screen2UiState.RECORDING -> recordPadButton
        Screen2UiState.DRAFT_READY -> saveClipButton
        Screen2UiState.SAVING -> recordPadButton
        Screen2UiState.PROMPT_SAVED -> nextPromptButton
        Screen2UiState.SCREEN_COMPLETE -> continueTutorialButton
    }

    private fun scrollPrimaryTargetIntoView(target: View) {
        val focusRect = Rect(0, -resources.displayMetrics.density.times(16).toInt(), target.width, target.height)
        target.requestRectangleOnScreen(focusRect, true)
    }

    private fun renderTutorialCardEmphasis() {
        val emphasized = tutorialMode && uiState != Screen2UiState.SCREEN_COMPLETE
        tutorialCard.animate()
            .scaleX(if (emphasized) 1.04f else 1f)
            .scaleY(if (emphasized) 1.04f else 1f)
            .setDuration(180L)
            .start()
    }

    private fun hasDraft(): Boolean = draftOutputFile?.exists() == true && draftDurationMs != null

    private fun deleteDraftIfPresent() {
        draftOutputFile?.delete()
        draftOutputFile = null
        draftDurationMs = null
        countdownText.text = getString(R.string.screen2_duration_initial)
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

    private val recordPadLongPressRunnable = Runnable {
        handleRecordPadLongPress()
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

    private fun defaultStatusFor(state: Screen2UiState): String = when (state) {
        Screen2UiState.NEEDS_FOLDER -> getString(R.string.screen2_status_select_folder_first)
        Screen2UiState.NEEDS_FILE_NAME -> getString(R.string.screen2_status_review_name)
        Screen2UiState.READY_TO_RECORD -> getString(R.string.screen2_status_press_and_hold)
        Screen2UiState.RECORDING -> getString(R.string.screen2_status_release_to_stop)
        Screen2UiState.DRAFT_READY -> getString(R.string.screen2_status_draft_ready)
        Screen2UiState.SAVING -> getString(R.string.screen2_status_saving)
        Screen2UiState.PROMPT_SAVED -> getString(R.string.screen2_status_prompt_saved)
        Screen2UiState.SCREEN_COMPLETE -> getString(R.string.screen2_status_tutorial_complete)
    }

    private fun tutorialStepIndexFor(state: Screen2UiState): Int = when (state) {
        Screen2UiState.NEEDS_FOLDER -> 0
        Screen2UiState.NEEDS_FILE_NAME -> 1
        Screen2UiState.READY_TO_RECORD,
        Screen2UiState.RECORDING -> 2
        Screen2UiState.DRAFT_READY,
        Screen2UiState.SAVING -> 3
        Screen2UiState.PROMPT_SAVED -> 4
        Screen2UiState.SCREEN_COMPLETE -> 5
    }

    private data class Screen2TutorialPrompt(
        val spokenPrompt: String,
        val suggestedBaseName: String,
    )

    private enum class Screen2UiState {
        NEEDS_FOLDER,
        NEEDS_FILE_NAME,
        READY_TO_RECORD,
        RECORDING,
        DRAFT_READY,
        SAVING,
        PROMPT_SAVED,
        SCREEN_COMPLETE,
    }

    private companion object {
        private const val MAX_DURATION_MS = 6_000L
    }
}
