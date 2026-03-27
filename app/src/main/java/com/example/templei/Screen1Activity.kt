package com.example.templei

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.templei.feature.camera.Screen1CameraController
import com.example.templei.feature.camera.Screen1MediaRepository
import com.example.templei.ui.navigation.TopNavigation

/**
 * Shell host for Screen 1 camera controls and preview.
 */
class Screen1Activity : ComponentActivity() {
    private lateinit var controller: Screen1CameraController

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val cameraGranted = grantResults[Manifest.permission.CAMERA] == true ||
            hasPermission(Manifest.permission.CAMERA)
        if (cameraGranted) {
            controller.startFeed()
        } else {
            findViewById<TextView>(R.id.cameraStatusText).text =
                getString(R.string.screen1_status_permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_screen1)
        TopNavigation.bind(activity = this, currentDestination = Screen1Activity::class.java)

        controller = Screen1CameraController(
            activity = this,
            repository = Screen1MediaRepository(this),
            views = Screen1CameraController.Views(
                renderContainer = findViewById<FrameLayout>(R.id.renderContainer),
                renderPlaceholder = findViewById<TextView>(R.id.renderPlaceholderText),
                startFeedButton = findViewById<Button>(R.id.startFeedButton),
                stopFeedButton = findViewById<Button>(R.id.stopFeedButton),
                capturePhotoButton = findViewById<Button>(R.id.capturePhotoButton),
                recordVideoButton = findViewById<Button>(R.id.recordVideoButton),
                mediaSpinner = findViewById<Spinner>(R.id.mediaSpinner),
                selectionPreviewImage = findViewById<ImageView>(R.id.selectedMediaPreview),
                selectionLabel = findViewById<TextView>(R.id.selectedMediaLabel),
                statusText = findViewById<TextView>(R.id.cameraStatusText)
            )
        )
        controller.bind()

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

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
