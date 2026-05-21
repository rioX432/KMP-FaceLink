package io.github.kmpfacelink.interact.twitch

/**
 * Configuration for connecting to the Twitch EventSub WebSocket API.
 *
 * @property accessToken User access token for Twitch API authentication.
 * @property clientId Twitch application client ID.
 * @property broadcasterId Twitch user ID of the broadcaster to monitor.
 * @property subscriptions Set of event types to subscribe to.
 */
public data class TwitchConfig(
    val accessToken: String,
    val clientId: String,
    val broadcasterId: String,
    val subscriptions: Set<TwitchSubscriptionType> = TwitchSubscriptionType.DEFAULT,
)
