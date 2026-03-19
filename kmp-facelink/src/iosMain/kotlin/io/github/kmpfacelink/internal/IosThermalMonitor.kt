@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.util.ThermalMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalStateDidChangeNotification
import platform.Foundation.valueForKey
import platform.darwin.NSObjectProtocol

internal class IosThermalMonitor : ThermalMonitor {
    private val _thermalState = MutableStateFlow(readThermalState())
    override val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private var observer: NSObjectProtocol? = null

    init {
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSProcessInfoThermalStateDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            _thermalState.value = readThermalState()
        }
    }

    override fun release() {
        observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observer = null
    }

    companion object {
        // NSProcessInfoThermalState raw values (stable NS_ENUM, will not change)
        private const val THERMAL_STATE_NOMINAL = 0L
        private const val THERMAL_STATE_FAIR = 1L
        private const val THERMAL_STATE_SERIOUS = 2L
        private const val THERMAL_STATE_CRITICAL = 3L

        /**
         * Read thermal state via KVO because K/N cinterop does not expose
         * `NSProcessInfo.thermalState` as a direct property.
         */
        private fun readThermalState(): ThermalState {
            val rawValue = (NSProcessInfo.processInfo.valueForKey("thermalState") as? NSNumber)
                ?.longValue ?: THERMAL_STATE_NOMINAL
            return when (rawValue) {
                THERMAL_STATE_NOMINAL -> ThermalState.NOMINAL
                THERMAL_STATE_FAIR -> ThermalState.FAIR
                THERMAL_STATE_SERIOUS -> ThermalState.SERIOUS
                THERMAL_STATE_CRITICAL -> ThermalState.CRITICAL
                else -> ThermalState.NOMINAL
            }
        }
    }
}
