package io.github.kmpfacelink.interact.twitch

/**
 * Twitch EventSub subscription types supported by [TwitchEventSubSource].
 *
 * Each entry maps to a Twitch EventSub subscription type string and version.
 *
 * @property type The EventSub subscription type identifier.
 * @property version The subscription type version.
 */
public enum class TwitchSubscriptionType(
    internal val type: String,
    internal val version: String,
) {
    CHAT_MESSAGE("channel.chat.message", "1"),
    CHANNEL_POINTS("channel.channel_points_custom_reward_redemption.add", "1"),
    SUBSCRIBE("channel.subscribe", "1"),
    GIFT_SUB("channel.subscription.gift", "1"),
    CHEER("channel.cheer", "1"),
    RAID("channel.raid", "1"),
    FOLLOW("channel.follow", "2"),
    ;

    public companion object {
        /** Default set of subscription types for typical VTuber use. */
        public val DEFAULT: Set<TwitchSubscriptionType> = setOf(
            CHAT_MESSAGE,
            CHANNEL_POINTS,
            SUBSCRIBE,
            CHEER,
            RAID,
        )
    }
}
