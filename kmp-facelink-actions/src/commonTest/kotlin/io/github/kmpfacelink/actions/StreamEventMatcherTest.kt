package io.github.kmpfacelink.actions

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamPlatform
import io.github.kmpfacelink.interact.model.SuperChat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamEventMatcherTest {

    // --- Type matching ---

    @Test
    fun matchesChatMessageType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.CHAT_MESSAGE)
        assertTrue(StreamEventMatcher.matches(trigger, chatMessage()))
    }

    @Test
    fun doesNotMatchWrongType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT)
        assertFalse(StreamEventMatcher.matches(trigger, chatMessage()))
    }

    @Test
    fun matchesGiftType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.GIFT)
        assertTrue(StreamEventMatcher.matches(trigger, gift()))
    }

    @Test
    fun matchesFollowType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.FOLLOW)
        assertTrue(StreamEventMatcher.matches(trigger, follow()))
    }

    @Test
    fun matchesRaidType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.RAID)
        assertTrue(StreamEventMatcher.matches(trigger, raid()))
    }

    @Test
    fun matchesChannelPointRedemptionType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.CHANNEL_POINT_REDEMPTION)
        assertTrue(StreamEventMatcher.matches(trigger, channelPointRedemption()))
    }

    @Test
    fun matchesSuperChatType() {
        val trigger = ActionTrigger.StreamEventTrigger(StreamEventType.SUPER_CHAT)
        assertTrue(StreamEventMatcher.matches(trigger, superChat()))
    }

    // --- MinAmount filter ---

    @Test
    fun minAmountPassesWhenGiftMeetsThreshold() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.GIFT,
            filter = StreamEventFilter.MinAmount(MIN_AMOUNT_5),
        )
        assertTrue(StreamEventMatcher.matches(trigger, gift(amount = AMOUNT_10)))
    }

    @Test
    fun minAmountPassesAtExactThreshold() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.GIFT,
            filter = StreamEventFilter.MinAmount(MIN_AMOUNT_5),
        )
        assertTrue(StreamEventMatcher.matches(trigger, gift(amount = MIN_AMOUNT_5)))
    }

    @Test
    fun minAmountFailsBelowThreshold() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.GIFT,
            filter = StreamEventFilter.MinAmount(MIN_AMOUNT_5),
        )
        assertFalse(StreamEventMatcher.matches(trigger, gift(amount = AMOUNT_2)))
    }

    @Test
    fun minAmountWorksWithSuperChat() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.SUPER_CHAT,
            filter = StreamEventFilter.MinAmount(MIN_AMOUNT_5),
        )
        assertTrue(StreamEventMatcher.matches(trigger, superChat(amount = AMOUNT_10)))
    }

    @Test
    fun minAmountFailsForNonMonetaryEvent() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.FOLLOW,
            filter = StreamEventFilter.MinAmount(MIN_AMOUNT_5),
        )
        assertFalse(StreamEventMatcher.matches(trigger, follow()))
    }

    // --- RewardTitle filter ---

    @Test
    fun rewardTitleMatchesExact() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHANNEL_POINT_REDEMPTION,
            filter = StreamEventFilter.RewardTitle("Hydrate!"),
        )
        assertTrue(StreamEventMatcher.matches(trigger, channelPointRedemption(rewardTitle = "Hydrate!")))
    }

    @Test
    fun rewardTitleMatchesCaseInsensitive() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHANNEL_POINT_REDEMPTION,
            filter = StreamEventFilter.RewardTitle("hydrate!"),
        )
        assertTrue(StreamEventMatcher.matches(trigger, channelPointRedemption(rewardTitle = "Hydrate!")))
    }

    @Test
    fun rewardTitleDoesNotMatchDifferentTitle() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHANNEL_POINT_REDEMPTION,
            filter = StreamEventFilter.RewardTitle("Hydrate!"),
        )
        assertFalse(StreamEventMatcher.matches(trigger, channelPointRedemption(rewardTitle = "Other")))
    }

    @Test
    fun rewardTitleFailsForNonRedemptionEvent() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHAT_MESSAGE,
            filter = StreamEventFilter.RewardTitle("Hydrate!"),
        )
        assertFalse(StreamEventMatcher.matches(trigger, chatMessage()))
    }

    // --- KeywordMatch filter ---

    @Test
    fun keywordMatchesChatMessage() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHAT_MESSAGE,
            filter = StreamEventFilter.KeywordMatch(listOf("hello")),
        )
        assertTrue(StreamEventMatcher.matches(trigger, chatMessage(text = "Hello world!")))
    }

    @Test
    fun keywordMatchesAnyKeyword() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHAT_MESSAGE,
            filter = StreamEventFilter.KeywordMatch(listOf("foo", "bar")),
        )
        assertTrue(StreamEventMatcher.matches(trigger, chatMessage(text = "I said bar")))
    }

    @Test
    fun keywordDoesNotMatchWhenNonePresent() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.CHAT_MESSAGE,
            filter = StreamEventFilter.KeywordMatch(listOf("alpha", "beta")),
        )
        assertFalse(StreamEventMatcher.matches(trigger, chatMessage(text = "no match here")))
    }

    @Test
    fun keywordMatchesSuperChatText() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.SUPER_CHAT,
            filter = StreamEventFilter.KeywordMatch(listOf("love")),
        )
        assertTrue(StreamEventMatcher.matches(trigger, superChat(text = "I love this")))
    }

    @Test
    fun keywordFailsForNonTextEvent() {
        val trigger = ActionTrigger.StreamEventTrigger(
            eventType = StreamEventType.GIFT,
            filter = StreamEventFilter.KeywordMatch(listOf("hello")),
        )
        assertFalse(StreamEventMatcher.matches(trigger, gift()))
    }

    // --- Helpers ---

    private fun chatMessage(text: String = "hello") = ChatMessage(
        platform = StreamPlatform.TWITCH,
        timestampMs = TIMESTAMP,
        author = "viewer",
        text = text,
    )

    private fun gift(amount: Double = DEFAULT_AMOUNT) = Gift(
        platform = StreamPlatform.TWITCH,
        timestampMs = TIMESTAMP,
        author = "donor",
        amount = amount,
        currency = "USD",
    )

    private fun follow() = Follow(
        platform = StreamPlatform.TWITCH,
        timestampMs = TIMESTAMP,
        author = "new_follower",
    )

    private fun raid() = Raid(
        platform = StreamPlatform.TWITCH,
        timestampMs = TIMESTAMP,
        author = "raider",
        viewerCount = RAID_VIEWERS,
    )

    private fun channelPointRedemption(rewardTitle: String = "Hydrate!") = ChannelPointRedemption(
        platform = StreamPlatform.TWITCH,
        timestampMs = TIMESTAMP,
        author = "redeemer",
        rewardTitle = rewardTitle,
        cost = REDEMPTION_COST,
    )

    private fun superChat(amount: Double = DEFAULT_AMOUNT, text: String = "Great!") = SuperChat(
        platform = StreamPlatform.YOUTUBE,
        timestampMs = TIMESTAMP,
        author = "super_chatter",
        amount = amount,
        currency = "USD",
        text = text,
    )

    companion object {
        private const val TIMESTAMP = 1000L
        private const val DEFAULT_AMOUNT = 4.99
        private const val AMOUNT_10 = 10.0
        private const val AMOUNT_2 = 2.0
        private const val MIN_AMOUNT_5 = 5.0
        private const val RAID_VIEWERS = 100
        private const val REDEMPTION_COST = 500
    }
}
