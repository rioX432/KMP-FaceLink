package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.interact.model.EffectParameter

/**
 * Returns a copy of this [EffectParameter] with the given [intensity].
 */
public fun EffectParameter.withIntensity(intensity: Float): EffectParameter {
    return when (this) {
        is EffectParameter.ThrowEffect -> copy(intensity = intensity)
        is EffectParameter.ExpressionOverride -> copy(intensity = intensity)
        is EffectParameter.ScreenShake -> copy(intensity = intensity)
    }
}
