package com.example.templei

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

class SoundboardAudioEngine private constructor(context: Context) {

    private var soundPool: SoundPool
    private val soundIds = mutableMapOf<String, Int>()

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)  // Max number of sounds that can be played simultaneously
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    // Singleton pattern: Ensure only one instance of this class exists
    companion object {
        @Volatile
        private var INSTANCE: SoundboardAudioEngine? = null

        fun getInstance(context: Context): SoundboardAudioEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SoundboardAudioEngine(context).also { INSTANCE = it }
            }
        }
    }

    // Load an audio clip into SoundPool
    fun loadClip(context: Context, clipUri: String): Int {
        return soundPool.load(clipUri, 1)  // Load sound clip and return its ID
    }

    // Play an audio clip
    fun playClip(clipId: Int) {
        soundPool.play(clipId, 1f, 1f, 0, 0, 1f)  // Play the clip at full volume, no loop, normal speed
    }

    // Release all resources
    fun release() {
        soundPool.release()
    }
}