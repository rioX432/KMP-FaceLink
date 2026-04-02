package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.util.BlockWeights
import io.github.kmpfacelink.util.CircularBuffer
import io.github.kmpfacelink.util.NEURO_FORECAST_SIZE
import io.github.kmpfacelink.util.NEURO_HIDDEN_SIZE
import io.github.kmpfacelink.util.NeuroPredictorSmoother
import io.github.kmpfacelink.util.NeuroPredictorWeights
import io.github.kmpfacelink.util.createPlaceholderWeights
import io.github.kmpfacelink.util.matVecMul
import io.github.kmpfacelink.util.matVecMulRelu
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NeuroPredictorSmootherTest {

    private fun assertApprox(expected: Float, actual: Float, epsilon: Float = 0.01f) {
        assertTrue(
            abs(expected - actual) < epsilon,
            "Expected ~$expected but was $actual (diff=${abs(expected - actual)})",
        )
    }

    // --- CircularBuffer tests ---

    @Test
    fun circularBufferReportsFullCorrectly() {
        val buf = CircularBuffer(3)
        assertFalse(buf.isFull())
        buf.add(1f)
        assertFalse(buf.isFull())
        buf.add(2f)
        assertFalse(buf.isFull())
        buf.add(3f)
        assertTrue(buf.isFull())
    }

    @Test
    fun circularBufferCopiesInChronologicalOrder() {
        val buf = CircularBuffer(3)
        buf.add(1f)
        buf.add(2f)
        buf.add(3f)

        val out = FloatArray(3)
        buf.copyTo(out)
        assertApprox(1f, out[0])
        assertApprox(2f, out[1])
        assertApprox(3f, out[2])
    }

    @Test
    fun circularBufferWrapsCorrectly() {
        val buf = CircularBuffer(3)
        buf.add(1f)
        buf.add(2f)
        buf.add(3f)
        buf.add(4f) // Overwrites 1

        val out = FloatArray(3)
        buf.copyTo(out)
        assertApprox(2f, out[0])
        assertApprox(3f, out[1])
        assertApprox(4f, out[2])
    }

    // --- matVecMul tests ---

    @Test
    fun matVecMulComputesCorrectly() {
        // 2x3 matrix [[1,2,3],[4,5,6]] * [1,1,1] + [0,0] = [6, 15]
        val w = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)
        val x = floatArrayOf(1f, 1f, 1f)
        val b = floatArrayOf(0f, 0f)
        val out = FloatArray(2)

        matVecMul(w, x, b, out)
        assertApprox(6f, out[0])
        assertApprox(15f, out[1])
    }

    @Test
    fun matVecMulAddsBias() {
        val w = floatArrayOf(1f, 0f, 0f, 1f)
        val x = floatArrayOf(3f, 5f)
        val b = floatArrayOf(10f, 20f)
        val out = FloatArray(2)

        matVecMul(w, x, b, out)
        assertApprox(13f, out[0])
        assertApprox(25f, out[1])
    }

    @Test
    fun matVecMulReluClampsNegatives() {
        val w = floatArrayOf(1f, -1f) // 1x2 -> produces [-2] before ReLU
        val x = floatArrayOf(1f, 3f)
        val b = floatArrayOf(0f)
        val out = FloatArray(1)

        matVecMulRelu(w, x, b, out)
        assertApprox(0f, out[0]) // ReLU clamps -2 to 0
    }

    @Test
    fun matVecMulReluPassesPositives() {
        val w = floatArrayOf(1f, 1f)
        val x = floatArrayOf(2f, 3f)
        val b = floatArrayOf(1f)
        val out = FloatArray(1)

        matVecMulRelu(w, x, b, out)
        assertApprox(6f, out[0]) // 2+3+1 = 6, positive so passes through
    }

    // --- Placeholder weights tests ---

    @Test
    fun placeholderWeightsHaveCorrectDimensions() {
        val inputSize = 9
        val weights = createPlaceholderWeights(inputSize)
        val outputSize = inputSize + NEURO_FORECAST_SIZE

        // Block 1 (trend)
        assertEquals(NEURO_HIDDEN_SIZE * inputSize, weights.trendBlock.fc1Weight.size)
        assertEquals(NEURO_HIDDEN_SIZE, weights.trendBlock.fc1Bias.size)
        assertEquals(outputSize * NEURO_HIDDEN_SIZE, weights.trendBlock.fc2Weight.size)
        assertEquals(outputSize, weights.trendBlock.fc2Bias.size)

        // Block 2 (seasonality)
        assertEquals(NEURO_HIDDEN_SIZE * inputSize, weights.seasonalityBlock.fc1Weight.size)
        assertEquals(NEURO_HIDDEN_SIZE, weights.seasonalityBlock.fc1Bias.size)
        assertEquals(outputSize * NEURO_HIDDEN_SIZE, weights.seasonalityBlock.fc2Weight.size)
        assertEquals(outputSize, weights.seasonalityBlock.fc2Bias.size)
    }

    @Test
    fun placeholderWeightsAreDeterministic() {
        val w1 = createPlaceholderWeights(9, seed = 123L)
        val w2 = createPlaceholderWeights(9, seed = 123L)
        assertTrue(w1.trendBlock.fc1Weight.contentEquals(w2.trendBlock.fc1Weight))
        assertTrue(w1.seasonalityBlock.fc2Weight.contentEquals(w2.seasonalityBlock.fc2Weight))
    }

    @Test
    fun differentSeedsProduceDifferentWeights() {
        val w1 = createPlaceholderWeights(9, seed = 1L)
        val w2 = createPlaceholderWeights(9, seed = 2L)
        assertFalse(w1.trendBlock.fc1Weight.contentEquals(w2.trendBlock.fc1Weight))
    }

    // --- Forward pass tests ---

    @Test
    fun passesThoughDuringBufferFillUp() {
        val smoother = NeuroPredictorSmoother(windowSize = 3)

        // First 3 frames (buffer needs 4 = windowSize+1 to be full)
        for (i in 0..2) {
            val value = i * 0.1f
            val result = smoother.smooth(
                mapOf(BlendShape.JAW_OPEN to value),
                i * 33L,
            )
            assertApprox(value, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
        }
    }

    @Test
    fun producesOutputAfterBufferFull() {
        val smoother = NeuroPredictorSmoother(windowSize = 3)

        // Fill buffer (needs 4 frames for windowSize=3)
        for (i in 0..3) {
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.5f), i * 33L)
        }

        // 5th frame should go through the network
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.5f), 132L)
        // With placeholder weights, output will differ from input but should be a valid float
        val output = result.getValue(BlendShape.JAW_OPEN)
        assertTrue(output in 0f..1f, "Output should be clamped to [0,1]: $output")
    }

    @Test
    fun outputIsClampedTo01() {
        val smoother = NeuroPredictorSmoother(windowSize = 2)

        // Fill buffer with extreme values
        for (i in 0..2) {
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f), i * 33L)
        }

        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 1.0f), 99L)
        val output = result.getValue(BlendShape.JAW_OPEN)
        assertTrue(output in 0f..1f, "Output must be in [0,1], was $output")
    }

    @Test
    fun resetClearsBuffersAndPassesThrough() {
        val smoother = NeuroPredictorSmoother(windowSize = 2)

        // Fill buffer
        for (i in 0..3) {
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.8f), i * 33L)
        }

        smoother.reset()

        // After reset, should pass through again (buffer empty)
        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to 0.3f), 200L)
        assertApprox(0.3f, result.getValue(BlendShape.JAW_OPEN), epsilon = 0.001f)
    }

    @Test
    fun multipleShapesProcessedIndependently() {
        val smoother = NeuroPredictorSmoother(windowSize = 2)

        // Fill buffers with different values per shape
        for (i in 0..2) {
            smoother.smooth(
                mapOf(
                    BlendShape.JAW_OPEN to 0.8f,
                    BlendShape.EYE_BLINK_LEFT to 0.2f,
                ),
                i * 33L,
            )
        }

        // Both shapes should have full buffers and produce network output
        val result = smoother.smooth(
            mapOf(
                BlendShape.JAW_OPEN to 0.8f,
                BlendShape.EYE_BLINK_LEFT to 0.2f,
            ),
            99L,
        )

        val jaw = result.getValue(BlendShape.JAW_OPEN)
        val eye = result.getValue(BlendShape.EYE_BLINK_LEFT)
        assertTrue(jaw in 0f..1f, "JAW_OPEN in range: $jaw")
        assertTrue(eye in 0f..1f, "EYE_BLINK_LEFT in range: $eye")
    }

    @Test
    fun identityWeightsPassThroughLastValue() {
        // Construct identity-like weights that make forecast = last input value
        val windowSize = 2
        val inputSize = windowSize + 1 // 3
        val outputSize = inputSize + NEURO_FORECAST_SIZE // 4

        // Build weights that make the network approximate identity for forecast:
        // FC1: identity-ish (just pass through first hidden units)
        // FC2: map hidden to output where forecast slot picks up the last input
        val identityBlock = createIdentityBlock(inputSize, outputSize)

        // Use zero weights for the second block so it contributes nothing
        val zeroBlock = BlockWeights(
            fc1Weight = FloatArray(NEURO_HIDDEN_SIZE * inputSize),
            fc1Bias = FloatArray(NEURO_HIDDEN_SIZE),
            fc2Weight = FloatArray(outputSize * NEURO_HIDDEN_SIZE),
            fc2Bias = FloatArray(outputSize),
        )

        val weights = NeuroPredictorWeights(identityBlock, zeroBlock)
        val smoother = NeuroPredictorSmoother(windowSize, weights)

        // Fill buffer with constant value
        val constValue = 0.5f
        for (i in 0..windowSize) {
            smoother.smooth(mapOf(BlendShape.JAW_OPEN to constValue), i * 33L)
        }

        val result = smoother.smooth(mapOf(BlendShape.JAW_OPEN to constValue), 132L)
        val output = result.getValue(BlendShape.JAW_OPEN)
        // With identity-like weights and constant input, output should be near the input
        assertApprox(constValue, output, epsilon = 0.1f)
    }

    /**
     * Creates block weights where FC1 copies the last input element to hidden[0]
     * with a large weight (to survive ReLU for positive inputs), and FC2 routes
     * hidden[0] to the forecast slot.
     */
    private fun createIdentityBlock(inputSize: Int, outputSize: Int): BlockWeights {
        val fc1W = FloatArray(NEURO_HIDDEN_SIZE * inputSize)
        // hidden[0] = x[inputSize-1] (the latest value in the window)
        fc1W[inputSize - 1] = 1f

        val fc2W = FloatArray(outputSize * NEURO_HIDDEN_SIZE)
        // forecast slot = outputSize-1, route from hidden[0]
        // Row (outputSize-1), column 0:
        fc2W[(outputSize - 1) * NEURO_HIDDEN_SIZE] = 1f

        return BlockWeights(
            fc1Weight = fc1W,
            fc1Bias = FloatArray(NEURO_HIDDEN_SIZE),
            fc2Weight = fc2W,
            fc2Bias = FloatArray(outputSize),
        )
    }
}
