package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionBinding
import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.actions.ActionTrigger
import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.allShapesAtRest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectBindingEngineTest {

    private val throwEffect = EffectParameter.ThrowEffect(
        durationMs = 1000L,
        intensity = 0.8f,
        anchor = AvatarAnchor.HEAD,
        objectName = "tomato",
        count = 1,
    )

    // --- ON_START lifecycle ---

    @Test
    fun onStartEmitsStartedOnActionStarted() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.ON_START,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))

        // Allow flow processing to complete
        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(EmissionState.STARTED, emissions[0].state)
        assertEquals(binding.bindingId, emissions[0].bindingId)

        collectJob.cancel()
        processJob.cancel()
    }

    @Test
    fun onStartIgnoresHeldAndReleasedEvents() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.ON_START,
            emitHeldEvents = true,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        // Trigger started
        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Trigger held
        system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Trigger released
        system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to LOW_VALUE))

        testScheduler.advanceUntilIdle()

        // Only the Started event should produce an emission
        assertEquals(1, emissions.size)
        assertEquals(EmissionState.STARTED, emissions[0].state)

        collectJob.cancel()
        processJob.cancel()
    }

    // --- WHILE_HELD lifecycle ---

    @Test
    fun whileHeldEmitsFullLifecycle() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.WHILE_HELD,
            emitHeldEvents = true,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        // Started
        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Held
        system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Released
        system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to LOW_VALUE))

        testScheduler.advanceUntilIdle()

        assertEquals(EXPECTED_WHILE_HELD_COUNT, emissions.size)
        assertEquals(EmissionState.STARTED, emissions[0].state)
        assertEquals(EmissionState.UPDATED, emissions[1].state)
        assertEquals(EmissionState.ENDED, emissions[2].state)

        collectJob.cancel()
        processJob.cancel()
    }

    // --- ON_RELEASE lifecycle ---

    @Test
    fun onReleaseEmitsStartedOnlyOnReleased() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.ON_RELEASE,
            emitHeldEvents = true,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        // Started + Held — no emission
        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Released — emission
        system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to LOW_VALUE))

        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(EmissionState.STARTED, emissions[0].state)

        collectJob.cancel()
        processJob.cancel()
    }

    // --- EXCLUSIVE blend mode ---

    @Test
    fun exclusiveBlendCancelsPreviousEffect() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.ON_START,
            blendMode = EffectBlendMode.EXCLUSIVE,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        // First activation
        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        // Deactivate
        system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to LOW_VALUE))
        // Second activation — should cancel first
        system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        // First STARTED, then on second activation: ENDED (cancel) + STARTED
        val startedEmissions = emissions.filter { it.state == EmissionState.STARTED }
        val endedEmissions = emissions.filter { it.state == EmissionState.ENDED }
        assertEquals(2, startedEmissions.size)
        assertEquals(1, endedEmissions.size)

        collectJob.cancel()
        processJob.cancel()
    }

    // --- maxConcurrent enforcement ---

    @Test
    fun maxConcurrentDropsOldestWhenExceeded() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(
            lifecycleMode = EffectLifecycleMode.ON_START,
            blendMode = EffectBlendMode.ADDITIVE,
            maxConcurrent = 2,
        )
        engine.register(binding)
        system.register(binding.actionBinding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        // Trigger 3 activations
        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to LOW_VALUE))
        system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to HIGH_VALUE))
        system.processFace(faceData(TIMESTAMP_400, BlendShape.TONGUE_OUT to LOW_VALUE))
        system.processFace(faceData(TIMESTAMP_500, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        // 3 STARTED + 1 ENDED (oldest dropped on 3rd activation)
        val startedCount = emissions.count { it.state == EmissionState.STARTED }
        val endedCount = emissions.count { it.state == EmissionState.ENDED }
        assertEquals(EXPECTED_STARTED_WITH_OVERFLOW, startedCount)
        assertEquals(1, endedCount)

        collectJob.cancel()
        processJob.cancel()
    }

    // --- Register/Unregister ---

    @Test
    fun unregisteredBindingDoesNotEmit() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(lifecycleMode = EffectLifecycleMode.ON_START)
        engine.register(binding)
        system.register(binding.actionBinding)

        // Unregister before triggering
        engine.unregister(binding.bindingId)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        assertTrue(emissions.isEmpty())

        collectJob.cancel()
        processJob.cancel()
    }

    @Test
    fun unregisterReturnsTrueForRegisteredBinding() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(lifecycleMode = EffectLifecycleMode.ON_START)
        engine.register(binding)

        assertTrue(engine.unregister(binding.bindingId))
    }

    @Test
    fun unregisterReturnsFalseForUnknownBinding() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)

        assertFalse(engine.unregister("unknown-id"))
    }

    // --- bind() convenience ---

    @Test
    fun bindRegistersInBothEngineAndActionSystem() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(lifecycleMode = EffectLifecycleMode.ON_START)

        engine.bind(binding)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        assertEquals(1, emissions.size)
        assertEquals(EmissionState.STARTED, emissions[0].state)

        collectJob.cancel()
        processJob.cancel()
    }

    @Test
    fun unbindRemovesFromBothEngineAndActionSystem() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(lifecycleMode = EffectLifecycleMode.ON_START)

        engine.bind(binding)
        engine.unbind(binding.bindingId)

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        assertTrue(emissions.isEmpty())

        collectJob.cancel()
        processJob.cancel()
    }

    // --- clear() ---

    @Test
    fun clearRemovesAllBindingsAndActiveEffects() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val binding = createBinding(lifecycleMode = EffectLifecycleMode.ON_START)
        engine.register(binding)
        system.register(binding.actionBinding)

        engine.clear()

        val emissions = mutableListOf<EffectEmission>()
        val collectJob = launch { engine.effects.toList(emissions) }
        val processJob = launch { engine.processedEvents().launchIn(this) }

        system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))

        testScheduler.advanceUntilIdle()

        assertTrue(emissions.isEmpty())

        collectJob.cancel()
        processJob.cancel()
    }

    // --- Helpers ---

    private fun createBinding(
        lifecycleMode: EffectLifecycleMode = EffectLifecycleMode.ON_START,
        blendMode: EffectBlendMode = EffectBlendMode.ADDITIVE,
        maxConcurrent: Int = EffectBinding.DEFAULT_MAX_CONCURRENT,
        emitHeldEvents: Boolean = false,
    ): EffectBinding = createBindingWithId("test-binding", lifecycleMode, blendMode, maxConcurrent, emitHeldEvents)

    @Suppress("LongParameterList")
    private fun createBindingWithId(
        bindingId: String,
        lifecycleMode: EffectLifecycleMode = EffectLifecycleMode.ON_START,
        blendMode: EffectBlendMode = EffectBlendMode.ADDITIVE,
        maxConcurrent: Int = EffectBinding.DEFAULT_MAX_CONCURRENT,
        emitHeldEvents: Boolean = false,
    ): EffectBinding = EffectBinding(
        bindingId = bindingId,
        actionBinding = ActionBinding(
            actionId = "tongue-out",
            trigger = ActionTrigger.ExpressionTrigger(
                blendShape = BlendShape.TONGUE_OUT,
                threshold = THRESHOLD,
            ),
            emitHeldEvents = emitHeldEvents,
        ),
        effectFactory = EffectFactory.Static(throwEffect),
        lifecycleMode = lifecycleMode,
        blendMode = blendMode,
        maxConcurrent = maxConcurrent,
    )

    private fun faceData(
        timestampMs: Long,
        vararg blendShapes: Pair<BlendShape, Float>,
    ): FaceTrackingData {
        val data = allShapesAtRest().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = true,
        )
    }

    companion object {
        private const val HIGH_VALUE = 0.8f
        private const val LOW_VALUE = 0.1f
        private const val THRESHOLD = 0.5f

        private const val TIMESTAMP_100 = 100L
        private const val TIMESTAMP_200 = 200L
        private const val TIMESTAMP_300 = 300L
        private const val TIMESTAMP_400 = 400L
        private const val TIMESTAMP_500 = 500L

        private const val EXPECTED_WHILE_HELD_COUNT = 3
        private const val EXPECTED_STARTED_WITH_OVERFLOW = 3
    }
}
