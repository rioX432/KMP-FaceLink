package io.github.kmpfacelink.effects

/**
 * Represents an effect that is currently active (intensity > 0).
 *
 * @property id The effect identifier
 * @property type The category of effect
 * @property intensity Current intensity (0.0–1.0)
 */
public data class ActiveEffect(
    val id: String,
    val type: EffectType,
    val intensity: Float,
)
