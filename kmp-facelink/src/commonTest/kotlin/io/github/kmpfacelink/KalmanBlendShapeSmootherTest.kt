package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.KalmanBlendShapeSmoother
import io.github.kmpfacelink.util.SmoothnessMetric
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KalmanBlendShapeSmootherTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.01f) {
        assertTrue(
            abs(expected - actual) < epsilon,
            "Expected ~$expected but was $actual (diff=${abs(expected - actual)})",
        )
    }

    // -- First frame / pass-through --

    @Test
    fun firstFramePassesThroughUnchanged() {
        val smoother = createSmoother()
        val data = mapOf(BlendShape.JAW_OPEN to 0.5f)
        val result = smoother.smooth(data, 0L)
        assertApprox(0.5f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
    }

    @Test
    fun sameTimestampPassesThrough() {
        val smoother = createSmoother()
        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.3f), 100L)
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.7f), 100L)
        assertApprox(0.7f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
    }

    // -- Smoothing reduces jitter --

    @Test
    fun smoothingReducesJitterOnNoisySignal() {
        val smoother = createSmoother(processNoise = 0.001f, measurementNoise = 0.5f)
        val rawMetric = SmoothnessMetric(windowSize = JITTER_WINDOW)
        val smoothedMetric = SmoothnessMetric(windowSize = JITTER_WINDOW)

        val noisySignal = generateNoisySignal()
        for (i in noisySignal.indices) {
            val raw = noisySignal[i]
            rawMetric.record(raw)
            val result = smoother.smooth(
                mapOf(BlendShape.JAW_OPEN to raw),
                i * FRAME_INTERVAL_MS,
            )
            smoothedMetric.record(result.getValue(BlendShape.JAW_OPEN))
        }

        assertTrue(
            smoothedMetric.ssj() < rawMetric.ssj(),
            "Smoothed SSJ (${smoothedMetric.ssj()}) should be less than raw SSJ (${rawMetric.ssj()})",
        )
    }

    // -- Prediction shifts output forward --

    @Test
    fun predictionShiftsOutputForward() {
        val noPrediction = createSmoother(predictionFrames = 0)
        val withPrediction = createSmoother(predictionFrames = 2)

        // Feed a rising ramp to both
        for (i in 0 until RAMP_LENGTH) {
            val value = i.toFloat() / RAMP_LENGTH
            noPrediction.smooth(mapOf(BlendShape.JAW_OPEN to value), i * FRAME_INTERVAL_MS)
            withPrediction.smooth(mapOf(BlendShape.JAW_OPEN to value), i * FRAME_INTERVAL_MS)
        }

        // At the end of the ramp, the predicted version should be ahead
        val finalValue = (RAMP_LENGTH - 1).toFloat() / RAMP_LENGTH
        val resultNone = noPrediction.smooth(
            mapOf(BlendShape.JAW_OPEN to finalValue),
            RAMP_LENGTH * FRAME_INTERVAL_MS,
        )
        val resultPred = withPrediction.smooth(
            mapOf(BlendShape.JAW_OPEN to finalValue),
            RAMP_LENGTH * FRAME_INTERVAL_MS,
        )

        assertTrue(
            resultPred.getValue(BlendShape.JAW_OPEN) >= resultNone.getValue(BlendShape.JAW_OPEN),
            "Predicted output should be >= non-predicted during rising ramp",
        )
    }

    // -- Reset clears state --

    @Test
    fun resetClearsState() {
        val smoother = createSmoother()
        // Feed several frames
        for (i in 0..5) {
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.8f), i * FRAME_INTERVAL_MS)
        }

        smoother.reset()

        // After reset, first frame should pass through
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.2f), 1000L)
        assertApprox(0.2f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
    }

    // -- Multiple blend shapes are independent --

    @Test
    fun multipleShapesProcessedIndependently() {
        val smoother = createSmoother()

        for (i in 0..5) {
            smoother.smooth(
                mapOf(
                    BlendShape.JAW_OPEN to 0.8f,
                    BlendShape.EYE_BLINK_LEFT to 0.2f,
                ),
                i * FRAME_INTERVAL_MS,
            )
        }

        val result = smoother.smooth(
            mapOf(
                BlendShape.JAW_OPEN to 0.8f,
                BlendShape.EYE_BLINK_LEFT to 0.2f,
            ),
            6 * FRAME_INTERVAL_MS,
        )

        val jaw = result.getValue(BlendShape.JAW_OPEN)
        val eye = result.getValue(BlendShape.EYE_BLINK_LEFT)
        assertTrue(jaw in 0f..1f, "JAW_OPEN in range: $jaw")
        assertTrue(eye in 0f..1f, "EYE_BLINK_LEFT in range: $eye")
        // Constant signals should converge near their true values
        assertApprox(0.8f, jaw, epsilon = 0.1f)
        assertApprox(0.2f, eye, epsilon = 0.1f)
    }

    // -- Output is clamped --

    @Test
    fun outputIsClampedTo01() {
        val smoother = createSmoother(predictionFrames = 5)
        // Feed a fast rising signal to push prediction above 1.0
        for (i in 0..10) {
            val value = (i.toFloat() / 10f).coerceIn(0f, 1f)
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to value), i * FRAME_INTERVAL_MS)
        }

        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f), 11 * FRAME_INTERVAL_MS)
        val output = result.getValue(BlendShape.JAW_OPEN)
        assertTrue(output in 0f..1f, "Output must be in [0,1], was $output")
    }

    // -- Zero dt --

    @Test
    fun zeroDtDoesNotCrash() {
        val smoother = createSmoother()
        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.5f), 100L)
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.6f), 100L)
        // Same timestamp re-initialises — should return raw value
        assertEquals(0.6f, result.getValue(BlendShape.JAW_OPEN))
    }

    // -- New shape appearing mid-stream --

    @Test
    fun newShapeAppearsAfterFirstFrame() {
        val smoother = createSmoother()
        smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.5f), 0L)
        // Second frame introduces a new shape
        val result = smoother.smooth(
            mapOf(
                BlendShape.JAW_OPEN to 0.5f,
                BlendShape.EYE_BLINK_LEFT to 0.3f,
            ),
            FRAME_INTERVAL_MS,
        )
        // New shape should be initialised with the raw value
        assertApprox(0.3f, result.getValue(BlendShape.EYE_BLINK_LEFT), epsilon = 0.05f)
    }

    // -- Config validation --

    @Test
    fun configRejectsNonPositiveProcessNoise() {
        val error = runCatching {
            io.github.kmpfacelink.model.SmoothingConfig.Kalman(processNoise = 0f)
        }
        assertTrue(error.isFailure)
    }

    @Test
    fun configRejectsNonPositiveMeasurementNoise() {
        val error = runCatching {
            io.github.kmpfacelink.model.SmoothingConfig.Kalman(measurementNoise = -0.1f)
        }
        assertTrue(error.isFailure)
    }

    @Test
    fun configRejectsNegativePredictionFrames() {
        val error = runCatching {
            io.github.kmpfacelink.model.SmoothingConfig.Kalman(predictionFrames = -1)
        }
        assertTrue(error.isFailure)
    }

    // -- Helpers --

    private fun createSmoother(
        processNoise: Float = 0.001f,
        measurementNoise: Float = 0.1f,
        predictionFrames: Int = 0,
    ): KalmanBlendShapeSmoother = KalmanBlendShapeSmoother(
        processNoise = processNoise,
        measurementNoise = measurementNoise,
        predictionFrames = predictionFrames,
    )

    /**
     * Generates a noisy signal: base sine wave with pseudo-random jitter.
     */
    private fun generateNoisySignal(): FloatArray {
        val size = JITTER_WINDOW
        return FloatArray(size) { i ->
            val base = 0.5f + 0.3f * kotlin.math.sin(
                i.toFloat() * 2f * kotlin.math.PI.toFloat() / size,
            )
            val noise = ((i * NOISE_SEED_PRIME) % NOISE_MOD).toFloat() / NOISE_MOD - 0.5f
            (base + noise * NOISE_AMPLITUDE).coerceIn(0f, 1f)
        }
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 33L
        private const val JITTER_WINDOW = 60
        private const val RAMP_LENGTH = 20
        private const val NOISE_SEED_PRIME = 7919
        private const val NOISE_MOD = 1000
        private const val NOISE_AMPLITUDE = 0.15f
    }
}
