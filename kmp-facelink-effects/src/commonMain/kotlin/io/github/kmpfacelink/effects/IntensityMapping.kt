package io.github.kmpfacelink.effects

/**
 * Maps a raw value (0.0–1.0) to an effect intensity.
 */
public sealed class IntensityMapping {

    /** Passes the raw value through unchanged. */
    public data object Linear : IntensityMapping()

    /**
     * Outputs 1.0 if raw >= [threshold], otherwise 0.0.
     *
     * @property threshold The step boundary (0.0–1.0)
     */
    public data class Step(val threshold: Float) : IntensityMapping()

    /**
     * Applies a custom curve function to the raw value.
     *
     * @property apply Function that transforms a raw value (0.0–1.0) to intensity (0.0–1.0)
     */
    public data class Curve(val apply: (Float) -> Float) : IntensityMapping()

    internal fun map(value: Float): Float = when (this) {
        is Linear -> value
        is Step -> if (value >= threshold) 1f else 0f
        is Curve -> apply(value).coerceIn(0f, 1f)
    }
}
