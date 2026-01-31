package io.github.kmpfacelink.model

/**
 * A map of blend shape values (0.0–1.0) keyed by [BlendShape].
 */
public typealias BlendShapeData = Map<BlendShape, Float>

/**
 * Creates an empty [BlendShapeData] with all blend shapes set to 0.0.
 */
public fun emptyBlendShapeData(): BlendShapeData =
    BlendShape.entries.associateWith { 0f }

/**
 * Returns the value for the given blend shape, defaulting to 0.0 if absent.
 */
public fun BlendShapeData.valueOf(shape: BlendShape): Float = getOrElse(shape) { 0f }
