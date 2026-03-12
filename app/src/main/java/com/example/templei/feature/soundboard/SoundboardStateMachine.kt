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
            val stage: LoadingStage,
            val processedFiles: Int,
            val totalFiles: Int,
            val playableFiles: Int,
            val discoveredFolders: Int,
            val discoveredFiles: Int
        ) : State

        data class Ready(val playableFiles: List<String>) : State
        data class Playing(val fileName: String) : State
        data class Error(val message: String) : State
    }

    enum class LoadingStage {
        Discovering,
        Validating
    }

    private var state: State = State.Loading(
        stage = LoadingStage.Discovering,
        processedFiles = 0,
        totalFiles = 0,
        playableFiles = 0,
        discoveredFolders = 0,
        discoveredFiles = 0
    )

    fun currentState(): State = state

    fun onLoadingProgress(
        stage: LoadingStage,
        processedFiles: Int,
        totalFiles: Int,
        playableFiles: Int,
        discoveredFolders: Int,
        discoveredFiles: Int
    ) {
        state = State.Loading(
            stage = stage,
            processedFiles = processedFiles,
            totalFiles = totalFiles,
            playableFiles = playableFiles,
            discoveredFolders = discoveredFolders,
            discoveredFiles = discoveredFiles
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
