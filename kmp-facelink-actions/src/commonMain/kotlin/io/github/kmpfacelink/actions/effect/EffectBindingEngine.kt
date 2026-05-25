package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.interact.model.EffectParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks a single active effect instance within a binding.
 *
 * @property instanceId Unique ID for this effect instance.
 * @property startedAtMs Timestamp when this instance was started.
 */
internal data class ActiveEffect(
    val instanceId: String,
    val startedAtMs: Long,
)

/**
 * Core engine that listens to [ActionSystem.events] and emits [EffectEmission]
 * instances based on registered [EffectBinding] configurations.
 *
 * The engine translates action lifecycle events (Started, Held, Released) into
 * effect emissions according to each binding's [EffectLifecycleMode] and [EffectBlendMode].
 *
 * @property actionSystem The action system whose events drive effect emission.
 */
@Suppress("TooManyFunctions") // Engine class aggregates registration + event processing + blend logic
public class EffectBindingEngine(
    private val actionSystem: ActionSystem,
) {
    private val mutex = Mutex()
    private val bindings = mutableMapOf<String, EffectBinding>()
    private val activeEffects = mutableMapOf<String, MutableList<ActiveEffect>>()

    private val _effects = MutableSharedFlow<EffectEmission>(extraBufferCapacity = BUFFER_CAPACITY)

    /** Flow of effect emissions. Collect to receive effect lifecycle notifications. */
    public val effects: Flow<EffectEmission> = _effects.asSharedFlow()

    /**
     * Returns a flow that, when collected, drives the engine by processing
     * [ActionSystem.events] and emitting [EffectEmission]s to [effects].
     */
    public fun processedEvents(): Flow<ActionEvent> {
        return actionSystem.events.onEach { event -> processEvent(event) }
    }

    /** Register an [EffectBinding]. Replaces any existing binding with the same ID. */
    public suspend fun register(binding: EffectBinding) {
        mutex.withLock {
            bindings[binding.bindingId] = binding
            if (binding.bindingId !in activeEffects) {
                activeEffects[binding.bindingId] = mutableListOf()
            }
        }
    }

    /** Register multiple [EffectBinding]s at once. */
    public suspend fun registerAll(bindings: List<EffectBinding>) {
        for (binding in bindings) {
            register(binding)
        }
    }

    /** Unregister a binding by its ID. Returns true if it was registered. */
    public suspend fun unregister(bindingId: String): Boolean {
        return mutex.withLock {
            activeEffects.remove(bindingId)
            bindings.remove(bindingId) != null
        }
    }

    /** Remove all bindings and active effects. */
    public suspend fun clear() {
        mutex.withLock {
            bindings.clear()
            activeEffects.clear()
        }
    }

    /**
     * Convenience: registers the [EffectBinding] in this engine AND
     * registers its [EffectBinding.actionBinding] in the [ActionSystem].
     */
    public suspend fun bind(binding: EffectBinding) {
        register(binding)
        actionSystem.register(binding.actionBinding)
    }

    /**
     * Convenience: unregisters the binding from this engine AND
     * unregisters its action binding from the [ActionSystem].
     */
    public suspend fun unbind(bindingId: String) {
        val binding = mutex.withLock { bindings[bindingId] }
        if (binding != null) {
            actionSystem.unregister(binding.actionBinding.actionId)
        }
        unregister(bindingId)
    }

    private suspend fun processEvent(event: ActionEvent) {
        mutex.withLock {
            for (binding in bindings.values) {
                if (binding.actionBinding.actionId != event.actionId) continue
                when (binding.lifecycleMode) {
                    EffectLifecycleMode.ON_START -> handleOnStart(binding, event)
                    EffectLifecycleMode.WHILE_HELD -> handleWhileHeld(binding, event)
                    EffectLifecycleMode.ON_RELEASE -> handleOnRelease(binding, event)
                }
            }
        }
    }

    private fun handleOnStart(binding: EffectBinding, event: ActionEvent) {
        if (event !is ActionEvent.Started) return
        enforceBlendAndConcurrency(binding, event.timestampMs)
        addActiveEffect(binding, event.timestampMs)
        emitEffect(
            binding.bindingId,
            binding.effectFactory.create(event),
            event.timestampMs,
            EmissionState.STARTED,
        )
    }

    private fun handleWhileHeld(binding: EffectBinding, event: ActionEvent) {
        when (event) {
            is ActionEvent.Started -> {
                enforceBlendAndConcurrency(binding, event.timestampMs)
                addActiveEffect(binding, event.timestampMs)
                emitEffect(
                    binding.bindingId,
                    binding.effectFactory.create(event),
                    event.timestampMs,
                    EmissionState.STARTED,
                )
            }
            is ActionEvent.Held -> emitEffect(
                binding.bindingId,
                binding.effectFactory.create(event),
                event.timestampMs,
                EmissionState.UPDATED,
            )
            is ActionEvent.Released -> {
                activeEffects[binding.bindingId]?.removeFirstOrNull()
                emitEffect(
                    binding.bindingId,
                    binding.effectFactory.create(event),
                    event.timestampMs,
                    EmissionState.ENDED,
                )
            }
        }
    }

    private fun handleOnRelease(binding: EffectBinding, event: ActionEvent) {
        if (event !is ActionEvent.Released) return
        enforceBlendAndConcurrency(binding, event.timestampMs)
        addActiveEffect(binding, event.timestampMs)
        emitEffect(
            binding.bindingId,
            binding.effectFactory.create(event),
            event.timestampMs,
            EmissionState.STARTED,
        )
    }

    private fun enforceBlendAndConcurrency(binding: EffectBinding, timestampMs: Long) {
        val actives = activeEffects.getOrPut(binding.bindingId) { mutableListOf() }
        when (binding.blendMode) {
            EffectBlendMode.EXCLUSIVE -> {
                if (actives.isEmpty()) return
                val endEvent = ActionEvent.Released(binding.actionBinding.actionId, timestampMs, 0L)
                val endEffect = binding.effectFactory.create(endEvent)
                repeat(actives.size) {
                    emitEffect(binding.bindingId, endEffect, timestampMs, EmissionState.ENDED)
                }
                actives.clear()
            }
            EffectBlendMode.PRIORITY -> cancelLowerPriorityBindings(binding, timestampMs)
            EffectBlendMode.ADDITIVE -> {
                while (actives.size >= binding.maxConcurrent) {
                    actives.removeFirst()
                    val endEvent = ActionEvent.Released(binding.actionBinding.actionId, timestampMs, 0L)
                    emitEffect(
                        binding.bindingId,
                        binding.effectFactory.create(endEvent),
                        timestampMs,
                        EmissionState.ENDED,
                    )
                }
            }
        }
    }

    private fun cancelLowerPriorityBindings(binding: EffectBinding, timestampMs: Long) {
        val targets = bindings.entries.filter { (id, other) ->
            id != binding.bindingId &&
                other.actionBinding.actionId == binding.actionBinding.actionId &&
                other.priority < binding.priority
        }
        for ((otherId, otherBinding) in targets) {
            val otherActives = activeEffects[otherId]?.takeIf { it.isNotEmpty() } ?: continue
            val endEvent = ActionEvent.Released(otherBinding.actionBinding.actionId, timestampMs, 0L)
            val endEffect = otherBinding.effectFactory.create(endEvent)
            repeat(otherActives.size) {
                emitEffect(otherId, endEffect, timestampMs, EmissionState.ENDED)
            }
            otherActives.clear()
        }
    }

    private fun addActiveEffect(binding: EffectBinding, timestampMs: Long) {
        val actives = activeEffects.getOrPut(binding.bindingId) { mutableListOf() }
        actives.add(ActiveEffect("${binding.bindingId}-$timestampMs", timestampMs))
    }

    private fun emitEffect(
        bindingId: String,
        effect: EffectParameter,
        timestampMs: Long,
        state: EmissionState,
    ) {
        _effects.tryEmit(
            EffectEmission(
                bindingId = bindingId,
                effect = effect,
                timestampMs = timestampMs,
                state = state,
            ),
        )
    }

    internal companion object {
        const val BUFFER_CAPACITY = 64
    }
}
