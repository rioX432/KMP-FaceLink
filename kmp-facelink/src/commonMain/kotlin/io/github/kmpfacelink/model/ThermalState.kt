package io.github.kmpfacelink.model

/**
 * Device thermal state levels.
 *
 * Maps directly to iOS `ProcessInfo.ThermalState` and approximates
 * Android `PowerManager.THERMAL_STATUS_*` into four levels.
 */
public enum class ThermalState {
    /** Normal operating temperature. No performance impact. */
    NOMINAL,

    /** Slightly elevated temperature. System may begin throttling. */
    FAIR,

    /** High temperature. Significant throttling recommended. */
    SERIOUS,

    /** Critical temperature. Maximum throttling to prevent shutdown. */
    CRITICAL,
}
