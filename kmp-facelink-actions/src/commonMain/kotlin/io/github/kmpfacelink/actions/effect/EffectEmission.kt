package io.github.kmpfacelink.actions.effect

import io.github.kmpfacelink.interact.model.EffectParameter
import kotlinx.serialization.Serializable

/**
 * An effect output event produced by the [EffectBinding] engine.
 *
 * @property bindingId Identifier of the [EffectBinding] that produced this emission.
 * @property effect The renderer-agnostic effect parameters.
 * @property timestampMs Timestamp in milliseconds when this emission was produced.
 * @property state Current state of the emission within its lifecycle.
 */
@Serializable
public data class EffectEmission(
    val bindingId: String,
    val effect: EffectParameter,
    val timestampMs: Long,
    val state: EmissionState,
)

/**
 * Lifecycle state of an [EffectEmission].
 */
@Serializable
public enum class EmissionState {
    /** The effect has just started. */
    STARTED,

    /** The effect parameters have been updated (e.g., intensity changed during hold). */
    UPDATED,

    /** The effect has ended. */
    ENDED,
}
