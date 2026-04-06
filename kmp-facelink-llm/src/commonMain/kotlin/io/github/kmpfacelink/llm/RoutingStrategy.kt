package io.github.kmpfacelink.llm

import io.github.kmpfacelink.model.ThermalState

/**
 * Strategy for routing queries between on-device and cloud LLM backends.
 *
 * Use [AlwaysOnDevice] or [AlwaysCloud] for fixed routing, [Auto] for
 * complexity-based routing, or [Adaptive] for multi-signal routing that
 * combines thermal state, latency, cost budgeting, and query complexity.
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

    /**
     * Adaptive multi-signal routing that combines thermal state, latency tracking,
     * cost budgeting, and query complexity to make routing decisions.
     *
     * Designed for long-running VTuber streaming sessions where device conditions change
     * over time. Routes to cloud when the device is overheating, on-device inference
     * is too slow, or when on-device is preferred but cost budget allows cloud fallback.
     *
     * @property complexityThreshold Threshold (0.0–1.0) above which queries route to cloud
     * @property thermalThreshold Thermal state at or above which on-device is avoided
     * @property latencyTimeoutMs Maximum on-device inference time in ms before fallback to cloud
     * @property dailyCostBudget Maximum daily cloud API cost budget in USD (0 = unlimited)
     * @property costPerCloudRequest Estimated cost per cloud API request in USD
     */
    public data class Adaptive(
        val complexityThreshold: Float = DEFAULT_THRESHOLD,
        val thermalThreshold: ThermalState = ThermalState.SERIOUS,
        val latencyTimeoutMs: Long = DEFAULT_LATENCY_TIMEOUT_MS,
        val dailyCostBudget: Double = DEFAULT_DAILY_COST_BUDGET,
        val costPerCloudRequest: Double = DEFAULT_COST_PER_REQUEST,
    ) : RoutingStrategy()
}

/** Raised from 0.5 to 0.6: Gemma 4 handles more complex queries on-device. */
private const val DEFAULT_THRESHOLD = 0.6f
private const val DEFAULT_LATENCY_TIMEOUT_MS = 5000L
private const val DEFAULT_DAILY_COST_BUDGET = 1.0
private const val DEFAULT_COST_PER_REQUEST = 0.003
