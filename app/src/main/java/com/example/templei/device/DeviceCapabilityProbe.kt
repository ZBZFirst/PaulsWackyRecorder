package com.example.templei.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Permission state used in capability snapshots.
 *
 * Invariant: [NOT_APPLICABLE] is only emitted for SDK-gated permissions unavailable on the
 * current platform level.
 */
enum class PermissionState {
    GRANTED,
    DENIED,
    NOT_APPLICABLE
}

enum class StorageModel {
    LEGACY_EXTERNAL_STORAGE,
    SCOPED_STORAGE
}

/**
 * Consistent capability readout for the host device.
 *
 * Intent: Provide one normalized snapshot that MainActivity and screen modules can observe
 * before screen-specific state machines attempt to start unavailable pipelines.
 */
data class DeviceCapabilitySnapshot(
    val sdkInt: Int,
    val hasCamera: Boolean,
    val hasMicrophone: Boolean,
    val hasGps: Boolean,
    val hasAccelerometer: Boolean,
    val cameraPermission: PermissionState,
    val microphonePermission: PermissionState,
    val fineLocationPermission: PermissionState,
    val coarseLocationPermission: PermissionState,
    val mediaAudioPermission: PermissionState,
    val readExternalStoragePermission: PermissionState,
    val storageModel: StorageModel
) {
    /**
     * Recorder precondition for Screen 1: camera HW + microphone HW + runtime permissions.
     */
    val recorderUsable: Boolean
        get() = hasCamera && hasMicrophone &&
            cameraPermission == PermissionState.GRANTED &&
            microphonePermission == PermissionState.GRANTED

    /**
     * Logger precondition for Screen 4 (GPS path): GPS HW + at least one location permission.
     */
    val gpsLoggerUsable: Boolean
        get() = hasGps &&
            (fineLocationPermission == PermissionState.GRANTED ||
                coarseLocationPermission == PermissionState.GRANTED)
}

/**
 * Process-local capability baseline registry.
 *
 * Intent: MainActivity publishes snapshots; orthogonal modules consume this as read-only input.
 * Invariant: This object does not orchestrate screen behavior; it only distributes baseline state.
 */
object DeviceCapabilityRegistry {
    private val mutableSnapshot = MutableStateFlow<DeviceCapabilitySnapshot?>(null)

    val snapshot: StateFlow<DeviceCapabilitySnapshot?> = mutableSnapshot.asStateFlow()

    fun update(snapshot: DeviceCapabilitySnapshot) {
        mutableSnapshot.value = snapshot
    }
}

object DeviceCapabilityProbe {
    fun snapshot(context: Context): DeviceCapabilitySnapshot {
        val packageManager = context.packageManager

        return DeviceCapabilitySnapshot(
            sdkInt = Build.VERSION.SDK_INT,
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            hasGps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            hasAccelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER),
            cameraPermission = permissionState(context, Manifest.permission.CAMERA),
            microphonePermission = permissionState(context, Manifest.permission.RECORD_AUDIO),
            fineLocationPermission = permissionState(context, Manifest.permission.ACCESS_FINE_LOCATION),
            coarseLocationPermission = permissionState(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            mediaAudioPermission = permissionState(
                context = context,
                permission = Manifest.permission.READ_MEDIA_AUDIO,
                minApi = Build.VERSION_CODES.TIRAMISU
            ),
            readExternalStoragePermission = permissionState(
                context = context,
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                maxApi = Build.VERSION_CODES.S_V2
            ),
            storageModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                StorageModel.SCOPED_STORAGE
            } else {
                StorageModel.LEGACY_EXTERNAL_STORAGE
            }
        )
    }

    private fun permissionState(
        context: Context,
        permission: String,
        minApi: Int? = null,
        maxApi: Int? = null
    ): PermissionState {
        if (minApi != null && Build.VERSION.SDK_INT < minApi) {
            return PermissionState.NOT_APPLICABLE
        }
        if (maxApi != null && Build.VERSION.SDK_INT > maxApi) {
            return PermissionState.NOT_APPLICABLE
        }

        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            PermissionState.GRANTED
        } else {
            PermissionState.DENIED
        }
    }
}
