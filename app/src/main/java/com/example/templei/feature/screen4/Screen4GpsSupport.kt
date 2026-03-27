package com.example.templei.feature.screen4

import java.util.Locale

/**
 * GPS role resolution and live stream state for Screen 4 entry surfaces.
 */
enum class Screen4GpsColumnRole {
    NONE,
    LATITUDE,
    LONGITUDE,
    COORDINATE,
}

enum class Screen4GpsStreamState {
    IDLE,
    WAITING_FOR_FIX,
    LIVE,
    PERMISSION_DENIED,
}

data class Screen4GpsStreamStatus(
    val state: Screen4GpsStreamState,
    val snapshot: Screen4LocationSnapshot? = null,
)

fun ActiveColumn.gpsColumnRole(
    resolvedType: ColumnTypeDefinition = Screen4ColumnTypeRegistry.resolveByConstraintType(constraintType),
): Screen4GpsColumnRole {
    val tokens = listOf(
        metadata?.columnName,
        metadata?.group,
        metadata?.groupClassification,
        metadata?.displayName,
        resolvedType.name,
        constraintType,
        label,
    )

    if (tokens.any { it.matchesGpsToken("latitude") }) {
        return Screen4GpsColumnRole.LATITUDE
    }
    if (tokens.any { it.matchesGpsToken("longitude") }) {
        return Screen4GpsColumnRole.LONGITUDE
    }
    if (tokens.any { it.matchesGpsToken("coordinate") }) {
        return Screen4GpsColumnRole.COORDINATE
    }
    return Screen4GpsColumnRole.NONE
}

class Screen4GpsStream(
    private val locationProvider: Screen4LocationProvider,
) {
    private var currentStatus = Screen4GpsStreamStatus(Screen4GpsStreamState.IDLE)

    fun status(): Screen4GpsStreamStatus = currentStatus

    fun syncStreaming(enabled: Boolean): Screen4GpsStreamStatus {
        return if (enabled) {
            start()
        } else {
            stop()
            currentStatus
        }
    }

    fun start(): Screen4GpsStreamStatus {
        if (!locationProvider.hasLocationPermission()) {
            currentStatus = Screen4GpsStreamStatus(Screen4GpsStreamState.PERMISSION_DENIED)
            return currentStatus
        }
        locationProvider.startLiveUpdates()
        return refresh(forceRefresh = false)
    }

    fun refresh(forceRefresh: Boolean): Screen4GpsStreamStatus {
        if (!locationProvider.hasLocationPermission()) {
            currentStatus = Screen4GpsStreamStatus(Screen4GpsStreamState.PERMISSION_DENIED)
            return currentStatus
        }
        val snapshot = if (forceRefresh) locationProvider.refreshLocation() else locationProvider.currentLocation()
        currentStatus = if (snapshot != null) {
            Screen4GpsStreamStatus(Screen4GpsStreamState.LIVE, snapshot)
        } else {
            Screen4GpsStreamStatus(Screen4GpsStreamState.WAITING_FOR_FIX)
        }
        return currentStatus
    }

    fun commitSnapshot(): Screen4LocationSnapshot? {
        val liveSnapshot = refresh(forceRefresh = false).snapshot
        if (liveSnapshot != null) return liveSnapshot
        return refresh(forceRefresh = true).snapshot
    }

    fun stop() {
        locationProvider.stopLiveUpdates()
        currentStatus = Screen4GpsStreamStatus(Screen4GpsStreamState.IDLE)
    }
}

private fun String?.matchesGpsToken(target: String): Boolean {
    val normalized = this.orEmpty().trim().lowercase(Locale.US)
    if (normalized.isEmpty()) return false
    return when (target) {
        "latitude" -> normalized == "lat" || normalized == "latitude" || normalized.contains("latitude")
        "longitude" -> normalized == "lon" || normalized == "long" || normalized == "longitude" || normalized.contains("longitude")
        "coordinate" -> normalized == "coordinate" || normalized == "coordinates" || normalized.contains("coord")
        else -> false
    }
}
