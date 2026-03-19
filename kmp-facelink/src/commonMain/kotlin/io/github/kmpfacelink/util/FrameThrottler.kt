package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.ThermalPolicy
import io.github.kmpfacelink.model.ThermalState

/**
 * Decides whether to process or skip a frame based on thermal state and policy.
 *
 * Thread-safety: read/write races on [lastProcessedMs] are benign — worst case
 * one extra frame is processed or skipped.
 */
internal class FrameThrottler(private val policy: ThermalPolicy) {
    private var lastProcessedMs: Long = -1_000_000L

    /**
     * Returns true if this frame should be processed, false if it should be skipped.
     *
     * When [policy] is [ThermalPolicy.Disabled] or thermal state is [ThermalState.NOMINAL],
     * all frames are processed.
     */
    fun shouldProcess(timestampMs: Long, thermalState: ThermalState): Boolean {
        if (policy is ThermalPolicy.Disabled || thermalState == ThermalState.NOMINAL) {
            lastProcessedMs = timestampMs
            return true
        }

        val adaptive = policy as ThermalPolicy.Adaptive
        val targetFps = when (thermalState) {
            ThermalState.NOMINAL -> return true.also { lastProcessedMs = timestampMs }
            ThermalState.FAIR -> adaptive.fairTargetFps
            ThermalState.SERIOUS -> adaptive.seriousTargetFps
            ThermalState.CRITICAL -> adaptive.criticalTargetFps
        }

        val minIntervalMs = 1000L / targetFps
        val elapsed = timestampMs - lastProcessedMs
        if (elapsed < minIntervalMs) return false

        lastProcessedMs = timestampMs
        return true
    }

    /** Whether the enhancer should be disabled at the current thermal state. */
    fun shouldDisableEnhancer(thermalState: ThermalState): Boolean {
        if (policy !is ThermalPolicy.Adaptive) return false
        if (!policy.disableEnhancerOnSerious) return false
        return thermalState.ordinal >= ThermalState.SERIOUS.ordinal
    }
}
