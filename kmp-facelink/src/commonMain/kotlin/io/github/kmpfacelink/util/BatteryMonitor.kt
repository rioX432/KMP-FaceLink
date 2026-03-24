package io.github.kmpfacelink.util

import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors device battery level and charging state.
 *
 * Platform implementations:
 * - Android: [BatteryManager] via sticky broadcast
 * - iOS: [UIDevice.batteryLevel] with notification observation
 * - JVM: No-op (always reports 1.0 / not charging)
 */
internal interface BatteryMonitor {
    /**
     * Current battery level as a fraction (0.0–1.0).
     * Returns 1.0 if battery level is unavailable.
     */
    val batteryLevel: StateFlow<Float>

    /** Whether the device is currently charging or fully charged. */
    val isCharging: StateFlow<Boolean>

    /** Release resources (unregister listeners). */
    fun release()
}
