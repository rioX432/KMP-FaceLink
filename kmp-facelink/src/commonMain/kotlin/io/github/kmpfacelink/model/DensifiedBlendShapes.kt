// sparse-contract-exempt: this file is the one place allowed to allocate a slot per channel,
// because it is the only one that also carries which of those slots were measured. Every other
// caller flattens by going through `densify`.

package io.github.kmpfacelink.model

/**
 * A blend shape frame flattened to one slot per channel, together with what was actually measured.
 *
 * Flattening is lossy in one specific way: afterwards a slot holding `0f` could be a measurement or
 * a shape the estimator never produced. Four places here did that flattening by hand — the Unity
 * facade, the C bridge, the React Native bridge and the Live Link lowering — and none of them kept
 * the difference. Three wrote the identical six-line loop.
 *
 * This type does not stop the flattening. A fixed-width transport needs it, and Live Link and
 * iFacialMocap define the width from outside this SDK. What it stops is the flattening also
 * throwing the distinction away: a transport that *can* carry presence still can, and one that
 * cannot at least knows what it dropped and can say so.
 *
 * @property values one entry per [BlendShape], indexed by `ordinal`.
 * @property measured the shapes the estimator actually produced. Every other index in [values]
 *   holds [substitute].
 * @property substitute what an unmeasured shape was filled with.
 */
public class DensifiedBlendShapes internal constructor(
    public val values: FloatArray,
    public val measured: Set<BlendShape>,
    public val substitute: Float,
) {
    /** How many slots carry [substitute] rather than a measurement. */
    public val substitutedCount: Int get() = values.size - measured.size

    /** Whether the slot for [shape] came from the estimator. */
    public fun wasMeasured(shape: BlendShape): Boolean = shape in measured
}

/**
 * Flattens this frame to one slot per [BlendShape], filling unmeasured shapes with [substitute].
 *
 * Use this wherever a fixed-width form is required, rather than allocating the array in place. The
 * result keeps [DensifiedBlendShapes.measured], so the caller can still tell the two kinds of
 * [substitute] apart even though the array cannot.
 */
public fun BlendShapeData.densify(substitute: Float = 0f): DensifiedBlendShapes {
    val shapes = BlendShape.entries
    val values = FloatArray(shapes.size) { substitute }
    for ((shape, value) in this) {
        values[shape.ordinal] = value
    }
    return DensifiedBlendShapes(values = values, measured = keys.toSet(), substitute = substitute)
}
