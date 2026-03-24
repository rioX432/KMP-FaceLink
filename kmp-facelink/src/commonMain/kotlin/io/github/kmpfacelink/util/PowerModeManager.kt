package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

private const val IDLE_FPS_DIVISOR = 2
private const val DEFAULT_LOW_BATTERY_THRESHOLD = 0.15f

/**
 * Orchestrates power-aware frame rate management by combining [PowerMode],
 * thermal state, idle detection, and battery level monitoring.
 *
 * @param powerMode The user-selected power mode profile.
 * @param thermalMonitor Source of thermal state changes.
 * @param batteryMonitor Source of battery level changes.
 * @param lowBatteryThreshold Battery level (0.0–1.0) at which auto-downgrade occurs.
 * @param enableIdleDetection Whether idle face detection reduces frame rate.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PowerModeManager(
    private val powerMode: PowerMode,
    private val thermalMonitor: ThermalMonitor,
    private val batteryMonitor: BatteryMonitor,
    private val lowBatteryThreshold: Float = DEFAULT_LOW_BATTERY_THRESHOLD,
    private val enableIdleDetection: Boolean = true,
) {
    private val lastProcessedMs = AtomicLong(-1_000_000L)
    private val idleDetector = if (enableIdleDetection) IdleDetector() else null

    /**
     * The effective power mode after battery-level auto-downgrade.
     *
     * If the battery is below [lowBatteryThreshold] and not charging,
     * the mode is downgraded by one step (HIGH -> BALANCED, BALANCED -> LOW_POWER).
     * LOW_POWER remains LOW_POWER.
     */
    val effectiveMode: PowerMode
        get() {
            if (batteryMonitor.isCharging.value) return powerMode
            val level = batteryMonitor.batteryLevel.value
            if (level > lowBatteryThreshold) return powerMode
            return downgradeMode(powerMode)
        }

    /**
     * Computes the current effective target FPS considering power mode,
     * thermal state, idle state, and battery level.
     */
    val effectiveFps: Int
        get() {
            val mode = effectiveMode
            var fps = computeThermalAdjustedFps(mode, thermalMonitor.thermalState.value)

            // Halve FPS when idle (minimum 5 fps)
            if (idleDetector?.isIdle == true) {
                fps = maxOf(fps / IDLE_FPS_DIVISOR, MIN_IDLE_FPS)
            }

            return fps
        }

    /** Current thermal state. */
    val thermalState: StateFlow<ThermalState> get() = thermalMonitor.thermalState

    /**
     * Returns true if this frame should be processed, false if it should be skipped.
     *
     * @param timestampMs Current frame timestamp in milliseconds.
     * @return Whether the frame should be processed.
     */
    fun shouldProcess(timestampMs: Long): Boolean {
        val targetFps = effectiveFps
        val minIntervalMs = MILLIS_PER_SECOND / targetFps
        val elapsed = timestampMs - lastProcessedMs.load()
        if (elapsed < minIntervalMs) return false

        lastProcessedMs.store(timestampMs)
        return true
    }

    /**
     * Notifies the manager of new blend shape data for idle detection.
     *
     * Call this after a frame is processed (not skipped).
     *
     * @param blendShapes The current frame's blend shape data.
     * @param timestampMs The current frame's timestamp.
     */
    fun onFrameProcessed(
        blendShapes: io.github.kmpfacelink.model.BlendShapeData,
        timestampMs: Long,
    ) {
        idleDetector?.update(blendShapes, timestampMs)
    }

    /** Whether the blend shape enhancer should be disabled at current thermal/power state. */
    fun shouldDisableEnhancer(): Boolean {
        if (effectiveMode == PowerMode.LOW_POWER) return true
        return thermalMonitor.thermalState.value.ordinal >= ThermalState.SERIOUS.ordinal
    }

    /** Releases resources held by the manager. */
    fun release() {
        idleDetector?.reset()
    }

    internal companion object {
        internal const val MILLIS_PER_SECOND = 1000L
        internal const val MIN_IDLE_FPS = 5
        private const val HALF_DIVISOR = 2
        private const val QUARTER_DIVISOR = 4
        private const val BALANCED_FAIR_NUMER = 5
        private const val BALANCED_FAIR_DENOM = 6
        private const val TWO_THIRDS_NUMER = 2
        private const val TWO_THIRDS_DENOM = 3

        internal fun downgradeMode(mode: PowerMode): PowerMode = when (mode) {
            PowerMode.HIGH -> PowerMode.BALANCED
            PowerMode.BALANCED -> PowerMode.LOW_POWER
            PowerMode.LOW_POWER -> PowerMode.LOW_POWER
        }

        /**
         * Adjusts FPS based on thermal state.
         * HIGH mode: no thermal reduction until SERIOUS/CRITICAL.
         * BALANCED: progressively reduces on FAIR/SERIOUS/CRITICAL.
         * LOW_POWER: already at minimum, only CRITICAL reduces further.
         */
        internal fun computeThermalAdjustedFps(mode: PowerMode, thermal: ThermalState): Int {
            val base = mode.baseFps
            return when (mode) {
                PowerMode.HIGH -> highModeThermalFps(base, thermal)
                PowerMode.BALANCED -> balancedModeThermalFps(base, thermal)
                PowerMode.LOW_POWER -> lowPowerModeThermalFps(base, thermal)
            }
        }

        private fun highModeThermalFps(base: Int, thermal: ThermalState): Int = when (thermal) {
            ThermalState.NOMINAL -> base
            ThermalState.FAIR -> base
            ThermalState.SERIOUS -> base / HALF_DIVISOR
            ThermalState.CRITICAL -> base / QUARTER_DIVISOR
        }

        private fun balancedModeThermalFps(base: Int, thermal: ThermalState): Int = when (thermal) {
            ThermalState.NOMINAL -> base
            ThermalState.FAIR -> base * BALANCED_FAIR_NUMER / BALANCED_FAIR_DENOM
            ThermalState.SERIOUS -> base * TWO_THIRDS_NUMER / TWO_THIRDS_DENOM
            ThermalState.CRITICAL -> base / HALF_DIVISOR
        }

        private fun lowPowerModeThermalFps(base: Int, thermal: ThermalState): Int = when (thermal) {
            ThermalState.NOMINAL -> base
            ThermalState.FAIR -> base
            ThermalState.SERIOUS -> base
            ThermalState.CRITICAL -> base * TWO_THIRDS_NUMER / TWO_THIRDS_DENOM
        }
    }
}
