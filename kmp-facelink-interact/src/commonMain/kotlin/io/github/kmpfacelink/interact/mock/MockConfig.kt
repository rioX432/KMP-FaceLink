package io.github.kmpfacelink.interact.mock

/**
 * Configuration for [MockStreamEventSource] controlling event generation behavior.
 *
 * Probabilities should sum to approximately 1.0. When generating an event, one type
 * is selected randomly based on these weights.
 *
 * @property intervalMs Delay between generated events in milliseconds.
 * @property chatProbability Weight for [ChatMessage][io.github.kmpfacelink.interact.model.ChatMessage] events.
 * @property giftProbability Weight for [Gift][io.github.kmpfacelink.interact.model.Gift] events.
 * @property followProbability Weight for [Follow][io.github.kmpfacelink.interact.model.Follow] events.
 * @property raidProbability Weight for [Raid][io.github.kmpfacelink.interact.model.Raid] events.
 * @property superChatProbability Weight for [SuperChat][io.github.kmpfacelink.interact.model.SuperChat] events.
 * @property channelPointProbability Weight for [ChannelPointRedemption][io.github.kmpfacelink.interact.model.ChannelPointRedemption] events.
 */
public data class MockConfig(
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    val chatProbability: Float = DEFAULT_CHAT_PROBABILITY,
    val giftProbability: Float = DEFAULT_GIFT_PROBABILITY,
    val followProbability: Float = DEFAULT_FOLLOW_PROBABILITY,
    val raidProbability: Float = DEFAULT_RAID_PROBABILITY,
    val superChatProbability: Float = DEFAULT_SUPER_CHAT_PROBABILITY,
    val channelPointProbability: Float = DEFAULT_CHANNEL_POINT_PROBABILITY,
)

private const val DEFAULT_INTERVAL_MS = 3000L
private const val DEFAULT_CHAT_PROBABILITY = 0.5f
private const val DEFAULT_GIFT_PROBABILITY = 0.15f
private const val DEFAULT_FOLLOW_PROBABILITY = 0.15f
private const val DEFAULT_RAID_PROBABILITY = 0.05f
private const val DEFAULT_SUPER_CHAT_PROBABILITY = 0.1f
private const val DEFAULT_CHANNEL_POINT_PROBABILITY = 0.05f
