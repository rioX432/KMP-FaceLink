package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.ThermalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Maps [ThermalState] changes to [QualityPreset] with hysteresis to prevent
 * oscillation on thermal boundaries.
 *
 * Downgrade policy:
 * - [ThermalState.NOMINAL] -> [QualityPreset.High]
 * - [ThermalState.FAIR] -> [QualityPreset.Medium] after [DOWNGRADE_DELAY_MS]
 * - [ThermalState.SERIOUS] -> [QualityPreset.Low] (immediate)
 * - [ThermalState.CRITICAL] -> [QualityPreset.Low] (immediate)
 *
 * Upgrade policy:
 * - Only upgrades after [UPGRADE_DELAY_MS] of sustained [ThermalState.NOMINAL].
 *
 * @param initialPreset The quality preset to start with.
 * @param scope Coroutine scope for hysteresis delay jobs.
 */
internal class QualityPolicy(
    private val initialPreset: QualityPreset,
    private val scope: CoroutineScope,
) {
    private val _recommendedPreset = MutableStateFlow(initialPreset)

    /** The current recommended [QualityPreset] after thermal-driven adjustments. */
    val recommendedPreset: StateFlow<QualityPreset> = _recommendedPreset.asStateFlow()

    private var pendingJob: Job? = null

    /**
     * Called when the thermal state changes. Applies hysteresis rules
     * to determine whether and when the quality preset should change.
     */
    fun onThermalStateChanged(thermalState: ThermalState) {
        val target = mapThermalToPreset(thermalState)
        val current = _recommendedPreset.value

        // Cancel any pending transition
        pendingJob?.cancel()
        pendingJob = null

        when {
            // Immediate downgrade for SERIOUS/CRITICAL
            isImmediateDowngrade(thermalState) -> {
                _recommendedPreset.value = target
            }
            // Delayed downgrade for FAIR
            isDelayedDowngrade(target, current) -> {
                pendingJob = scope.launch {
                    delay(DOWNGRADE_DELAY_MS)
                    _recommendedPreset.value = target
                }
            }
            // Delayed upgrade (only from NOMINAL)
            isUpgrade(target, current, thermalState) -> {
                pendingJob = scope.launch {
                    delay(UPGRADE_DELAY_MS)
                    _recommendedPreset.value = target
                }
            }
            // Already at target — no change
        }
    }

    /** Cancels any pending transitions. */
    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
    }

    internal companion object {
        /** Delay before downgrading on sustained FAIR thermal state. */
        internal const val DOWNGRADE_DELAY_MS = 5_000L

        /** Delay before upgrading after returning to NOMINAL. */
        internal const val UPGRADE_DELAY_MS = 10_000L

        /**
         * Maps a [ThermalState] to the target [QualityPreset].
         */
        internal fun mapThermalToPreset(thermalState: ThermalState): QualityPreset = when (thermalState) {
            ThermalState.NOMINAL -> QualityPreset.High
            ThermalState.FAIR -> QualityPreset.Medium
            ThermalState.SERIOUS -> QualityPreset.Low
            ThermalState.CRITICAL -> QualityPreset.Low
        }

        private fun isImmediateDowngrade(thermalState: ThermalState): Boolean =
            thermalState == ThermalState.SERIOUS || thermalState == ThermalState.CRITICAL

        private fun isDelayedDowngrade(target: QualityPreset, current: QualityPreset): Boolean =
            presetOrdinal(target) < presetOrdinal(current)

        private fun isUpgrade(
            target: QualityPreset,
            current: QualityPreset,
            thermalState: ThermalState,
        ): Boolean = thermalState == ThermalState.NOMINAL && presetOrdinal(target) > presetOrdinal(current)

        /**
         * Returns an ordinal for comparison: Low=0, Medium=1, High=2.
         */
        internal fun presetOrdinal(preset: QualityPreset): Int = when (preset) {
            QualityPreset.Low -> 0
            QualityPreset.Medium -> 1
            QualityPreset.High -> 2
        }
    }
}
