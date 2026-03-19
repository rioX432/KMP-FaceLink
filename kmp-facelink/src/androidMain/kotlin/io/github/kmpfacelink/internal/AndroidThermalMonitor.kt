package io.github.kmpfacelink.internal

import android.content.Context
import android.os.Build
import android.os.PowerManager
import io.github.kmpfacelink.model.ThermalState
import io.github.kmpfacelink.util.ThermalMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidThermalMonitor(context: Context) : ThermalMonitor {
    private val _thermalState = MutableStateFlow(ThermalState.NOMINAL)
    override val thermalState: StateFlow<ThermalState> = _thermalState.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
                _thermalState.value = mapThermalStatus(status)
            }
            listener = thermalListener
            powerManager?.addThermalStatusListener(thermalListener)
            // Read initial state
            powerManager?.currentThermalStatus?.let { status ->
                _thermalState.value = mapThermalStatus(status)
            }
        }
    }

    override fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listener?.let { powerManager?.removeThermalStatusListener(it) }
            listener = null
        }
    }

    companion object {
        // Note: On API < 29, thermal monitoring is not available. State stays NOMINAL.
        private fun mapThermalStatus(status: Int): ThermalState = when (status) {
            PowerManager.THERMAL_STATUS_NONE,
            PowerManager.THERMAL_STATUS_LIGHT,
            -> ThermalState.NOMINAL
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.FAIR
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SERIOUS
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN,
            -> ThermalState.CRITICAL
            else -> ThermalState.NOMINAL
        }
    }
}
