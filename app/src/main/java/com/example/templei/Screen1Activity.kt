package com.example.templei

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.templei.feature.camera.Screen1CameraController
import com.example.templei.feature.camera.Screen1CameraCaptureState
import com.example.templei.feature.camera.Screen1CameraFeedState
import com.example.templei.feature.camera.Screen1FolderTarget
import com.example.templei.feature.camera.Screen1MediaRepository
import com.example.templei.feature.camera.Screen1MediaType
import com.example.templei.feature.camera.Screen1StorageMode
import com.example.templei.feature.screen4.Screen4MusicRuntime
import com.example.templei.feature.screen4.Screen4TransportState
import com.example.templei.feature.screen4.Screen4UiState
import com.example.templei.feature.tutorial.TutorialScreen
import com.example.templei.feature.tutorial.TutorialSpotlightOverlayView
import com.example.templei.feature.tutorial.TutorialStatus
import com.example.templei.feature.tutorial.TutorialStore
import com.example.templei.ui.navigation.AppShellInsets
import com.example.templei.ui.navigation.AppShellNavigation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shell host for Screen 1 camera controls and preview.
 */
class Screen1Activity : ComponentActivity() {
    private lateinit var controller: Screen1CameraController
    private lateinit var mediaRepository: Screen1MediaRepository
    private val screen4Coordinator by lazy { Screen4MusicRuntime.coordinator(applicationContext) }
    private val tutorialStore by lazy { TutorialStore(this) }
    private var tutorialMode: Boolean = false
    private var tutorialFeedState: Screen1CameraFeedState = Screen1CameraFeedState.Stopped
    private var tutorialCaptureState: Screen1CameraCaptureState = Screen1CameraCaptureState.Idle
    private var tutorialVideoSaved: Boolean = false
    private var screen4UiState: Screen4UiState = Screen4UiState()

