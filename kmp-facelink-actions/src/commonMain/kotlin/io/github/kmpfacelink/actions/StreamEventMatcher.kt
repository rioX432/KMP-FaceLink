package io.github.kmpfacelink.actions

import io.github.kmpfacelink.interact.model.ChannelPointRedemption
import io.github.kmpfacelink.interact.model.ChatMessage
import io.github.kmpfacelink.interact.model.Follow
import io.github.kmpfacelink.interact.model.Gift
import io.github.kmpfacelink.interact.model.Raid
import io.github.kmpfacelink.interact.model.StreamEvent
import io.github.kmpfacelink.interact.model.SuperChat

/**
 * Stateless matcher that checks whether a [StreamEvent] satisfies
 * a [ActionTrigger.StreamEventTrigger]'s type and filter criteria.
 */
internal object StreamEventMatcher {

    fun matches(
        trigger: ActionTrigger.StreamEventTrigger,
        event: StreamEvent,
    ): Boolean {
        if (typeOf(event) != trigger.eventType) return false
        return matchesFilter(trigger.filter, event)
    }

    private fun typeOf(event: StreamEvent): StreamEventType = when (event) {
        is ChatMessage -> StreamEventType.CHAT_MESSAGE
        is Gift -> StreamEventType.GIFT
        is Follow -> StreamEventType.FOLLOW
        is Raid -> StreamEventType.RAID
        is ChannelPointRedemption -> StreamEventType.CHANNEL_POINT_REDEMPTION
        is SuperChat -> StreamEventType.SUPER_CHAT
    }

    private fun matchesFilter(
        filter: StreamEventFilter,
        event: StreamEvent,
    ): Boolean = when (filter) {
        is StreamEventFilter.Any -> true
        is StreamEventFilter.MinAmount -> matchesMinAmount(filter, event)
        is StreamEventFilter.RewardTitle -> matchesRewardTitle(filter, event)
        is StreamEventFilter.KeywordMatch -> matchesKeyword(filter, event)
    }

    private fun matchesMinAmount(
        filter: StreamEventFilter.MinAmount,
        event: StreamEvent,
    ): Boolean = when (event) {
        is Gift -> event.amount >= filter.amount
        is SuperChat -> event.amount >= filter.amount
        else -> false
    }

    private fun matchesRewardTitle(
        filter: StreamEventFilter.RewardTitle,
        event: StreamEvent,
    ): Boolean = when (event) {
        is ChannelPointRedemption ->
            event.rewardTitle.equals(filter.title, ignoreCase = true)
        else -> false
    }

    private fun matchesKeyword(
        filter: StreamEventFilter.KeywordMatch,
        event: StreamEvent,
    ): Boolean {
        val text = when (event) {
            is ChatMessage -> event.text
            is SuperChat -> event.text
            else -> return false
        }
        val lowerText = text.lowercase()
        return filter.keywords.any { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }
}
