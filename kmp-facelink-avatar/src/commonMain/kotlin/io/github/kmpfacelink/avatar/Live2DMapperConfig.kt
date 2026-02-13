package io.github.kmpfacelink.avatar

/**
 * Configuration for [Live2DParameterMapper].
 *
 * @property blendShapeOverrides replaces default per-blend-shape mappings.
 *   Key = Live2D parameter ID, Value = replacement mapping.
 *   Pass an empty list value (not applicable here — set mapping to null) to suppress a mapping.
 *   Set value to `null` to remove a default mapping entirely.
 * @property customMappings additional derived parameters added after defaults.
 *   These take priority over defaults when keys overlap (last-write-wins).
 */
public data class Live2DMapperConfig(
    val blendShapeOverrides: Map<String, ParameterMapping?> = emptyMap(),
    val customMappings: Map<String, ParameterMapping> = emptyMap(),
)
