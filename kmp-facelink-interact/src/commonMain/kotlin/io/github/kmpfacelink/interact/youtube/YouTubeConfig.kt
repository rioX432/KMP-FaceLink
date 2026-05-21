package io.github.kmpfacelink.interact.youtube

/**
 * Configuration for connecting to the YouTube Live Chat API.
 *
 * @property apiKey YouTube Data API v3 key for unauthenticated polling.
 * @property liveChatId Live chat ID to poll. If null, auto-detect from the active broadcast.
 * @property accessToken OAuth 2.0 token. Required when [liveChatId] is null (auto-detect).
 * @property maxPollsPerMinute Upper bound on polling frequency to manage API quota usage.
 *   YouTube Data API quota is 10,000 units/day; each liveChatMessages.list costs ~5 units.
 */
public data class YouTubeConfig(
    val apiKey: String,
    val liveChatId: String? = null,
    val accessToken: String? = null,
    val maxPollsPerMinute: Int = DEFAULT_MAX_POLLS_PER_MINUTE,
)

internal const val DEFAULT_MAX_POLLS_PER_MINUTE = 6
