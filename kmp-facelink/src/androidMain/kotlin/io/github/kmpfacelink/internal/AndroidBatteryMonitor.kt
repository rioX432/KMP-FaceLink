package io.github.kmpfacelink.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.kmpfacelink.util.BatteryMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android battery monitor using [BatteryManager] sticky broadcast.
 */
internal class AndroidBatteryMonitor(private val context: Context) : BatteryMonitor {

    private val _batteryLevel = MutableStateFlow(1.0f)
    override val batteryLevel: StateFlow<Float> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    override val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateFromIntent(it) }
        }
    }

    init {
        // Read initial state from sticky broadcast
        val stickyIntent = context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        stickyIntent?.let { updateFromIntent(it) }
    }

    private fun updateFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            _batteryLevel.value = level.toFloat() / scale.toFloat()
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    override fun release() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Receiver may already be unregistered — safe to ignore
        }
    }
}
