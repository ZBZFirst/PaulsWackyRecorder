package com.example.templei.feature.screen4

/**
 * Explicit workspace session vocabulary for table lifecycle transitions.
 */
class Screen4WorkspaceSessionStateMachine {
    enum class State { NO_SELECTION, ACTIVE, ARCHIVED }

    private var state: State = State.NO_SELECTION

    fun markNoSelection() { state = State.NO_SELECTION }

    fun onWorkspaceSelectedActive() { state = State.ACTIVE }

    fun onWorkspaceArchived() {
        require(state == State.ACTIVE) { "Can only archive from ACTIVE state" }
        state = State.ARCHIVED
    }

    fun currentState(): State = state
}
