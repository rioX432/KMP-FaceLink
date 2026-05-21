package io.github.kmpfacelink.interact

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Emote
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamEventSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun chatMessageRoundTrip() {
        val original: StreamEvent = ChatMessage(
            platform = StreamPlatform.TWITCH,
            timestampMs = 1000L,
            author = "viewer1",
            text = "Hello stream!",
            badges = listOf("subscriber", "moderator"),
            emotes = listOf(Emote(id = "emote1", name = "Kappa")),
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun chatMessageWithDefaultsRoundTrip() {
        val original: StreamEvent = ChatMessage(
            platform = StreamPlatform.YOUTUBE,
            timestampMs = 2000L,
            author = "viewer2",
            text = "Minimal message",
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun giftRoundTrip() {
        val original: StreamEvent = Gift(
            platform = StreamPlatform.TWITCH,
            timestampMs = 3000L,
            author = "generous_viewer",
            amount = 4.99,
            currency = "USD",
            tier = 1,
            message = "Keep it up!",
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun followRoundTrip() {
        val original: StreamEvent = Follow(
            platform = StreamPlatform.BILIBILI,
            timestampMs = 4000L,
            author = "new_follower",
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun raidRoundTrip() {
        val original: StreamEvent = Raid(
            platform = StreamPlatform.TWITCH,
            timestampMs = 5000L,
            author = "raider_channel",
            viewerCount = 150,
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun channelPointRedemptionRoundTrip() {
        val original: StreamEvent = ChannelPointRedemption(
            platform = StreamPlatform.TWITCH,
            timestampMs = 6000L,
            author = "point_spender",
            rewardTitle = "Hydrate!",
            input = "Drink water please",
            cost = 500,
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun superChatRoundTrip() {
        val original: StreamEvent = SuperChat(
            platform = StreamPlatform.YOUTUBE,
            timestampMs = 7000L,
            author = "super_chatter",
            amount = 50.0,
            currency = "JPY",
            text = "Great stream!",
        )

        val encoded = json.encodeToString(StreamEvent.serializer(), original)
        val decoded = json.decodeFromString(StreamEvent.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun polymorphicDeserializationPreservesType() {
        val chat: StreamEvent = ChatMessage(
            platform = StreamPlatform.TWITCH,
            timestampMs = 8000L,
            author = "tester",
            text = "type check",
        )
        val gift: StreamEvent = Gift(
            platform = StreamPlatform.YOUTUBE,
            timestampMs = 9000L,
            author = "donor",
            amount = 10.0,
            currency = "USD",
        )

        val chatJson = json.encodeToString(StreamEvent.serializer(), chat)
        val giftJson = json.encodeToString(StreamEvent.serializer(), gift)

        val decodedChat = json.decodeFromString(StreamEvent.serializer(), chatJson)
        val decodedGift = json.decodeFromString(StreamEvent.serializer(), giftJson)

        assertEquals(true, decodedChat is ChatMessage)
        assertEquals(true, decodedGift is Gift)
    }
}
