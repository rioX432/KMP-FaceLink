package io.github.kmpfacelink.effects

/**
 * Categories of face effects.
 */
public enum class EffectType {
    /** Positioned at a face landmark. */
    ANCHOR,

    /** Driven by blend shape expressions. */
    EXPRESSION,

    /** Driven by hand gestures. */
    HAND,

    /** Custom blend shape to parameter transform. */
    TRANSFORM,

    /** Custom ambient effect from any tracking data. */
    AMBIENT,
}
