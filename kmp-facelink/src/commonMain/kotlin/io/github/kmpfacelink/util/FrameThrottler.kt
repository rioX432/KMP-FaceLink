package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.ThermalPolicy
import io.github.kmpfacelink.model.ThermalState
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Decides whether to process or skip a frame based on thermal state and policy.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class FrameThrottler(private val policy: ThermalPolicy) {
    private val lastProcessedMs = AtomicLong(-1_000_000L)

    /**
     * Returns true if this frame should be processed, false if it should be skipped.
     *
     * When [policy] is [ThermalPolicy.Disabled] or thermal state is [ThermalState.NOMINAL],
     * all frames are processed.
     */
    fun shouldProcess(timestampMs: Long, thermalState: ThermalState): Boolean {
        if (policy is ThermalPolicy.Disabled || thermalState == ThermalState.NOMINAL) {
            lastProcessedMs.store(timestampMs)
            return true
        }

        val adaptive = policy as ThermalPolicy.Adaptive
        val targetFps = when (thermalState) {
            ThermalState.FAIR -> adaptive.fairTargetFps
            ThermalState.SERIOUS -> adaptive.seriousTargetFps
            ThermalState.CRITICAL -> adaptive.criticalTargetFps
            ThermalState.NOMINAL -> return true // unreachable, handled above
        }

        val minIntervalMs = 1000L / targetFps
        val elapsed = timestampMs - lastProcessedMs.load()
        if (elapsed < minIntervalMs) return false

        lastProcessedMs.store(timestampMs)
        return true
    }

    /** Whether the enhancer should be disabled at the current thermal state. */
    fun shouldDisableEnhancer(thermalState: ThermalState): Boolean {
        if (policy !is ThermalPolicy.Adaptive) return false
        if (!policy.disableEnhancerOnSerious) return false
        return thermalState.ordinal >= ThermalState.SERIOUS.ordinal
    }
}
