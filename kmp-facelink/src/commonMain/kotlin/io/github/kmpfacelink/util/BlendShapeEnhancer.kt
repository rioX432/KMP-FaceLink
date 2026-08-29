package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.FaceLandmark

/**
 * Enhances MediaPipe blend shape output using geometric landmark calculations.
 *
 * Processing order:
 * 1. **Geometric solver override** — blend geometric values with ML output
 * 1.5. **Expression correlation** — apply FACS AU co-activation rules (if enabled)
 * 2. **Sensitivity scaling** — amplify weak signals
 * 3. **Dead zone threshold** — snap near-zero values to 0
 *
 * @see BlendShapeEnhancerConfig
 */
internal class BlendShapeEnhancer(
    private val config: BlendShapeEnhancerConfig.Default,
) {

    private val correlator = ExpressionCorrelator()

    // Pre-allocated scratch map reused each frame to avoid per-call toMutableMap() allocations.
    // Callers that store the result must copy it via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    /**
     * Enhance blend shape data using face landmarks.
     *
     * @param blendShapes Raw blend shape values from MediaPipe ML model
     * @param landmarks 478 face landmarks from MediaPipe
     * @return Enhanced blend shape data
     */
    fun enhance(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
    ): BlendShapeData {
        if (landmarks.size < LandmarkSolvers.MIN_LANDMARKS) return blendShapes

        scratch.clear()
        scratch.putAll(blendShapes)
        val result = scratch

        // Step 1: Geometric solver override
        applyGeometricOverrides(result, landmarks)

        // Step 1.5: Expression correlation (FACS AU co-activation)
        if (config.enableCorrelation) {
            correlator.apply(result)
        }

        // Step 2: Sensitivity scaling
        applySensitivity(result)

        // Step 3: Dead zone threshold
        applyDeadZone(result)

        return result
    }

    /**
     * Blend explicitly enabled geometric candidates with estimator values.
     * - Near-zero estimator shapes: use the larger value from the two signals
     * - Other configured shapes: combine both signals with the configured weight
     *
     * This algorithmic choice does not assert that the geometric signal is more accurate.
     */
    private fun applyGeometricOverrides(
        result: MutableMap<BlendShape, Float>,
        landmarks: List<FaceLandmark>,
    ) {
        // Near-zero shapes: use max(geometric, ml)
        for ((shape, solver) in nearZeroSolvers) {
            val ml = result[shape] ?: 0f
            val geometric = solver(landmarks)
            result[shape] = maxOf(geometric, ml).coerceIn(0f, 1f)
        }

        // Low-accuracy shapes: weighted blend
        val w = config.geometricBlendWeight
        for ((shape, solver) in lowAccuracySolvers) {
            val ml = result[shape] ?: 0f
            val geometric = solver(landmarks)
            result[shape] = (w * geometric + (1f - w) * ml).coerceIn(0f, 1f)
        }
    }

    private fun applySensitivity(result: MutableMap<BlendShape, Float>) {
        for ((shape, multiplier) in config.sensitivityOverrides) {
            val value = result[shape] ?: continue
            result[shape] = (value * multiplier).coerceIn(0f, 1f)
        }
    }

    private fun applyDeadZone(result: MutableMap<BlendShape, Float>) {
        for ((shape, threshold) in config.deadZoneOverrides) {
            val value = result[shape] ?: continue
            if (value < threshold) {
                result[shape] = 0f
            }
        }
    }

    companion object {
        /**
         * Create an enhancer from config, or null if enhancement is disabled.
         */
        fun create(config: BlendShapeEnhancerConfig): BlendShapeEnhancer? =
            when (config) {
                is BlendShapeEnhancerConfig.None -> null
                is BlendShapeEnhancerConfig.Default -> BlendShapeEnhancer(config)
            }

        /** Shapes assigned to the explicitly enabled max-combination path. */
        private val nearZeroSolvers: Map<BlendShape, (List<FaceLandmark>) -> Float> = mapOf(
            BlendShape.CHEEK_PUFF to LandmarkSolvers::solveCheekPuff,
            BlendShape.CHEEK_SQUINT_LEFT to CheekBrowSolvers::solveCheekSquintLeft,
            BlendShape.CHEEK_SQUINT_RIGHT to CheekBrowSolvers::solveCheekSquintRight,
            BlendShape.EYE_WIDE_LEFT to LandmarkSolvers::solveEyeWideLeft,
            BlendShape.EYE_WIDE_RIGHT to LandmarkSolvers::solveEyeWideRight,
            BlendShape.NOSE_SNEER_LEFT to LandmarkSolvers::solveNoseSneerLeft,
            BlendShape.NOSE_SNEER_RIGHT to LandmarkSolvers::solveNoseSneerRight,
        )

        /** Shapes assigned to the explicitly enabled weighted-combination path. */
        private val lowAccuracySolvers: Map<BlendShape, (List<FaceLandmark>) -> Float> = mapOf(
            BlendShape.BROW_DOWN_LEFT to CheekBrowSolvers::solveBrowDownLeft,
            BlendShape.BROW_DOWN_RIGHT to CheekBrowSolvers::solveBrowDownRight,
            BlendShape.BROW_INNER_UP to CheekBrowSolvers::solveBrowInnerUp,
            BlendShape.BROW_OUTER_UP_LEFT to CheekBrowSolvers::solveBrowOuterUpLeft,
            BlendShape.BROW_OUTER_UP_RIGHT to CheekBrowSolvers::solveBrowOuterUpRight,
            BlendShape.JAW_FORWARD to LandmarkSolvers::solveJawForward,
            BlendShape.JAW_LEFT to LandmarkSolvers::solveJawLeft,
            BlendShape.JAW_RIGHT to LandmarkSolvers::solveJawRight,
            BlendShape.MOUTH_DIMPLE_LEFT to LandmarkSolvers::solveMouthDimpleLeft,
            BlendShape.MOUTH_DIMPLE_RIGHT to LandmarkSolvers::solveMouthDimpleRight,
            BlendShape.MOUTH_SMILE_LEFT to MouthSolvers::solveMouthSmileLeft,
            BlendShape.MOUTH_SMILE_RIGHT to MouthSolvers::solveMouthSmileRight,
            BlendShape.MOUTH_FROWN_LEFT to MouthSolvers::solveMouthFrownLeft,
            BlendShape.MOUTH_FROWN_RIGHT to MouthSolvers::solveMouthFrownRight,
            BlendShape.MOUTH_STRETCH_LEFT to MouthSolvers::solveMouthStretchLeft,
            BlendShape.MOUTH_STRETCH_RIGHT to MouthSolvers::solveMouthStretchRight,
            BlendShape.MOUTH_PRESS_LEFT to MouthSolvers::solveMouthPressLeft,
            BlendShape.MOUTH_PRESS_RIGHT to MouthSolvers::solveMouthPressRight,
        )
    }
}
