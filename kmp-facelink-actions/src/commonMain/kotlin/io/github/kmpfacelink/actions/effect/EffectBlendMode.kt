package io.github.kmpfacelink.actions.effect

import kotlinx.serialization.Serializable

/**
 * Determines how concurrent effects from the same binding are combined.
 */
@Serializable
public enum class EffectBlendMode {
    /** Multiple effects stack additively. */
    ADDITIVE,

    /** Only one effect instance may be active; new triggers are ignored. */
    EXCLUSIVE,

    /** Higher-priority effects suppress lower-priority ones. */
    PRIORITY,
}
