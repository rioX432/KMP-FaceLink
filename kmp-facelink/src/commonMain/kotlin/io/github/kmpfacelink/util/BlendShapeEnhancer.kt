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
 * 2. **Sensitivity scaling** — amplify weak signals
 * 3. **Dead zone threshold** — snap near-zero values to 0
 *
 * @see BlendShapeEnhancerConfig
 */
internal class BlendShapeEnhancer(
    private val config: BlendShapeEnhancerConfig.Default,
) {

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

        val result = blendShapes.toMutableMap()

        // Step 1: Geometric solver override
        applyGeometricOverrides(result, landmarks)

        // Step 2: Sensitivity scaling
        applySensitivity(result)

        // Step 3: Dead zone threshold
        applyDeadZone(result)

        return result
    }

    /**
     * Blend geometric solver values with ML values.
     * - Near-zero shapes: max(geometric, ml) — geometric is strictly better
     * - Low-accuracy shapes: weighted blend — both signals contribute
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

        /** Shapes where ML almost always returns ~0. Use max(geometric, ml). */
        private val nearZeroSolvers: Map<BlendShape, (List<FaceLandmark>) -> Float> = mapOf(
            BlendShape.CHEEK_PUFF to LandmarkSolvers::solveCheekPuff,
            BlendShape.EYE_WIDE_LEFT to LandmarkSolvers::solveEyeWideLeft,
            BlendShape.EYE_WIDE_RIGHT to LandmarkSolvers::solveEyeWideRight,
            BlendShape.NOSE_SNEER_LEFT to LandmarkSolvers::solveNoseSneerLeft,
            BlendShape.NOSE_SNEER_RIGHT to LandmarkSolvers::solveNoseSneerRight,
        )

        /** Shapes with low but non-zero ML accuracy. Use weighted blend. */
        private val lowAccuracySolvers: Map<BlendShape, (List<FaceLandmark>) -> Float> = mapOf(
            BlendShape.JAW_FORWARD to LandmarkSolvers::solveJawForward,
            BlendShape.JAW_LEFT to LandmarkSolvers::solveJawLeft,
            BlendShape.JAW_RIGHT to LandmarkSolvers::solveJawRight,
            BlendShape.MOUTH_DIMPLE_LEFT to LandmarkSolvers::solveMouthDimpleLeft,
            BlendShape.MOUTH_DIMPLE_RIGHT to LandmarkSolvers::solveMouthDimpleRight,
        )
    }
}
