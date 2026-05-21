package io.github.kmpfacelink.interact.model

import kotlinx.serialization.Serializable

/**
 * Anchor points on an avatar where effects can be attached.
 *
 * These are renderer-agnostic positions; the consumer app maps
 * them to actual coordinates in its rendering system.
 */
@Serializable
public enum class AvatarAnchor {
    /** Top of the avatar's head. */
    HEAD,

    /** Left hand position. */
    LEFT_HAND,

    /** Right hand position. */
    RIGHT_HAND,

    /** Center of the avatar's body/chest. */
    BODY,

    /** The entire screen area. */
    SCREEN,
}
