package io.github.kmpfacelink.llm

/**
 * Strategy for routing queries between on-device and cloud LLM backends.
 */
public sealed class RoutingStrategy {
    /** Always route to the on-device model. */
    public data object AlwaysOnDevice : RoutingStrategy()

    /** Always route to the cloud model. */
    public data object AlwaysCloud : RoutingStrategy()

    /**
     * Automatically route based on query complexity.
     *
     * Queries with complexity score below [complexityThreshold] are sent to the
     * on-device model; those at or above the threshold are sent to the cloud.
     *
     * @property complexityThreshold Threshold (0.0–1.0) above which queries route to cloud
     */
    public data class Auto(
        val complexityThreshold: Float = DEFAULT_THRESHOLD,
    ) : RoutingStrategy()
}

private const val DEFAULT_THRESHOLD = 0.5f
