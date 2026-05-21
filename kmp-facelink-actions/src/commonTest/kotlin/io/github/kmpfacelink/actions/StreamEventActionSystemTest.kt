package io.github.kmpfacelink.actions

import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamPlatform
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamEventActionSystemTest {

    @Test
    fun processStreamEventEmitsStartedForMatchingBinding() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding(
                actionId = "gift-action",
                trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT),
            ),
        )

        val events = system.processStreamEvent(
            Gift(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "donor",
                amount = GIFT_AMOUNT,
                currency = "USD",
            ),
        )

        assertEquals(1, events.size)
        val started = events[0]
        assertIs<ActionEvent.Started>(started)
        assertEquals("gift-action", started.actionId)
        assertEquals(TIMESTAMP, started.timestampMs)
    }

    @Test
    fun processStreamEventIgnoresNonMatchingBindings() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding(
                actionId = "gift-action",
                trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT),
            ),
        )

        val events = system.processStreamEvent(
            Follow(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "new_follower",
            ),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun processStreamEventIgnoresNonStreamTriggerBindings() = runTest {
        val system = ActionSystem()
        system.register(ActionBinding("tongue", tongueOutTrigger()))

        val events = system.processStreamEvent(
            Gift(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "donor",
                amount = GIFT_AMOUNT,
                currency = "USD",
            ),
        )

        assertTrue(events.isEmpty())
    }

    @Test
    fun processStreamEventMatchesMultipleBindings() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding(
                actionId = "gift-throw",
                trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT),
            ),
        )
        system.register(
            ActionBinding(
                actionId = "gift-sound",
                trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT),
            ),
        )

        val events = system.processStreamEvent(
            Gift(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "donor",
                amount = GIFT_AMOUNT,
                currency = "USD",
            ),
        )

        assertEquals(2, events.size)
        assertTrue(events.all { it is ActionEvent.Started })
    }

    @Test
    fun processStreamEventRespectsFilter() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding(
                actionId = "big-gift",
                trigger = ActionTrigger.StreamEventTrigger(
                    eventType = StreamEventType.GIFT,
                    filter = StreamEventFilter.MinAmount(MIN_GIFT_AMOUNT),
                ),
            ),
        )

        val smallGift = system.processStreamEvent(
            Gift(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "donor",
                amount = SMALL_GIFT_AMOUNT,
                currency = "USD",
            ),
        )
        assertTrue(smallGift.isEmpty())

        val bigGift = system.processStreamEvent(
            Gift(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP_2,
                author = "big_donor",
                amount = BIG_GIFT_AMOUNT,
                currency = "USD",
            ),
        )
        assertEquals(1, bigGift.size)
        assertIs<ActionEvent.Started>(bigGift[0])
    }

    @Test
    fun processStreamEventReturnsEmptyAfterRelease() = runTest {
        val system = ActionSystem()
        system.register(
            ActionBinding(
                actionId = "raid-action",
                trigger = ActionTrigger.StreamEventTrigger(StreamEventType.RAID),
            ),
        )
        system.release()

        val events = system.processStreamEvent(
            Raid(
                platform = StreamPlatform.TWITCH,
                timestampMs = TIMESTAMP,
                author = "raider",
                viewerCount = RAID_VIEWERS,
            ),
        )

        assertTrue(events.isEmpty())
    }

    companion object {
        private const val TIMESTAMP = 1000L
        private const val TIMESTAMP_2 = 2000L
        private const val GIFT_AMOUNT = 5.0
        private const val SMALL_GIFT_AMOUNT = 2.0
        private const val BIG_GIFT_AMOUNT = 20.0
        private const val MIN_GIFT_AMOUNT = 10.0
        private const val RAID_VIEWERS = 100
    }
}
