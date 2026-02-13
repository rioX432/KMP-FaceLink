package io.github.kmpfacelink.actions

/**
 * Mutable state machine tracking a single [ActionBinding]'s lifecycle.
 *
 * Phase transitions: IDLE → PENDING → ACTIVE → COOLDOWN → IDLE
 */
internal class TriggerState {

    enum class Phase {
        IDLE,
        PENDING,
        ACTIVE,
        COOLDOWN,
    }

    var phase: Phase = Phase.IDLE
        private set

    /** Timestamp when the current phase was entered. */
    var phaseEnteredAt: Long = 0L
        private set

    /** Timestamp when condition was last lost during ACTIVE phase (for debounce). */
    var conditionLostAt: Long = 0L
        private set

    /** Whether the condition is currently met. */
    var conditionMet: Boolean = false
        private set

    fun transitionTo(newPhase: Phase, timestampMs: Long) {
        phase = newPhase
        phaseEnteredAt = timestampMs
    }

    fun markConditionMet(met: Boolean) {
        conditionMet = met
    }

    fun markConditionLost(timestampMs: Long) {
        conditionLostAt = timestampMs
    }

    fun reset() {
        phase = Phase.IDLE
        phaseEnteredAt = 0L
        conditionLostAt = 0L
        conditionMet = false
    }
}
