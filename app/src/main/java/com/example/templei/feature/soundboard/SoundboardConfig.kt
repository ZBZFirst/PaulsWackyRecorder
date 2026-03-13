package com.example.templei.feature.soundboard

enum class CachePolicy {
    AGGRESSIVE, BALANCED, STICKY
}

data class SoundboardConfig(
    val maxStreams: Int = 4,       // Max streams for the Soundboard
    val cooldownMs: Long = 120L,   // Cooldown time between clips (in milliseconds)
    val maxCacheSize: Int = 24,    // Max cache size for clips
    val unloadOnFolderChange: Boolean = true, // Option to unload clips when changing folders
    val cachePolicy: CachePolicy = CachePolicy.BALANCED // Cache policy
)