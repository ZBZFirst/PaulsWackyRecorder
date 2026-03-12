package com.example.templei.feature.soundboard

/**
 * Screen 3 soundboard state machine.
 *
 * Intent: keep playback behavior explicit and button-driven.
 * Invariant: sound playback is started only from an explicit button press event.
 */
class SoundboardStateMachine {
    sealed interface State {
        data object Loading : State
        data class Ready(val playableFiles: List<String>) : State
        data class Playing(val fileName: String) : State
        data class Error(val message: String) : State
    }

    private var state: State = State.Loading

    fun currentState(): State = state

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
