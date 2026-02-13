package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionSystemTest {

    // --- Registration ---

    @Test
    fun duplicateActionIdThrows() = runTest {
        val system = ActionSystem()
        system.register(ActionBinding("a", tongueOutTrigger()))
        assertFailsWith<IllegalArgumentException> {
            system.register(ActionBinding("a", smileTrigger()))
        }
    }

    @Test
    fun unregisterRemovesBinding() = runTest {
        val system = ActionSystem()
        system.register(ActionBinding("a", tongueOutTrigger()))
        assertTrue(system.unregister("a"))
        // Re-register succeeds after unregister
        system.register(ActionBinding("a", tongueOutTrigger()))
    }

    // --- Immediate activation (holdTimeMs = 0) ---

    @Test
    fun immediateActivationEmitsStartedAndReleased() = runTest {
        val system = ActionSystem()
        system.register(ActionBinding("tongue", tongueOutTrigger()))

        val events1 = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, events1.size)
        val started = events1[0]
        assertIs<ActionEvent.Started>(started)
        assertEquals("tongue", started.actionId)

        val events2 = system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertEquals(1, events2.size)
        val released = events2[0]
        assertIs<ActionEvent.Released>(released)
        assertEquals(TIMESTAMP_100, released.totalDurationMs)
    }

    // --- Hold time ---

    @Test
    fun holdTimeDelaysActivation() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding("tongue", tongueOutTrigger(), holdTimeMs = HOLD_TIME_500),
        )

        val events1 = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(events1.isEmpty())

        val events2 = system.processFace(faceData(TIMESTAMP_400, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(events2.isEmpty())

        val events3 = system.processFace(faceData(TIMESTAMP_600, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, events3.size)
        assertIs<ActionEvent.Started>(events3[0])
    }

    @Test
    fun holdTimeCanceledWhenConditionLost() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding("tongue", tongueOutTrigger(), holdTimeMs = HOLD_TIME_500),
        )

        // Start hold
        val events1 = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(events1.isEmpty())

        // Condition lost — resets hold timer
        val events2 = system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertTrue(events2.isEmpty())

        // Re-engage — hold timer starts fresh
        val events3 = system.processFace(faceData(TIMESTAMP_1000, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(events3.isEmpty())

        // Hold time elapsed from re-engagement
        val events4 = system.processFace(faceData(TIMESTAMP_1500, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, events4.size)
        assertIs<ActionEvent.Started>(events4[0])
    }

    // --- Cooldown ---

    @Test
    fun cooldownPreventsReactivation() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding("tongue", tongueOutTrigger(), cooldownMs = COOLDOWN_1000),
        )

        val started = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, started.size)
        assertIs<ActionEvent.Started>(started[0])

        val released = system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertEquals(1, released.size)
        assertIs<ActionEvent.Released>(released[0])

        // During cooldown — no event
        val duringCooldown = system.processFace(faceData(TIMESTAMP_500, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(duringCooldown.isEmpty())

        // After cooldown expires
        val afterCooldown = system.processFace(faceData(TIMESTAMP_1300, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, afterCooldown.size)
        assertIs<ActionEvent.Started>(afterCooldown[0])
    }

    // --- Debounce ---

    @Test
    fun debounceIgnoresBriefConditionLoss() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding("tongue", tongueOutTrigger(), debounceMs = DEBOUNCE_200),
        )

        val started = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, started.size)
        assertIs<ActionEvent.Started>(started[0])

        // Brief loss — no release
        val briefLoss = system.processFace(faceData(TIMESTAMP_200, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertTrue(briefLoss.isEmpty())

        // Condition returns within debounce window
        val returned = system.processFace(faceData(TIMESTAMP_300, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(returned.isEmpty())

        // Lose condition again
        val lostAgain = system.processFace(faceData(TIMESTAMP_400, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertTrue(lostAgain.isEmpty())

        // Debounce elapsed
        val debounceElapsed = system.processFace(faceData(TIMESTAMP_700, BlendShape.TONGUE_OUT to LOW_VALUE))
        assertEquals(1, debounceElapsed.size)
        assertIs<ActionEvent.Released>(debounceElapsed[0])
    }

    // --- Held events ---

    @Test
    fun heldEventsEmittedEachFrame() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding("tongue", tongueOutTrigger(), emitHeldEvents = true),
        )

        val started = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, started.size)
        assertIs<ActionEvent.Started>(started[0])

        val held = system.processFace(faceData(TIMESTAMP_133, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertEquals(1, held.size)
        val heldEvent = held[0]
        assertIs<ActionEvent.Held>(heldEvent)
        assertEquals(HELD_DURATION_33, heldEvent.durationMs)
    }

    // --- Combined face + hand trigger ---

    @Test
    fun combinedTriggerRequiresBothFaceAndHand() = runTest {
        val trigger = ActionTrigger.CombinedTrigger(
            listOf(
                ActionTrigger.ExpressionTrigger(BlendShape.MOUTH_SMILE_LEFT, threshold = THRESHOLD_05),
                ActionTrigger.GestureTrigger(HandGesture.VICTORY),
            ),
        )
        val system = ActionSystem()
        system.register(ActionBinding("combo", trigger))

        // Only face data — not activated
        val faceOnly = system.processFace(faceData(TIMESTAMP_100, BlendShape.MOUTH_SMILE_LEFT to HIGH_VALUE))
        assertTrue(faceOnly.isEmpty())

        // Add hand data — activated
        val withHand = system.processHand(handData(TIMESTAMP_200, HandGesture.VICTORY))
        assertEquals(1, withHand.size)
        assertIs<ActionEvent.Started>(withHand[0])
    }

    // --- Clear ---

    @Test
    fun clearRemovesAllBindings() = runTest {
        val system = ActionSystem()
        system.register(ActionBinding("a", tongueOutTrigger()))
        system.register(ActionBinding("b", smileTrigger()))
        system.clear()

        val events = system.processFace(faceData(TIMESTAMP_100, BlendShape.TONGUE_OUT to HIGH_VALUE))
        assertTrue(events.isEmpty())
    }

    // --- Helpers ---

    private fun faceData(
        timestampMs: Long,
        vararg blendShapes: Pair<BlendShape, Float>,
    ): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = true,
        )
    }

    private fun handData(
        timestampMs: Long,
        gesture: HandGesture,
        confidence: Float = DEFAULT_CONFIDENCE,
    ): HandTrackingData = HandTrackingData(
        hands = listOf(
            TrackedHand(
                handedness = Handedness.RIGHT,
                landmarks = emptyList(),
                gesture = gesture,
                gestureConfidence = confidence,
            ),
        ),
        timestampMs = timestampMs,
        isTracking = true,
    )

    companion object {
        private const val HIGH_VALUE = 0.8f
        private const val LOW_VALUE = 0.1f
        private const val THRESHOLD_05 = 0.5f
        private const val DEFAULT_CONFIDENCE = 0.9f

        private const val TIMESTAMP_100 = 100L
        private const val TIMESTAMP_133 = 133L
        private const val TIMESTAMP_200 = 200L
        private const val TIMESTAMP_300 = 300L
        private const val TIMESTAMP_400 = 400L
        private const val TIMESTAMP_500 = 500L
        private const val TIMESTAMP_600 = 600L
        private const val TIMESTAMP_700 = 700L
        private const val TIMESTAMP_1000 = 1000L
        private const val TIMESTAMP_1300 = 1300L
        private const val TIMESTAMP_1500 = 1500L

        private const val HOLD_TIME_500 = 500L
        private const val COOLDOWN_1000 = 1000L
        private const val DEBOUNCE_200 = 200L
        private const val HELD_DURATION_33 = 33L
    }
}
