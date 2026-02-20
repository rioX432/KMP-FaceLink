package io.github.kmpfacelink.actions.emotion

import io.github.kmpfacelink.actions.ActionTrigger
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmotionTriggersTest {

    @Test
    fun happyTriggerIsCombined() {
        val trigger = happyTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun sadTriggerIsCombined() {
        val trigger = sadTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun angryTriggerIsCombined() {
        val trigger = angryTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun surprisedEmotionTriggerIsCombined() {
        val trigger = surprisedEmotionTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun disgustedTriggerIsCombined() {
        val trigger = disgustedTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun fearTriggerIsCombined() {
        val trigger = fearTrigger()
        assertIs<ActionTrigger.CombinedTrigger>(trigger)
        assertTrue(trigger.triggers.size >= 2)
    }

    @Test
    fun allTriggersAcceptCustomThresholds() {
        // Verify custom thresholds don't throw
        happyTrigger(threshold = 0.3f)
        sadTrigger(frownThreshold = 0.3f, browThreshold = 0.2f)
        angryTrigger(browThreshold = 0.4f, sneerThreshold = 0.2f)
        surprisedEmotionTrigger(eyeThreshold = 0.4f, browThreshold = 0.3f, jawThreshold = 0.2f)
        disgustedTrigger(sneerThreshold = 0.4f, lipThreshold = 0.2f)
        fearTrigger(
            eyeThreshold = 0.4f,
            browUpThreshold = 0.3f,
            browDownThreshold = 0.1f,
            mouthThreshold = 0.2f,
        )
    }
}
