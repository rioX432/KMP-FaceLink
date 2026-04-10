package io.github.kmpfacelink.util

/**
 * Weight matrices for a single N-BEATS block (trend or seasonality).
 *
 * Each block consists of two fully connected layers:
 * - FC1: input_size -> hidden_size (with ReLU activation)
 * - FC2: hidden_size -> (backcast_size + forecast_size)
 *
 * @property fc1Weight Weight matrix for the first FC layer, shape [hiddenSize x inputSize].
 * @property fc1Bias Bias vector for the first FC layer, length [hiddenSize].
 * @property fc2Weight Weight matrix for the second FC layer, shape [outputSize x hiddenSize].
 * @property fc2Bias Bias vector for the second FC layer, length [outputSize].
 */
internal data class BlockWeights(
    val fc1Weight: FloatArray,
    val fc1Bias: FloatArray,
    val fc2Weight: FloatArray,
    val fc2Bias: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockWeights) return false
        return fc1Weight.contentEquals(other.fc1Weight) &&
            fc1Bias.contentEquals(other.fc1Bias) &&
            fc2Weight.contentEquals(other.fc2Weight) &&
            fc2Bias.contentEquals(other.fc2Bias)
    }

    override fun hashCode(): Int {
        var result = fc1Weight.contentHashCode()
        result = 31 * result + fc1Bias.contentHashCode()
        result = 31 * result + fc2Weight.contentHashCode()
        result = 31 * result + fc2Bias.contentHashCode()
        return result
    }
}

/**
 * Complete weight set for the N-euro Predictor (2-block N-BEATS architecture).
 *
 * @property trendBlock Weights for Block 1 (trend).
 * @property seasonalityBlock Weights for Block 2 (seasonality).
 */
internal data class NeuroPredictorWeights(
    val trendBlock: BlockWeights,
    val seasonalityBlock: BlockWeights,
)

/**
 * Creates default weights that implement an exponentially-weighted moving average (EWA).
 *
 * Block 1 (trend): FC1 passes each input element through to a dedicated hidden unit
 * (identity mapping, ReLU-safe for blend shape values in [0,1]). FC2 maps the hidden
 * layer to: backcast = input reconstruction, forecast = exponentially-weighted average
 * favoring recent frames (decay factor [DEFAULT_EWA_ALPHA]).
 *
 * Block 2 (seasonality): All zeros. Since Block 1's backcast reconstructs the input,
 * the residual is ~0 and Block 2 contributes nothing. Trained weights would capture
 * periodic patterns here.
 *
 * The net effect is a functional smoother comparable to a basic EMA filter. For
 * production-quality smoothing, train optimized weights using tools/train_neuro_weights.py.
 * See docs/research/neuro-predictor-smoothing.md for details.
 *
 * @param inputSize Lookback window size + 1 (number of input frames).
 * @param hiddenSize Number of hidden units per FC layer (default: 32).
 * @param forecastSize Number of forecast steps (default: 1 for real-time smoothing).
 */
internal fun createDefaultWeights(
    inputSize: Int,
    hiddenSize: Int = NEURO_HIDDEN_SIZE,
    forecastSize: Int = NEURO_FORECAST_SIZE,
): NeuroPredictorWeights {
    val outputSize = inputSize + forecastSize

    return NeuroPredictorWeights(
        trendBlock = createEwaTrendBlock(inputSize, hiddenSize, outputSize),
        seasonalityBlock = createZeroBlock(inputSize, hiddenSize, outputSize),
    )
}

/**
 * Creates trend block weights that implement an exponentially-weighted average.
 *
 * FC1 (inputSize -> hiddenSize): Identity mapping -- hidden[i] = x[i] for i < inputSize.
 * FC2 (hiddenSize -> outputSize):
 *   - Backcast rows [0, inputSize): identity from hidden[i] (reconstructs input).
 *   - Forecast rows [inputSize, outputSize): EWA with [DEFAULT_EWA_ALPHA] decay.
 */
private fun createEwaTrendBlock(
    inputSize: Int,
    hiddenSize: Int,
    outputSize: Int,
): BlockWeights {
    // FC1: identity mapping -- hidden[i] = ReLU(1.0 * x[i]) = x[i] for x[i] >= 0
    val fc1Weight = FloatArray(hiddenSize * inputSize)
    for (i in 0 until minOf(inputSize, hiddenSize)) {
        fc1Weight[i * inputSize + i] = 1f
    }

    // FC2: backcast = identity, forecast = EWA
    val fc2Weight = FloatArray(outputSize * hiddenSize)

    // Backcast rows: identity from hidden
    for (i in 0 until inputSize) {
        fc2Weight[i * hiddenSize + i] = 1f
    }

    // Forecast rows: exponentially-weighted average
    val ewaWeights = computeEwaWeights(inputSize)
    for (f in 0 until (outputSize - inputSize)) {
        val row = inputSize + f
        for (j in 0 until inputSize) {
            fc2Weight[row * hiddenSize + j] = ewaWeights[j]
        }
    }

    return BlockWeights(
        fc1Weight = fc1Weight,
        fc1Bias = FloatArray(hiddenSize),
        fc2Weight = fc2Weight,
        fc2Bias = FloatArray(outputSize),
    )
}

/** Creates a block with all-zero weights (contributes nothing to the output). */
private fun createZeroBlock(
    inputSize: Int,
    hiddenSize: Int,
    outputSize: Int,
): BlockWeights = BlockWeights(
    fc1Weight = FloatArray(hiddenSize * inputSize),
    fc1Bias = FloatArray(hiddenSize),
    fc2Weight = FloatArray(outputSize * hiddenSize),
    fc2Bias = FloatArray(outputSize),
)

/**
 * Computes normalized exponential weights: w[i] = alpha^(n-1-i) / sum.
 * Earlier indices (older frames) get lower weight; the last index (most recent) gets highest.
 */
private fun computeEwaWeights(size: Int): FloatArray {
    val raw = FloatArray(size) { i ->
        var result = 1f
        val exp = size - 1 - i
        repeat(exp) { result *= DEFAULT_EWA_ALPHA }
        result
    }
    val sum = raw.sum()
    return FloatArray(size) { raw[it] / sum }
}

/** Default hidden layer size for N-euro Predictor blocks. */
internal const val NEURO_HIDDEN_SIZE: Int = 32

/** Default forecast horizon (1 step ahead for real-time smoothing). */
internal const val NEURO_FORECAST_SIZE: Int = 1

/** Exponential decay factor for default EWA weights. Higher = more weight on recent frames. */
private const val DEFAULT_EWA_ALPHA: Float = 0.6f
