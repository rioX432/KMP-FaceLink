package io.github.kmpfacelink.interact.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base type for all stream interaction events.
 *
 * Each subtype represents a distinct audience interaction
 * (chat message, gift, follow, raid, etc.) from a [StreamPlatform].
 */
@Serializable
public sealed class StreamEvent {
    /** The platform this event originated from. */
    public abstract val platform: StreamPlatform

    /** Event timestamp in epoch milliseconds. */
    public abstract val timestampMs: Long

    /** Display name of the user who triggered the event. */
    public abstract val author: String
}

/**
 * A chat message from a viewer.
 *
 * @property text The message content.
 * @property badges Badge identifiers (e.g. "moderator", "subscriber").
 * @property emotes Emotes used in the message.
 */
@Serializable
@SerialName("chat_message")
public data class ChatMessage(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
    val text: String,
    val badges: List<String> = emptyList(),
    val emotes: List<Emote> = emptyList(),
) : StreamEvent()

/**
 * A monetary gift or donation.
 *
 * @property amount Monetary value normalized to USD.
 * @property currency ISO 4217 currency code.
 * @property tier Platform-specific tier (e.g. Twitch sub tier, YouTube membership level).
 * @property message Optional message attached to the gift.
 */
@Serializable
@SerialName("gift")
public data class Gift(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
    val amount: Double,
    val currency: String,
    val tier: Int = 1,
    val message: String = "",
) : StreamEvent()

/**
 * A new follower event.
 */
@Serializable
@SerialName("follow")
public data class Follow(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
) : StreamEvent()

/**
 * A raid from another channel.
 *
 * @property viewerCount Number of viewers in the raid.
 */
@Serializable
@SerialName("raid")
public data class Raid(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
    val viewerCount: Int,
) : StreamEvent()

/**
 * A channel point redemption (Twitch-specific, but modeled generically).
 *
 * @property rewardTitle Title of the redeemed reward.
 * @property input Optional user input for the redemption.
 * @property cost Point cost of the redemption.
 */
@Serializable
@SerialName("channel_point_redemption")
public data class ChannelPointRedemption(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
    val rewardTitle: String,
    val input: String = "",
    val cost: Int,
) : StreamEvent()

/**
 * A Super Chat / Super Sticker (YouTube) or equivalent.
 *
 * @property amount Monetary value.
 * @property currency ISO 4217 currency code.
 * @property text Message text.
 */
@Serializable
@SerialName("super_chat")
public data class SuperChat(
    override val platform: StreamPlatform,
    override val timestampMs: Long,
    override val author: String,
    val amount: Double,
    val currency: String,
    val text: String,
) : StreamEvent()

/**
 * An emote reference within a [ChatMessage].
 *
 * @property id Platform-specific emote identifier.
 * @property name Display name of the emote.
 */
@Serializable
public data class Emote(
    val id: String,
    val name: String,
)
