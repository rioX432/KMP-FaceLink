package io.github.kmpfacelink.actions

import kotlinx.serialization.Serializable

/**
 * Filter criteria for [ActionTrigger.StreamEventTrigger].
 *
 * When a stream event's type matches the trigger, the filter provides
 * additional matching logic (e.g., minimum gift amount, keyword matching).
 */
@Serializable
public sealed class StreamEventFilter {

    /** Matches any event of the specified type without additional filtering. */
    @Serializable
    public data object Any : StreamEventFilter()

    /**
     * Matches monetary events (gifts, super chats) with amount >= [amount].
     *
     * @property amount Minimum monetary value (inclusive).
     */
    @Serializable
    public data class MinAmount(val amount: Double) : StreamEventFilter()

    /**
     * Matches channel point redemptions with the specified reward title.
     *
     * @property title Exact reward title to match (case-insensitive).
     */
    @Serializable
    public data class RewardTitle(val title: String) : StreamEventFilter()

    /**
     * Matches chat messages or super chats containing any of the specified keywords.
     *
     * @property keywords List of keywords to match (case-insensitive, any match suffices).
     */
    @Serializable
    public data class KeywordMatch(val keywords: List<String>) : StreamEventFilter()
}
