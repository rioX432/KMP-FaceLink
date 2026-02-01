package io.github.kmpfacelink.model

/**
 * A single face landmark point with normalized coordinates.
 *
 * Coordinates are normalized to [0.0, 1.0] relative to the input image:
 * - (0, 0) is the top-left corner of the image
 * - (1, 1) is the bottom-right corner of the image
 *
 * On Android (MediaPipe): 478 landmarks per face.
 * On iOS (ARKit): mapped from ARFaceAnchor geometry vertices.
 *
 * @property x Horizontal position, normalized 0.0 (left) to 1.0 (right)
 * @property y Vertical position, normalized 0.0 (top) to 1.0 (bottom)
 * @property z Depth relative to the face center (negative = closer to camera)
 */
public data class FaceLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
)
