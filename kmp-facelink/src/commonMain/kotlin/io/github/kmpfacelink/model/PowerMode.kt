package io.github.kmpfacelink.model

/**
 * Power management profiles controlling tracking frame rate and thermal behavior.
 *
 * Each mode defines a base target frame rate and thermal management strategy.
 * The actual frame rate may be further reduced by thermal state, idle detection,
 * or battery level monitoring.
 *
 * @property baseFps Base target frame rate for the mode (before thermal adjustments).
 */
public enum class PowerMode(public val baseFps: Int) {
    /** Maximum performance: 60fps, no automatic downgrade. */
    HIGH(baseFps = 60),

    /** Balanced performance: 30fps, thermal-aware throttling, idle detection. */
    BALANCED(baseFps = 30),

    /** Battery saver: 15fps, aggressive throttling. */
    LOW_POWER(baseFps = 15),
}
