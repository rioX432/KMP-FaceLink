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

/** Frozen, default-off research producers for the C2 lip-composition comparison. */
@ExperimentalFaceLinkApi
public object CheekSignalCandidateFactories {
    /** Three coefficient-only candidates fixed by the C2 V1 preregistration. */
    public fun c2LipOnly(): List<CandidateObservationProducerFactory> =
        LipVariant.entries.map { variant -> CheekCandidateFactory(variant, withFrozenContour = false) }

    /** The same three values gated by the frozen C1 contour decision. */
    public fun c2LipPlusFrozenContour(): List<CandidateObservationProducerFactory> =
        LipVariant.entries.map { variant -> CheekCandidateFactory(variant, withFrozenContour = true) }
}

private enum class LipVariant(
    val producerId: String,
) {
    ROLL_LOWER("research:c2-roll-lower/v1"),
    ROLL_MAX("research:c2-roll-max/v1"),
    LIP_GATED("research:c2-lip-gated/v1"),
}

@OptIn(ExperimentalFaceLinkApi::class)
private class CheekCandidateFactory(
    private val variant: LipVariant,
    private val withFrozenContour: Boolean,
) : CandidateObservationProducerFactory {
    override val descriptor: CandidateProducerDescriptor = CandidateProducerDescriptor(
        producerId = if (withFrozenContour) {
            "${variant.producerId}+c1-frozen-contour/v1"
        } else {
            variant.producerId
        },
        implementationRevision = if (withFrozenContour) C2_C1_REVISION else C2_REVISION,
        semanticId = CHEEK_PUFF_SEMANTIC_ID,
        semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
        proposedTarget = BlendShape.CHEEK_PUFF,
    )

    override fun create(): CandidateObservationProducer = CheekCandidateProducer(variant, withFrozenContour)
}

@OptIn(ExperimentalFaceLinkApi::class)
private class CheekCandidateProducer(
    private val variant: LipVariant,
    private val withFrozenContour: Boolean,
) : CandidateObservationProducer {
    private var firstUsableTimestampMs: Long? = null
    private val baselineSamples: MutableList<FloatArray> = mutableListOf()
    private var baseline: FloatArray? = null
    private var contour: CheekPuffContourDetector? = null
    private var imageDimensions: Pair<Int, Int>? = null
    private var terminalFailure: Failure? = null

    @Suppress("ReturnCount")
    override fun observe(input: CandidateObservationInput): CandidateProducerOutput {
        terminalFailure?.let { return it.output() }
        val values = when (val read = readValues(input)) {
            is ReadResult.Failed -> return read.failure.output()
            is ReadResult.Ready -> read.values
        }
        if (withFrozenContour) {
            validateContourInput(input)?.let { return it.output() }
        }
        val start = firstUsableTimestampMs ?: input.identity.timestampMs.also { firstUsableTimestampMs = it }
        val elapsed = input.identity.timestampMs - start
        if (baseline == null) {
            baselineStep(input, values, elapsed)?.let { return it }
        }
        val c2Value = calculate(values, checkNotNull(baseline))
        if (!withFrozenContour) return CandidateProducerOutput.ready(c2Value)
        return contourStep(input, c2Value)
    }

    private fun baselineStep(
        input: CandidateObservationInput,
        values: FloatArray,
        elapsedMs: Long,
    ): CandidateProducerOutput? {
        if (elapsedMs <= BASELINE_END_MS) {
            if (elapsedMs >= BASELINE_START_MS) {
                baselineSamples += values.copyOf()
                if (withFrozenContour) {
                    contour?.addCalibrationFrame(input.landmarks, input.identity.timestampMs / MILLIS_PER_SECOND)
                }
            }
            return CandidateProducerOutput.absent(CandidateReadiness.NOT_READY, BASELINE_PENDING)
        }
        if (baselineSamples.size < BASELINE_MIN_FRAMES) {
            return reject("insufficient_opening_neutral_frames")
        }
        baseline = componentMedians(baselineSamples)
        baselineSamples.clear()
        if (withFrozenContour) {
            try {
                contour?.finishCalibration()
            } catch (_: IllegalArgumentException) {
                return reject("frozen_contour_calibration_rejected")
            } catch (_: IllegalStateException) {
                return reject("frozen_contour_calibration_state_invalid")
            }
        }
        return null
    }

    private fun contourStep(input: CandidateObservationInput, c2Value: Float): CandidateProducerOutput = try {
        val decision = checkNotNull(contour).update(
            input.landmarks,
            input.identity.timestampMs / MILLIS_PER_SECOND,
        )
        CandidateProducerOutput.ready(if (decision.fired) c2Value else 0f)
    } catch (_: IllegalArgumentException) {
        reject("frozen_contour_input_rejected")
    } catch (_: IllegalStateException) {
        reject("frozen_contour_state_invalid")
    }

    private fun validateContourInput(input: CandidateObservationInput): Failure? {
        if (input.landmarks.size != CheekPuffContourDetector.LANDMARK_COUNT) {
            return Failure(CandidateReadiness.MISSING_INPUT, "frozen_contour_landmarks_missing")
        }
        if (input.landmarks.any { it.relativeDepth == null }) {
            return Failure(CandidateReadiness.MISSING_INPUT, "frozen_contour_depth_missing")
        }
        val incoming = input.sourceImageWidth to input.sourceImageHeight
        val expected = imageDimensions
        if (expected != null && expected != incoming) {
            return Failure(CandidateReadiness.REJECTED, "source_dimensions_changed_within_epoch")
        }
        if (expected == null) {
            imageDimensions = incoming
            contour = CheekPuffContourDetector(incoming.first, incoming.second)
        }
        return null
    }

    private fun calculate(values: FloatArray, neutral: FloatArray): Float {
        fun delta(index: Int): Float = (values[index] - neutral[index]).coerceAtLeast(0f)
        val rollLower = delta(ROLL_LOWER)
        val rollMax = maxOf(rollLower, delta(ROLL_UPPER))
        val lateral = maxOf(delta(MOUTH_LEFT), delta(MOUTH_RIGHT))
        val mouthGate = maxOf(
            delta(MOUTH_FUNNEL),
            delta(JAW_OPEN),
            delta(SMILE_LEFT),
            delta(SMILE_RIGHT),
        )
        return when (variant) {
            LipVariant.ROLL_LOWER -> rollLower
            LipVariant.ROLL_MAX -> rollMax
            LipVariant.LIP_GATED -> maxOf(rollMax, lateral) - mouthGate
        }.coerceIn(0f, 1f)
    }

    private fun reject(reason: String): CandidateProducerOutput {
        val failure = Failure(CandidateReadiness.REJECTED, reason)
        terminalFailure = failure
        return failure.output()
    }
}

