package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData

/**
 * N-euro Predictor smoother for blend shape data.
 *
 * Implements a pruned N-BEATS (Neural Basis Expansion Analysis for Time Series) forward pass
 * that simultaneously reduces jitter and lag. Unlike traditional filters (EMA, OneEuro) that
 * face an inherent jitter-lag tradeoff, this neural approach learns to minimize both.
 *
 * Architecture: 2 sequential blocks (trend + seasonality), each with FC(input->32)->ReLU->FC(32->output).
 * Residual connection between blocks. Total: ~1,200 parameters, sub-1ms inference.
 *
 * **Note**: Ships with placeholder (Xavier-initialized) weights. For production quality,
 * train weights on blend shape time-series data. See docs/research/neuro-predictor-smoothing.md.
 *
 * Reference: Shao et al., "N-euro Predictor: A Neural Network Approach for Smoothing and
 * Predicting Motion Trajectory", Proc. ACM IMWUT 2023.
 *
 * @param windowSize Number of past frames to keep in the lookback buffer (W).
 *        The network input size is windowSize + 1 (current frame included).
 * @param weights Pre-trained or placeholder model weights. If null, creates Xavier-initialized
 *        placeholder weights for the given window size.
 */
internal class NeuroPredictorSmoother(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val weights: NeuroPredictorWeights = createPlaceholderWeights(windowSize + 1),
) : BlendShapeSmoother {

    private val inputSize = windowSize + 1
    private val outputSize = inputSize + NEURO_FORECAST_SIZE

    // Per-shape circular buffer of recent values
    private val buffers = mutableMapOf<BlendShape, CircularBuffer>()

    // Pre-allocated scratch map reused each frame
    private val scratch: HashMap<BlendShape, Float> = BlendShapeMapPool().create()

    // Pre-allocated work arrays to avoid per-frame allocations
    private val inputVec = FloatArray(inputSize)
    private val hidden = FloatArray(NEURO_HIDDEN_SIZE)
    private val blockOutput = FloatArray(outputSize)
    private val residual = FloatArray(inputSize)
    private val forecast = FloatArray(NEURO_FORECAST_SIZE)

    override fun smooth(data: BlendShapeData, timestampMs: Long): BlendShapeData {
        scratch.clear()

        for ((shape, value) in data) {
            val buffer = buffers.getOrPut(shape) { CircularBuffer(inputSize) }
            buffer.add(value)

            if (!buffer.isFull()) {
                // Not enough history yet -- pass through raw value
                scratch[shape] = value
                continue
            }

            buffer.copyTo(inputVec)
            val predicted = forwardPass(inputVec)
            scratch[shape] = predicted.coerceIn(0f, 1f)
        }

        return scratch
    }

    override fun reset() {
        buffers.clear()
    }

    /**
     * Execute the 2-block N-BEATS forward pass.
     *
     * Block 1 (Trend):
     *   h = ReLU(W1 * input + b1)
     *   [backcast1, forecast1] = W2 * h + b2
     *   residual = input - backcast1
     *
     * Block 2 (Seasonality):
     *   h = ReLU(W3 * residual + b3)
     *   [backcast2, forecast2] = W4 * h + b4
     *
     * Output = forecast1[0] + forecast2[0]
     */
    private fun forwardPass(input: FloatArray): Float {
        // Block 1: Trend
        runBlock(weights.trendBlock, input)
        extractForecast()
        var forecastSum = forecast[0]

        // Compute residual: input - backcast1
        computeResidual(input)

        // Block 2: Seasonality (operates on residual)
        runBlock(weights.seasonalityBlock, residual)
        extractForecast()
        forecastSum += forecast[0]

        return forecastSum
    }

    /** FC1 -> ReLU -> FC2, storing intermediate results in [hidden] and [blockOutput]. */
    private fun runBlock(block: BlockWeights, x: FloatArray) {
        // FC1: hidden = ReLU(W1 * x + b1)
        matVecMulRelu(block.fc1Weight, x, block.fc1Bias, hidden)
        // FC2: blockOutput = W2 * hidden + b2
        matVecMul(block.fc2Weight, hidden, block.fc2Bias, blockOutput)
    }

    /** Extract the forecast portion from blockOutput (last NEURO_FORECAST_SIZE elements). */
    private fun extractForecast() {
        for (i in 0 until NEURO_FORECAST_SIZE) {
            forecast[i] = blockOutput[inputSize + i]
        }
    }

    /** Compute residual = input - backcast (first inputSize elements of blockOutput). */
    private fun computeResidual(input: FloatArray) {
        for (i in 0 until inputSize) {
            residual[i] = input[i] - blockOutput[i]
        }
    }

    internal companion object {
        /** Default lookback window: 8 past frames (9 total including current). */
        internal const val DEFAULT_WINDOW_SIZE: Int = 8
    }
}

/**
 * Matrix-vector multiply with bias and ReLU activation: out = max(0, W * x + b).
 *
 * @param w Weight matrix in row-major order, shape [outSize x inSize].
 * @param x Input vector, length inSize.
 * @param b Bias vector, length outSize.
 * @param out Output vector, length outSize (written in-place).
 */
internal fun matVecMulRelu(
    w: FloatArray,
    x: FloatArray,
    b: FloatArray,
    out: FloatArray,
) {
    val inSize = x.size
    for (i in out.indices) {
        var sum = b[i]
        val rowOffset = i * inSize
        for (j in 0 until inSize) {
            sum += w[rowOffset + j] * x[j]
        }
        out[i] = if (sum > 0f) sum else 0f // ReLU
    }
}

/**
 * Matrix-vector multiply with bias (no activation): out = W * x + b.
 *
 * @param w Weight matrix in row-major order, shape [outSize x inSize].
 * @param x Input vector, length inSize.
 * @param b Bias vector, length outSize.
 * @param out Output vector, length outSize (written in-place).
 */
internal fun matVecMul(
    w: FloatArray,
    x: FloatArray,
    b: FloatArray,
    out: FloatArray,
) {
    val inSize = x.size
    for (i in out.indices) {
        var sum = b[i]
        val rowOffset = i * inSize
        for (j in 0 until inSize) {
            sum += w[rowOffset + j] * x[j]
        }
        out[i] = sum
    }
}

/**
 * Fixed-size circular buffer for maintaining a sliding window of float values.
 *
 * @param capacity Maximum number of elements.
 */
internal class CircularBuffer(private val capacity: Int) {
    private val data = FloatArray(capacity)
    private var writeIndex = 0
    private var count = 0

    /** Add a value to the buffer. Overwrites the oldest value when full. */
    fun add(value: Float) {
        data[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    /** Whether the buffer has been filled at least once. */
    fun isFull(): Boolean = count >= capacity

    /**
     * Copy buffer contents to [dest] in chronological order (oldest first).
     * [dest] must have length >= [capacity].
     */
    fun copyTo(dest: FloatArray) {
        val start = if (count < capacity) 0 else writeIndex
        for (i in 0 until capacity) {
            dest[i] = data[(start + i) % capacity]
        }
    }
}
