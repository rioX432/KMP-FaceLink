package io.github.kmpfacelink.interact.youtube

import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Maps YouTube Live Chat API message items to [StreamEvent] instances.
 */
internal object YouTubeEventMapper {

    /**
     * Maps a YouTube liveChatMessage resource to a [StreamEvent].
     *
     * @param item The liveChatMessage JSON object (must include snippet and authorDetails).
     * @param timestampMs Event timestamp in epoch milliseconds.
     * @return The mapped [StreamEvent], or null if the message type is unsupported.
     */
    fun map(item: JsonObject, timestampMs: Long): StreamEvent? {
        val snippet = item["snippet"]?.jsonObject ?: return null
        val type = snippet.string("type")
        val author = extractAuthorName(item)
        return when (type) {
            "textMessageEvent" -> mapTextMessage(snippet, author, timestampMs)
            "superChatEvent" -> mapSuperChat(snippet, author, timestampMs)
            "superStickerEvent" -> mapSuperSticker(snippet, author, timestampMs)
            "membershipGiftingEvent" -> mapMembershipGifting(snippet, author, timestampMs)
            "newSponsorEvent" -> mapNewSponsor(snippet, author, timestampMs)
            else -> null
        }
    }
}

private fun mapTextMessage(
    snippet: JsonObject,
    author: String,
    timestampMs: Long,
): ChatMessage {
    val details = snippet["textMessageDetails"]?.jsonObject
    val text = details?.string("messageText")
        ?: snippet.string("displayMessage")
    return ChatMessage(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = timestampMs,
        author = author,
        text = text,
    )
}

private fun mapSuperChat(
    snippet: JsonObject,
    author: String,
    timestampMs: Long,
): SuperChat {
    val details = snippet["superChatDetails"]?.jsonObject
    val amountMicros = details?.get("amountMicros")?.jsonPrimitive?.longOrNull ?: 0L
    val currency = details?.string("currency").orEmpty()
    val comment = details?.string("userComment").orEmpty()
    return SuperChat(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = timestampMs,
        author = author,
        amount = amountMicros / MICROS_PER_UNIT,
        currency = currency,
        text = comment,
    )
}

private fun mapSuperSticker(
    snippet: JsonObject,
    author: String,
    timestampMs: Long,
): Gift {
    val details = snippet["superStickerDetails"]?.jsonObject
    val amountMicros = details?.get("amountMicros")?.jsonPrimitive?.longOrNull ?: 0L
    val currency = details?.string("currency").orEmpty()
    val tier = details?.get("tier")?.jsonPrimitive?.intOrNull ?: 1
    return Gift(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = timestampMs,
        author = author,
        amount = amountMicros / MICROS_PER_UNIT,
        currency = currency,
        tier = tier,
    )
}

private fun mapMembershipGifting(
    snippet: JsonObject,
    author: String,
    timestampMs: Long,
): Gift {
    val details = snippet["membershipGiftingDetails"]?.jsonObject
    val count = details?.get("giftMembershipsCount")?.jsonPrimitive?.intOrNull ?: 1
    return Gift(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = timestampMs,
        author = author,
        amount = count.toDouble(),
        currency = "MEMBERSHIP",
        tier = 1,
        message = details?.string("giftMembershipsLevelName").orEmpty(),
    )
}

@Suppress("UnusedParameter")
private fun mapNewSponsor(
    snippet: JsonObject,
    author: String,
    timestampMs: Long,
): Follow =
    // New member joins are modeled as Follow; upgrades are also Follow
    // since the SDK consumer can distinguish via other means if needed.
    Follow(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = timestampMs,
        author = author,
    )

private fun extractAuthorName(item: JsonObject): String {
    val authorDetails = item["authorDetails"]?.jsonObject
    return authorDetails?.string("displayName").orEmpty()
}

private fun JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.content.orEmpty()

private const val MICROS_PER_UNIT = 1_000_000.0
