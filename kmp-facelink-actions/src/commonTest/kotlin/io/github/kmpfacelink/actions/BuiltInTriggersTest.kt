package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.HandGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BuiltInTriggersTest {

    @Test
    fun winkLeftIsCombinedTrigger() {
        val trigger = winkLeftTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertEquals(2, trigger.triggers.size)

        val blink = trigger.triggers[0]
        assertIs<ActionTrigger.ExpressionTrigger>(blink)
        assertEquals(BlendShape.EYE_BLINK_LEFT, blink.blendShape)
        assertEquals(ThresholdDirection.ABOVE, blink.direction)

        val open = trigger.triggers[1]
        assertIs<ActionTrigger.ExpressionTrigger>(open)
        assertEquals(BlendShape.EYE_BLINK_RIGHT, open.blendShape)
        assertEquals(ThresholdDirection.BELOW, open.direction)
    }

    @Test
    fun winkRightIsCombinedTrigger() {
        val trigger = winkRightTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertEquals(2, trigger.triggers.size)

        val blink = trigger.triggers[0]
        assertIs<ActionTrigger.ExpressionTrigger>(blink)
        assertEquals(BlendShape.EYE_BLINK_RIGHT, blink.blendShape)
    }

    @Test
    fun smileIsCombinedTrigger() {
        val trigger = smileTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertEquals(2, trigger.triggers.size)
    }

    @Test
    fun tongueOutIsExpressionTrigger() {
        val trigger = tongueOutTrigger()
        assertIs<ActionTrigger.ExpressionTrigger>(trigger)
        assertEquals(BlendShape.TONGUE_OUT, trigger.blendShape)
    }

    @Test
    fun surprisedIsCombinedTrigger() {
        val trigger = surprisedTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertEquals(3, trigger.triggers.size)
    }

    @Test
    fun thumbsUpIsGestureTrigger() {
        val trigger = thumbsUpTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.THUMB_UP, trigger.gesture)
    }

    @Test
    fun victoryIsGestureTrigger() {
        val trigger = victoryTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.VICTORY, trigger.gesture)
    }

    @Test
    fun openPalmIsGestureTrigger() {
        val trigger = openPalmTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.OPEN_PALM, trigger.gesture)
    }

    @Test
    fun closedFistIsGestureTrigger() {
        val trigger = closedFistTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.CLOSED_FIST, trigger.gesture)
    }

    @Test
    fun pinchIsGestureTrigger() {
        val trigger = pinchTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.PINCH, trigger.gesture)
    }

    @Test
    fun okSignIsGestureTrigger() {
        val trigger = okSignTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.OK_SIGN, trigger.gesture)
    }

    @Test
    fun rockIsGestureTrigger() {
        val trigger = rockTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.ROCK, trigger.gesture)
    }

    @Test
    fun fingerCountThreeIsGestureTrigger() {
        val trigger = fingerCountThreeTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.FINGER_COUNT_THREE, trigger.gesture)
    }

    @Test
    fun fingerCountFourIsGestureTrigger() {
        val trigger = fingerCountFourTrigger()
        assertIs<ActionTrigger.GestureTrigger>(trigger)
        assertEquals(HandGesture.FINGER_COUNT_FOUR, trigger.gesture)
    }
}
