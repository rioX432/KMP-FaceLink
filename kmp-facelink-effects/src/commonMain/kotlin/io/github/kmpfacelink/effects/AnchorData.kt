package io.github.kmpfacelink.effects

/**
 * Resolved anchor data for an effect attachment point.
 *
 * @property position Normalized 2D position on the face
 * @property rotationDegrees Rotation in degrees (from head roll or 0 if NONE)
 */
public data class AnchorData(
    val position: Position2D,
    val rotationDegrees: Float = 0f,
)
