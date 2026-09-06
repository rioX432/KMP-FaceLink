@file:Suppress("MagicNumber", "TooGenericExceptionCaught")

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.CheekPuffContourDetector

@ExperimentalFaceLinkApi
public enum class FrozenResearchReadiness { READY, NOT_READY, REJECTED }

@ExperimentalFaceLinkApi
public data class FrozenC2C1Input(
    public val timestampMillis: Long,
    public val estimator: BlendShapeData,
    public val landmarks: List<FaceLandmark>,
    public val sourceWidth: Int,
    public val sourceHeight: Int,
)

@ExperimentalFaceLinkApi
public data class FrozenC2C1Output(
    public val timestampMillis: Long,
    public val value: Float?,
    public val readiness: FrozenResearchReadiness,
    public val reason: String?,
) {
    init {
        require((readiness == FrozenResearchReadiness.READY) == (value != null))
        require(value == null || value.isFinite() && value in 0f..1f)
    }
}

/**
 * Narrow default-off adapter for the hash-verified frozen C2 lip-gated plus C1 contour producer.
 * It does not read or write canonical 52 and must be reset at every exact take boundary.
 */
@ExperimentalFaceLinkApi
public class FrozenC2C1ResearchAdapter {
    private val baselineSamples: MutableList<FloatArray> = mutableListOf()
    private var baseline: FloatArray? = null
    private var contour: CheekPuffContourDetector? = null
    private var dimensions: Pair<Int, Int>? = null
    private var terminalReason: String? = null

    @Suppress("LongMethod", "ReturnCount")
    public fun observe(input: FrozenC2C1Input): FrozenC2C1Output {
        terminalReason?.let { reason ->
            return absent(
                input,
                FrozenResearchReadiness.REJECTED,
                reason,
            )
        }
        val channels = readChannels(input)
            ?: return reject(input, "c2_required_channel_invalid")
        if (!validateContour(input)) {
            return absent(
                input,
                FrozenResearchReadiness.REJECTED,
                checkNotNull(terminalReason),
            )
        }
        require(input.timestampMillis >= 0) { "timestampMillis must be take-relative" }
        val elapsed = input.timestampMillis
        if (baseline == null) {
            if (elapsed <= BASELINE_END_MILLIS) {
                if (elapsed >= BASELINE_START_MILLIS) {
                    baselineSamples += channels.copyOf()
                    contour?.addCalibrationFrame(input.landmarks, input.timestampMillis / 1000.0)
                }
                return absent(input, FrozenResearchReadiness.NOT_READY, "opening_neutral_baseline_pending")
            }
            if (baselineSamples.size < BASELINE_MIN_FRAMES) {
                return reject(input, "insufficient_opening_neutral_frames")
            }
            baseline = componentMedians(baselineSamples)
            baselineSamples.clear()
            try {
                contour?.finishCalibration()
            } catch (_: Exception) {
                return reject(input, "frozen_contour_calibration_rejected")
            }
        }
        val neutral = checkNotNull(baseline)
        fun delta(index: Int): Float = (channels[index] - neutral[index]).coerceAtLeast(0f)
        val positive = maxOf(
            delta(ROLL_LOWER),
            delta(ROLL_UPPER),
            delta(MOUTH_LEFT),
            delta(MOUTH_RIGHT),
        )
        val negative = maxOf(
            delta(MOUTH_FUNNEL),
            delta(JAW_OPEN),
            delta(SMILE_LEFT),
            delta(SMILE_RIGHT),
        )
        val lipValue = (positive - negative).coerceIn(0f, 1f)
        val fired = try {
            checkNotNull(contour).update(input.landmarks, input.timestampMillis / 1000.0).fired
        } catch (_: Exception) {
            return reject(input, "frozen_contour_input_rejected")
        }
        return FrozenC2C1Output(
            input.timestampMillis,
            if (fired) lipValue else 0f,
            FrozenResearchReadiness.READY,
            null,
        )
    }

    public fun reset() {
        baselineSamples.clear()
        baseline = null
        contour = null
        dimensions = null
        terminalReason = null
    }

    private fun validateContour(input: FrozenC2C1Input): Boolean {
        if (input.landmarks.size != CheekPuffContourDetector.LANDMARK_COUNT ||
            input.landmarks.any { it.relativeDepth == null }
        ) {
            terminalReason = "frozen_contour_landmarks_invalid"
            return false
        }
        val incoming = input.sourceWidth to input.sourceHeight
        if (dimensions != null && dimensions != incoming) {
            terminalReason = "source_dimensions_changed"
            return false
        }
        if (dimensions == null) {
            dimensions = incoming
            contour = CheekPuffContourDetector(input.sourceWidth, input.sourceHeight)
        }
        return true
    }

    private fun readChannels(input: FrozenC2C1Input): FloatArray? {
        val values = FloatArray(REQUIRED_CHANNELS.size)
        REQUIRED_CHANNELS.forEachIndexed { index, shape ->
            val value = input.estimator[shape] ?: return null
            if (!value.isFinite() || value !in 0f..1f) return null
            values[index] = value
        }
        return values
    }

    private fun reject(input: FrozenC2C1Input, reason: String): FrozenC2C1Output {
        terminalReason = reason
        return absent(input, FrozenResearchReadiness.REJECTED, reason)
    }

    private fun absent(
        input: FrozenC2C1Input,
        readiness: FrozenResearchReadiness,
        reason: String,
    ): FrozenC2C1Output = FrozenC2C1Output(input.timestampMillis, null, readiness, reason)

    public companion object {
        public const val PRODUCER_ID: String = "research:c2-lip-gated/v1+c1-frozen-contour/v1"
        public const val IMPLEMENTATION_REVISION: String = "dk-c2-lip-plus-frozen-contour/v1"
        public const val VERIFIED_ORIGINAL_SOURCE_SHA256: String =
            "207b95d493c26a30aa779d7b91eeed9815089465f13009944e1ec7790e37f737"
    }
}

private fun componentMedians(samples: List<FloatArray>): FloatArray =
    FloatArray(REQUIRED_CHANNELS.size) { channel ->
        val values = FloatArray(samples.size) { samples[it][channel] }
        values.sort()
        if (values.size % 2 == 1) {
            values[values.size / 2]
        } else {
            (values[values.size / 2 - 1] + values[values.size / 2]) * 0.5f
        }
    }

private val REQUIRED_CHANNELS = listOf(
    BlendShape.MOUTH_ROLL_LOWER,
    BlendShape.MOUTH_ROLL_UPPER,
    BlendShape.MOUTH_LEFT,
    BlendShape.MOUTH_RIGHT,
    BlendShape.MOUTH_FUNNEL,
    BlendShape.JAW_OPEN,
    BlendShape.MOUTH_SMILE_LEFT,
    BlendShape.MOUTH_SMILE_RIGHT,
)
private const val ROLL_LOWER = 0
private const val ROLL_UPPER = 1
private const val MOUTH_LEFT = 2
private const val MOUTH_RIGHT = 3
private const val MOUTH_FUNNEL = 4
private const val JAW_OPEN = 5
private const val SMILE_LEFT = 6
private const val SMILE_RIGHT = 7
private const val BASELINE_START_MILLIS = 250L
private const val BASELINE_END_MILLIS = 1_250L
private const val BASELINE_MIN_FRAMES = 15
