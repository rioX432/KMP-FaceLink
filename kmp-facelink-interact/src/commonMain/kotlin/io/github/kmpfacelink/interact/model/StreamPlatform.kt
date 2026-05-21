package io.github.kmpfacelink.interact.model

import kotlinx.serialization.Serializable

/**
 * Supported streaming platforms for event sources.
 */
@Serializable
public enum class StreamPlatform {
    TWITCH,
    YOUTUBE,
    BILIBILI,
}
