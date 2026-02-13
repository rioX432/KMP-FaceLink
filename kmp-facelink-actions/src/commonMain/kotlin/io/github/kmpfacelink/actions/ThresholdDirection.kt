package io.github.kmpfacelink.actions

/**
 * Direction in which a blend shape value must cross a threshold to activate.
 */
public enum class ThresholdDirection {
    /** Activates when value >= threshold. */
    ABOVE,

    /** Activates when value <= threshold. */
    BELOW,
}
