package com.example.templei.feature.screen4

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deterministic next-cycle scheduler for one active pattern stream.
 */
class Screen4SchedulerEngine(
    private val playbackEngine: Screen4SamplePlaybackEngine,
    private val onCycleWindow: (Screen4CycleWindow) -> Unit,
    private val onStepTick: (Int) -> Unit,
    private val onRuntimeError: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private var transportJob: Job? = null
    private var activePattern: Screen4CompiledPattern? = null
    private var pendingPattern: Screen4CompiledPattern? = null
    private var bpm: Int = Screen4Coordinator.DEFAULT_BPM
    private var cycleIndex: Long = 0L

    suspend fun start(
        initialPattern: Screen4CompiledPattern,
        bpm: Int,
    ) {
        stop()
        playbackEngine.preload(initialPattern.resolvedSamples.values.map { it.uri }).getOrThrow()
        this.bpm = bpm
        this.activePattern = initialPattern
        this.pendingPattern = null
        this.cycleIndex = 0L

        transportJob = scope.launch {
            var nextCycleStartMs = System.currentTimeMillis() + START_DELAY_MS
            while (isActive) {
                val active = stateMutex.withLock {
                    pendingPattern?.let {
                        activePattern = it
                        pendingPattern = null
                    }
                    activePattern
                } ?: break

                val cycleDurationMs = cycleDurationMs(this@Screen4SchedulerEngine.bpm)
                val cycleWindow = Screen4CycleWindow(
                    cycleIndex = cycleIndex,
                    bpm = this@Screen4SchedulerEngine.bpm,
                    startTimeMs = nextCycleStartMs,
                    durationMs = cycleDurationMs,
                )
                onCycleWindow(cycleWindow)

                val stepDurationMs = if (active.stepsPerCycle <= 0) cycleDurationMs else cycleDurationMs / active.stepsPerCycle
                repeat(active.stepsPerCycle) { stepIndex ->
                    val tickAtMs = nextCycleStartMs + (stepIndex * stepDurationMs)
                    launch {
                        val waitMs = (tickAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        delay(waitMs)
                        onStepTick(stepIndex)
                    }
                }

                active.scheduledEvents.forEach { event ->
                    val descriptor = active.resolvedSamples[event.sampleId.lowercase()]
                    if (descriptor == null) {
                        onRuntimeError("Resolved sample ${event.sampleId} disappeared before playback.")
                        return@forEach
                    }
                    val instruction = Screen4PlaybackInstruction(
                        sampleId = event.sampleId,
                        sampleUri = descriptor.uri,
                        triggerAtMs = nextCycleStartMs + event.offsetMs,
                        gain = event.gain,
                        pan = event.pan,
                        speed = event.speed,
                    )
                    launch {
                        val waitMs = (instruction.triggerAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
                        delay(waitMs)
                        playbackEngine.play(instruction)
                    }
                }

                val sleepMs = (nextCycleStartMs + cycleDurationMs - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(sleepMs)
                nextCycleStartMs += cycleDurationMs
                cycleIndex += 1L
            }
        }
    }

    suspend fun queuePattern(
        pattern: Screen4CompiledPattern,
        bpm: Int,
    ) {
        playbackEngine.preload(pattern.resolvedSamples.values.map { it.uri }).getOrThrow()
        stateMutex.withLock {
            this.bpm = bpm
            this.pendingPattern = pattern
        }
    }

    suspend fun previewSample(
        descriptor: Screen4SampleDescriptor,
        gain: Float = 1f,
        pan: Float = 0f,
        speed: Float = 1f,
    ) {
        playbackEngine.preload(listOf(descriptor.uri)).getOrThrow()
        val played = playbackEngine.play(
            Screen4PlaybackInstruction(
                sampleId = descriptor.sampleId,
                sampleUri = descriptor.uri,
                triggerAtMs = System.currentTimeMillis(),
                gain = gain,
                pan = pan,
                speed = speed,
            )
        )
        if (!played) {
            throw IllegalStateException("Preview playback failed for ${descriptor.sampleId}.")
        }
    }

    suspend fun stop() {
        transportJob?.cancel()
        transportJob = null
        stateMutex.withLock {
            activePattern = null
            pendingPattern = null
            cycleIndex = 0L
        }
    }

    fun release() {
        transportJob?.cancel()
        playbackEngine.release()
    }

    private fun cycleDurationMs(bpm: Int): Long {
        return (4 * 60_000L) / bpm.coerceAtLeast(1)
    }

    private companion object {
        private const val START_DELAY_MS = 120L
    }
}
