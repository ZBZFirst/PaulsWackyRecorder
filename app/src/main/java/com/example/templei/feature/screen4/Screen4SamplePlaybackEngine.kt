package com.example.templei.feature.screen4

import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * SoundPool-backed playback path with per-event gain, pan, and speed.
 */
class Screen4SamplePlaybackEngine(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .build()

    private val contentResolver: ContentResolver = context.applicationContext.contentResolver
    private val soundIdsByUri = ConcurrentHashMap<String, Int>()
    private val pendingLoads = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()

    init {
        soundPool.setOnLoadCompleteListener { _, soundId, status ->
            pendingLoads.remove(soundId)?.complete(status == 0)
        }
    }

    suspend fun preload(sampleUris: Collection<Uri>): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                sampleUris.distinctBy { it.toString() }.forEach { uri ->
                    ensureLoaded(uri).getOrThrow()
                }
            }
        }
    }

    fun play(instruction: Screen4PlaybackInstruction): Boolean {
        val soundId = soundIdsByUri[instruction.sampleUri.toString()] ?: return false
        val gain = instruction.gain.coerceIn(0f, 1f)
        val pan = instruction.pan.coerceIn(-1f, 1f)
        val speed = instruction.speed.coerceIn(0.5f, 2f)
        val left = gain * (if (pan >= 0f) 1f - pan else 1f)
        val right = gain * (if (pan <= 0f) 1f + pan else 1f)
        return soundPool.play(soundId, left, right, 1, 0, speed) != 0
    }

    fun release() {
        soundPool.release()
        soundIdsByUri.clear()
        pendingLoads.clear()
    }

    private suspend fun ensureLoaded(uri: Uri): Result<Int> {
        soundIdsByUri[uri.toString()]?.let { return Result.success(it) }

        return withContext(Dispatchers.IO) {
            runCatching {
                val deferred = CompletableDeferred<Boolean>()
                val soundId = contentResolver.openAssetFileDescriptor(uri, "r")?.use { asset ->
                    soundPool.load(asset, 1)
                } ?: throw IllegalStateException("Unable to open asset for $uri")

                pendingLoads[soundId] = deferred
                val loaded = deferred.await()
                if (!loaded) {
                    throw IllegalStateException("SoundPool load failed for $uri")
                }
                soundIdsByUri[uri.toString()] = soundId
                soundId
            }
        }
    }
}
