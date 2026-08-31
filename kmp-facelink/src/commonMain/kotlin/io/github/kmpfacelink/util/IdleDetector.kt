package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.abs

private const val DEFAULT_IDLE_THRESHOLD = 0.02f
private const val DEFAULT_IDLE_DURATION_MS = 2000L

/**
 * Detects when a tracked face is stationary (idle) to enable frame rate reduction.
 *
 * Compares consecutive blend shape frames and considers the face idle when
 * the maximum change across all blend shapes is below a threshold for a
 * sustained duration.
 *
 * @param idleThreshold Maximum per-blend-shape delta to consider as "no movement".
 * @param idleDurationMs Minimum duration in ms below threshold before reporting idle.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class IdleDetector(
    private val idleThreshold: Float = DEFAULT_IDLE_THRESHOLD,
    private val idleDurationMs: Long = DEFAULT_IDLE_DURATION_MS,
) {
    private var previousBlendShapes: BlendShapeData? = null
    private val stationarySinceMs = AtomicLong(0L)

    private val _currentlyIdle = AtomicBoolean(false)

    /**
     * Updates the detector with a new frame and returns whether the face is idle.
     *
     * @param blendShapes Current frame's blend shape data.
     * @param timestampMs Current frame timestamp in milliseconds.
     * @return true if the face has been stationary for at least [idleDurationMs].
     */
    fun update(blendShapes: BlendShapeData, timestampMs: Long): Boolean {
        val prev = previousBlendShapes
        previousBlendShapes = blendShapes

        if (prev == null) {
            stationarySinceMs.store(timestampMs)
            _currentlyIdle.store(false)
            return false
        }

        val maxDelta = computeMaxDelta(prev, blendShapes)

        if (maxDelta > idleThreshold) {
            // Movement detected — reset
            stationarySinceMs.store(timestampMs)
            _currentlyIdle.store(false)
            return false
        }

        // Still below threshold — check duration
        val elapsed = timestampMs - stationarySinceMs.load()
        val idle = elapsed >= idleDurationMs
        _currentlyIdle.store(idle)
        return idle
    }

    /** Whether the face is currently considered idle. */
    val isIdle: Boolean get() = _currentlyIdle.load()

    /** Resets idle detection state. */
    fun reset() {
        previousBlendShapes = null
        stationarySinceMs.store(0L)
        _currentlyIdle.store(false)
    }

    internal companion object {
        /** Computes the maximum absolute delta between two blend shape frames. */
        internal fun computeMaxDelta(a: BlendShapeData, b: BlendShapeData): Float {
            var maxDelta = 0f
            for ((shape, aVal) in a) {
                // Both frames have to carry the shape for the difference to mean anything; see
                // ConfidenceFrameStrategy.computeMaxDelta for the same reasoning.
                val bVal = b[shape] ?: continue
                val delta = abs(aVal - bVal)
                if (delta > maxDelta) maxDelta = delta
            }
            return maxDelta
        }
    }
}
