package io.github.kmpfacelink.interact.mock

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlin.random.Random

/**
 * Generates realistic mock [StreamEvent]s for demo and testing purposes.
 *
 * All data is deterministic when the same [Random] seed is used, enabling
 * reproducible tests.
 */
internal class FakeDataGenerator(
    private val random: Random = Random.Default,
    private val platform: StreamPlatform = StreamPlatform.TWITCH,
    private val timeProvider: () -> Long = { 0L },
) {

    /** Generate a random [StreamEvent] based on the probability thresholds. */
    internal fun generate(config: MockConfig): StreamEvent {
        val roll = random.nextFloat()
        return selectEventType(roll, config)
    }

    @Suppress("MagicNumber")
    private fun selectEventType(roll: Float, config: MockConfig): StreamEvent {
        val index = resolveEventIndex(roll, config)
        return when (index) {
            0 -> generateChatMessage()
            1 -> generateGift()
            2 -> generateFollow()
            3 -> generateRaid()
            4 -> generateSuperChat()
            else -> generateChannelPointRedemption()
        }
    }

    private fun resolveEventIndex(roll: Float, config: MockConfig): Int {
        val thresholds = buildThresholds(config)
        val found = thresholds.indexOfFirst { roll < it }
        return if (found < 0) thresholds.lastIndex else found
    }

    private fun buildThresholds(config: MockConfig): FloatArray {
        val probs = floatArrayOf(
            config.chatProbability,
            config.giftProbability,
            config.followProbability,
            config.raidProbability,
            config.superChatProbability,
            config.channelPointProbability,
        )
        for (i in 1 until probs.size) {
            probs[i] += probs[i - 1]
        }
        return probs
    }

    private fun generateChatMessage(): ChatMessage = ChatMessage(
        platform = platform,
        timestampMs = timeProvider(),
        author = randomUsername(),
        text = CHAT_MESSAGES[random.nextInt(CHAT_MESSAGES.size)],
    )

    private fun generateGift(): Gift = Gift(
        platform = platform,
        timestampMs = timeProvider(),
        author = randomUsername(),
        amount = (random.nextInt(GIFT_AMOUNT_RANGE) + 1).toDouble(),
        currency = "USD",
        tier = random.nextInt(GIFT_TIER_COUNT) + 1,
    )

    private fun generateFollow(): Follow = Follow(
        platform = platform,
        timestampMs = timeProvider(),
        author = randomUsername(),
    )

    private fun generateRaid(): Raid = Raid(
        platform = platform,
        timestampMs = timeProvider(),
        author = randomUsername(),
        viewerCount = random.nextInt(RAID_VIEWER_RANGE) + RAID_VIEWER_MIN,
    )

    private fun generateSuperChat(): SuperChat = SuperChat(
        platform = platform,
        timestampMs = timeProvider(),
        author = randomUsername(),
        amount = (random.nextInt(SUPER_CHAT_AMOUNT_RANGE) + 1).toDouble(),
        currency = "USD",
        text = SUPER_CHAT_MESSAGES[random.nextInt(SUPER_CHAT_MESSAGES.size)],
    )

    private fun generateChannelPointRedemption(): ChannelPointRedemption =
        ChannelPointRedemption(
            platform = platform,
            timestampMs = timeProvider(),
            author = randomUsername(),
            rewardTitle = REWARD_TITLES[random.nextInt(REWARD_TITLES.size)],
            cost = REWARD_COSTS[random.nextInt(REWARD_COSTS.size)],
        )

    private fun randomUsername(): String {
        val prefix = USERNAME_PREFIXES[random.nextInt(USERNAME_PREFIXES.size)]
        val suffix = random.nextInt(USERNAME_SUFFIX_RANGE)
        return "$prefix$suffix"
    }
}

private val USERNAME_PREFIXES = listOf(
    "TwitchFan",
    "VTuberLover",
    "NightOwl_",
    "StreamWatcher",
    "ChatHero",
    "PixelKnight",
    "CozyViewer",
    "HypeTrainRider",
    "EmoteMaster",
    "LurkerPro",
)

private val CHAT_MESSAGES = listOf(
    "Amazing stream!",
    "kekw",
    "LUL",
    "pog",
    "let's gooo!",
    "hi everyone!",
    "GG",
    "nice play!",
    "first time here",
    "love the vibes",
    "can you play that again?",
    "haha",
    "KEKW KEKW",
    "that was insane!",
    "hello from Japan!",
)

private val SUPER_CHAT_MESSAGES = listOf(
    "Keep up the great work!",
    "Love your content!",
    "You're the best streamer!",
    "This stream is fire!",
    "Thanks for the entertainment!",
)

private val REWARD_TITLES = listOf(
    "Hydration Check",
    "Do a Pose",
    "Song Request",
    "VIP for a Day",
    "Choose My Avatar",
)

private val REWARD_COSTS = listOf(
    100,
    500,
    1000,
    2000,
    5000,
)

private const val GIFT_AMOUNT_RANGE = 100
private const val GIFT_TIER_COUNT = 3
private const val RAID_VIEWER_MIN = 10
private const val RAID_VIEWER_RANGE = 4991
private const val SUPER_CHAT_AMOUNT_RANGE = 50
private const val USERNAME_SUFFIX_RANGE = 1000
