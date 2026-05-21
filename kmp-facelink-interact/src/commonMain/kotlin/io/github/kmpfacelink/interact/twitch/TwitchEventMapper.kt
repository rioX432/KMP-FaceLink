package io.github.kmpfacelink.interact.twitch

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Emote
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps Twitch EventSub notification payloads to [StreamEvent] instances.
 */
internal object TwitchEventMapper {

    /**
     * Maps a Twitch EventSub event payload to a [StreamEvent].
     *
     * @param type The EventSub subscription type string.
     * @param payload The event JSON object from the notification.
     * @param timestampMs Event timestamp in epoch milliseconds.
     * @return The mapped [StreamEvent], or null if the type is unsupported.
     */
    fun map(type: String, payload: JsonObject, timestampMs: Long): StreamEvent? =
        when (type) {
            "channel.chat.message" -> mapChatMessage(payload, timestampMs)
            "channel.subscribe" -> mapSubscribe(payload, timestampMs)
            "channel.subscription.gift" -> mapGiftSub(payload, timestampMs)
            "channel.cheer" -> mapCheer(payload, timestampMs)
            "channel.raid" -> mapRaid(payload, timestampMs)
            "channel.follow" -> mapFollow(payload, timestampMs)
            "channel.channel_points_custom_reward_redemption.add" ->
                mapChannelPoints(payload, timestampMs)
            else -> null
        }
}

private fun mapChatMessage(event: JsonObject, timestampMs: Long): ChatMessage {
    val author = event.string("chatter_user_name")
    val messageObj = event["message"]?.jsonObject
    val text = messageObj?.string("text").orEmpty()
    val badges = event["badges"]?.jsonArray?.map { badge ->
        badge.jsonObject.string("set_id")
    }.orEmpty()
    val emotes = extractEmotes(messageObj)
    return ChatMessage(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        text = text,
        badges = badges,
        emotes = emotes,
    )
}

private fun extractEmotes(messageObj: JsonObject?): List<Emote> {
    val fragments = messageObj?.get("fragments")?.jsonArray ?: return emptyList()
    return fragments.mapNotNull { fragment ->
        val obj = fragment.jsonObject
        if (obj.string("type") == "emote") {
            val emoteObj = obj["emote"]?.jsonObject ?: return@mapNotNull null
            Emote(
                id = emoteObj.string("id"),
                name = obj.string("text"),
            )
        } else {
            null
        }
    }
}

private fun mapSubscribe(event: JsonObject, timestampMs: Long): Gift {
    val author = event.string("user_name")
    val tier = parseTier(event.string("tier"))
    return Gift(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        amount = tierToAmount(tier),
        currency = "USD",
        tier = tier,
    )
}

private fun mapGiftSub(event: JsonObject, timestampMs: Long): Gift {
    val author = if (event.booleanOrDefault("is_anonymous", false)) {
        "Anonymous"
    } else {
        event.string("user_name")
    }
    val tier = parseTier(event.string("tier"))
    val total = event["total"]?.jsonPrimitive?.int ?: 1
    return Gift(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        amount = tierToAmount(tier) * total,
        currency = "USD",
        tier = tier,
    )
}

private fun mapCheer(event: JsonObject, timestampMs: Long): SuperChat {
    val isAnonymous = event.booleanOrDefault("is_anonymous", false)
    val author = if (isAnonymous) "Anonymous" else event.string("user_name")
    val bits = event["bits"]?.jsonPrimitive?.int ?: 0
    val message = event.string("message")
    return SuperChat(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        amount = bits / BITS_PER_CENT,
        currency = "USD",
        text = message,
    )
}

private fun mapRaid(event: JsonObject, timestampMs: Long): Raid {
    val author = event.string("from_broadcaster_user_name")
    val viewers = event["viewers"]?.jsonPrimitive?.int ?: 0
    return Raid(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        viewerCount = viewers,
    )
}

private fun mapFollow(event: JsonObject, timestampMs: Long): Follow =
    Follow(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = event.string("user_name"),
    )

private fun mapChannelPoints(
    event: JsonObject,
    timestampMs: Long,
): ChannelPointRedemption {
    val author = event.string("user_name")
    val reward = event["reward"]?.jsonObject
    val title = reward?.string("title").orEmpty()
    val cost = reward?.get("cost")?.jsonPrimitive?.intOrNull ?: 0
    val input = event.string("user_input")
    return ChannelPointRedemption(
        platform = StreamPlatform.TWITCH,
        timestampMs = timestampMs,
        author = author,
        rewardTitle = title,
        input = input,
        cost = cost,
    )
}

/** Twitch tiers are "1000", "2000", "3000" corresponding to tier 1, 2, 3. */
private fun parseTier(tierString: String): Int =
    when (tierString) {
        TIER_2_STRING -> TIER_2
        TIER_3_STRING -> TIER_3
        else -> TIER_1
    }

/** Approximate USD value per Twitch subscription tier. */
private fun tierToAmount(tier: Int): Double =
    when (tier) {
        TIER_2 -> TIER_2_PRICE
        TIER_3 -> TIER_3_PRICE
        else -> TIER_1_PRICE
    }

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean =
    this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: default

private const val BITS_PER_CENT = 100.0
private const val TIER_1 = 1
private const val TIER_2 = 2
private const val TIER_3 = 3
private const val TIER_2_STRING = "2000"
private const val TIER_3_STRING = "3000"
private const val TIER_1_PRICE = 4.99
private const val TIER_2_PRICE = 9.99
private const val TIER_3_PRICE = 24.99
