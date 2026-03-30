package io.github.kmpfacelink.vts

/**
 * A single post-processing effect value to set in VTube Studio.
 *
 * Used with [VtsClient.setPostProcessingValues] to control visual effects
 * like bloom, color grading, vignette, etc.
 *
 * Config IDs and their valid values are listed in the VTS API documentation.
 * Effect IDs are case-insensitive and underscores/hyphens are ignored
 * (e.g., "Backlight_Strength" and "backlightstrength" are equivalent).
 *
 * @property configId The effect configuration ID (e.g., "Backlight_Strength")
 * @property configValue The value as a string (e.g., "0.8", "true", "220308FF")
 */
public data class PostProcessingValue(
    val configId: String,
    val configValue: String,
)

/**
 * Information about a post-processing effect in VTube Studio.
 *
 * Returned by [VtsClient.getPostProcessingEffects].
 *
 * @property id Internal effect ID
 * @property enumId Human-readable effect identifier (e.g., "ChromaticAberration")
 * @property explanation Description of the effect
 * @property isActive Whether the effect is currently enabled
 * @property configEntries Available configuration entries for this effect
 */
public data class PostProcessingEffect(
    val id: String,
    val enumId: String,
    val explanation: String,
    val isActive: Boolean,
    val configEntries: List<PostProcessingConfigEntry>,
)

/**
 * A configuration entry for a post-processing effect.
 *
 * @property id Configuration ID used in [PostProcessingValue.configId]
 * @property explanation Description of what this config controls
 * @property type Value type (e.g., "Float", "Bool", "Color")
 * @property currentValue Current value as string
 * @property minValue Minimum value (for numeric types)
 * @property maxValue Maximum value (for numeric types)
 * @property defaultValue Default value
 */
public data class PostProcessingConfigEntry(
    val id: String,
    val explanation: String,
    val type: String,
    val currentValue: String,
    val minValue: String,
    val maxValue: String,
    val defaultValue: String,
)

/**
 * Overview of the post-processing system state in VTube Studio.
 *
 * @property isSupported Whether post-processing is supported (always true on desktop)
 * @property isActive Whether post-processing is currently enabled
 * @property activePreset Name of the currently active preset, or empty
 * @property effects List of available effects with their current configurations
 */
public data class PostProcessingState(
    val isSupported: Boolean,
    val isActive: Boolean,
    val activePreset: String,
    val effects: List<PostProcessingEffect>,
)
