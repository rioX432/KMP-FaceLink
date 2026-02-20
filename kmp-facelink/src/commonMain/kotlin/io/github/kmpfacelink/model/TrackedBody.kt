package io.github.kmpfacelink.model

/**
 * Tracking result for a single detected body.
 *
 * @property landmarks The 33 body landmark points (MediaPipe superset).
 *           On iOS, only 17 joints are mapped; the rest have visibility = 0.
 */
public data class TrackedBody(
    val landmarks: List<BodyLandmarkPoint>,
)
