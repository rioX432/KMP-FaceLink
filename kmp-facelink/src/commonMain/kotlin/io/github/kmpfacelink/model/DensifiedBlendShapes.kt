// sparse-contract-exempt: this file is the one place allowed to allocate a slot per channel,
// because it is the only one that also carries which of those slots held a value. Every other
// caller flattens by going through `densify`.

package io.github.kmpfacelink.model

/**
 * A blend shape frame flattened to one slot per channel, together with which slots held a value.
 *
 * Flattening is lossy in one specific way: afterwards a slot holding `0f` could be a value that was
 * there or a shape that was absent. Four places here did that flattening by hand — the Unity facade,
 * the C bridge, the React Native bridge and the Live Link lowering — and none kept the difference.
 * Three wrote the identical six-line loop.
 *
 * **[present] says nothing about where a value came from.** It says the shape had a value in the
 * map this was built from, and no more. Whether that value came from the estimator, from a
 * landmark-derived solver, or from an interpolated frame depends on which plane the map is:
 *
 * - the estimator plane, straight off the platform tracker
 * - the alternative plane, values a solver proposed
 * - the selected plane, what the SDK chose to emit
 *
 * Those are separate maps by design. Reading origin off a presence bit is the mistake this type is
 * named to avoid: an earlier version called this `measured`, which was false the moment a derived
 * value entered the map.
 *
 * @property values one entry per [BlendShape], indexed by `ordinal`.
 * @property present the shapes that had a value. Every other index in [values] holds [substitute].
 * @property substitute what an absent shape was filled with.
 */
public class DensifiedBlendShapes internal constructor(
    public val values: FloatArray,
    public val present: Set<BlendShape>,
    public val substitute: Float,
) {
    /** How many slots carry [substitute] because the shape was absent. */
    public val substitutedCount: Int get() = values.size - present.size

    /** Whether the slot for [shape] held a value, rather than being filled with [substitute]. */
    public fun isPresent(shape: BlendShape): Boolean = shape in present
}

/**
 * Flattens this frame to one slot per [BlendShape], filling absent shapes with [substitute].
 *
 * Use this wherever a fixed-width form is required, rather than allocating the array in place. The
 * result keeps [DensifiedBlendShapes.present], so a caller can still tell an absent shape from one
 * whose value happens to equal [substitute], even though the array alone cannot.
 */
public fun BlendShapeData.densify(substitute: Float = 0f): DensifiedBlendShapes {
    val values = FloatArray(BlendShape.entries.size) { substitute }
    for ((shape, value) in this) {
        values[shape.ordinal] = value
    }
    return DensifiedBlendShapes(values = values, present = keys.toSet(), substitute = substitute)
}
