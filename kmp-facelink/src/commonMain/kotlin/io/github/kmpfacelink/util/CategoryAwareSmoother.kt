package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.SmoothingConfig
import kotlin.math.PI
import kotlin.math.abs

/**
 * A [BlendShapeSmoother] that applies per-category One Euro filtering.
 *
 * Each [BlendShape] is routed to its [BlendShapeCategory] and smoothed using the
 * One Euro parameters configured for that category. This allows fast-moving regions
 * (mouth, jaw) to use higher cutoff frequencies while slower regions (brow, cheek)
 * get heavier smoothing.
 *
 * @param config Category-specific One Euro configuration.
 */
internal class CategoryAwareSmoother(
    private val config: SmoothingConfig.CategoryAware,
) : BlendShapeSmoother {

    private var lastTimestampMs: Long = -1L
    private val states = mutableMapOf<BlendShape, ShapeState>()

    // Pre-allocated scratch map reused each frame to avoid per-call HashMap allocations.
    // Callers that store the result must copy it via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData {
        // First frame or same timestamp — pass through and initialise states
        if (lastTimestampMs < 0 || timestampMs <= lastTimestampMs) {
            lastTimestampMs = timestampMs
            scratch.clear()
            for ((shape, value) in data) {
                states[shape] = ShapeState(value, 0f)
                scratch[shape] = value
            }
            return scratch
        }

        val dt = (timestampMs - lastTimestampMs) / MILLIS_PER_SECOND
        lastTimestampMs = timestampMs

        scratch.clear()
        for ((shape, rawValue) in data) {
            val params = shape.category().toParams()
            val state = states[shape]
            if (state == null) {
                states[shape] = ShapeState(rawValue, 0f)
                scratch[shape] = rawValue
                continue
            }

            val dx = (rawValue - state.x) / dt
            val edx = lowPass(dx, state.dx, smoothingFactor(dt, params.dCutoff))

            val cutoff = params.minCutoff + params.beta * abs(edx)
            val filtered = lowPass(rawValue, state.x, smoothingFactor(dt, cutoff))

            states[shape] = ShapeState(filtered, edx)
            scratch[shape] = filtered
        }

        return scratch
    }

    override fun reset() {
        states.clear()
        lastTimestampMs = -1L
    }

    private fun BlendShapeCategory.toParams(): FilterParams = when (this) {
        BlendShapeCategory.MOUTH -> FilterParams(
            config.mouthConfig.minCutoff,
            config.mouthConfig.beta,
            config.mouthConfig.dCutoff,
        )
        BlendShapeCategory.EYE -> FilterParams(
            config.eyeConfig.minCutoff,
            config.eyeConfig.beta,
            config.eyeConfig.dCutoff,
        )
        BlendShapeCategory.BROW -> FilterParams(
            config.browConfig.minCutoff,
            config.browConfig.beta,
            config.browConfig.dCutoff,
        )
        BlendShapeCategory.CHEEK -> FilterParams(
            config.cheekConfig.minCutoff,
            config.cheekConfig.beta,
            config.cheekConfig.dCutoff,
        )
        BlendShapeCategory.JAW -> FilterParams(
            config.jawConfig.minCutoff,
            config.jawConfig.beta,
            config.jawConfig.dCutoff,
        )
        BlendShapeCategory.NOSE,
        BlendShapeCategory.OTHER,
        -> FilterParams(
            config.otherConfig.minCutoff,
            config.otherConfig.beta,
            config.otherConfig.dCutoff,
        )
    }

    private fun smoothingFactor(dt: Float, cutoff: Float): Float {
        val r = 2 * PI.toFloat() * cutoff * dt
        return r / (r + 1f)
    }

    private fun lowPass(x: Float, xPrev: Float, alpha: Float): Float =
        alpha * x + (1f - alpha) * xPrev

    private data class ShapeState(val x: Float, val dx: Float)

    private data class FilterParams(val minCutoff: Float, val beta: Float, val dCutoff: Float)

    private companion object {
        private const val MILLIS_PER_SECOND = 1000f
    }
}
