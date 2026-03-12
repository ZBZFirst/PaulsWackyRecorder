package com.example.templei.feature.soundboard

/**
 * Screen 3 soundboard state machine.
 *
 * Intent: keep playback behavior explicit and button-driven.
 * Invariant: sound playback is started only from an explicit button press event.
 */
class SoundboardStateMachine {
    sealed interface State {
        data class Loading(
            val processedFiles: Int,
            val totalFiles: Int,
            val playableFiles: Int
        ) : State
        data class Ready(val playableFiles: List<String>) : State
        data class Playing(val fileName: String) : State
        data class Error(val message: String) : State
    }

    private var state: State = State.Loading(
        processedFiles = 0,
        totalFiles = 0,
        playableFiles = 0
    )

    fun currentState(): State = state

    fun onLoadingProgress(processedFiles: Int, totalFiles: Int, playableFiles: Int) {
        state = State.Loading(
            processedFiles = processedFiles,
            totalFiles = totalFiles,
            playableFiles = playableFiles
        )
    }

    fun onCatalogLoaded(playableFiles: List<String>) {
        state = State.Ready(playableFiles)
    }

    fun onPlayPressed(fileName: String) {
        state = State.Playing(fileName)
    }

    fun onPlaybackCompleted(playableFiles: List<String>) {
        state = State.Ready(playableFiles)
    }

    fun onError(message: String) {
        state = State.Error(message)
    }
}
