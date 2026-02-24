package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData

/**
 * Describes how a blend shape value is transformed into an avatar parameter.
 */
public sealed class ParameterMapping {

    /**
     * Pass-through: output = blend shape value.
     */
    public data class Direct(val blendShape: BlendShape) : ParameterMapping()

    /**
     * Inverted: output = 1.0 - blend shape value.
     * Useful for converting "blink" (closed amount) to "eye open" (open amount).
     */
    public data class Inverted(val blendShape: BlendShape) : ParameterMapping()

    /**
     * Scaled and clamped: output = (blend shape value * [scale] + [offset]).coerceIn([min], [max]).
     */
    public data class Scaled(
        val blendShape: BlendShape,
        val scale: Float,
        val offset: Float = 0f,
        val min: Float = 0f,
        val max: Float = 1f,
    ) : ParameterMapping()

    /**
     * Custom mapping that takes the full [FaceTrackingData] to compute a derived value.
     * Used for combined parameters like eye gaze or mouth form.
     *
     * Note: This is intentionally a regular class (not `data class`) because
     * lambda-based equality/hashCode is not meaningful.
     */
    public class Custom(
        public val compute: (FaceTrackingData) -> Float,
    ) : ParameterMapping()
}
