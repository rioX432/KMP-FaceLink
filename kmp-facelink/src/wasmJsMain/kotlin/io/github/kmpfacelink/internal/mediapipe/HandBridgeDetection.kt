package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny

/**
 * JS bridge functions for per-frame hand detection and result extraction.
 *
 * These functions access properties on the opaque JS result objects returned
 * by MediaPipe's HandLandmarker.detectForVideo().
 */

/**
 * Runs hand detection on the current video frame.
 *
 * @param landmarker The HandLandmarker instance
 * @param video The HTMLVideoElement to detect hands in
 * @param timestampMs Current timestamp in milliseconds
 * @return The HandLandmarkerResult JS object, or null if video is not ready
 */
internal fun detectHandsForVideoJs(
    landmarker: JsHandLandmarker,
    video: JsVideoElement,
    timestampMs: Int,
): JsAny? =
    js("(video.readyState >= 2 ? landmarker.detectForVideo(video, timestampMs) : null)")

/**
 * Returns the number of detected hands in the result.
 */
internal fun getHandCountJs(result: JsHandLandmarkerResult): Int =
    js("(result.landmarks ? result.landmarks.length : 0)")

// -- Handedness accessors --

/**
 * Returns the handedness label ("Left" or "Right") for the given hand.
 */
internal fun getHandednessLabelJs(
    result: JsHandLandmarkerResult,
    handIndex: Int,
): String =
    js("(result.handedness[handIndex][0].categoryName)")

// -- Landmark accessors --

/**
 * Returns the number of landmarks for the given hand.
 */
internal fun getHandLandmarkCountJs(
    result: JsHandLandmarkerResult,
    handIndex: Int,
): Int =
    js("(result.landmarks && result.landmarks[handIndex] ? result.landmarks[handIndex].length : 0)")

/**
 * Returns the x coordinate of the hand landmark at the given index.
 */
internal fun getHandLandmarkXJs(
    result: JsHandLandmarkerResult,
    handIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[handIndex][landmarkIndex].x")

/**
 * Returns the y coordinate of the hand landmark at the given index.
 */
internal fun getHandLandmarkYJs(
    result: JsHandLandmarkerResult,
    handIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[handIndex][landmarkIndex].y")

/**
 * Returns the z coordinate of the hand landmark at the given index.
 */
internal fun getHandLandmarkZJs(
    result: JsHandLandmarkerResult,
    handIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.landmarks[handIndex][landmarkIndex].z")
