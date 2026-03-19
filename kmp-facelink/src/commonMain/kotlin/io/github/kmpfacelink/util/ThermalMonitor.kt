package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.flow.StateFlow

/**
 * Monitors device thermal state and exposes it as a [StateFlow].
 */
internal interface ThermalMonitor {
    /** Current thermal state of the device. */
    val thermalState: StateFlow<ThermalState>

    /** Release resources (unregister listeners). */
    fun release()
}
