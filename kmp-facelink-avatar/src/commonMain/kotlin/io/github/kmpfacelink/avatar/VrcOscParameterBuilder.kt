package io.github.kmpfacelink.avatar

/**
 * Builds VRChat-compatible OSC address paths for avatar parameters.
 *
 * VRChat receives avatar parameters via OSC at `/avatar/parameters/{name}` with
 * a single float argument. This helper converts the output of [UnifiedExpressionsMapper]
 * into the correct address format.
 *
 * ## Usage with VmcSender-style UDP sending
 *
 * ```kotlin
 * val mapper = UnifiedExpressionsMapper(
 *     UnifiedExpressionsConfig(parameterPrefix = UnifiedExpressionsConfig.NO_PREFIX)
 * )
 * val params = mapper.map(trackingData)
 * val oscEntries = VrcOscParameterBuilder.buildOscEntries(params)
 * // oscEntries = [("/avatar/parameters/JawOpen", 0.8f), ("/avatar/parameters/EyeClosedLeft", 0.5f), ...]
 * ```
 *
 * @see UnifiedExpressionsMapper
 */
public object VrcOscParameterBuilder {

    /** VRChat OSC avatar parameter address prefix. */
    public const val AVATAR_PARAMETER_PREFIX: String = "/avatar/parameters/"

    /**
     * Converts a parameter map into a list of VRChat OSC address-value pairs.
     *
     * @param parameters Output from [UnifiedExpressionsMapper.map] (or any parameter map).
     *   Keys are parameter names (with or without prefix), values are floats.
     * @return List of (OSC address, float value) pairs ready for OSC message construction.
     */
    public fun buildOscEntries(
        parameters: Map<String, Float>,
    ): List<Pair<String, Float>> =
        parameters.map { (name, value) ->
            AVATAR_PARAMETER_PREFIX + name to value
        }

    /**
     * Builds the VRChat OSC address path for a single parameter.
     *
     * @param parameterName The parameter name (e.g., "v2/JawOpen" or "JawOpen")
     * @return The full OSC address (e.g., "/avatar/parameters/v2/JawOpen")
     */
    public fun oscAddress(parameterName: String): String =
        AVATAR_PARAMETER_PREFIX + parameterName
}
