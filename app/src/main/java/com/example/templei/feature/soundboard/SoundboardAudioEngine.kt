package com.example.templei

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.util.Log

class SoundboardAudioEngine private constructor(context: Context) {

    private val soundPool: SoundPool
    private val soundIdsByUri = mutableMapOf<String, Int>()
    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingPlayBySoundId = mutableMapOf<Int, () -> Unit>()

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
                pendingPlayBySoundId.remove(sampleId)?.invoke()
            } else {
                pendingPlayBySoundId.remove(sampleId)
                Log.w(TAG, "SoundPool failed loading sampleId=$sampleId, status=$status")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SoundboardAudioEngine? = null

        fun getInstance(context: Context): SoundboardAudioEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundboardAudioEngine(context).also { INSTANCE = it }
            }
        }

        private const val TAG = "SoundboardAudioEngine"
    }

    /**
     * Loads and plays a clip URI. If already loaded, play starts immediately.
     * If newly loaded, play starts on SoundPool load completion callback.
     */
    fun playClipUri(context: Context, clipUri: Uri): Boolean {
        val key = clipUri.toString()
        val existingSoundId = soundIdsByUri[key]
        if (existingSoundId != null) {
            if (existingSoundId in loadedSoundIds) {
                return playClip(existingSoundId)
            }

            pendingPlayBySoundId[existingSoundId] = {
                playClip(existingSoundId)
            }
            return true
        }

        val afd = context.contentResolver.openAssetFileDescriptor(clipUri, "r")
            ?: return false

        val soundId = afd.use { descriptor ->
            soundPool.load(descriptor, 1)
        }

        if (soundId == 0) {
            return false
        }

        soundIdsByUri[key] = soundId
        pendingPlayBySoundId[soundId] = {
            playClip(soundId)
        }
        return true
    }

    private fun playClip(clipId: Int): Boolean {
        val streamId = soundPool.play(clipId, 1f, 1f, 0, 0, 1f)
        return streamId != 0
    }

    fun release() {
        soundPool.release()
        soundIdsByUri.clear()
        loadedSoundIds.clear()
        pendingPlayBySoundId.clear()
    }
}
