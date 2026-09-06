package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.AlternativeSignalObservation
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceSignalCandidates

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

    // Reused for the same reason. Callers that keep the candidates must copy the list.
    private val candidates: ArrayList<AlternativeSignalObservation> = ArrayList(SOLVER_COUNT)

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

        // Step 1: choose between the estimator's values and what the solvers proposed
        select(observe(blendShapes, landmarks), result)

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
     * The values the landmark solvers propose for this frame, with the estimator's own values
     * beside them and nothing merged yet.
     *
     * This is the seam that keeps a derived value from arriving as a measurement. [enhance] is
     * defined in terms of it rather than repeating the solver walk, so the two cannot drift: the
     * merged output is always a selection over exactly these candidates.
     */
    fun observe(
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
    ): FaceSignalCandidates {
        candidates.clear()
        if (landmarks.size >= LandmarkSolvers.MIN_LANDMARKS) {
            for ((shape, solver) in nearZeroSolvers) {
                propose(shape, solver(landmarks))
            }
            for ((shape, solver) in lowAccuracySolvers) {
                propose(shape, solver(landmarks))
            }
        }
        return FaceSignalCandidates(estimator = blendShapes, alternatives = candidates)
    }

    private fun propose(shape: BlendShape, value: Float?) {
        if (value == null) return
        candidates += AlternativeSignalObservation(
            producerId = "$GEOMETRY_PRODUCER_PREFIX${shape.name}",
            value = value,
            proposedTarget = shape,
        )
    }

    /**
     * Decide, per shape, between the estimator's value and a proposed one.
     * - Near-zero estimator shapes: take the larger of the two
     * - Other configured shapes: combine both with the configured weight
     *
     * This algorithmic choice does not assert that the geometric signal is more accurate.
     *
     * A solver that could not run proposed nothing, so the shape stays as the estimator left it,
     * including absent. An estimator that did not produce the shape leaves the proposal to stand
     * alone: blending it against a substituted zero would drag a real value down toward one nobody
     * produced.
     */
    private fun select(
        proposals: FaceSignalCandidates,
        result: MutableMap<BlendShape, Float>,
    ) {
        val w = config.geometricBlendWeight
        for (candidate in proposals.alternatives) {
            val shape = candidate.proposedTarget ?: continue
            val estimated = result[shape]
            val combined = when {
                estimated == null -> candidate.value
                shape in nearZeroSolvers -> maxOf(candidate.value, estimated)
                else -> w * candidate.value + (1f - w) * estimated
            }
            result[shape] = combined.coerceIn(0f, 1f)
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
        /** Names every landmark-derived proposal, so a reader can tell it from an estimator value. */
        private const val GEOMETRY_PRODUCER_PREFIX = "landmark-geometry:"

        /** Upper bound on proposals per frame; only used to size the reused list. */
        private const val SOLVER_COUNT = 32

        /**
         * Create an enhancer from config, or null if enhancement is disabled.
         */
        fun create(config: BlendShapeEnhancerConfig): BlendShapeEnhancer? =
            when (config) {
                is BlendShapeEnhancerConfig.None -> null
                is BlendShapeEnhancerConfig.Default -> BlendShapeEnhancer(config)
            }

        /** Shapes assigned to the explicitly enabled max-combination path. */
        private val nearZeroSolvers: Map<BlendShape, (List<FaceLandmark>) -> Float?> = mapOf(
            BlendShape.CHEEK_PUFF to LandmarkSolvers::solveCheekPuff,
            BlendShape.CHEEK_SQUINT_LEFT to CheekBrowSolvers::solveCheekSquintLeft,
            BlendShape.CHEEK_SQUINT_RIGHT to CheekBrowSolvers::solveCheekSquintRight,
            BlendShape.EYE_WIDE_LEFT to LandmarkSolvers::solveEyeWideLeft,
            BlendShape.EYE_WIDE_RIGHT to LandmarkSolvers::solveEyeWideRight,
            BlendShape.NOSE_SNEER_LEFT to LandmarkSolvers::solveNoseSneerLeft,
            BlendShape.NOSE_SNEER_RIGHT to LandmarkSolvers::solveNoseSneerRight,
        )

        /** Shapes assigned to the explicitly enabled weighted-combination path. */
        private val lowAccuracySolvers: Map<BlendShape, (List<FaceLandmark>) -> Float?> = mapOf(
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
