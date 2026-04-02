package io.github.kmpfacelink.model

/**
 * A map of blend shape values (0.0–1.0) keyed by [BlendShape].
 */
public typealias BlendShapeData = Map<BlendShape, Float>

/** Cached empty [BlendShapeData] — all 52 blend shapes set to 0.0. Allocated once. */
private val EMPTY_BLEND_SHAPE_DATA: BlendShapeData = BlendShape.entries.associateWith { 0f }

/**
 * Returns a cached [BlendShapeData] with all blend shapes set to 0.0.
 *
 * The returned map is shared and immutable — do not attempt to cast or mutate it.
 */
public fun emptyBlendShapeData(): BlendShapeData = EMPTY_BLEND_SHAPE_DATA

/**
 * Returns the value for the given blend shape, defaulting to 0.0 if absent.
 */
public fun BlendShapeData.valueOf(shape: BlendShape): Float = getOrElse(shape) { 0f }

/** All 52 blend shapes as an immutable set. */
public val ALL_BLEND_SHAPES: Set<BlendShape> = BlendShape.entries.toSet()

/**
 * Returns a new [BlendShapeData] containing only the shapes present in [active].
 *
 * When [active] contains all blend shapes, returns `this` unchanged (no allocation).
 */
public fun BlendShapeData.filterByActive(active: Set<BlendShape>): BlendShapeData {
    if (active.size == BlendShape.entries.size) return this
    val result = HashMap<BlendShape, Float>(active.size)
    for (shape in active) {
        result[shape] = this[shape] ?: 0f
    }
    return result
}
