package io.github.kmpfacelink.effects

/**
 * Determines how an anchor effect's rotation is computed.
 */
public enum class RotationSource {
    /** Use the head transform roll angle. */
    HEAD_TRANSFORM,

    /** No rotation (always 0 degrees). */
    NONE,
}
