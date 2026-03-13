package com.example.templei.feature.soundboard

import android.content.ContentResolver
import android.media.SoundPool
import android.net.Uri

/**
 * Manages Screen 3 clip loading/cache bookkeeping for SoundPool-backed clips.
 *
 * This extraction keeps cache policy details out of `Screen3Activity` so activity
 * code can remain focused on wiring and lifecycle.
 */
class Screen3ClipCacheManager(
    private val soundPool: SoundPool,
    private val contentResolver: ContentResolver,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    data class ClipLoadRequest(
        val clipId: String,
        val clipUri: Uri,
        val pinned: Boolean
    )

    data class CacheEntry(
        val soundId: Int?,
        var state: SoundboardStateMachine.ClipLoadState,
        var lastUsedMs: Long,
        var pinned: Boolean
    )

    enum class TrimReason {
        FOLDER_SWITCH,
        MEMORY_PRESSURE,
        SETTINGS_APPLY
    }

    private val clipCache = linkedMapOf<String, CacheEntry>()
    private val pendingLoadCallbacks = mutableMapOf<Int, MutableList<(Boolean) -> Unit>>()
    private val soundIdToClipId = mutableMapOf<Int, String>()

    /** Call from SoundPool onLoadComplete to fan out pending callbacks. */
    fun onSoundPoolLoadComplete(soundId: Int, status: Int) {
        val clipId = soundIdToClipId[soundId]
        val entry = clipId?.let { clipCache[it] }
        val succeeded = status == 0 && clipId != null && entry != null

        if (succeeded) {
            entry?.state = SoundboardStateMachine.ClipLoadState.LOADED
        } else if (clipId != null) {
            clipCache[clipId]?.state = SoundboardStateMachine.ClipLoadState.FAILED
        }

        pendingLoadCallbacks.remove(soundId).orEmpty().forEach { it(succeeded) }
    }

    /**
     * Ensures a clip is loaded and invokes [onResult] when its load is resolved.
     *
     * If a load is already in-flight for the clip, callback is attached and reused.
     */
    fun ensureClipLoaded(request: ClipLoadRequest, onResult: (Boolean) -> Unit) {
        val existing = clipCache[request.clipId]
        when (existing?.state) {
            SoundboardStateMachine.ClipLoadState.LOADED -> {
                existing.lastUsedMs = nowMs()
                onResult(true)
                return
            }

            SoundboardStateMachine.ClipLoadState.LOADING -> {
                val soundId = existing.soundId ?: run {
                    onResult(false)
                    return
                }
                pendingLoadCallbacks.getOrPut(soundId) { mutableListOf() }.add(onResult)
                return
            }

            SoundboardStateMachine.ClipLoadState.FAILED -> {
                onResult(false)
                return
            }

            else -> Unit
        }

        val soundId = runCatching {
            contentResolver.openAssetFileDescriptor(request.clipUri, "r")?.use { afd ->
                soundPool.load(afd, 1)
            }
        }.getOrNull() ?: 0

        if (soundId == 0) {
            onResult(false)
            return
        }

        clipCache[request.clipId] = CacheEntry(
            soundId = soundId,
            state = SoundboardStateMachine.ClipLoadState.LOADING,
            lastUsedMs = nowMs(),
            pinned = request.pinned
        )
        soundIdToClipId[soundId] = request.clipId
        pendingLoadCallbacks.getOrPut(soundId) { mutableListOf() }.add(onResult)
    }

    /**
     * Applies clip eviction policy.
     *
     * Caller provides currently active folder clip ids and pinned clip ids.
     */
    fun trimCache(
        activeFolderClipIds: Set<String>,
        pinnedClipIds: Set<String>,
        reason: TrimReason,
        config: SoundboardConfig
    ) {
        clipCache.keys.toList().forEach { clipId -> clipCache[clipId]?.pinned = clipId in pinnedClipIds }

        val nonPinnedLoaded = clipCache
            .filter { (_, entry) -> !entry.pinned && entry.state != SoundboardStateMachine.ClipLoadState.LOADING }
            .toList()
            .sortedBy { (_, entry) -> entry.lastUsedMs }

        val toRemove = linkedMapOf<String, CacheEntry>()

        if (reason == TrimReason.FOLDER_SWITCH && config.unloadOnFolderChange) {
            when (config.cachePolicy) {
                CachePolicy.AGGRESSIVE,
                CachePolicy.BALANCED -> {
                    nonPinnedLoaded
                        .filter { (clipId, _) -> clipId !in activeFolderClipIds }
                        .forEach { (clipId, entry) -> toRemove[clipId] = entry }
                }

                CachePolicy.STICKY -> Unit
            }
        }

        if (config.cachePolicy == CachePolicy.AGGRESSIVE && reason == TrimReason.MEMORY_PRESSURE) {
            nonPinnedLoaded
                .filter { (clipId, _) -> clipId !in activeFolderClipIds }
                .forEach { (clipId, entry) -> toRemove[clipId] = entry }
        }

        val projectedSize = clipCache.size - toRemove.size
        val overflow = (projectedSize - config.maxCacheSize).coerceAtLeast(0)
        if (overflow > 0) {
            nonPinnedLoaded
                .filter { (clipId, _) -> clipId !in toRemove }
                .take(overflow)
                .forEach { (clipId, entry) -> toRemove[clipId] = entry }
        }

        toRemove.forEach { (clipId, entry) ->
            entry.soundId?.let { soundPool.unload(it) }
            clipCache.remove(clipId)
        }
    }

    fun markClipUsed(clipId: String) {
        clipCache[clipId]?.lastUsedMs = nowMs()
    }

    fun pinClip(clipId: String) {
        clipCache[clipId]?.pinned = true
    }

    fun getSoundId(clipId: String): Int? = clipCache[clipId]?.soundId

    fun cachedLoadedCount(): Int = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADED }

    fun snapshotClipLoad(activeFolderClipIds: Set<String>): SoundboardStateMachine.ClipLoadSnapshot {
        val loading = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADING }
        val loaded = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.LOADED }
        val failed = clipCache.count { it.value.state == SoundboardStateMachine.ClipLoadState.FAILED }
        val activeKnown = activeFolderClipIds.count { it in clipCache }
        val unloaded = (activeFolderClipIds.size - activeKnown).coerceAtLeast(0)
        return SoundboardStateMachine.ClipLoadSnapshot(
            unloaded = unloaded,
            loading = loading,
            loaded = loaded,
            failed = failed
        )
    }

    fun snapshotCacheState(activeFolder: String?, pinnedClipIds: Set<String>): SoundboardStateMachine.FolderCacheState {
        return SoundboardStateMachine.FolderCacheState(
            activeFolder = activeFolder,
            cachedClipIds = clipCache.keys,
            pinnedClipIds = pinnedClipIds
        )
    }

    fun clearAndRelease() {
        pendingLoadCallbacks.clear()
        clipCache.values.forEach { it.soundId?.let(soundPool::unload) }
        clipCache.clear()
        soundIdToClipId.clear()
    }
}
