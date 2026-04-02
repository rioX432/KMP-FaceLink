package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FrameSkipConfig
import io.github.kmpfacelink.model.FrameSkipLevel
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs

/**
 * Result of the frame skip decision.
 */
internal enum class FrameDecision {
    /** Frame should be fully processed by the tracker. */
    PROCESS,

    /** Frame can be skipped; use interpolated data instead. */
    SKIP,
}

/**
 * Decides whether a frame needs full detection or can be interpolated.
 *
 * Tracks recent confidence and blend shape stability to determine when
 * it is safe to skip detection. Integrates with [FrameThrottler] and
 * [PowerModeManager] — this strategy runs *after* those checks pass,
 * providing an additional layer of compute savings.
 *
 * @param config The frame skip configuration.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class ConfidenceFrameStrategy(private val config: FrameSkipConfig) {

    private val level: FrameSkipLevel = config.level
    private val consecutiveSkips = AtomicInt(0)
    private var previousProcessedBlendShapes: BlendShapeData? = null
    private var latestProcessedBlendShapes: BlendShapeData? = null
    private var lastMovementDelta: Float = Float.MAX_VALUE

    /**
     * Evaluates whether the current frame should be fully processed or skipped.
     *
     * The decision is based on: (1) tracking confidence from the previous frame,
     * (2) movement stability between the last two processed frames, and
     * (3) the number of consecutive skips not exceeding the configured maximum.
     *
     * @param confidence Tracking confidence from the previous frame (0.0–1.0).
     * @return [FrameDecision.PROCESS] if full detection is needed,
     *         [FrameDecision.SKIP] if interpolation can be used.
     */
    fun evaluate(confidence: Float): FrameDecision {
        if (!config.enabled) return FrameDecision.PROCESS

        val shouldProcess = confidence < level.confidenceThreshold ||
            consecutiveSkips.load() >= level.maxConsecutiveSkips ||
            previousProcessedBlendShapes == null ||
            latestProcessedBlendShapes == null ||
            lastMovementDelta > level.movementThreshold

        if (shouldProcess) {
            resetSkipCount()
            return FrameDecision.PROCESS
        }

        // All conditions met — safe to skip
        consecutiveSkips.store(consecutiveSkips.load() + 1)
        return FrameDecision.SKIP
    }

    /**
     * Notifies the strategy that a frame was fully processed.
     * Must be called after each processed frame to update internal state.
     *
     * @param blendShapes The processed blend shape data.
     * @param confidence The confidence from this frame (unused; kept for API clarity).
     */
    @Suppress("UNUSED_PARAMETER")
    fun onFrameProcessed(blendShapes: BlendShapeData, confidence: Float) {
        val prev = latestProcessedBlendShapes
        previousProcessedBlendShapes = prev
        latestProcessedBlendShapes = blendShapes
        if (prev != null) {
            lastMovementDelta = computeMaxDelta(prev, blendShapes)
        }
        resetSkipCount()
    }

    /** Resets all internal state. */
    fun reset() {
        consecutiveSkips.store(0)
        previousProcessedBlendShapes = null
        latestProcessedBlendShapes = null
        lastMovementDelta = Float.MAX_VALUE
    }

    private fun resetSkipCount() {
        consecutiveSkips.store(0)
    }

    internal companion object {
        /**
         * Computes the maximum absolute delta between two blend shape frames.
         */
        internal fun computeMaxDelta(a: BlendShapeData, b: BlendShapeData): Float {
            var maxDelta = 0f
            for ((shape, aVal) in a) {
                val bVal = b[shape] ?: 0f
                val delta = abs(aVal - bVal)
                if (delta > maxDelta) maxDelta = delta
            }
            return maxDelta
        }
    }
}
