package io.github.kmpfacelink.interact.model

import io.github.kmpfacelink.model.BlendShape
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Renderer-agnostic effect output triggered by stream interactions.
 *
 * The SDK produces these parameters to describe *what* effect should happen
 * (position, intensity, duration). Actual visual rendering (particles, shaders, etc.)
 * is the consumer app's responsibility.
 *
 * @property durationMs How long the effect should last in milliseconds.
 * @property intensity Effect strength from 0.0 (none) to 1.0 (maximum).
 */
@Serializable
public sealed class EffectParameter {
    public abstract val durationMs: Long
    public abstract val intensity: Float

    /**
     * Throw an object at an anchor point on the avatar.
     *
     * @property anchor Where the thrown object should land.
     * @property objectName Identifier for the thrown object (e.g., "tomato", "heart").
     * @property count Number of objects to throw.
     */
    @Serializable
    @SerialName("throw")
    public data class ThrowEffect(
        override val durationMs: Long,
        override val intensity: Float,
        val anchor: AvatarAnchor,
        val objectName: String,
        val count: Int = 1,
    ) : EffectParameter()

    /**
     * Temporarily override one or more blend shapes on the avatar.
     *
     * @property blendShapes Map of blend shape to target value (0.0–1.0).
     */
    @Serializable
    @SerialName("expression_override")
    public data class ExpressionOverride(
        override val durationMs: Long,
        override val intensity: Float,
        val blendShapes: Map<BlendShape, Float>,
    ) : EffectParameter()

    /**
     * Shake the entire screen or camera.
     *
     * @property amplitude Shake distance multiplier (relative to screen size).
     */
    @Serializable
    @SerialName("screen_shake")
    public data class ScreenShake(
        override val durationMs: Long,
        override val intensity: Float,
        val amplitude: Float,
    ) : EffectParameter()
}
