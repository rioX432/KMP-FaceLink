package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionSystem
import io.github.kmpfacelink.actions.ActionTrigger
import io.github.kmpfacelink.actions.StreamEventType
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.HandGesture
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuiltInEffectBindingsTest {

    // --- Celebration pack ---

    @Test
    fun celebrationPackContainsThreeBindings() {
        val bindings = BuiltInEffectBindings.celebrationPack()
        assertEquals(EXPECTED_PACK_SIZE, bindings.size)
    }

    @Test
    fun celebrationPackHasGestureTriggers() {
        val bindings = BuiltInEffectBindings.celebrationPack()
        bindings.forEach { binding ->
            assertIs<ActionTrigger.GestureTrigger>(binding.actionBinding.trigger)
        }
    }

    @Test
    fun celebrationPackUsesExpectedGestures() {
        val bindings = BuiltInEffectBindings.celebrationPack()
        val gestures = bindings.map {
            (it.actionBinding.trigger as ActionTrigger.GestureTrigger).gesture
        }.toSet()
        assertTrue(HandGesture.VICTORY in gestures)
        assertTrue(HandGesture.THUMB_UP in gestures)
        assertTrue(HandGesture.OPEN_PALM in gestures)
    }

    @Test
    fun celebrationPackEffectsAreThrowEffects() {
        val bindings = BuiltInEffectBindings.celebrationPack()
        bindings.forEach { binding ->
            val factory = binding.effectFactory
            assertIs<EffectFactory.Static>(factory)
            assertIs<EffectParameter.ThrowEffect>(factory.effect)
        }
    }

    // --- Expression pack ---

    @Test
    fun expressionPackContainsThreeBindings() {
        val bindings = BuiltInEffectBindings.expressionPack()
        assertEquals(EXPECTED_PACK_SIZE, bindings.size)
    }

    @Test
    fun expressionPackHasExpressionTriggers() {
        val bindings = BuiltInEffectBindings.expressionPack()
        bindings.forEach { binding ->
            assertIs<ActionTrigger.ExpressionTrigger>(binding.actionBinding.trigger)
        }
    }

    @Test
    fun expressionPackContainsScreenShake() {
        val bindings = BuiltInEffectBindings.expressionPack()
        val hasShake = bindings.any { binding ->
            val factory = binding.effectFactory as? EffectFactory.Static
            factory?.effect is EffectParameter.ScreenShake
        }
        assertTrue(hasShake)
    }

    // --- Stream pack ---

    @Test
    fun streamPackContainsThreeBindings() {
        val bindings = BuiltInEffectBindings.streamPack()
        assertEquals(EXPECTED_PACK_SIZE, bindings.size)
    }

    @Test
    fun streamPackHasStreamEventTriggers() {
        val bindings = BuiltInEffectBindings.streamPack()
        bindings.forEach { binding ->
            assertIs<ActionTrigger.StreamEventTrigger>(binding.actionBinding.trigger)
        }
    }

    @Test
    fun streamPackCoversExpectedEventTypes() {
        val bindings = BuiltInEffectBindings.streamPack()
        val eventTypes = bindings.map {
            (it.actionBinding.trigger as ActionTrigger.StreamEventTrigger).eventType
        }.toSet()
        assertTrue(StreamEventType.GIFT in eventTypes)
        assertTrue(StreamEventType.RAID in eventTypes)
        assertTrue(StreamEventType.FOLLOW in eventTypes)
    }

    @Test
    fun streamPackContainsScreenShake() {
        val bindings = BuiltInEffectBindings.streamPack()
        val hasShake = bindings.any { binding ->
            val factory = binding.effectFactory as? EffectFactory.Static
            factory?.effect is EffectParameter.ScreenShake
        }
        assertTrue(hasShake)
    }

    // --- All packs have unique binding IDs ---

    @Test
    fun allPacksHaveUniqueBindingIds() {
        val allBindings = BuiltInEffectBindings.celebrationPack() +
            BuiltInEffectBindings.expressionPack() +
            BuiltInEffectBindings.streamPack()
        val ids = allBindings.map { it.bindingId }
        assertEquals(ids.size, ids.toSet().size)
    }

    // --- All effects have valid parameters ---

    @Test
    fun allEffectsHavePositiveDuration() {
        val allBindings = BuiltInEffectBindings.celebrationPack() +
            BuiltInEffectBindings.expressionPack() +
            BuiltInEffectBindings.streamPack()
        allBindings.forEach { binding ->
            val factory = binding.effectFactory
            assertIs<EffectFactory.Static>(factory)
            assertTrue(factory.effect.durationMs > 0L)
        }
    }

    @Test
    fun allEffectsHaveValidIntensity() {
        val allBindings = BuiltInEffectBindings.celebrationPack() +
            BuiltInEffectBindings.expressionPack() +
            BuiltInEffectBindings.streamPack()
        allBindings.forEach { binding ->
            val factory = binding.effectFactory
            assertIs<EffectFactory.Static>(factory)
            assertTrue(factory.effect.intensity in 0f..1f)
        }
    }

    // --- registerAll integration ---

    @Test
    fun registerAllRegistersAllBindingsInEngine() = runTest {
        val system = ActionSystem()
        val engine = EffectBindingEngine(system)
        val bindings = BuiltInEffectBindings.celebrationPack()

        engine.registerAll(bindings)

        // Verify by unregistering each — all should return true
        bindings.forEach { binding ->
            assertTrue(engine.unregister(binding.bindingId))
        }
    }

    companion object {
        private const val EXPECTED_PACK_SIZE = 3
    }
}