@OptIn(ExperimentalFaceLinkApi::class)
private fun readValues(input: CandidateObservationInput): ReadResult {
    val values = FloatArray(REQUIRED_CHANNELS.size)
    REQUIRED_CHANNELS.forEachIndexed { index, shape ->
        val value = input.estimator[shape]
            ?: return ReadResult.Failed(Failure(CandidateReadiness.MISSING_INPUT, "missing_${shape.arKitName}"))
        if (!value.isFinite()) {
            return ReadResult.Failed(Failure(CandidateReadiness.NON_FINITE, "non_finite_${shape.arKitName}"))
        }
        if (value !in 0f..1f) {
            return ReadResult.Failed(Failure(CandidateReadiness.REJECTED, "out_of_range_${shape.arKitName}"))
        }
        values[index] = value
    }
    return ReadResult.Ready(values)
}

private fun componentMedians(samples: List<FloatArray>): FloatArray =
    FloatArray(REQUIRED_CHANNELS.size) { channel ->
        val values = FloatArray(samples.size) { sample -> samples[sample][channel] }
        values.sort()
        val middle = values.size / 2
        if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2f
    }

private sealed interface ReadResult {
    class Ready(val values: FloatArray) : ReadResult
    class Failed(val failure: Failure) : ReadResult
}

private class Failure(
    val readiness: CandidateReadiness,
    val reason: String,
) {
    fun output(): CandidateProducerOutput = CandidateProducerOutput.absent(readiness, reason)
}

private val REQUIRED_CHANNELS: List<BlendShape> = listOf(
    BlendShape.MOUTH_ROLL_LOWER,
    BlendShape.MOUTH_ROLL_UPPER,
    BlendShape.MOUTH_LEFT,
    BlendShape.MOUTH_RIGHT,
    BlendShape.MOUTH_FUNNEL,
    BlendShape.JAW_OPEN,
    BlendShape.MOUTH_SMILE_LEFT,
    BlendShape.MOUTH_SMILE_RIGHT,
)

private const val ROLL_LOWER: Int = 0
private const val ROLL_UPPER: Int = 1
private const val MOUTH_LEFT: Int = 2
private const val MOUTH_RIGHT: Int = 3
private const val MOUTH_FUNNEL: Int = 4
private const val JAW_OPEN: Int = 5
private const val SMILE_LEFT: Int = 6
private const val SMILE_RIGHT: Int = 7
private const val BASELINE_START_MS: Long = 250L
private const val BASELINE_END_MS: Long = 1250L
private const val BASELINE_MIN_FRAMES: Int = 15
private const val MILLIS_PER_SECOND: Double = 1000.0
private const val BASELINE_PENDING: String = "opening_neutral_baseline_pending"
private const val CHEEK_PUFF_SEMANTIC_ID: String = "canonical:cheekPuff"
private const val C2_REVISION: String = "dk-c2-lip-composition/v1"
private const val C2_C1_REVISION: String = "dk-c2-lip-plus-frozen-contour/v1"
