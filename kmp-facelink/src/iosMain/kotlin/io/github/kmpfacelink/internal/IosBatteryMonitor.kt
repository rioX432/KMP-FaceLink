@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.util.BatteryMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.darwin.NSObjectProtocol

/**
 * iOS battery monitor using [UIDevice] battery monitoring.
 */
internal class IosBatteryMonitor : BatteryMonitor {

    private val _batteryLevel = MutableStateFlow(1.0f)
    override val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    override val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private var levelObserver: NSObjectProtocol? = null
    private var stateObserver: NSObjectProtocol? = null

    init {
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true

        readBatteryState(device)

        levelObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceBatteryLevelDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            readBatteryLevel(device)
        }

        stateObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceBatteryStateDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ ->
            readChargingState(device)
        }
    }

    private fun readBatteryState(device: UIDevice) {
        readBatteryLevel(device)
        readChargingState(device)
    }

    private fun readBatteryLevel(device: UIDevice) {
        val level = device.batteryLevel
        // UIDevice returns -1.0 when monitoring is disabled or unavailable
        _batteryLevel.value = if (level < 0f) 1.0f else level
    }

    private fun readChargingState(device: UIDevice) {
        val state = device.batteryState
        _isCharging.value = state == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
            state == UIDeviceBatteryState.UIDeviceBatteryStateFull
    }

    override fun release() {
        levelObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        stateObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        levelObserver = null
        stateObserver = null
    }
}
