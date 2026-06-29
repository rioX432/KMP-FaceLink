package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.PowerMode
import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
 * @param npuAvailable Whether the device has an NPU capable of ML inference.
 *        Used by [recommendedDelegate] to suggest power-efficient delegate choices.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class PowerModeManager(
    private val powerMode: PowerMode,
    private val thermalMonitor: ThermalMonitor,
    private val batteryMonitor: BatteryMonitor,
    private val lowBatteryThreshold: Float = DEFAULT_LOW_BATTERY_THRESHOLD,
    private val enableIdleDetection: Boolean = true,
    private val npuAvailable: Boolean = false,
) {
    private val lastProcessedMs = AtomicLong(-1_000_000L)
    private val idleDetector = if (enableIdleDetection) IdleDetector() else null
    private var qualityPolicy: QualityPolicy? = null
    private var thermalCollectJob: Job? = null

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
     * The recommended [QualityPreset] based on thermal state with hysteresis.
     * Returns null if [startQualityPolicy] has not been called.
     */
    val recommendedPreset: StateFlow<QualityPreset>?
        get() = qualityPolicy?.recommendedPreset

    /**
     * Starts the quality policy that reacts to thermal state changes and
     * produces [recommendedPreset] values with hysteresis.
     *
     * Must be called after construction with a [CoroutineScope] that lives
     * as long as the tracker session.
     *
     * @param initialPreset The quality preset the session started with.
     * @param scope Coroutine scope for thermal collection and hysteresis delays.
     */
    fun startQualityPolicy(initialPreset: QualityPreset, scope: CoroutineScope) {
        val policy = QualityPolicy(initialPreset, scope)
        qualityPolicy = policy
        thermalCollectJob = thermalMonitor.thermalState
            .onEach { policy.onThermalStateChanged(it) }
            .launchIn(scope)
    }

    /**
     * Returns true if this frame should be processed, false if it should be skipped.
     *
     * @param timestampMs Current frame timestamp in milliseconds.
     * @return Whether the frame should be processed.
     */
    fun shouldProcess(timestampMs: Long): Boolean {
        val targetFps = effectiveFps
        val minIntervalMs = TimeConstants.MILLIS_PER_SECOND_L / targetFps
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

    /**
     * Returns the recommended [InferenceDelegate] based on NPU availability,
     * current power mode, and thermal state.
     *
     * NPU is preferred when available and power efficiency matters:
     * - [PowerMode.LOW_POWER]: NPU (most power-efficient, 3-5x over GPU)
     * - [PowerMode.BALANCED]: NPU (good efficiency without sacrificing quality)
     * - [PowerMode.HIGH] at [ThermalState.SERIOUS]+: NPU (less heat than GPU)
     * - [PowerMode.HIGH] at nominal thermal: GPU (maximum throughput)
     *
     * When no NPU is available, falls back to GPU (or CPU if thermal is critical
     * and effective mode has been downgraded to LOW_POWER).
     *
     * Note: The MediaPipe NPU delegate is not yet functional. This method
     * expresses the *intent* so that when NPU inference becomes available,
     * the power-aware routing is already in place.
     */
    fun recommendedDelegate(): InferenceDelegate {
        return recommendDelegate(
            effectiveMode,
            thermalMonitor.thermalState.value,
            npuAvailable,
        )
    }

    /** Releases resources held by the manager. */
    fun release() {
        thermalCollectJob?.cancel()
        thermalCollectJob = null
        qualityPolicy?.cancel()
        qualityPolicy = null
        idleDetector?.reset()
    }

    internal companion object {
        internal const val MIN_IDLE_FPS = 5
        private const val HALF_DIVISOR = 2
        private const val QUARTER_DIVISOR = 4
        private const val BALANCED_FAIR_NUMER = 5
        private const val BALANCED_FAIR_DENOM = 6
        private const val TWO_THIRDS_NUMER = 2
        private const val TWO_THIRDS_DENOM = 3

        /**
         * Determines the recommended [InferenceDelegate] based on power mode,
         * thermal state, and NPU availability.
         *
         * When [npuAvailable] is true:
         * - LOW_POWER / BALANCED: always NPU (power efficiency priority)
         * - HIGH + SERIOUS/CRITICAL thermal: NPU (reduces heat generation)
         * - HIGH + NOMINAL/FAIR thermal: GPU (maximum throughput)
         *
         * When [npuAvailable] is false:
         * - Always GPU (AUTO behavior — GPU with CPU fallback)
         */
        internal fun recommendDelegate(
            mode: PowerMode,
            thermal: ThermalState,
            npuAvailable: Boolean,
        ): InferenceDelegate {
            if (!npuAvailable) return InferenceDelegate.AUTO

            return when (mode) {
                PowerMode.LOW_POWER -> InferenceDelegate.NPU
                PowerMode.BALANCED -> InferenceDelegate.NPU
                PowerMode.HIGH -> if (thermal.ordinal >= ThermalState.SERIOUS.ordinal) {
                    InferenceDelegate.NPU
                } else {
                    InferenceDelegate.GPU
                }
            }
        }

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
