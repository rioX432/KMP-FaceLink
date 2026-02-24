package io.github.kmpfacelink.actions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerStateTest {

    @Test
    fun initialPhaseIsIdle() {
        val state = TriggerState()
        assertEquals(TriggerState.Phase.IDLE, state.phase)
        assertEquals(0L, state.phaseEnteredAt)
    }

    @Test
    fun transitionUpdatesPhaseAndTimestamp() {
        val state = TriggerState()
        state.transitionTo(TriggerState.Phase.PENDING, 100L)
        assertEquals(TriggerState.Phase.PENDING, state.phase)
        assertEquals(100L, state.phaseEnteredAt)
    }

    @Test
    fun markConditionMet() {
        val state = TriggerState()
        assertFalse(state.conditionMet)
        state.markConditionMet(true)
        assertTrue(state.conditionMet)
    }

    @Test
    fun markConditionLost() {
        val state = TriggerState()
        assertEquals(TriggerState.CONDITION_NOT_LOST, state.conditionLostAt)
        state.markConditionLost(200L)
        assertEquals(200L, state.conditionLostAt)
    }

    @Test
    fun resetRestoresInitialState() {
        val state = TriggerState()
        state.transitionTo(TriggerState.Phase.ACTIVE, 500L)
        state.markConditionMet(true)
        state.markConditionLost(600L)

        state.reset()

        assertEquals(TriggerState.Phase.IDLE, state.phase)
        assertEquals(0L, state.phaseEnteredAt)
        assertEquals(TriggerState.CONDITION_NOT_LOST, state.conditionLostAt)
        assertFalse(state.conditionMet)
    }

    @Test
    fun fullLifecycleTransitions() {
        val state = TriggerState()

        state.transitionTo(TriggerState.Phase.PENDING, 100L)
        assertEquals(TriggerState.Phase.PENDING, state.phase)

        state.transitionTo(TriggerState.Phase.ACTIVE, 200L)
        assertEquals(TriggerState.Phase.ACTIVE, state.phase)

        state.transitionTo(TriggerState.Phase.COOLDOWN, 300L)
        assertEquals(TriggerState.Phase.COOLDOWN, state.phase)

        state.transitionTo(TriggerState.Phase.IDLE, 400L)
        assertEquals(TriggerState.Phase.IDLE, state.phase)
    }
}
