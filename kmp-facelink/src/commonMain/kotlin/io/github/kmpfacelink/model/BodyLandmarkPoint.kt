package io.github.kmpfacelink.model

/**
 * A single body landmark point with normalized coordinates.
 *
 * Coordinates are normalized to [0.0, 1.0] relative to the input image:
 * - (0, 0) is the top-left corner of the image
 * - (1, 1) is the bottom-right corner of the image
 *
 * @property joint The body joint this point represents
 * @property x Horizontal position, normalized 0.0 (left) to 1.0 (right)
 * @property y Vertical position, normalized 0.0 (top) to 1.0 (bottom)
 * @property z Depth relative to the hip midpoint (negative = closer to camera).
 *           Always 0 on iOS (Vision framework does not provide depth).
 * @property visibility Landmark visibility (0.0-1.0). 0 means the joint is not
 *           detected or not supported on the current platform.
 */
public data class BodyLandmarkPoint(
    val joint: BodyJoint,
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 0f,
)
