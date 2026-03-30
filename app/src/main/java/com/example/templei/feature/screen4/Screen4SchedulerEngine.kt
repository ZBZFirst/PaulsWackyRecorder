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
import kotlin.math.roundToLong

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
            var nextStepStartMs = System.currentTimeMillis() + START_DELAY_MS
            var activeStepIndex = 0
            while (isActive) {
                val state = stateMutex.withLock {
                    if (activeStepIndex == 0) {
                        pendingPattern?.let {
                            activePattern = it
                            pendingPattern = null
                        }
                    }
                    SchedulerStepState(
                        pattern = activePattern,
                        bpm = this@Screen4SchedulerEngine.bpm,
                        cycleIndex = cycleIndex,
                    )
                }
                val active = state.pattern ?: break
                val stepsPerCycle = active.stepsPerCycle.coerceAtLeast(1)
                val stepDurationMs = stepDurationMs(state.bpm, stepsPerCycle)
                if (activeStepIndex == 0) {
                    onCycleWindow(
                        Screen4CycleWindow(
                            cycleIndex = state.cycleIndex,
                            bpm = state.bpm,
                            startTimeMs = nextStepStartMs,
                            durationMs = stepDurationMs * stepsPerCycle,
                        )
                    )
                }

                val waitMs = (nextStepStartMs - System.currentTimeMillis()).coerceAtLeast(0L)
                delay(waitMs)
                onStepTick(activeStepIndex)

                active.scheduledEvents
                    .filter { it.stepIndex == activeStepIndex }
                    .forEach { event ->
                    val descriptor = active.resolvedSamples[event.sampleId.lowercase()]
                    if (descriptor == null) {
                        onRuntimeError("Resolved sample ${event.sampleId} disappeared before playback.")
                        return@forEach
                    }
                    val relativeStepPosition = (event.stepPosition - activeStepIndex.toDouble()).coerceAtLeast(0.0)
                    val instruction = Screen4PlaybackInstruction(
                        sampleId = event.sampleId,
                        sampleUri = descriptor.uri,
                        triggerAtMs = nextStepStartMs + (relativeStepPosition * stepDurationMs.toDouble()).roundToLong(),
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

                nextStepStartMs += stepDurationMs
                activeStepIndex += 1
                if (activeStepIndex >= stepsPerCycle) {
                    activeStepIndex = 0
                    stateMutex.withLock {
                        cycleIndex += 1L
                    }
                }
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

    suspend fun updateTempo(bpm: Int) {
        stateMutex.withLock {
            this.bpm = bpm
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

    private fun stepDurationMs(
        bpm: Int,
        stepsPerCycle: Int,
    ): Long {
        val cycleDurationMs = cycleDurationMs(bpm)
        return (cycleDurationMs / stepsPerCycle.coerceAtLeast(1)).coerceAtLeast(1L)
    }

    private data class SchedulerStepState(
        val pattern: Screen4CompiledPattern?,
        val bpm: Int,
        val cycleIndex: Long,
    )

    private companion object {
        private const val START_DELAY_MS = 120L
    }
}
