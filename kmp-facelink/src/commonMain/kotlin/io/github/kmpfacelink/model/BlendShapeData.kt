package io.github.kmpfacelink.model

/**
 * The blend shape values an estimator produced for one frame, keyed by [BlendShape].
 *
 * A shape is present only when the estimator measured it. A present `0f` means the estimator looked
 * and saw no activation; an absent key means it did not produce that shape at all. Nothing in this
 * package collapses the second case into the first, because a consumer that cannot tell them apart
 * will read a shape the estimator never emits as a confident zero.
 *
 * Where treating an absent shape as a rest value is the right call — driving an avatar parameter,
 * for instance — the consumer makes that choice explicitly at its own boundary, so the decision is
 * visible where it is taken rather than built into this type.
 */
public typealias BlendShapeData = Map<BlendShape, Float>

/** Every blend shape, measured and reported at its rest value. Allocated once. */
private val ALL_SHAPES_AT_REST: BlendShapeData = BlendShape.entries.associateWith { 0f }

/**
 * Returns a frame in which every blend shape was measured and every one of them is at rest.
 *
 * This is a statement about a complete observation, not a stand-in for a missing one. Do not reach
 * for it to describe a frame where nothing was measured — an empty map says that, and says it
 * without claiming 52 measurements that never happened.
 *
 * The returned map is shared and immutable.
 */
public fun allShapesAtRest(): BlendShapeData = ALL_SHAPES_AT_REST

/** All 52 blend shapes as an immutable set. */
public val ALL_BLEND_SHAPES: Set<BlendShape> = BlendShape.entries.toSet()

/**
 * Returns a new [BlendShapeData] restricted to the shapes present in [active].
 *
 * Shapes in [active] that this data does not carry stay absent. Filtering narrows what is reported;
 * it does not invent values for shapes the estimator never produced.
 *
 * When [active] contains all blend shapes, returns `this` unchanged (no allocation).
 */
public fun BlendShapeData.filterByActive(active: Set<BlendShape>): BlendShapeData {
    if (active.size == BlendShape.entries.size) return this
    val result = HashMap<BlendShape, Float>(active.size)
    for (shape in active) {
        val value = this[shape] ?: continue
        result[shape] = value
    }
    return result
}
