package com.example.templei.feature.tutorial

import android.content.Context

/**
 * Shared tutorial progress store for the multi-screen onboarding sequence.
 *
 * This store keeps the current tutorial position explicit so each screen can
 * render deterministic onboarding behavior without guessing user intent.
 */
class TutorialStore(private val context: Context) {

    fun loadProgress(): TutorialProgress {
        val prefs = prefs()
        val status = TutorialStatus.fromStored(prefs.getString(KEY_STATUS, null))
        val currentScreen = TutorialScreen.fromStored(prefs.getString(KEY_CURRENT_SCREEN, null))
        val completedScreens = prefs.getStringSet(KEY_COMPLETED_SCREENS, emptySet())
            .orEmpty()
            .mapNotNullTo(linkedSetOf()) { TutorialScreen.fromStoredOrNull(it) }

        return TutorialProgress(
            status = status,
            currentScreen = currentScreen,
            currentStepIndex = prefs.getInt(KEY_CURRENT_STEP_INDEX, 0).coerceAtLeast(0),
            screen2PromptIndex = prefs.getInt(KEY_SCREEN2_PROMPT_INDEX, 0).coerceAtLeast(0),
            screen2FolderConfirmed = prefs.getBoolean(KEY_SCREEN2_FOLDER_CONFIRMED, false),
            screen1MediaFolderConfirmed = prefs.getBoolean(KEY_SCREEN1_MEDIA_FOLDER_CONFIRMED, false),
            completedScreens = completedScreens,
        )
    }

    fun startTutorial() {
        saveProgress(
            TutorialProgress(
                status = TutorialStatus.IN_PROGRESS,
                currentScreen = TutorialScreen.SCREEN2,
                currentStepIndex = 0,
                screen2PromptIndex = 0,
                screen2FolderConfirmed = false,
                screen1MediaFolderConfirmed = false,
                completedScreens = emptySet(),
            )
        )
    }

    fun redoTutorial() {
        startTutorial()
    }

    fun skipTutorial() {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.SKIPPED,
                currentStepIndex = 0,
            )
        )
    }

    fun completeTutorial() {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.COMPLETED,
                currentStepIndex = 0,
                currentScreen = TutorialScreen.SCREEN1,
                completedScreens = TutorialScreen.entries.toSet(),
            )
        )
    }

    fun setCurrentScreen(screen: TutorialScreen, stepIndex: Int = 0) {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.IN_PROGRESS,
                currentScreen = screen,
                currentStepIndex = stepIndex.coerceAtLeast(0),
            )
        )
    }

    fun setScreen2PromptIndex(promptIndex: Int, stepIndex: Int = 0) {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.IN_PROGRESS,
                currentScreen = TutorialScreen.SCREEN2,
                currentStepIndex = stepIndex.coerceAtLeast(0),
                screen2PromptIndex = promptIndex.coerceAtLeast(0),
            )
        )
    }

    fun setScreen2FolderConfirmed(confirmed: Boolean) {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.IN_PROGRESS,
                currentScreen = TutorialScreen.SCREEN2,
                screen2FolderConfirmed = confirmed,
            )
        )
    }

    fun setScreen1MediaFolderConfirmed(confirmed: Boolean) {
        val current = loadProgress()
        saveProgress(
            current.copy(
                status = TutorialStatus.IN_PROGRESS,
                currentScreen = TutorialScreen.SCREEN1,
                screen1MediaFolderConfirmed = confirmed,
            )
        )
    }

    fun markScreenCompleted(screen: TutorialScreen, nextScreen: TutorialScreen? = null) {
        val current = loadProgress()
        val completed = current.completedScreens.toMutableSet().apply { add(screen) }
        val nextStatus = if (nextScreen == null) TutorialStatus.COMPLETED else TutorialStatus.IN_PROGRESS
        saveProgress(
            current.copy(
                status = nextStatus,
                currentScreen = nextScreen ?: current.currentScreen,
                currentStepIndex = 0,
                completedScreens = completed,
            )
        )
    }

    private fun saveProgress(progress: TutorialProgress) {
        prefs().edit()
            .putString(KEY_STATUS, progress.status.storedValue)
            .putString(KEY_CURRENT_SCREEN, progress.currentScreen.storedValue)
            .putInt(KEY_CURRENT_STEP_INDEX, progress.currentStepIndex.coerceAtLeast(0))
            .putInt(KEY_SCREEN2_PROMPT_INDEX, progress.screen2PromptIndex.coerceAtLeast(0))
            .putBoolean(KEY_SCREEN2_FOLDER_CONFIRMED, progress.screen2FolderConfirmed)
            .putBoolean(KEY_SCREEN1_MEDIA_FOLDER_CONFIRMED, progress.screen1MediaFolderConfirmed)
            .putStringSet(
                KEY_COMPLETED_SCREENS,
                progress.completedScreens.mapTo(linkedSetOf()) { it.storedValue },
            )
            .apply()
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "screen_tutorial"
        private const val KEY_STATUS = "status"
        private const val KEY_CURRENT_SCREEN = "current_screen"
        private const val KEY_CURRENT_STEP_INDEX = "current_step_index"
        private const val KEY_SCREEN2_PROMPT_INDEX = "screen2_prompt_index"
        private const val KEY_SCREEN2_FOLDER_CONFIRMED = "screen2_folder_confirmed"
        private const val KEY_SCREEN1_MEDIA_FOLDER_CONFIRMED = "screen1_media_folder_confirmed"
        private const val KEY_COMPLETED_SCREENS = "completed_screens"
    }
}

data class TutorialProgress(
    val status: TutorialStatus,
    val currentScreen: TutorialScreen,
    val currentStepIndex: Int,
    val screen2PromptIndex: Int,
    val screen2FolderConfirmed: Boolean,
    val screen1MediaFolderConfirmed: Boolean,
    val completedScreens: Set<TutorialScreen>,
)

enum class TutorialStatus(val storedValue: String) {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    SKIPPED("skipped"),
    COMPLETED("completed");

    companion object {
        fun fromStored(raw: String?): TutorialStatus = entries.firstOrNull { it.storedValue == raw } ?: NOT_STARTED
    }
}

enum class TutorialScreen(val storedValue: String) {
    SCREEN2("screen2"),
    SCREEN3("screen3"),
    SCREEN4("screen4"),
    SCREEN1("screen1");

    companion object {
        fun fromStored(raw: String?): TutorialScreen = fromStoredOrNull(raw) ?: SCREEN2

        fun fromStoredOrNull(raw: String?): TutorialScreen? = entries.firstOrNull { it.storedValue == raw }
    }
}
