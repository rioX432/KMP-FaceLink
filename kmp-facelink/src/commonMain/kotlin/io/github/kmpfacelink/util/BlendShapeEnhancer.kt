package io.github.kmpfacelink.util

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.AlternativeSignalObservation
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducer
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateObservationReceipt
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateProducerDescriptor
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSelectionMode
import io.github.kmpfacelink.model.CandidateSelectionPolicy
import io.github.kmpfacelink.model.CandidateSelectionRoute
import io.github.kmpfacelink.model.CandidateSemanticIntent
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
@OptIn(ExperimentalFaceLinkApi::class)
internal class BlendShapeEnhancer(
    private val config: BlendShapeEnhancerConfig.Default,
) {

    private val correlator = ExpressionCorrelator()

    // Pre-allocated scratch map reused each frame to avoid per-call toMutableMap() allocations.
    // Callers that store the result must copy it via HashMap(result).
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    // Reused only while adapting the durable receipt to the legacy two-plane type.
    private val candidates: ArrayList<AlternativeSignalObservation> = ArrayList(SOLVER_COUNT)
    private val candidateSession = CandidateObservationSession(createProducerFactories())
    private val selectionPolicy = createSelectionPolicy(config.geometricBlendWeight)
    private var legacyTimestampMs: Long = 0L

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
        val receipt = observeReceipt(
            identity = legacyIdentity(),
            blendShapes = blendShapes,
            landmarks = landmarks,
            sourceImageWidth = NORMALIZED_IMAGE_SIZE,
            sourceImageHeight = NORMALIZED_IMAGE_SIZE,
        )
        return postProcess(receipt.selected)
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
        val receipt = observeReceipt(
            identity = legacyIdentity(),
            blendShapes = blendShapes,
            landmarks = landmarks,
            sourceImageWidth = NORMALIZED_IMAGE_SIZE,
            sourceImageHeight = NORMALIZED_IMAGE_SIZE,
        )
        candidates.clear()
        for (observation in receipt.alternatives) {
            val value = observation.value ?: continue
            candidates += AlternativeSignalObservation(
                producerId = observation.producerId,
                value = value,
                proposedTarget = observation.proposedTarget,
            )
        }
        return FaceSignalCandidates(estimator = receipt.estimator, alternatives = candidates)
    }

    /** Shared observation call used by live processing and controlled replay. */
    fun observeReceipt(
        identity: CandidateInputIdentity,
        blendShapes: BlendShapeData,
        landmarks: List<FaceLandmark>,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
    ): CandidateObservationReceipt = candidateSession.observe(
        input = CandidateObservationInput(
            identity = identity,
            estimator = blendShapes,
            landmarks = landmarks,
            sourceImageWidth = sourceImageWidth,
            sourceImageHeight = sourceImageHeight,
        ),
        selectionPolicy = selectionPolicy,
    )

    /** Applies only post-selection transforms to the already snapshotted selected plane. */
    fun postProcess(selected: BlendShapeData): BlendShapeData {
        scratch.clear()
        scratch.putAll(selected)
        if (config.enableCorrelation) {
            correlator.apply(scratch)
        }
        applySensitivity(scratch)
        applyDeadZone(scratch)
        return scratch
    }

    private fun legacyIdentity(): CandidateInputIdentity = CandidateInputIdentity(
        contextEpochId = 0u,
        processingStateRevision = 0u,
        faceIndex = 0,
        timestampMs = legacyTimestampMs++,
    )

    private fun createSelectionPolicy(weight: Float): CandidateSelectionPolicy {
        val routes = mutableMapOf<BlendShape, CandidateSelectionRoute>()
        nearZeroSolvers.keys.forEach { shape ->
            routes[shape] = CandidateSelectionRoute(
                producerId = "$GEOMETRY_PRODUCER_PREFIX${shape.name}",
                mode = CandidateSelectionMode.MAX_WITH_ESTIMATOR,
            )
        }
        lowAccuracySolvers.keys.forEach { shape ->
            routes[shape] = CandidateSelectionRoute(
                producerId = "$GEOMETRY_PRODUCER_PREFIX${shape.name}",
                mode = CandidateSelectionMode.WEIGHTED_WITH_ESTIMATOR,
                alternativeWeight = weight,
            )
        }
        return CandidateSelectionPolicy(routes)
    }

    private fun createProducerFactories(): List<CandidateObservationProducerFactory> =
        (nearZeroSolvers + lowAccuracySolvers).map { (shape, solver) ->
            GeometryProducerFactory(shape, solver)
        }

    private class GeometryProducerFactory(
        shape: BlendShape,
        private val solver: (List<FaceLandmark>) -> Float?,
    ) : CandidateObservationProducerFactory {
        override val descriptor: CandidateProducerDescriptor = CandidateProducerDescriptor(
            producerId = "$GEOMETRY_PRODUCER_PREFIX${shape.name}",
            implementationRevision = GENERIC_GEOMETRY_IMPLEMENTATION_REVISION,
            semanticId = "canonical:${shape.arKitName}",
            semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
            proposedTarget = shape,
        )

        override fun create(): CandidateObservationProducer = CandidateObservationProducer { input ->
            if (input.landmarks.size < LandmarkSolvers.MIN_LANDMARKS) {
                CandidateProducerOutput.absent(CandidateReadiness.MISSING_INPUT, "insufficient_landmarks")
            } else {
                val value = solver(input.landmarks)
                if (value == null) {
                    CandidateProducerOutput.absent(CandidateReadiness.NOT_READY, "solver_returned_no_value")
                } else {
                    CandidateProducerOutput.ready(value)
                }
            }
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

        /** Evidence identity for the pre-existing generic stateless solver family. */
        internal const val GENERIC_GEOMETRY_IMPLEMENTATION_REVISION = "generic-stateless-landmark-solvers/v1"

        /** Reserved research identity; never alias it to the generic stateless cheek solver. */
        internal const val FROZEN_CONTOUR_PRODUCER_ID = "research:c1-frozen-contour"

        private const val NORMALIZED_IMAGE_SIZE = 1

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
