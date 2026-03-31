package io.github.kmpfacelink.internal

import io.github.kmpfacelink.util.BatteryMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * No-op battery monitor for Wasm/JS where battery monitoring is not applicable.
 */
internal class NoOpBatteryMonitor : BatteryMonitor {
    override val batteryLevel: StateFlow<Float> = MutableStateFlow(1.0f)
    override val isCharging: StateFlow<Boolean> = MutableStateFlow(false)
    override fun release() { /* no-op */ }
}
