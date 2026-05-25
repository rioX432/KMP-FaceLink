package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.actions.ActionEvent
import io.github.kmpfacelink.interact.model.EffectParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strategy for creating [EffectParameter] instances from [ActionEvent]s.
 *
 * Different factories allow the same binding to produce fixed effects
 * or effects that scale with user input (e.g., hold duration).
 */
@Serializable
public sealed class EffectFactory {

    /**
     * Creates an [EffectParameter] based on the given [event].
     */
    public abstract fun create(event: ActionEvent): EffectParameter

    /**
     * Always produces the same effect regardless of the triggering event.
     *
     * @property effect The fixed effect to produce.
     */
    @Serializable
    @SerialName("static")
    public data class Static(val effect: EffectParameter) : EffectFactory() {
        override fun create(event: ActionEvent): EffectParameter = effect
    }

    /**
     * Scales effect intensity proportionally to the action's hold duration.
     *
     * - For [ActionEvent.Held]: intensity = durationMs / maxDurationMs (clamped to 0–1).
     * - For [ActionEvent.Released]: intensity = totalDurationMs / maxDurationMs (clamped to 0–1).
     * - For [ActionEvent.Started]: full intensity (1.0 scale).
     *
     * @property baseEffect The effect template whose intensity is scaled.
     * @property maxDurationMs Duration in ms that represents full intensity.
     */
    @Serializable
    @SerialName("scaled")
    public data class Scaled(
        val baseEffect: EffectParameter,
        val maxDurationMs: Long = MAX_DURATION_DEFAULT,
    ) : EffectFactory() {

        override fun create(event: ActionEvent): EffectParameter {
            val scale = when (event) {
                is ActionEvent.Held ->
                    (event.durationMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
                is ActionEvent.Released ->
                    (event.totalDurationMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)
                is ActionEvent.Started -> 1f
            }
            return baseEffect.withIntensity(baseEffect.intensity * scale)
        }

        internal companion object {
            const val MAX_DURATION_DEFAULT = 5000L
        }
    }
}
