package com.example.templei.feature.screen4

import android.net.Uri

/**
 * Explicit state and contract models for the Screen 4 music controller.
 */
enum class Screen4TransportState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
}

data class Screen4PatternSource(
    val text: String,
    val compileResult: Screen4CompileResult = Screen4CompileResult.Idle,
)

sealed class Screen4CompileResult {
    data object Idle : Screen4CompileResult()
    data object Compiling : Screen4CompileResult()
    data class Success(val pattern: Screen4CompiledPattern) : Screen4CompileResult()
    data class Failure(
        val message: String,
        val tokenIndex: Int? = null,
    ) : Screen4CompileResult()
}

data class Screen4CycleWindow(
    val cycleIndex: Long,
    val bpm: Int,
    val startTimeMs: Long,
    val durationMs: Long,
)

data class Screen4ScheduledEvent(
    val sampleId: String,
    val stepIndex: Int,
    val offsetMs: Long,
    val gain: Float,
    val pan: Float,
    val speed: Float,
)

data class Screen4PlaybackInstruction(
    val sampleId: String,
    val sampleUri: Uri,
    val triggerAtMs: Long,
    val gain: Float,
    val pan: Float,
    val speed: Float,
)

data class Screen4SampleDescriptor(
    val sampleId: String,
    val clipId: String,
    val uri: Uri,
    val folderName: String,
    val displayName: String,
    val baseName: String,
    val durationMs: Long,
)

data class Screen4SamplePackIndex(
    val rootUri: Uri?,
    val folderCount: Int,
    val sampleCount: Int,
    val descriptorsById: Map<String, Screen4SampleDescriptor>,
    val descriptorsByClipId: Map<String, Screen4SampleDescriptor>,
) {
    fun summaryLabel(): String {
        return if (rootUri == null) {
            "No sample folder selected."
        } else {
            "Indexed $sampleCount playable wav clips across $folderCount folders."
        }
    }
}

data class Screen4CompiledPattern(
    val sourceText: String,
    val stepsPerCycle: Int,
    val scheduledEvents: List<Screen4ScheduledEvent>,
    val resolvedSamples: Map<String, Screen4SampleDescriptor>,
) {
    fun summaryLabel(): String {
        return "${scheduledEvents.size} events across $stepsPerCycle steps."
    }
}

data class Screen4UiState(
    val transportState: Screen4TransportState = Screen4TransportState.STOPPED,
    val bpm: Int = Screen4Coordinator.DEFAULT_BPM,
    val activeCycle: Long = 0L,
    val activeStepIndex: Int = 0,
    val currentFavoritePageId: String = Screen4Coordinator.DEFAULT_FAVORITE_PAGE_ID,
    val currentFavoritePageIndex: Int = 0,
    val favoritePageCount: Int = 1,
    val patternSource: Screen4PatternSource = Screen4PatternSource(Screen4Coordinator.DEFAULT_PATTERN),
    val activePatternSummary: String = "No active pattern.",
    val pendingPatternSummary: String = "No queued next-cycle update.",
    val sampleLibrarySummary: String = "No sample folder selected.",
    val selectedRootLabel: String = "No sample folder selected.",
    val favoriteSlots: List<Screen4VisualSlot> = emptyList(),
    val sequenceBars: List<Screen4SequenceBarUi> = emptyList(),
    val runtimeStatus: String = "Screen 4 music controller is idle.",
    val lastError: String? = null,
    val isPlaying: Boolean = false,
)

data class Screen4SequenceBarUi(
    val barIndex: Int,
    val label: String,
    val steps: List<Screen4VisualSlot>,
)

data class Screen4VisualSlot(
    val index: Int,
    val sampleId: String? = null,
    val label: String = "(empty)",
    val sourcePageId: String? = null,
    val sourcePageIndex: Int? = null,
    val sourceSlotIndex: Int? = null,
)

data class Screen4FavoritePadReference(
    val pageId: String,
    val slotIndex: Int,
    val clipId: String? = null,
)

data class Screen4WorkingSequenceState(
    val bpm: Int,
    val displayedFavoritePageId: String?,
    val playBars: List<List<Screen4FavoritePadReference?>>,
)

data class Screen4SavedBarSnapshot(
    val name: String,
    val savedAtMs: Long,
    val steps: List<Screen4FavoritePadReference?>,
)

data class Screen4SavedSongSnapshot(
    val name: String,
    val savedAtMs: Long,
    val bpm: Int,
    val displayedFavoritePageId: String?,
    val playBars: List<List<Screen4FavoritePadReference?>>,
)

sealed class Screen4PatternNode {
    data class Sequence(val children: List<Screen4PatternNode>) : Screen4PatternNode()
    data object Rest : Screen4PatternNode()
    data class Event(
        val sampleId: String,
        val params: Screen4EventParameters = Screen4EventParameters(),
    ) : Screen4PatternNode()
    data class Repeat(
        val node: Screen4PatternNode,
        val count: Int,
    ) : Screen4PatternNode()
}

data class Screen4EventParameters(
    val gain: Float = 1f,
    val pan: Float = 0f,
    val speed: Float = 1f,
)

data class Screen4ParsedPattern(
    val ast: Screen4PatternNode,
)
