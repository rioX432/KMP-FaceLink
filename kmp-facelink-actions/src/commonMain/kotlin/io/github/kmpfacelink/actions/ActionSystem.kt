package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Main API for the action trigger system.
 *
 * Register [ActionBinding]s and feed tracking data via [processFace] / [processHand].
 * Both methods return the list of [ActionEvent]s produced for that frame **and** send
 * them to the [events] flow, so callers can choose whichever consumption style fits best.
 */
@OptIn(ExperimentalAtomicApi::class)
public class ActionSystem {

    private val mutex = Mutex()
    private val released = AtomicInt(0)
    private val bindings = mutableMapOf<String, ActionBinding>()
    private val states = mutableMapOf<String, TriggerState>()

    private var latestFace: FaceTrackingData? = null
    private var latestHand: HandTrackingData? = null

    private val _events = Channel<ActionEvent>(Channel.UNLIMITED)

    /** Flow of action events. Collect this to receive trigger notifications. */
    public val events: Flow<ActionEvent> = _events.receiveAsFlow()

    /**
     * Register a binding. Throws if a binding with the same [ActionBinding.actionId] already exists.
     */
    public suspend fun register(binding: ActionBinding) {
        mutex.withLock {
            require(binding.actionId !in bindings) {
                "Action '${binding.actionId}' is already registered"
            }
            bindings[binding.actionId] = binding
            states[binding.actionId] = TriggerState()
        }
    }

    /** Remove a binding by action ID. Returns true if it was registered. */
    public suspend fun unregister(actionId: String): Boolean = mutex.withLock {
        states.remove(actionId)
        bindings.remove(actionId) != null
    }

    /** Remove all bindings and reset all state. */
    public suspend fun clear() {
        mutex.withLock {
            bindings.clear()
            states.clear()
            latestFace = null
            latestHand = null
        }
    }

    /** Release all resources. Closes the [events] channel and clears all state. */
    public fun release() {
        released.store(1)
        _events.close()
    }

    /**
     * Feed a face tracking frame and evaluate all bindings.
     *
     * @return the list of [ActionEvent]s produced for this frame.
     */
    public suspend fun processFace(data: FaceTrackingData): List<ActionEvent> {
        if (released.load() != 0) return emptyList()
        val pendingEvents = mutex.withLock {
            if (released.load() != 0) return emptyList()
            latestFace = data
            processAllBindings(data.timestampMs)
        }
        pendingEvents.forEach { _events.trySend(it) }
        return pendingEvents
    }

    /**
     * Feed a hand tracking frame and evaluate all bindings.
     *
     * @return the list of [ActionEvent]s produced for this frame.
     */
    public suspend fun processHand(data: HandTrackingData): List<ActionEvent> {
        if (released.load() != 0) return emptyList()
        val pendingEvents = mutex.withLock {
            if (released.load() != 0) return emptyList()
            latestHand = data
            processAllBindings(data.timestampMs)
        }
        pendingEvents.forEach { _events.trySend(it) }
        return pendingEvents
    }

    private fun processAllBindings(timestampMs: Long): List<ActionEvent> {
        val result = mutableListOf<ActionEvent>()
        for ((actionId, binding) in bindings) {
            val state = states[actionId] ?: continue
            processBinding(binding, state, timestampMs, result)
        }
        return result
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private fun processBinding(
        binding: ActionBinding,
        state: TriggerState,
        timestampMs: Long,
        events: MutableList<ActionEvent>,
    ) {
        val conditionMet = TriggerEvaluator.evaluate(
            binding.trigger,
            latestFace,
            latestHand,
        )
        state.markConditionMet(conditionMet)

        // Loop allows same-frame phase progression so that transient phases
        // (IDLE -> PENDING with holdTimeMs=0, COOLDOWN -> IDLE) do not cost
        // an extra frame.  The loop terminates when the phase stabilises or
        // when ACTIVE / COOLDOWN is newly entered (those need a future frame).
        val entryPhase = state.phase
        var previousPhase: TriggerState.Phase
        do {
            previousPhase = state.phase
            when (state.phase) {
                TriggerState.Phase.IDLE -> handleIdlePhase(state, conditionMet, timestampMs)
                TriggerState.Phase.PENDING -> handlePendingPhase(binding, state, conditionMet, timestampMs, events)
                TriggerState.Phase.ACTIVE -> {
                    // Only process ACTIVE when it was the phase at method entry.
                    // If we just transitioned here from PENDING, the Started event
                    // was already emitted — re-entering would emit a spurious Held(0).
                    if (state.phase == entryPhase) {
                        handleActivePhase(binding, state, conditionMet, timestampMs, events)
                    }
                    break
                }
                TriggerState.Phase.COOLDOWN -> {
                    if (state.phase == entryPhase) {
                        handleCooldownPhase(binding, state, timestampMs)
                    } else {
                        break
                    }
                }
            }
        } while (state.phase != previousPhase)
    }

    private fun handleIdlePhase(
        state: TriggerState,
        conditionMet: Boolean,
        timestampMs: Long,
    ) {
        if (conditionMet) {
            state.transitionTo(TriggerState.Phase.PENDING, timestampMs)
        }
    }

    private fun handlePendingPhase(
        binding: ActionBinding,
        state: TriggerState,
        conditionMet: Boolean,
        timestampMs: Long,
        events: MutableList<ActionEvent>,
    ) {
        if (!conditionMet) {
            state.transitionTo(TriggerState.Phase.IDLE, timestampMs)
            return
        }
        val elapsed = timestampMs - state.phaseEnteredAt
        if (elapsed >= binding.holdTimeMs) {
            state.transitionTo(TriggerState.Phase.ACTIVE, timestampMs)
            events.add(ActionEvent.Started(binding.actionId, timestampMs))
        }
    }

    private fun handleActivePhase(
        binding: ActionBinding,
        state: TriggerState,
        conditionMet: Boolean,
        timestampMs: Long,
        events: MutableList<ActionEvent>,
    ) {
        if (conditionMet) {
            state.markConditionLost(TriggerState.CONDITION_NOT_LOST)
            if (binding.emitHeldEvents) {
                val duration = timestampMs - state.phaseEnteredAt
                events.add(ActionEvent.Held(binding.actionId, timestampMs, duration))
            }
            return
        }
        handleActiveConditionLost(binding, state, timestampMs, events)
    }

    private fun handleActiveConditionLost(
        binding: ActionBinding,
        state: TriggerState,
        timestampMs: Long,
        events: MutableList<ActionEvent>,
    ) {
        if (state.conditionLostAt == TriggerState.CONDITION_NOT_LOST) {
            state.markConditionLost(timestampMs)
        }
        val lostDuration = timestampMs - state.conditionLostAt
        if (lostDuration >= binding.debounceMs) {
            val totalDuration = timestampMs - state.phaseEnteredAt
            events.add(ActionEvent.Released(binding.actionId, timestampMs, totalDuration))
            if (binding.cooldownMs > 0L) {
                state.transitionTo(TriggerState.Phase.COOLDOWN, timestampMs)
            } else {
                state.transitionTo(TriggerState.Phase.IDLE, timestampMs)
            }
        }
    }

    private fun handleCooldownPhase(
        binding: ActionBinding,
        state: TriggerState,
        timestampMs: Long,
    ) {
        val elapsed = timestampMs - state.phaseEnteredAt
        if (elapsed >= binding.cooldownMs) {
            state.transitionTo(TriggerState.Phase.IDLE, timestampMs)
        }
    }
}
