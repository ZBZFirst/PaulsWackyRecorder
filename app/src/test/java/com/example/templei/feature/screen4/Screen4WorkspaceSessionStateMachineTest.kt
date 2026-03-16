package com.example.templei.feature.screen4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Screen4WorkspaceSessionStateMachineTest {

    @Test
    fun `initial state is no selection`() {
        val machine = Screen4WorkspaceSessionStateMachine()
        assertEquals(Screen4WorkspaceSessionStateMachine.State.NO_SELECTION, machine.currentState())
    }

    @Test
    fun `archive requires active state`() {
        val machine = Screen4WorkspaceSessionStateMachine()
        assertThrows(IllegalArgumentException::class.java) {
            machine.onWorkspaceArchived()
        }
    }

    @Test
    fun `active then archive transitions correctly`() {
        val machine = Screen4WorkspaceSessionStateMachine()
        machine.onWorkspaceSelectedActive()
        machine.onWorkspaceArchived()
        assertEquals(Screen4WorkspaceSessionStateMachine.State.ARCHIVED, machine.currentState())
    }
}
