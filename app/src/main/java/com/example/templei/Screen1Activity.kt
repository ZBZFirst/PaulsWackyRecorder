package com.example.templei

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.templei.feature.camera.Screen1CameraController
import com.example.templei.feature.camera.Screen1FolderTarget
import com.example.templei.feature.camera.Screen1MediaRepository
import com.example.templei.feature.camera.Screen1StorageMode
import com.example.templei.ui.navigation.AppShellInsets
import com.example.templei.ui.navigation.AppShellNavigation

/**
 * Shell host for Screen 1 camera controls and preview.
 */
class Screen1Activity : ComponentActivity() {
    private lateinit var controller: Screen1CameraController
    private lateinit var mediaRepository: Screen1MediaRepository

    private lateinit var cameraStatusText: TextView
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
            bindStorageConfiguration()
            controller.refreshMediaLibrary()
            cameraStatusText.text = getString(
                when (pendingFolderTarget) {
                    Screen1FolderTarget.Shared -> R.string.screen1_status_shared_folder_selected
                    Screen1FolderTarget.Photo -> R.string.screen1_status_photo_folder_selected
                    Screen1FolderTarget.Video -> R.string.screen1_status_video_folder_selected
                }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen1)
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
        bindStorageViews()

        controller = Screen1CameraController(
            activity = this,
            repository = mediaRepository,
            views = Screen1CameraController.Views(
                renderContainer = findViewById(R.id.renderContainer),
                renderPlaceholder = findViewById(R.id.renderPlaceholderText),
                startFeedButton = findViewById(R.id.startFeedButton),
                stopFeedButton = findViewById(R.id.stopFeedButton),
                capturePhotoButton = findViewById(R.id.capturePhotoButton),
                recordVideoButton = findViewById(R.id.recordVideoButton),
                mediaSpinner = findViewById(R.id.mediaSpinner),
                selectionPreviewImage = findViewById(R.id.selectedMediaPreview),
                selectionLabel = findViewById(R.id.selectedMediaLabel),
                statusText = cameraStatusText,
            )
        )
        controller.bind()
        bindStorageConfiguration()

        findViewById<Button>(R.id.startFeedButton).setOnClickListener {
            ensureCameraPermissionsAndStart()
        }
        findViewById<Button>(R.id.stopFeedButton).setOnClickListener {
            controller.stopFeed()
        }
        findViewById<Button>(R.id.capturePhotoButton).setOnClickListener {
            controller.capturePhoto()
        }
        findViewById<Button>(R.id.recordVideoButton).setOnClickListener {
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
    }

    override fun onPause() {
        controller.release()
        super.onPause()
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

    private fun bindStorageConfiguration() {
        val mode = mediaRepository.storageMode()
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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
