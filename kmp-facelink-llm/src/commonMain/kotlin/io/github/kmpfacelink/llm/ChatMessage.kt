package io.github.kmpfacelink.llm

import kotlinx.serialization.Serializable

/**
 * A single message in a conversation.
 *
 * @property role The role of the message sender
 * @property content The text content of the message
 */
@Serializable
public data class ChatMessage(
    val role: Role,
    val content: String,
) {
    /** The role of a message sender. */
    public enum class Role {
        /** System instruction message. */
        System,

        /** User input message. */
        User,

        /** Assistant response message. */
        Assistant,
    }
}
