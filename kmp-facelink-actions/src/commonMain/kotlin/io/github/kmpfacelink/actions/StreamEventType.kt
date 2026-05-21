package io.github.kmpfacelink.actions

import kotlinx.serialization.Serializable

/**
 * Types of stream events that can be matched by [ActionTrigger.StreamEventTrigger].
 */
@Serializable
public enum class StreamEventType {
    CHAT_MESSAGE,
    GIFT,
    FOLLOW,
    RAID,
    CHANNEL_POINT_REDEMPTION,
    SUPER_CHAT,
}
