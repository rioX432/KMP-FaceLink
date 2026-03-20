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
