package io.github.kmpfacelink.rive

/**
 * Configuration for the default [RiveParameterMapper].
 *
 * @property inputNameOverrides remaps default input names.
 *   Key = default ARKit blend shape name (e.g. "eyeBlinkLeft"),
 *   Value = custom Rive input name to use instead.
 * @property excludedInputs set of default input names to exclude from mapping.
 * @property includeHeadRotation whether to include head rotation inputs (headYaw, headPitch, headRoll).
 */
public data class RiveMapperConfig(
    val inputNameOverrides: Map<String, String> = emptyMap(),
    val excludedInputs: Set<String> = emptySet(),
    val includeHeadRotation: Boolean = true,
)
