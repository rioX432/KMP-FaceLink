package io.github.kmpfacelink.avatar

/**
 * Configuration for [VrmParameterMapper].
 *
 * @property version VRM specification version. Determines expression naming convention.
 * @property expressionWeightOverrides Custom weight multipliers for specific expressions.
 *   Key = expression ID (version-specific), Value = weight multiplier (default is 1.0).
 *   For example, `mapOf("happy" to 1.5f)` boosts the happy expression by 50%.
 * @property headTransformSensitivity Multiplier for head rotation values. Higher values
 *   make head movement more responsive. Must be positive.
 * @property customMappings Additional expression mappings added after defaults.
 *   Key = expression ID, Value = mapping definition. Overrides defaults with same key.
 */
public data class VrmConfig(
    val version: VrmVersion = VrmVersion.V1_0,
    val expressionWeightOverrides: Map<String, Float> = emptyMap(),
    val headTransformSensitivity: Float = 1f,
    val customMappings: Map<String, ParameterMapping> = emptyMap(),
)
