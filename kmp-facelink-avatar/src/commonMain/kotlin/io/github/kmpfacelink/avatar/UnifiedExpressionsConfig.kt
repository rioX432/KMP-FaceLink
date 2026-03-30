package io.github.kmpfacelink.avatar

/**
 * Configuration for [UnifiedExpressionsMapper].
 *
 * @property parameterPrefix Prefix prepended to all output parameter keys.
 *   VRCFaceTracking v2 convention uses `"v2/"` (e.g., `"v2/JawOpen"`).
 *   Set to empty string for raw parameter names.
 * @property includeBlendedShapes Whether to include blended (combined) shapes
 *   in addition to base shapes. Blended shapes average left/right counterparts
 *   for simpler avatar setups.
 * @property weightOverrides Per-expression weight multipliers.
 *   Key = expression parameter name (without prefix), Value = weight (default 1.0).
 * @property customMappings Additional mappings merged after defaults.
 *   Key = expression parameter name (without prefix). Overrides defaults with the same key.
 */
public data class UnifiedExpressionsConfig(
    val parameterPrefix: String = DEFAULT_PREFIX,
    val includeBlendedShapes: Boolean = true,
    val weightOverrides: Map<String, Float> = emptyMap(),
    val customMappings: Map<String, ParameterMapping> = emptyMap(),
) {
    public companion object {
        /** Default VRCFaceTracking v2 parameter prefix. */
        public const val DEFAULT_PREFIX: String = "v2/"

        /** No prefix — raw Unified Expression parameter names. */
        public const val NO_PREFIX: String = ""
    }
}
