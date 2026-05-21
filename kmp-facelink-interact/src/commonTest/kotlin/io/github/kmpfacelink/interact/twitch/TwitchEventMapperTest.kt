package io.github.kmpfacelink.interact.twitch

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchEventMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val timestamp = 1689778611000L

    @Test
    fun mapChatMessageWithEmotes() {
        val payload = parseJson(CHAT_MESSAGE_PAYLOAD)
        val event = TwitchEventMapper.map("channel.chat.message", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is ChatMessage)
        assertEquals(StreamPlatform.TWITCH, event.platform)
        assertEquals("cool_viewer", event.author)
        assertEquals("Hello Kappa world!", event.text)
        assertEquals(listOf("subscriber"), event.badges)
        assertEquals(1, event.emotes.size)
        assertEquals("25", event.emotes[0].id)
        assertEquals("Kappa", event.emotes[0].name)
        assertEquals(timestamp, event.timestampMs)
    }

    @Test
    fun mapChatMessageMinimal() {
        val payload = parseJson(CHAT_MESSAGE_MINIMAL)
        val event = TwitchEventMapper.map("channel.chat.message", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is ChatMessage)
        assertEquals("viewer1", event.author)
        assertEquals("Hi!", event.text)
        assertTrue(event.badges.isEmpty())
        assertTrue(event.emotes.isEmpty())
    }

    @Test
    fun mapSubscribe() {
        val payload = parseJson(SUBSCRIBE_PAYLOAD)
        val event = TwitchEventMapper.map("channel.subscribe", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals(StreamPlatform.TWITCH, event.platform)
        assertEquals("sub_user", event.author)
        assertEquals(1, event.tier)
        assertEquals(4.99, event.amount)
        assertEquals("USD", event.currency)
    }

    @Test
    fun mapSubscribeTier3() {
        val payload = parseJson(SUBSCRIBE_TIER3_PAYLOAD)
        val event = TwitchEventMapper.map("channel.subscribe", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals(3, event.tier)
        assertEquals(24.99, event.amount)
    }

    @Test
    fun mapGiftSub() {
        val payload = parseJson(GIFT_SUB_PAYLOAD)
        val event = TwitchEventMapper.map("channel.subscription.gift", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals("gifter_name", event.author)
        assertEquals(1, event.tier)
        // 5 gifts at tier 1 = 5 * 4.99 = 24.95
        assertEquals(24.95, event.amount, 0.01)
    }

    @Test
    fun mapGiftSubAnonymous() {
        val payload = parseJson(GIFT_SUB_ANONYMOUS_PAYLOAD)
        val event = TwitchEventMapper.map("channel.subscription.gift", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Gift)
        assertEquals("Anonymous", event.author)
    }

    @Test
    fun mapCheer() {
        val payload = parseJson(CHEER_PAYLOAD)
        val event = TwitchEventMapper.map("channel.cheer", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is SuperChat)
        assertEquals("cheerer", event.author)
        assertEquals(5.0, event.amount) // 500 bits = $5.00
        assertEquals("USD", event.currency)
        assertEquals("Cheer500 You rock!", event.text)
    }

    @Test
    fun mapCheerAnonymous() {
        val payload = parseJson(CHEER_ANONYMOUS_PAYLOAD)
        val event = TwitchEventMapper.map("channel.cheer", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is SuperChat)
        assertEquals("Anonymous", event.author)
    }

    @Test
    fun mapRaid() {
        val payload = parseJson(RAID_PAYLOAD)
        val event = TwitchEventMapper.map("channel.raid", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Raid)
        assertEquals("raider_channel", event.author)
        assertEquals(150, event.viewerCount)
    }

    @Test
    fun mapFollow() {
        val payload = parseJson(FOLLOW_PAYLOAD)
        val event = TwitchEventMapper.map("channel.follow", payload, timestamp)
        assertNotNull(event)
        assertTrue(event is Follow)
        assertEquals("new_follower", event.author)
        assertEquals(timestamp, event.timestampMs)
    }

    @Test
    fun mapChannelPointRedemption() {
        val payload = parseJson(CHANNEL_POINTS_PAYLOAD)
        val event = TwitchEventMapper.map(
            "channel.channel_points_custom_reward_redemption.add",
            payload,
            timestamp,
        )
        assertNotNull(event)
        assertTrue(event is ChannelPointRedemption)
        assertEquals("redeemer", event.author)
        assertEquals("Hydrate!", event.rewardTitle)
        assertEquals("Drink water please", event.input)
        assertEquals(500, event.cost)
    }

    @Test
    fun mapChannelPointRedemptionNoInput() {
        val payload = parseJson(CHANNEL_POINTS_NO_INPUT_PAYLOAD)
        val event = TwitchEventMapper.map(
            "channel.channel_points_custom_reward_redemption.add",
            payload,
            timestamp,
        )
        assertNotNull(event)
        assertTrue(event is ChannelPointRedemption)
        assertEquals("", event.input)
    }

    @Test
    fun mapUnknownTypeReturnsNull() {
        val payload = parseJson("{}")
        val event = TwitchEventMapper.map("unknown.type", payload, timestamp)
        assertNull(event)
    }

    private fun parseJson(raw: String): JsonObject =
        json.parseToJsonElement(raw).let { it as JsonObject }

    companion object {
        private val CHAT_MESSAGE_PAYLOAD = """
            {
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "chatter_user_id": "67890",
                "chatter_user_login": "cool_viewer",
                "chatter_user_name": "cool_viewer",
                "message_id": "msg-001",
                "message": {
                    "text": "Hello Kappa world!",
                    "fragments": [
                        {"type": "text", "text": "Hello ", "cheermote": null, "emote": null, "mention": null},
                        {"type": "emote", "text": "Kappa", "cheermote": null, "emote": {"id": "25", "emote_set_id": "0", "owner_id": "0", "format": ["static"]}, "mention": null},
                        {"type": "text", "text": " world!", "cheermote": null, "emote": null, "mention": null}
                    ]
                },
                "color": "#FF4500",
                "badges": [{"set_id": "subscriber", "id": "1", "info": ""}],
                "message_type": "text"
            }
        """.trimIndent()

        private val CHAT_MESSAGE_MINIMAL = """
            {
                "broadcaster_user_id": "12345",
                "chatter_user_name": "viewer1",
                "message": {
                    "text": "Hi!",
                    "fragments": [{"type": "text", "text": "Hi!"}]
                },
                "badges": [],
                "message_type": "text"
            }
        """.trimIndent()

        private val SUBSCRIBE_PAYLOAD = """
            {
                "user_id": "11111",
                "user_login": "sub_user",
                "user_name": "sub_user",
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "tier": "1000",
                "is_gift": false
            }
        """.trimIndent()

        private val SUBSCRIBE_TIER3_PAYLOAD = """
            {
                "user_id": "11111",
                "user_login": "sub_user",
                "user_name": "sub_user",
                "broadcaster_user_id": "12345",
                "tier": "3000",
                "is_gift": false
            }
        """.trimIndent()

        private val GIFT_SUB_PAYLOAD = """
            {
                "user_id": "22222",
                "user_login": "gifter",
                "user_name": "gifter_name",
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "total": 5,
                "tier": "1000",
                "cumulative_total": 10,
                "is_anonymous": false
            }
        """.trimIndent()

        private val GIFT_SUB_ANONYMOUS_PAYLOAD = """
            {
                "user_id": "",
                "user_login": "",
                "user_name": "",
                "broadcaster_user_id": "12345",
                "total": 1,
                "tier": "1000",
                "cumulative_total": null,
                "is_anonymous": true
            }
        """.trimIndent()

        private val CHEER_PAYLOAD = """
            {
                "is_anonymous": false,
                "user_id": "33333",
                "user_login": "cheerer",
                "user_name": "cheerer",
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "message": "Cheer500 You rock!",
                "bits": 500
            }
        """.trimIndent()

        private val CHEER_ANONYMOUS_PAYLOAD = """
            {
                "is_anonymous": true,
                "user_id": null,
                "user_login": null,
                "user_name": null,
                "broadcaster_user_id": "12345",
                "message": "Cheer100",
                "bits": 100
            }
        """.trimIndent()

        private val RAID_PAYLOAD = """
            {
                "from_broadcaster_user_id": "44444",
                "from_broadcaster_user_login": "raider_channel",
                "from_broadcaster_user_name": "raider_channel",
                "to_broadcaster_user_id": "12345",
                "to_broadcaster_user_login": "streamer",
                "to_broadcaster_user_name": "Streamer",
                "viewers": 150
            }
        """.trimIndent()

        private val FOLLOW_PAYLOAD = """
            {
                "user_id": "55555",
                "user_login": "new_follower",
                "user_name": "new_follower",
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "followed_at": "2023-07-19T14:56:51.634234626Z"
            }
        """.trimIndent()

        private val CHANNEL_POINTS_PAYLOAD = """
            {
                "id": "redemption-001",
                "broadcaster_user_id": "12345",
                "broadcaster_user_login": "streamer",
                "broadcaster_user_name": "Streamer",
                "user_id": "66666",
                "user_login": "redeemer",
                "user_name": "redeemer",
                "user_input": "Drink water please",
                "status": "unfulfilled",
                "reward": {
                    "id": "reward-001",
                    "title": "Hydrate!",
                    "cost": 500,
                    "prompt": "Remind the streamer to drink water"
                },
                "redeemed_at": "2023-07-19T14:56:51.634234626Z"
            }
        """.trimIndent()

        private val CHANNEL_POINTS_NO_INPUT_PAYLOAD = """
            {
                "id": "redemption-002",
                "broadcaster_user_id": "12345",
                "user_id": "77777",
                "user_login": "user2",
                "user_name": "user2",
                "user_input": "",
                "status": "unfulfilled",
                "reward": {
                    "id": "reward-002",
                    "title": "Sound Alert",
                    "cost": 100,
                    "prompt": ""
                },
                "redeemed_at": "2023-07-19T15:00:00Z"
            }
        """.trimIndent()
    }
}
