package com.example.templei.feature.screen2

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Records mono PCM audio into a local WAV file with a fixed six-second ceiling.
 */
class Screen2WavRecorder {

    fun start(outputFile: File, onMaxDurationReached: () -> Unit): Result<Unit> {
        synchronized(lock) {
            if (state == RecordingState.RECORDING) {
                return Result.failure(IllegalStateException("Recorder already active"))
            }

            val config = createAudioConfig()
                ?: return Result.failure(IllegalStateException("No supported audio configuration"))
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                CHANNEL_CONFIG,
                ENCODING,
                config.bufferSize,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return Result.failure(IllegalStateException("AudioRecord initialization failed"))
            }

            outputFile.parentFile?.mkdirs()
            if (outputFile.exists()) outputFile.delete()

            audioRecord = record
            currentOutputFile = outputFile
            currentConfig = config
            stopRequested.set(false)
            maxDurationReached.set(false)
            bytesWritten = 0L
            state = RecordingState.RECORDING

            try {
                record.startRecording()
            } catch (error: IllegalStateException) {
                releaseRecorder()
                state = RecordingState.IDLE
                return Result.failure(error)
            }

            recordingThread = Thread(
                RecordingRunnable(outputFile, config, onMaxDurationReached),
                "Screen2WavRecorder",
            ).apply { start() }

            return Result.success(Unit)
        }
    }

    fun stop(): Result<RecordingResult> {
        val threadToJoin: Thread?
        synchronized(lock) {
            if (state != RecordingState.RECORDING && currentOutputFile == null) {
                return Result.failure(IllegalStateException("Recorder is not active"))
            }
            stopRequested.set(true)
            threadToJoin = recordingThread
        }

        threadToJoin?.join()

        synchronized(lock) {
            val outputFile = currentOutputFile
                ?: return Result.failure(IllegalStateException("No recording result available"))
            val config = currentConfig
                ?: return Result.failure(IllegalStateException("Missing recording config"))
            val result = RecordingResult(
                outputFile = outputFile,
                durationMs = bytesWritten * 1000L / config.bytesPerSecond,
                reachedMaxDuration = maxDurationReached.get(),
            )
            clearSession()
            return Result.success(result)
        }
    }

    fun isRecording(): Boolean = state == RecordingState.RECORDING

    private inner class RecordingRunnable(
        private val outputFile: File,
        private val config: AudioConfig,
        private val onMaxDurationReached: () -> Unit,
    ) : Runnable {
        override fun run() {
            val record = audioRecord ?: return
            val buffer = ByteArray(config.bufferSize)

            try {
                RandomAccessFile(outputFile, "rw").use { wavFile ->
                    wavFile.setLength(0L)
                    writeHeader(wavFile, config.sampleRate, 0L)

                    while (!stopRequested.get()) {
                        val remainingBytes = config.maxAudioBytes - bytesWritten
                        if (remainingBytes <= 0L) {
                            maxDurationReached.set(true)
                            break
                        }

                        val readSize = min(buffer.size.toLong(), remainingBytes).toInt()
                        val bytesRead = record.read(buffer, 0, readSize)
                        if (bytesRead > 0) {
                            wavFile.write(buffer, 0, bytesRead)
                            bytesWritten += bytesRead
                        }
                    }

                    if (bytesWritten >= config.maxAudioBytes) {
                        maxDurationReached.set(true)
                    }

                    stopRecord(record)
                    writeHeader(wavFile, config.sampleRate, bytesWritten)
                }
            } catch (_: Throwable) {
                stopRecord(record)
            } finally {
                synchronized(lock) {
                    recordingThread = null
                    state = RecordingState.IDLE
                }
                if (maxDurationReached.get()) {
                    mainHandler.post(onMaxDurationReached)
                }
            }
        }
    }

    private fun createAudioConfig(): AudioConfig? {
        return SAMPLE_RATES.firstNotNullOfOrNull { sampleRate ->
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, ENCODING)
            if (minBufferSize <= 0) {
                null
            } else {
                AudioConfig(
                    sampleRate = sampleRate,
                    bufferSize = max(minBufferSize * 2, sampleRate / 2),
                )
            }
        }
    }

    private fun stopRecord(record: AudioRecord) {
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        releaseRecorder()
    }

    private fun releaseRecorder() {
        audioRecord?.release()
        audioRecord = null
    }

    private fun writeHeader(file: RandomAccessFile, sampleRate: Int, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRate * CHANNEL_COUNT * BYTES_PER_SAMPLE

        file.seek(0L)
        file.writeBytes("RIFF")
        file.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
        file.writeBytes("WAVE")
        file.writeBytes("fmt ")
        file.writeInt(Integer.reverseBytes(16))
        file.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        file.writeShort(java.lang.Short.reverseBytes(CHANNEL_COUNT.toShort()).toInt())
        file.writeInt(Integer.reverseBytes(sampleRate))
        file.writeInt(Integer.reverseBytes(byteRate))
        file.writeShort(java.lang.Short.reverseBytes((CHANNEL_COUNT * BYTES_PER_SAMPLE).toShort()).toInt())
        file.writeShort(java.lang.Short.reverseBytes((BYTES_PER_SAMPLE * 8).toShort()).toInt())
        file.writeBytes("data")
        file.writeInt(Integer.reverseBytes(totalAudioLen.toInt()))
    }

    private fun clearSession() {
        currentOutputFile = null
        currentConfig = null
        bytesWritten = 0L
        maxDurationReached.set(false)
    }

    data class RecordingResult(
        val outputFile: File,
        val durationMs: Long,
        val reachedMaxDuration: Boolean,
    )

    private data class AudioConfig(
        val sampleRate: Int,
        val bufferSize: Int,
    ) {
        val bytesPerSecond: Long = sampleRate.toLong() * CHANNEL_COUNT * BYTES_PER_SAMPLE
        val maxAudioBytes: Long = bytesPerSecond * MAX_DURATION_MS / 1000L
    }

    private enum class RecordingState {
        IDLE,
        RECORDING,
    }

    private companion object {
        private val SAMPLE_RATES = listOf(44_100, 22_050, 16_000)
        private const val CHANNEL_COUNT = 1
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_DURATION_MS = 6_000L
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val lock = Any()
    private val stopRequested = AtomicBoolean(false)
    private val maxDurationReached = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var state: RecordingState = RecordingState.IDLE
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recordingThread: Thread? = null
    @Volatile private var currentOutputFile: File? = null
    @Volatile private var currentConfig: AudioConfig? = null
    @Volatile private var bytesWritten: Long = 0L
}