    private lateinit var cameraStatusText: TextView
    private lateinit var tutorialCard: LinearLayout
    private lateinit var tutorialProgressText: TextView
    private lateinit var tutorialPromptText: TextView
    private lateinit var tutorialInstructionText: TextView
    private lateinit var tutorialSkipButton: Button
    private lateinit var tutorialOverlay: TutorialSpotlightOverlayView
    private lateinit var mainScrollView: ScrollView
    private lateinit var startFeedButton: Button
    private lateinit var recordVideoButton: Button
    private lateinit var sequencerToggleButton: Button
    private lateinit var storageModeButton: Button
    private lateinit var photoFolderLabel: TextView
    private lateinit var photoFolderValue: TextView
    private lateinit var choosePhotoFolderButton: Button
    private lateinit var videoFolderGroup: LinearLayout
    private lateinit var videoFolderValue: TextView
    private lateinit var chooseVideoFolderButton: Button
    private var pendingFolderTarget: Screen1FolderTarget = Screen1FolderTarget.Shared

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val cameraGranted = grantResults[Manifest.permission.CAMERA] == true ||
            hasPermission(Manifest.permission.CAMERA)
        if (cameraGranted) {
            controller.startFeed()
        } else {
            cameraStatusText.text = getString(R.string.screen1_status_permission_denied)
        }
    }

    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                cameraStatusText.text = getString(R.string.screen1_status_storage_pick_cancelled)
                return@registerForActivityResult
            }

            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }

            mediaRepository.saveFolderUri(pendingFolderTarget, uri)
            if (tutorialMode && pendingFolderTarget == Screen1FolderTarget.Shared) {
                tutorialStore.setScreen1MediaFolderConfirmed(true)
            }
            bindStorageConfiguration()
            controller.refreshMediaLibrary()
            cameraStatusText.text = getString(
                when (pendingFolderTarget) {
                    Screen1FolderTarget.Shared -> R.string.screen1_status_shared_folder_selected
                    Screen1FolderTarget.Photo -> R.string.screen1_status_photo_folder_selected
                    Screen1FolderTarget.Video -> R.string.screen1_status_video_folder_selected
                }
            )
            renderTutorialChrome()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen1)
        tutorialMode = shouldRunTutorial()
        AppShellNavigation.bind(
            activity = this,
            currentDestination = Screen1Activity::class.java,
            title = getString(R.string.screen1TitleText),
            chipText = getString(R.string.screen1_header_chip),
        )
        AppShellInsets.apply(
            activity = this,
            rootId = R.id.screen1Root,
            scrollViewId = R.id.screen1ScrollView,
        )

        mediaRepository = Screen1MediaRepository(this)
        prepareTutorialMediaFolderModeIfNeeded()
        bindStorageViews()
        bindTutorialViews()

        controller = Screen1CameraController(
            activity = this,
            repository = mediaRepository,
            views = Screen1CameraController.Views(
                renderContainer = findViewById(R.id.renderContainer),
                renderPlaceholder = findViewById(R.id.renderPlaceholderText),
                startFeedButton = startFeedButton,
                stopFeedButton = findViewById(R.id.stopFeedButton),
                capturePhotoButton = findViewById(R.id.capturePhotoButton),
                recordVideoButton = recordVideoButton,
                mediaSpinner = findViewById(R.id.mediaSpinner),
                selectionPreviewImage = findViewById(R.id.selectedMediaPreview),
                selectionLabel = findViewById(R.id.selectedMediaLabel),
                statusText = cameraStatusText,
            )
        )
        controller.onStateChanged = { feedState, captureState ->
            tutorialFeedState = feedState
            tutorialCaptureState = captureState
            renderTutorialChrome()
        }
        controller.onMediaSaved = { mediaType ->
            if (mediaType == Screen1MediaType.Video) {
                tutorialVideoSaved = true
                renderTutorialChrome()
            }
        }
        controller.bind()
        bindStorageConfiguration()
        bindSequencerControls()
        renderTutorialChrome()

        startFeedButton.setOnClickListener {
            ensureCameraPermissionsAndStart()
        }
        findViewById<Button>(R.id.stopFeedButton).setOnClickListener {
            controller.stopFeed()
        }
        findViewById<Button>(R.id.capturePhotoButton).setOnClickListener {
            controller.capturePhoto()
        }
        recordVideoButton.setOnClickListener {
            if (!hasPermission(Manifest.permission.CAMERA)) {
                ensureCameraPermissionsAndStart()
            } else {
                controller.toggleRecording(withAudio = hasPermission(Manifest.permission.RECORD_AUDIO))
            }
        }
        findViewById<Spinner>(R.id.mediaSpinner).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    controller.handleMediaSelection(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    controller.handleMediaSelection(0)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        bindStorageConfiguration()
        controller.refreshMediaLibrary()
        renderTutorialChrome()
    }

    override fun onPause() {
        controller.release()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && tutorialMode) {
            renderTutorialChrome()
        }
    }

    private fun ensureCameraPermissionsAndStart() {
        if (hasPermission(Manifest.permission.CAMERA)) {
            controller.startFeed()
            return
        }

        permissionRequestLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )
    }

    private fun bindStorageViews() {
        cameraStatusText = findViewById(R.id.cameraStatusText)
        startFeedButton = findViewById(R.id.startFeedButton)
        recordVideoButton = findViewById(R.id.recordVideoButton)
        sequencerToggleButton = findViewById(R.id.screen1SequencerToggleButton)
        storageModeButton = findViewById(R.id.screen1StorageModeButton)
        photoFolderLabel = findViewById(R.id.screen1PhotoFolderLabel)
        photoFolderValue = findViewById(R.id.screen1PhotoFolderValue)
        choosePhotoFolderButton = findViewById(R.id.screen1ChoosePhotoFolderButton)
        videoFolderGroup = findViewById(R.id.screen1VideoFolderGroup)
        videoFolderValue = findViewById(R.id.screen1VideoFolderValue)
        chooseVideoFolderButton = findViewById(R.id.screen1ChooseVideoFolderButton)

        storageModeButton.setOnClickListener {
            val nextMode = when (mediaRepository.storageMode()) {
                Screen1StorageMode.Shared -> Screen1StorageMode.Separate
                Screen1StorageMode.Separate -> Screen1StorageMode.Shared
            }
            mediaRepository.setStorageMode(nextMode)
            bindStorageConfiguration()
            controller.refreshMediaLibrary()
            cameraStatusText.text = getString(
                when (nextMode) {
                    Screen1StorageMode.Shared -> R.string.screen1_status_storage_shared_enabled
                    Screen1StorageMode.Separate -> R.string.screen1_status_storage_separate_enabled
                }
            )
        }

        choosePhotoFolderButton.setOnClickListener {
            pendingFolderTarget = when (mediaRepository.storageMode()) {
                Screen1StorageMode.Shared -> Screen1FolderTarget.Shared
                Screen1StorageMode.Separate -> Screen1FolderTarget.Photo
            }
            folderPickerLauncher.launch(mediaRepository.selectedFolderUri(pendingFolderTarget))
        }

        chooseVideoFolderButton.setOnClickListener {
            pendingFolderTarget = Screen1FolderTarget.Video
            folderPickerLauncher.launch(mediaRepository.selectedFolderUri(Screen1FolderTarget.Video))
        }
    }

    private fun bindSequencerControls() {
        sequencerToggleButton.setOnClickListener {
            if (isSequencerRunning()) {
                screen4Coordinator.stop()
            } else {
                screen4Coordinator.play()
            }
        }

        lifecycleScope.launch {
            screen4Coordinator.uiState.collectLatest { state ->
                screen4UiState = state
                renderSequencerToggle()
            }
        }
    }

    private fun bindTutorialViews() {
        tutorialCard = findViewById(R.id.screen1TutorialCard)
        tutorialProgressText = findViewById(R.id.screen1TutorialProgressText)
        tutorialPromptText = findViewById(R.id.screen1TutorialPromptText)
        tutorialInstructionText = findViewById(R.id.screen1TutorialInstructionText)
        tutorialSkipButton = findViewById(R.id.screen1TutorialSkipButton)
        tutorialOverlay = findViewById(R.id.screen1TutorialOverlay)
        mainScrollView = findViewById(R.id.screen1ScrollView)

        tutorialSkipButton.setOnClickListener {
            tutorialStore.skipTutorial()
            tutorialMode = false
            renderTutorialChrome()
        }

        mainScrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            if (tutorialMode) {
                tutorialOverlay.invalidate()
            }
        }
    }

    private fun bindStorageConfiguration() {
        val mode = mediaRepository.storageMode()
        storageModeButton.isEnabled = !tutorialMode
        storageModeButton.text = getString(
            when (mode) {
                Screen1StorageMode.Shared -> R.string.screen1_storage_mode_shared
                Screen1StorageMode.Separate -> R.string.screen1_storage_mode_separate
            }
        )

        if (mode == Screen1StorageMode.Shared) {
            photoFolderLabel.text = getString(R.string.screen1_storage_shared_folder_label)
            choosePhotoFolderButton.text = getString(R.string.screen1_choose_shared_folder)
            videoFolderGroup.visibility = View.GONE
            photoFolderValue.text = mediaRepository.selectedFolderLabel(Screen1FolderTarget.Shared)
                ?: getString(R.string.screen1_storage_folder_unselected)
        } else {
            photoFolderLabel.text = getString(R.string.screen1_storage_photo_folder_label)
            choosePhotoFolderButton.text = getString(R.string.screen1_choose_photo_folder)
            videoFolderGroup.visibility = View.VISIBLE
            photoFolderValue.text = mediaRepository.selectedFolderLabel(Screen1FolderTarget.Photo)
                ?: getString(R.string.screen1_storage_folder_unselected)
            videoFolderValue.text = mediaRepository.selectedFolderLabel(Screen1FolderTarget.Video)
                ?: getString(R.string.screen1_storage_folder_unselected)
        }
    }

    private fun prepareTutorialMediaFolderModeIfNeeded() {
        if (!tutorialMode) return
        if (mediaRepository.storageMode() != Screen1StorageMode.Shared) {
            mediaRepository.setStorageMode(Screen1StorageMode.Shared)
        }
        tutorialVideoSaved = false
    }

    private fun renderSequencerToggle() {
        if (!::sequencerToggleButton.isInitialized) return
        sequencerToggleButton.text = getString(
            if (isSequencerRunning()) {
                R.string.screen1_sequencer_toggle_stop
            } else {
                R.string.screen1_sequencer_toggle_start
            }
        )
    }

    private fun isSequencerRunning(): Boolean {
        return screen4UiState.isPlaying || screen4UiState.transportState == Screen4TransportState.STARTING
    }

    private fun shouldRunTutorial(): Boolean {
        val progress = tutorialStore.loadProgress()
        return progress.status == TutorialStatus.IN_PROGRESS && progress.currentScreen == TutorialScreen.SCREEN1
    }

    private fun renderTutorialChrome() {
        if (!::tutorialCard.isInitialized) return
        if (::storageModeButton.isInitialized) {
            storageModeButton.isEnabled = !tutorialMode
        }
        if (!tutorialMode) {
            tutorialCard.isVisible = false
            tutorialCard.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            tutorialOverlay.clearTargets()
            return
        }

        reconcileTutorialProgress()
        if (!tutorialMode) {
            tutorialCard.isVisible = false
            tutorialCard.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
            tutorialOverlay.clearTargets()
            return
        }

        val phase = currentTutorialPhase()
        tutorialCard.isVisible = phase != Screen1TutorialPhase.COMPLETE
        if (phase == Screen1TutorialPhase.COMPLETE) {
            tutorialOverlay.clearTargets()
            return
        }

        tutorialProgressText.text = when (phase) {
            Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER -> getString(R.string.screen1_tutorial_progress, 1, 4)
            Screen1TutorialPhase.START_CAMERA -> getString(R.string.screen1_tutorial_progress, 2, 4)
            Screen1TutorialPhase.RECORD_VIDEO -> getString(R.string.screen1_tutorial_progress, 3, 4)
            Screen1TutorialPhase.STOP_AND_SAVE -> getString(R.string.screen1_tutorial_progress, 4, 4)
            Screen1TutorialPhase.COMPLETE -> ""
        }
        tutorialPromptText.text = when (phase) {
            Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER -> getString(R.string.screen1_tutorial_choose_folder_title)
            Screen1TutorialPhase.START_CAMERA -> getString(R.string.screen1_tutorial_start_camera_title)
            Screen1TutorialPhase.RECORD_VIDEO -> getString(R.string.screen1_tutorial_record_title)
            Screen1TutorialPhase.STOP_AND_SAVE -> getString(R.string.screen1_tutorial_stop_save_title)
            Screen1TutorialPhase.COMPLETE -> ""
        }
        tutorialInstructionText.text = when (phase) {
            Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER -> getString(R.string.screen1_tutorial_choose_folder_instruction)
            Screen1TutorialPhase.START_CAMERA -> getString(R.string.screen1_tutorial_start_camera_instruction)
            Screen1TutorialPhase.RECORD_VIDEO -> getString(R.string.screen1_tutorial_record_instruction)
            Screen1TutorialPhase.STOP_AND_SAVE -> getString(R.string.screen1_tutorial_stop_save_instruction)
            Screen1TutorialPhase.COMPLETE -> ""
        }

        tutorialCard.animate()
            .scaleX(1.04f)
            .scaleY(1.04f)
            .setDuration(180L)
            .start()

        val targets = when (phase) {
            Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER -> listOf(tutorialCard, choosePhotoFolderButton)
            Screen1TutorialPhase.START_CAMERA -> listOf(tutorialCard, startFeedButton)
            Screen1TutorialPhase.RECORD_VIDEO -> listOf(tutorialCard, recordVideoButton)
            Screen1TutorialPhase.STOP_AND_SAVE -> listOf(tutorialCard, recordVideoButton)
            Screen1TutorialPhase.COMPLETE -> emptyList()
        }.filter { it.isShown }

        if (targets.isEmpty()) {
            tutorialOverlay.clearTargets()
            return
        }

        tutorialOverlay.post {
            scrollTutorialTargetsIntoView(targets)
            tutorialOverlay.post {
                tutorialOverlay.showTargets(targets)
            }
        }
    }

    private fun reconcileTutorialProgress() {
        when (currentTutorialPhase()) {
            Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER -> {
                if (tutorialStore.loadProgress().screen1MediaFolderConfirmed) {
                    tutorialStore.setCurrentScreen(TutorialScreen.SCREEN1, STEP_START_CAMERA)
                }
            }
            Screen1TutorialPhase.START_CAMERA -> {
                if (tutorialFeedState == Screen1CameraFeedState.Live) {
                    tutorialStore.setCurrentScreen(TutorialScreen.SCREEN1, STEP_RECORD_VIDEO)
                }
            }
            Screen1TutorialPhase.RECORD_VIDEO -> {
                if (tutorialCaptureState == Screen1CameraCaptureState.Recording) {
                    tutorialStore.setCurrentScreen(TutorialScreen.SCREEN1, STEP_STOP_AND_SAVE)
                }
            }
            Screen1TutorialPhase.STOP_AND_SAVE -> {
                if (tutorialVideoSaved) {
                    tutorialStore.completeTutorial()
                    tutorialMode = false
                }
            }
            Screen1TutorialPhase.COMPLETE -> Unit
        }
    }

    private fun scrollTutorialTargetsIntoView(targets: List<View>) {
        val scrollContent = mainScrollView.getChildAt(0) as? LinearLayout ?: return
        val focusRect = Rect()
        var hasBounds = false
        targets.filter { it.isShown }.forEach { target ->
            val targetRect = Rect()
            scrollContent.offsetDescendantRectToMyCoords(target, targetRect.apply { target.getDrawingRect(this) })
            targetRect.inset(-16, -16)
            if (!hasBounds) {
                focusRect.set(targetRect)
                hasBounds = true
            } else {
                focusRect.union(targetRect)
            }
        }
        if (hasBounds) {
            mainScrollView.requestChildRectangleOnScreen(scrollContent, focusRect, true)
        }
    }

    private fun currentTutorialPhase(): Screen1TutorialPhase {
        if (!tutorialMode) return Screen1TutorialPhase.COMPLETE
        return when (tutorialStore.loadProgress().currentStepIndex) {
            STEP_START_CAMERA -> Screen1TutorialPhase.START_CAMERA
            STEP_RECORD_VIDEO -> Screen1TutorialPhase.RECORD_VIDEO
            STEP_STOP_AND_SAVE -> Screen1TutorialPhase.STOP_AND_SAVE
            STEP_COMPLETE -> Screen1TutorialPhase.COMPLETE
            else -> Screen1TutorialPhase.CHOOSE_MEDIA_FOLDER
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        private const val STEP_CHOOSE_MEDIA_FOLDER = 0
        private const val STEP_START_CAMERA = 1
        private const val STEP_RECORD_VIDEO = 2
        private const val STEP_STOP_AND_SAVE = 3
        private const val STEP_COMPLETE = 4
    }

    private enum class Screen1TutorialPhase {
        CHOOSE_MEDIA_FOLDER,
        START_CAMERA,
        RECORD_VIDEO,
        STOP_AND_SAVE,
        COMPLETE,
    }
}
