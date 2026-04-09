package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny

/**
 * JS bridge functions for per-frame body/pose detection and result extraction.
 *
 * These functions access properties on the opaque JS result objects returned
 * by MediaPipe's PoseLandmarker.detectForVideo().
 */

/**
 * Runs pose detection on the current video frame.
 *
 * @param landmarker The PoseLandmarker instance
 * @param video The HTMLVideoElement to detect bodies in
 * @param timestampMs Current timestamp in milliseconds
 * @return The PoseLandmarkerResult JS object, or null if video is not ready
 */
internal fun detectPoseForVideoJs(
    landmarker: JsPoseLandmarker,
    video: JsVideoElement,
    timestampMs: Int,
): JsAny? =
    js("(video.readyState >= 2 ? landmarker.detectForVideo(video, timestampMs) : null)")

/**
 * Returns the number of detected bodies in the result.
 */
internal fun getBodyCountJs(result: JsPoseLandmarkerResult): Int =
    js("(result.landmarks ? result.landmarks.length : 0)")

// -- Landmark accessors --

/**
 * Returns the number of landmarks for the given body.
 */
internal fun getBodyLandmarkCountJs(
    result: JsPoseLandmarkerResult,
    bodyIndex: Int,
): Int =
    js("(result.landmarks && result.landmarks[bodyIndex] ? result.landmarks[bodyIndex].length : 0)")

/**
 * Returns the x coordinate of the body landmark at the given index.
 */
internal fun getBodyLandmarkXJs(
    result: JsPoseLandmarkerResult,
    bodyIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[bodyIndex][landmarkIndex].x")

/**
 * Returns the y coordinate of the body landmark at the given index.
 */
internal fun getBodyLandmarkYJs(
    result: JsPoseLandmarkerResult,
    bodyIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[bodyIndex][landmarkIndex].y")

/**
 * Returns the z coordinate of the body landmark at the given index.
 */
internal fun getBodyLandmarkZJs(
    result: JsPoseLandmarkerResult,
    bodyIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[bodyIndex][landmarkIndex].z")

/**
 * Returns the visibility of the body landmark at the given index.
 */
internal fun getBodyLandmarkVisibilityJs(
    result: JsPoseLandmarkerResult,
    bodyIndex: Int,
    landmarkIndex: Int,
): Float =
    js("(result.landmarks[bodyIndex][landmarkIndex].visibility || 0)")
