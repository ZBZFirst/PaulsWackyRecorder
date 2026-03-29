package com.example.templei

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.templei.device.DeviceCapabilityProbe
import com.example.templei.device.DeviceCapabilityRegistry
import com.example.templei.device.DeviceCapabilitySnapshot
import com.example.templei.device.PermissionState
import com.example.templei.device.StorageModel
import com.example.templei.ui.navigation.TopNavigation

/**
 * Entry screen shell that routes to Screens 1-4 and observes host-device readiness.
 *
 * MainActivity remains a router/status console; each screen still owns its own behavior.
 */
class MainActivity : ComponentActivity() {
    private lateinit var deviceStatusText: TextView

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDeviceStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.mainRoot)
        val rootPaddingStart = root.paddingStart
        val rootPaddingTop = root.paddingTop
        val rootPaddingEnd = root.paddingEnd
        val rootPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPaddingRelative(
                rootPaddingStart + systemBars.left,
                rootPaddingTop + systemBars.top,
                rootPaddingEnd + systemBars.right,
                rootPaddingBottom + systemBars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        deviceStatusText = findViewById(R.id.deviceStatusText)

        findViewById<Button>(R.id.requestPermissionsButton).setOnClickListener {
            requestMissingPermissions()
        }

        TopNavigation.bindMainMenuGrid(activity = this)

        refreshDeviceStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshDeviceStatus()
    }

    private fun requestMissingPermissions() {
        val snapshot = DeviceCapabilityProbe.snapshot(this)
        val permissionsToRequest = collectRequestableMissingPermissions(snapshot)

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(this, getString(R.string.permission_request_none_needed), Toast.LENGTH_SHORT).show()
            refreshDeviceStatus()
            return
        }

        permissionRequestLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun refreshDeviceStatus() {
        val snapshot = DeviceCapabilityProbe.snapshot(this)
        DeviceCapabilityRegistry.update(snapshot)
        deviceStatusText.text = formatSnapshot(snapshot)
    }

    private fun formatSnapshot(snapshot: DeviceCapabilitySnapshot): String {
        val missingRequestable = collectRequestableMissingPermissions(snapshot)
            .joinToString(separator = ", ") { permissionLabel(it) }
            .ifEmpty { getString(R.string.permission_request_none_missing_label) }
        val cameraCaptureReady = snapshot.hasCamera &&
            snapshot.cameraPermission == PermissionState.GRANTED
        val audioRecorderReady = snapshot.hasMicrophone &&
            snapshot.microphonePermission == PermissionState.GRANTED

        return buildString {
            appendLine(getString(R.string.device_status_header, snapshot.sdkInt))
            appendLine(
                getString(
                    R.string.device_status_camera,
                    yesNo(snapshot.hasCamera),
                    permission(snapshot.cameraPermission),
                )
            )
            appendLine(
                getString(
                    R.string.device_status_microphone,
                    yesNo(snapshot.hasMicrophone),
                    permission(snapshot.microphonePermission),
                )
            )
            appendLine(
                getString(
                    R.string.device_status_files,
                    storageModel(snapshot.storageModel),
                    permission(snapshot.readExternalStoragePermission),
                    permission(snapshot.mediaAudioPermission),
                )
            )
            appendLine(getString(R.string.device_status_gate_recorder, yesNo(cameraCaptureReady)))
            appendLine(getString(R.string.device_status_gate_logger, yesNo(audioRecorderReady)))
            appendLine(getString(R.string.device_status_missing_permissions, missingRequestable))
        }
    }

    private fun collectRequestableMissingPermissions(snapshot: DeviceCapabilitySnapshot): List<String> {
        val requestables = mutableListOf<String>()

        if (snapshot.hasCamera && snapshot.cameraPermission == PermissionState.DENIED) {
            requestables += Manifest.permission.CAMERA
        }
        if (snapshot.hasMicrophone && snapshot.microphonePermission == PermissionState.DENIED) {
            requestables += Manifest.permission.RECORD_AUDIO
        }
        if (snapshot.readExternalStoragePermission == PermissionState.DENIED) {
            requestables += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (snapshot.mediaAudioPermission == PermissionState.DENIED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestables += Manifest.permission.READ_MEDIA_AUDIO
        }

        return requestables.distinct()
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.CAMERA -> getString(R.string.permission_camera)
        Manifest.permission.RECORD_AUDIO -> getString(R.string.permission_microphone)
        Manifest.permission.READ_EXTERNAL_STORAGE -> getString(R.string.permission_read_external_storage)
        Manifest.permission.READ_MEDIA_AUDIO -> getString(R.string.permission_read_media_audio)
        else -> permission
    }

    private fun yesNo(value: Boolean): String = if (value) {
        getString(R.string.status_yes)
    } else {
        getString(R.string.status_no)
    }

    private fun permission(state: PermissionState): String = when (state) {
        PermissionState.GRANTED -> getString(R.string.permission_granted)
        PermissionState.DENIED -> getString(R.string.permission_denied)
        PermissionState.NOT_APPLICABLE -> getString(R.string.permission_not_applicable)
    }

    private fun storageModel(model: StorageModel): String = when (model) {
        StorageModel.LEGACY_EXTERNAL_STORAGE -> getString(R.string.storage_model_legacy)
        StorageModel.SCOPED_STORAGE -> getString(R.string.storage_model_scoped)
    }
}
