package com.example.templei.feature.soundboard

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

class SoundboardAudioEngine private constructor(context: Context) {

    private val activePlayers = mutableSetOf<MediaPlayer>()

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
     * Reliable SAF URI playback path using MediaPlayer and async prepare.
     */
    fun playClipUri(context: Context, clipUri: Uri): Boolean {
        val player = MediaPlayer()

        return runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(context, clipUri)
            player.setOnPreparedListener { prepared ->
                prepared.start()
            }
            player.setOnCompletionListener { completed ->
                completed.reset()
                completed.release()
                activePlayers.remove(completed)
            }
            player.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra uri=$clipUri")
                failed.reset()
                failed.release()
                activePlayers.remove(failed)
                true
            }
            activePlayers.add(player)
            player.prepareAsync()
            true
        }.onFailure { error ->
            Log.e(TAG, "Failed to start playback for uri=$clipUri", error)
            runCatching {
                player.reset()
                player.release()
            }
            activePlayers.remove(player)
        }.getOrDefault(false)
    }

    fun release() {
        activePlayers.toList().forEach { player ->
            runCatching {
                player.stop()
                player.reset()
                player.release()
            }
        }
        activePlayers.clear()
    }
}
