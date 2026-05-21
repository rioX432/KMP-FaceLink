package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultStreamBindingsTest {

    @Test
    fun giftThrowCreatesGiftTriggerWithMinAmount() {
        val binding = DefaultStreamBindings.giftThrow(minAmount = MIN_AMOUNT)

        assertEquals("stream.gift.throw", binding.actionId)
        val trigger = binding.trigger
        assertIs<ActionTrigger.StreamEventTrigger>(trigger)
        assertEquals(StreamEventType.GIFT, trigger.eventType)
        val filter = trigger.filter
        assertIs<StreamEventFilter.MinAmount>(filter)
        assertEquals(MIN_AMOUNT, filter.amount)
    }

    @Test
    fun giftThrowUsesDefaultMinAmount() {
        val binding = DefaultStreamBindings.giftThrow()

        val trigger = binding.trigger
        assertIs<ActionTrigger.StreamEventTrigger>(trigger)
        val filter = trigger.filter
        assertIs<StreamEventFilter.MinAmount>(filter)
        assertEquals(DEFAULT_MIN_AMOUNT, filter.amount)
    }

    @Test
    fun raidScreenShakeCreatesRaidTrigger() {
        val binding = DefaultStreamBindings.raidScreenShake()

        assertEquals("stream.raid.shake", binding.actionId)
        val trigger = binding.trigger
        assertIs<ActionTrigger.StreamEventTrigger>(trigger)
        assertEquals(StreamEventType.RAID, trigger.eventType)
        assertIs<StreamEventFilter.Any>(trigger.filter)
    }

    @Test
    fun channelPointExpressionCreatesRewardTitleTrigger() {
        val blendShapes = mapOf(
            BlendShape.MOUTH_SMILE_LEFT to SMILE_VALUE,
            BlendShape.MOUTH_SMILE_RIGHT to SMILE_VALUE,
        )
        val binding = DefaultStreamBindings.channelPointExpression(
            rewardTitle = "Smile!",
            blendShapes = blendShapes,
        )

        assertEquals("stream.channelpoint.expression", binding.actionId)
        val trigger = binding.trigger
        assertIs<ActionTrigger.StreamEventTrigger>(trigger)
        assertEquals(StreamEventType.CHANNEL_POINT_REDEMPTION, trigger.eventType)
        val filter = trigger.filter
        assertIs<StreamEventFilter.RewardTitle>(filter)
        assertEquals("Smile!", filter.title)
    }

    companion object {
        private const val MIN_AMOUNT = 5.0
        private const val DEFAULT_MIN_AMOUNT = 1.0
        private const val SMILE_VALUE = 0.9f
    }
}
