package com.example.templei.feature.screen4

import android.content.Context
import com.example.templei.feature.soundboard.ClipIndexRepository
import com.example.templei.feature.soundboard.Screen3SettingsStore

/**
 * Process-level Screen 4 music runtime.
 *
 * The sequencer transport must survive activity navigation so playback can
 * continue while the user moves through the app. This runtime owns the
 * coordinator, scheduler, and sample playback engine independent of any
 * one screen host.
 */
object Screen4MusicRuntime {
    @Volatile
    private var coordinatorInstance: Screen4Coordinator? = null

    fun coordinator(context: Context): Screen4Coordinator {
        coordinatorInstance?.let { return it }

        return synchronized(this) {
            coordinatorInstance ?: buildCoordinator(context.applicationContext).also { coordinator ->
                coordinator.initialize()
                coordinatorInstance = coordinator
            }
        }
    }

    fun release() {
        synchronized(this) {
            coordinatorInstance?.release()
            coordinatorInstance = null
        }
    }

    private fun buildCoordinator(appContext: Context): Screen4Coordinator {
        lateinit var coordinator: Screen4Coordinator
        val sampleLibraryRepository = Screen4SampleLibraryRepository(ClipIndexRepository(appContext))
        val sharedFavoritesStore = Screen3SettingsStore(appContext)
        val sequenceStore = Screen4SequenceStore(appContext)
        val playbackEngine = Screen4SamplePlaybackEngine(appContext)
        val schedulerEngine = Screen4SchedulerEngine(
            playbackEngine = playbackEngine,
            onCycleWindow = { cycleWindow -> coordinator.onCycleWindow(cycleWindow) },
            onStepTick = { stepIndex -> coordinator.onStepTick(stepIndex) },
            onRuntimeError = { message -> coordinator.onRuntimeError(message) },
        )
        coordinator = Screen4Coordinator(
            sampleLibraryRepository = sampleLibraryRepository,
            sharedFavoritesStore = sharedFavoritesStore,
            sequenceStore = sequenceStore,
            schedulerEngine = schedulerEngine,
        )
        return coordinator
    }
}
