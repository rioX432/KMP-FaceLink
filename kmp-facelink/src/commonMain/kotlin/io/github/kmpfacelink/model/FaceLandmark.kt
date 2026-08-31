package io.github.kmpfacelink.model

/**
 * One face landmark in image-normalized coordinates.
 *
 * The x/y coordinates are normalized to [0.0, 1.0] relative to the input image:
 * - (0, 0) is the top-left corner of the image
 * - (1, 1) is the bottom-right corner of the image
 *
 * Everything beyond x and y is optional, because not every estimator produces it. A 2D landmark
 * model has no depth to report, and an estimator that reports depth is not reporting it on the same
 * scale as x and y. Making those fields nullable is what keeps "this estimator does not measure
 * depth" from arriving as a confident depth of zero — a value that reads as a real measurement and
 * that downstream geometry will happily compute with.
 *
 * The current Android MediaPipe adapter supplies 478 landmarks per face. The current iOS ARKit
 * adapter does not populate this type: ARKit's native face-geometry vertices use a different,
 * face-local 3D coordinate contract that is not mapped into this image-normalized model.
 *
 * @property x Horizontal position, normalized 0.0 (left) to 1.0 (right).
 * @property y Vertical position, normalized 0.0 (top) to 1.0 (bottom).
 * @property relativeDepth Adapter-provided relative depth, or null when the estimator does not
 *   measure depth. It is not constrained to [0.0, 1.0] and is not comparable across adapters.
 * @property visibility Likelihood that the landmark is visible and not occluded (0.0–1.0), or null
 *   when the estimator did not supply it.
 * @property presence Likelihood that the landmark exists on the face (0.0–1.0), or null when the
 *   estimator did not supply it.
 */
public data class FaceLandmark(
    val x: Float,
    val y: Float,
    val relativeDepth: Float? = null,
    val visibility: Float? = null,
    val presence: Float? = null,
)
