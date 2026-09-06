@file:Suppress("MagicNumber")
@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducer
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateProducerDescriptor
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSemanticIntent
import io.github.kmpfacelink.util.CheekPuffContourDetector

/** Role assigned upstream from the recorded take timeline; it is not inferred from face values. */
@ExperimentalFaceLinkApi
public enum class C1StandaloneFrameRole(public val encodedValue: Float) {
    BEFORE_CALIBRATION(0f),
    OPENING_NEUTRAL_CALIBRATION(1f),
    EVALUATION(2f),
}

/** Historical standalone C1 factory with the preregistered opening-neutral calibration schedule. */
@ExperimentalFaceLinkApi
public object C1FrozenContourCandidate {
    public const val FRAME_ROLE_PLANE_KEY: String = "research:c1-frozen-contour/frame-role/v1"

    public fun factory(): CandidateObservationProducerFactory = C1StandaloneFactory()
}

private class C1StandaloneFactory : CandidateObservationProducerFactory {
    override val descriptor: CandidateProducerDescriptor = CandidateProducerDescriptor(
        producerId = "research:c1-frozen-contour/v1",
        implementationRevision = "dk-c1-frozen-contour-standalone/v1",
        semanticId = "canonical:cheekPuff",
        semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
        proposedTarget = BlendShape.CHEEK_PUFF,
    )

    override fun create(): CandidateObservationProducer = C1StandaloneProducer()
}

private class C1StandaloneProducer : CandidateObservationProducer {
    private var detector: CheekPuffContourDetector? = null
    private var imageDimensions: Pair<Int, Int>? = null
    private var lastRole: C1StandaloneFrameRole? = null
    private var calibrationFrames: Int = 0
    private var calibrationFinished: Boolean = false
    private var terminalFailure: C1Failure? = null

    @Suppress("ReturnCount")
    override fun observe(input: CandidateObservationInput): CandidateProducerOutput {
        terminalFailure?.let { return it.output() }
        val role = when (val parsed = parseRole(input)) {
            is C1RoleResult.Failed -> return parsed.failure.output()
            is C1RoleResult.Ready -> parsed.role
        }
        validateOrder(role)?.let { return reject(it) }
        validateLandmarks(input)?.let { return it.output() }

        return when (role) {
            C1StandaloneFrameRole.BEFORE_CALIBRATION ->
                CandidateProducerOutput.absent(CandidateReadiness.NOT_READY, "c1_opening_neutral_burn_in")
            C1StandaloneFrameRole.OPENING_NEUTRAL_CALIBRATION -> calibrate(input)
            C1StandaloneFrameRole.EVALUATION -> evaluate(input)
        }
    }

    private fun validateOrder(role: C1StandaloneFrameRole): String? {
        val previous = lastRole
        if (previous != null && role.ordinal < previous.ordinal) return "c1_frame_role_regressed"
        lastRole = role
        return null
    }

    private fun validateLandmarks(input: CandidateObservationInput): C1Failure? {
        if (input.landmarks.size != CheekPuffContourDetector.LANDMARK_COUNT) {
            return C1Failure(CandidateReadiness.MISSING_INPUT, "c1_landmarks_missing")
        }
        if (input.landmarks.any { it.relativeDepth == null }) {
            return C1Failure(CandidateReadiness.MISSING_INPUT, "c1_depth_missing")
        }
        val incoming = input.sourceImageWidth to input.sourceImageHeight
        val expected = imageDimensions
        if (expected != null && expected != incoming) {
            return C1Failure(CandidateReadiness.REJECTED, "c1_source_dimensions_changed_within_epoch")
        }
        if (expected == null) {
            imageDimensions = incoming
            detector = CheekPuffContourDetector(incoming.first, incoming.second)
        }
        return null
    }

    private fun calibrate(input: CandidateObservationInput): CandidateProducerOutput {
        if (calibrationFinished) return reject("c1_calibration_after_evaluation")
        return try {
            checkNotNull(detector).addCalibrationFrame(
                input.landmarks,
                input.identity.timestampMs / MILLIS_PER_SECOND,
            )
            calibrationFrames++
            CandidateProducerOutput.absent(CandidateReadiness.NOT_READY, "c1_opening_neutral_calibration")
        } catch (_: IllegalArgumentException) {
            reject("c1_calibration_input_rejected")
        } catch (_: IllegalStateException) {
            reject("c1_calibration_state_invalid")
        }
    }

    private fun evaluate(input: CandidateObservationInput): CandidateProducerOutput {
        if (!calibrationFinished) {
            if (calibrationFrames < MIN_CALIBRATION_FRAMES) {
                return reject("c1_insufficient_opening_neutral_frames")
            }
            try {
                checkNotNull(detector).finishCalibration()
                calibrationFinished = true
            } catch (_: IllegalArgumentException) {
                return reject("c1_calibration_rejected")
            } catch (_: IllegalStateException) {
                return reject("c1_calibration_state_invalid")
            }
        }
        return try {
            val decision = checkNotNull(detector).update(
                input.landmarks,
                input.identity.timestampMs / MILLIS_PER_SECOND,
            )
            CandidateProducerOutput.ready(if (decision.fired) 1f else 0f)
        } catch (_: IllegalArgumentException) {
            reject("c1_evaluation_input_rejected")
        } catch (_: IllegalStateException) {
            reject("c1_evaluation_state_invalid")
        }
    }

    private fun reject(reason: String): CandidateProducerOutput {
        val failure = C1Failure(CandidateReadiness.REJECTED, reason)
        terminalFailure = failure
        return failure.output()
    }
}

private fun parseRole(input: CandidateObservationInput): C1RoleResult {
    val values = input.featurePlanes[C1FrozenContourCandidate.FRAME_ROLE_PLANE_KEY]
        ?: return C1RoleResult.Failed(C1Failure(CandidateReadiness.MISSING_INPUT, "c1_frame_role_missing"))
    if (values.size != 1) {
        return C1RoleResult.Failed(C1Failure(CandidateReadiness.REJECTED, "c1_frame_role_length_invalid"))
    }
    val value = values.single()
    if (!value.isFinite()) {
        return C1RoleResult.Failed(C1Failure(CandidateReadiness.NON_FINITE, "c1_frame_role_non_finite"))
    }
    val role = C1StandaloneFrameRole.entries.firstOrNull { it.encodedValue == value }
        ?: return C1RoleResult.Failed(C1Failure(CandidateReadiness.REJECTED, "c1_frame_role_unknown"))
    return C1RoleResult.Ready(role)
}

private sealed interface C1RoleResult {
    class Ready(val role: C1StandaloneFrameRole) : C1RoleResult
    class Failed(val failure: C1Failure) : C1RoleResult
}

private class C1Failure(
    val readiness: CandidateReadiness,
    val reason: String,
) {
    fun output(): CandidateProducerOutput = CandidateProducerOutput.absent(readiness, reason)
}

private const val MIN_CALIBRATION_FRAMES: Int = 15
private const val MILLIS_PER_SECOND: Double = 1000.0
