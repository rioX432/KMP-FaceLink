package io.github.kmpfacelink.llm

/**
 * Model quantization level.
 *
 * Lower precision reduces model size and memory usage at the cost of accuracy.
 */
public enum class Quantization {
    /** 4-bit integer quantization. Smallest size, lowest accuracy. */
    INT4,

    /** 8-bit integer quantization. Balanced size and accuracy. */
    INT8,

    /** 16-bit floating point. Largest size, highest accuracy. */
    FP16,
}
