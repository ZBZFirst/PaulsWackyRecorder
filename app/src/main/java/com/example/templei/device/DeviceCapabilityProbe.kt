package com.example.templei.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
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
 * Read-only coordinate sample for capability diagnostics.
 */
data class CoordinateSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)

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
    val highSamplingRateSensorsPermission: PermissionState,
    val regularSensorRateUsable: Boolean,
    val highSensorRateUsable: Boolean,
    val fineLocationSample: CoordinateSample?,
    val coarseLocationSample: CoordinateSample?,
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

        val finePermission = permissionState(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePermission = permissionState(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        val fineLocation = if (finePermission == PermissionState.GRANTED) {
            lastKnownLocation(
                context = context,
                providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            )
        } else {
            null
        }

        val coarseLocation = if (coarsePermission == PermissionState.GRANTED) {
            lastKnownLocation(
                context = context,
                providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            )
        } else {
            null
        }

        val highSamplingPermission = permissionState(
            context = context,
            permission = Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
            minApi = Build.VERSION_CODES.S
        )

        val hasAccelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)

        return DeviceCapabilitySnapshot(
            sdkInt = Build.VERSION.SDK_INT,
            hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            hasGps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            hasAccelerometer = hasAccelerometer,
            cameraPermission = permissionState(context, Manifest.permission.CAMERA),
            microphonePermission = permissionState(context, Manifest.permission.RECORD_AUDIO),
            fineLocationPermission = finePermission,
            coarseLocationPermission = coarsePermission,
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
            highSamplingRateSensorsPermission = highSamplingPermission,
            regularSensorRateUsable = hasAccelerometer,
            highSensorRateUsable = hasAccelerometer &&
                (highSamplingPermission == PermissionState.GRANTED ||
                    highSamplingPermission == PermissionState.NOT_APPLICABLE),
            fineLocationSample = fineLocation?.toCoordinateSample(),
            coarseLocationSample = coarseLocation?.toCoordinateSample(),
            storageModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                StorageModel.SCOPED_STORAGE
            } else {
                StorageModel.LEGACY_EXTERNAL_STORAGE
            }
        )
    }

    private fun lastKnownLocation(context: Context, providers: List<String>): Location? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return providers.asSequence()
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .firstOrNull()
    }

    private fun Location.toCoordinateSample(): CoordinateSample {
        return CoordinateSample(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null
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
