package com.example.templei.feature.screen4

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.math.abs
import java.util.Locale

/**
 * Bounded device location provider used by Screen 4 auto-fill policies.
 */
interface Screen4LocationProvider {
    fun hasLocationPermission(): Boolean = true
    fun currentLocation(): Screen4LocationSnapshot?
    fun refreshLocation(): Screen4LocationSnapshot? = currentLocation()
    fun startLiveUpdates() = Unit
    fun stopLiveUpdates() = Unit
}

data class Screen4LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val provider: String?,
    val accuracyMeters: Float?,
    val timestampMillis: Long,
) {
    fun latitudeText(): String = String.format(Locale.US, "%.6f", latitude)
    fun longitudeText(): String = String.format(Locale.US, "%.6f", longitude)
    fun coordinateText(): String = "${latitudeText()},${longitudeText()}"

    fun providerLabel(): String = when (provider?.lowercase(Locale.US)) {
        LocationManager.GPS_PROVIDER.lowercase(Locale.US) -> "GPS"
        LocationManager.NETWORK_PROVIDER.lowercase(Locale.US) -> "Network"
        LocationManager.PASSIVE_PROVIDER.lowercase(Locale.US) -> "Passive"
        else -> "Unknown"
    }
}

class AndroidScreen4LocationProvider(
    private val context: Context,
) : Screen4LocationProvider {
    private var latestSnapshot: Screen4LocationSnapshot? = null
    private var latestLocation: Location? = null
    private var listener: android.location.LocationListener? = null

    override fun currentLocation(): Screen4LocationSnapshot? {
        if (!hasLocationPermission()) return null
        latestSnapshot?.let { return it }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val bestLocation = providers
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxWithOrNull(::compareLocations)
            ?: return null
        latestLocation = bestLocation
        return bestLocation.toSnapshot().also { latestSnapshot = it }
    }

    override fun refreshLocation(): Screen4LocationSnapshot? {
        latestSnapshot = null
        latestLocation = null
        return currentLocation()
    }

    override fun startLiveUpdates() {
        if (!hasLocationPermission() || listener != null) return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        latestSnapshot = currentLocation()
        val locationListener = android.location.LocationListener { location ->
            val currentBest = latestLocation
            val shouldReplace = currentBest == null || compareLocations(location, currentBest) > 0
            if (shouldReplace) {
                latestLocation = location
                latestSnapshot = location.toSnapshot()
            }
        }
        listener = locationListener
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, locationListener)
            }
        }
    }

    override fun stopLiveUpdates() {
        val locationListener = listener ?: return
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        runCatching { locationManager.removeUpdates(locationListener) }
        listener = null
    }

    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toSnapshot(): Screen4LocationSnapshot {
        return Screen4LocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            provider = provider,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            timestampMillis = time,
        )
    }

    private fun compareLocations(left: Location, right: Location): Int {
        val freshnessDelta = left.time - right.time
        if (abs(freshnessDelta) > 30_000L) {
            return freshnessDelta.compareTo(0L)
        }

        val leftAccuracy = if (left.hasAccuracy()) left.accuracy else Float.MAX_VALUE
        val rightAccuracy = if (right.hasAccuracy()) right.accuracy else Float.MAX_VALUE
        val accuracyDelta = rightAccuracy.compareTo(leftAccuracy)
        if (accuracyDelta != 0) {
            return accuracyDelta
        }

        val providerDelta = providerRank(left.provider).compareTo(providerRank(right.provider))
        if (providerDelta != 0) {
            return providerDelta
        }

        return freshnessDelta.compareTo(0L)
    }

    private fun providerRank(provider: String?): Int = when (provider) {
        LocationManager.GPS_PROVIDER -> 3
        LocationManager.NETWORK_PROVIDER -> 2
        LocationManager.PASSIVE_PROVIDER -> 1
        else -> 0
    }
}
