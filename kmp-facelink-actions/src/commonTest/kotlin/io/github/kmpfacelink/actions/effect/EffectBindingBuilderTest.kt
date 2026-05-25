package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionTrigger
import io.github.kmpfacelink.actions.StreamEventFilter
import io.github.kmpfacelink.actions.StreamEventType
import io.github.kmpfacelink.actions.ThresholdDirection
import io.github.kmpfacelink.interact.model.AvatarAnchor
import io.github.kmpfacelink.interact.model.EffectParameter
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EffectBindingBuilderTest {

    private val testThrowEffect = EffectParameter.ThrowEffect(
        durationMs = 1000L,
        intensity = 0.8f,
        anchor = AvatarAnchor.HEAD,
        objectName = "confetti",
        count = 5,
    )

    private val testShakeEffect = EffectParameter.ScreenShake(
        durationMs = 500L,
        intensity = 0.6f,
        amplitude = 0.1f,
    )

    // --- DSL produces correct bindings ---

    @Test
    fun onGestureProducesCorrectBinding() {
        val bindings = effectBindings {
            onGesture(HandGesture.VICTORY, hand = Handedness.RIGHT, minConfidence = 0.7f) {
                emit(testThrowEffect)
            }
        }

        assertEquals(1, bindings.size)
        val binding = bindings[0]
        assertTrue(binding.bindingId.startsWith("gesture.victory."))
        val trigger = binding.actionBinding.trigger
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.VICTORY, trigger.gesture)
        assertEquals(Handedness.RIGHT, trigger.hand)
        assertEquals(0.7f, trigger.minConfidence)
    }

    @Test
    fun onExpressionProducesCorrectBinding() {
        val bindings = effectBindings {
            onExpression(BlendShape.TONGUE_OUT, threshold = 0.6f, direction = ThresholdDirection.ABOVE) {
                emit(testShakeEffect)
            }
        }

        assertEquals(1, bindings.size)
        val binding = bindings[0]
        assertTrue(binding.bindingId.startsWith("expression.tongue_out."))
        val trigger = binding.actionBinding.trigger
        assertIs<ActionTrigger.ExpressionTrigger>(trigger)
        assertEquals(BlendShape.TONGUE_OUT, trigger.blendShape)
        assertEquals(0.6f, trigger.threshold)
        assertEquals(ThresholdDirection.ABOVE, trigger.direction)
    }

    @Test
    fun onStreamEventProducesCorrectBinding() {
        val filter = StreamEventFilter.MinAmount(10.0)
        val bindings = effectBindings {
            onStreamEvent(StreamEventType.GIFT, filter) {
                emit(testThrowEffect)
            }
        }

        assertEquals(1, bindings.size)
        val binding = bindings[0]
        assertTrue(binding.bindingId.startsWith("stream.gift."))
        val trigger = binding.actionBinding.trigger
        assertIs<ActionTrigger.StreamEventTrigger>(trigger)
        assertEquals(StreamEventType.GIFT, trigger.eventType)
        assertEquals(filter, trigger.filter)
    }

    // --- Effect factory configuration ---

    @Test
    fun defaultFactoryIsStatic() {
        val bindings = effectBindings {
            onGesture(HandGesture.THUMB_UP) {
                emit(testThrowEffect)
            }
        }

        assertIs<EffectFactory.Static>(bindings[0].effectFactory)
    }

    @Test
    fun scaleWithDurationProducesScaledFactory() {
        val bindings = effectBindings {
            onGesture(HandGesture.THUMB_UP) {
                emit(testThrowEffect)
                scaleWithDuration(maxMs = 3000L)
            }
        }

        val factory = bindings[0].effectFactory
        assertIs<EffectFactory.Scaled>(factory)
        assertEquals(3000L, factory.maxDurationMs)
    }

    // --- Lifecycle and blend mode configuration ---

    @Test
    fun lifecycleModeIsConfigurable() {
        val bindings = effectBindings {
            onGesture(HandGesture.CLOSED_FIST) {
                emit(testShakeEffect)
                lifecycle(EffectLifecycleMode.WHILE_HELD)
            }
        }

        assertEquals(EffectLifecycleMode.WHILE_HELD, bindings[0].lifecycleMode)
        assertTrue(bindings[0].actionBinding.emitHeldEvents)
    }

    @Test
    fun blendModeIsConfigurable() {
        val bindings = effectBindings {
            onGesture(HandGesture.CLOSED_FIST) {
                emit(testShakeEffect)
                blendMode(EffectBlendMode.EXCLUSIVE)
            }
        }

        assertEquals(EffectBlendMode.EXCLUSIVE, bindings[0].blendMode)
    }

    @Test
    fun priorityIsConfigurable() {
        val bindings = effectBindings {
            onGesture(HandGesture.CLOSED_FIST) {
                emit(testShakeEffect)
                priority(10)
            }
        }

        assertEquals(10, bindings[0].priority)
    }

    @Test
    fun maxConcurrentIsConfigurable() {
        val bindings = effectBindings {
            onGesture(HandGesture.CLOSED_FIST) {
                emit(testShakeEffect)
                maxConcurrent(5)
            }
        }

        assertEquals(5, bindings[0].maxConcurrent)
    }

    @Test
    fun cooldownAndDebounceArePassedToActionBinding() {
        val bindings = effectBindings {
            onGesture(HandGesture.CLOSED_FIST) {
                emit(testShakeEffect)
                cooldown(500L)
                debounce(100L)
            }
        }

        assertEquals(500L, bindings[0].actionBinding.cooldownMs)
        assertEquals(100L, bindings[0].actionBinding.debounceMs)
    }

    // --- Multiple bindings ---

    @Test
    fun multipleBindingsHaveUniqueIds() {
        val bindings = effectBindings {
            onGesture(HandGesture.VICTORY) { emit(testThrowEffect) }
            onGesture(HandGesture.VICTORY) { emit(testThrowEffect) }
            onExpression(BlendShape.JAW_OPEN) { emit(testShakeEffect) }
        }

        assertEquals(3, bindings.size)
        val ids = bindings.map { it.bindingId }.toSet()
        assertEquals(3, ids.size)
    }

    // --- Error cases ---

    @Test
    fun missingEmitThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            effectBindings {
                onGesture(HandGesture.THUMB_UP) {
                    lifecycle(EffectLifecycleMode.ON_START)
                }
            }
        }
    }

    // --- Empty builder ---

    @Test
    fun emptyBuilderProducesEmptyList() {
        val bindings = effectBindings { }
        assertTrue(bindings.isEmpty())
    }
}
