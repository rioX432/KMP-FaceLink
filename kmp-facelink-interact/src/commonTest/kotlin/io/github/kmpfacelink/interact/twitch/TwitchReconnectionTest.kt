package io.github.kmpfacelink.interact.twitch

import kotlin.test.Test
import kotlin.test.assertEquals

class TwitchReconnectionTest {

    @Test
    fun backoffStartsAtOneSecond() {
        assertEquals(1000L, calculateBackoff(0))
    }

    @Test
    fun backoffDoublesEachAttempt() {
        assertEquals(1000L, calculateBackoff(0))
        assertEquals(2000L, calculateBackoff(1))
        assertEquals(4000L, calculateBackoff(2))
        assertEquals(8000L, calculateBackoff(3))
        assertEquals(16000L, calculateBackoff(4))
    }

    @Test
    fun backoffCapsAtThirtySeconds() {
        // attempt 5: 1000 << 5 = 32000, capped to 30000
        assertEquals(30000L, calculateBackoff(5))
        assertEquals(30000L, calculateBackoff(6))
        assertEquals(30000L, calculateBackoff(10))
        assertEquals(30000L, calculateBackoff(100))
    }

    @Test
    fun backoffAttemptFour() {
        // attempt 4: 1000 << 4 = 16000 (not yet capped)
        assertEquals(16000L, calculateBackoff(4))
    }

    @Test
    fun twitchSubscriptionTypeDefaults() {
        val defaults = TwitchSubscriptionType.DEFAULT
        assertEquals(5, defaults.size)
        assertEquals(
            setOf(
                TwitchSubscriptionType.CHAT_MESSAGE,
                TwitchSubscriptionType.CHANNEL_POINTS,
                TwitchSubscriptionType.SUBSCRIBE,
                TwitchSubscriptionType.CHEER,
                TwitchSubscriptionType.RAID,
            ),
            defaults,
        )
    }

    @Test
    fun twitchSubscriptionTypeStrings() {
        assertEquals("channel.chat.message", TwitchSubscriptionType.CHAT_MESSAGE.type)
        assertEquals("1", TwitchSubscriptionType.CHAT_MESSAGE.version)
        assertEquals("channel.follow", TwitchSubscriptionType.FOLLOW.type)
        assertEquals("2", TwitchSubscriptionType.FOLLOW.version)
        assertEquals(
            "channel.channel_points_custom_reward_redemption.add",
            TwitchSubscriptionType.CHANNEL_POINTS.type,
        )
    }

    @Test
    fun twitchConfigDefaults() {
        val config = TwitchConfig(
            accessToken = "token",
            clientId = "client",
            broadcasterId = "12345",
        )
        assertEquals(TwitchSubscriptionType.DEFAULT, config.subscriptions)
    }

    @Test
    fun twitchConfigCustomSubscriptions() {
        val config = TwitchConfig(
            accessToken = "token",
            clientId = "client",
            broadcasterId = "12345",
            subscriptions = setOf(TwitchSubscriptionType.CHAT_MESSAGE),
        )
        assertEquals(1, config.subscriptions.size)
        assertEquals(TwitchSubscriptionType.CHAT_MESSAGE, config.subscriptions.first())
    }
}
