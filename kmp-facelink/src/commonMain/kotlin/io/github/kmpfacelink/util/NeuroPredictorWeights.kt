package io.github.kmpfacelink.util

import kotlin.math.sqrt

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
 * Creates placeholder weights using Xavier uniform initialization.
 *
 * These are NOT trained weights -- they serve as a structural placeholder so the
 * forward pass can execute. Real weights should come from training on blend shape
 * time-series data. See docs/research/neuro-predictor-smoothing.md for training details.
 *
 * Xavier uniform: U(-limit, limit) where limit = sqrt(6 / (fan_in + fan_out))
 *
 * @param inputSize Lookback window size + 1 (number of input frames).
 * @param hiddenSize Number of hidden units per FC layer (default: 32).
 * @param forecastSize Number of forecast steps (default: 1 for real-time smoothing).
 * @param seed Random seed for reproducible initialization.
 */
internal fun createPlaceholderWeights(
    inputSize: Int,
    hiddenSize: Int = NEURO_HIDDEN_SIZE,
    forecastSize: Int = NEURO_FORECAST_SIZE,
    seed: Long = NEURO_DEFAULT_SEED,
): NeuroPredictorWeights {
    val outputSize = inputSize + forecastSize
    val rng = SimpleRng(seed)

    return NeuroPredictorWeights(
        trendBlock = createBlockWeights(inputSize, hiddenSize, outputSize, rng),
        seasonalityBlock = createBlockWeights(inputSize, hiddenSize, outputSize, rng),
    )
}

private fun createBlockWeights(
    inputSize: Int,
    hiddenSize: Int,
    outputSize: Int,
    rng: SimpleRng,
): BlockWeights {
    val fc1Limit = sqrt(6.0f / (inputSize + hiddenSize))
    val fc2Limit = sqrt(6.0f / (hiddenSize + outputSize))

    return BlockWeights(
        fc1Weight = FloatArray(hiddenSize * inputSize) { rng.nextUniform(-fc1Limit, fc1Limit) },
        fc1Bias = FloatArray(hiddenSize),
        fc2Weight = FloatArray(outputSize * hiddenSize) { rng.nextUniform(-fc2Limit, fc2Limit) },
        fc2Bias = FloatArray(outputSize),
    )
}

/**
 * Simple deterministic PRNG for reproducible weight initialization.
 * Uses a linear congruential generator (same constants as java.util.Random).
 */
internal class SimpleRng(seed: Long) {
    private var state: Long = (seed xor LCG_MULTIPLIER) and LCG_MASK

    fun nextUniform(min: Float, max: Float): Float {
        state = (state * LCG_MULTIPLIER + LCG_INCREMENT) and LCG_MASK
        val fraction = (state ushr LCG_SHIFT).toFloat() / LCG_DIVISOR
        return min + fraction * (max - min)
    }

    private companion object {
        private const val LCG_MULTIPLIER = 0x5DEECE66DL
        private const val LCG_INCREMENT = 0xBL
        private const val LCG_MASK = (1L shl 48) - 1
        private const val LCG_SHIFT = 24
        private const val LCG_DIVISOR = (1 shl 24).toFloat()
    }
}

/** Default hidden layer size for N-euro Predictor blocks. */
internal const val NEURO_HIDDEN_SIZE: Int = 32

/** Default forecast horizon (1 step ahead for real-time smoothing). */
internal const val NEURO_FORECAST_SIZE: Int = 1

/** Default random seed for placeholder weight initialization. */
internal const val NEURO_DEFAULT_SEED: Long = 42L
