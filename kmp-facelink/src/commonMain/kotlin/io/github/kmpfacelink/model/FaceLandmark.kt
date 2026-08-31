package io.github.kmpfacelink.model

/**
 * A single face landmark point with image-normalized x/y coordinates and adapter-defined z.
 *
 * The x/y coordinates are normalized to [0.0, 1.0] relative to the input image:
 * - (0, 0) is the top-left corner of the image
 * - (1, 1) is the bottom-right corner of the image
 *
 * The current Android MediaPipe adapter supplies 478 landmarks per face. The current iOS ARKit adapter
 * does not populate this type. ARKit's native face-geometry vertices use a different,
 * face-local 3D coordinate contract and are not automatically mapped into this image-normalized
 * model.
 *
 * @property x Horizontal position, normalized 0.0 (left) to 1.0 (right)
 * @property y Vertical position, normalized 0.0 (top) to 1.0 (bottom)
 * @property z Adapter-provided relative depth. It is not constrained to [0.0, 1.0].
 * @property visibility Likelihood that the landmark is visible (not occluded) in the image
 *           (0.0–1.0). The Android mapper uses the MediaPipe optional value when present and 0.0
 *           when omitted, so 0.0 does not distinguish measured zero from missing metadata.
 *           The current iOS adapter does not emit [FaceLandmark] values.
 * @property presence Likelihood that the landmark exists on the face (0.0–1.0).
 *           The Android mapper uses the MediaPipe optional value when present and 0.0 when
 *           omitted, so 0.0 does not distinguish measured zero from missing metadata.
 *           The current iOS adapter does not emit [FaceLandmark] values.
 */
public data class FaceLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 0f,
    val presence: Float = 0f,
)
