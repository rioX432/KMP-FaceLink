package io.github.kmpfacelink.actions.effect

import kotlinx.serialization.Serializable

/**
 * Determines when an effect is emitted relative to the action lifecycle.
 */
@Serializable
public enum class EffectLifecycleMode {
    /** Emit the effect once when the action starts. */
    ON_START,

    /** Emit/update the effect continuously while the action is held. */
    WHILE_HELD,

    /** Emit the effect once when the action is released. */
    ON_RELEASE,
}
