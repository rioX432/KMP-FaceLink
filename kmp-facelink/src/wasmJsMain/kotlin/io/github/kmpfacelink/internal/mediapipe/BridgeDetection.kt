package io.github.kmpfacelink.internal.mediapipe

import kotlin.js.JsAny

/**
 * JS bridge functions for per-frame face detection and result extraction.
 *
 * These functions access properties on the opaque JS result objects returned
 * by MediaPipe's FaceLandmarker.detectForVideo().
 */

/**
 * Runs face detection on the current video frame.
 *
 * @param landmarker The FaceLandmarker instance
 * @param video The HTMLVideoElement to detect faces in
 * @param timestampMs Current timestamp in milliseconds
 * @return The FaceLandmarkerResult JS object, or null if video is not ready
 */
internal fun detectForVideoJs(
    landmarker: JsFaceLandmarker,
    video: JsVideoElement,
    timestampMs: Int,
): JsAny? =
    js("(video.readyState >= 2 ? landmarker.detectForVideo(video, timestampMs) : null)")

/**
 * Returns the number of detected faces in the result.
 */
internal fun getResultFaceCountJs(result: JsFaceLandmarkerResult): Int =
    js("(result.faceLandmarks ? result.faceLandmarks.length : 0)")

// -- Blend shape accessors --

/**
 * Returns the number of blend shape categories for the given face.
 */
internal fun getBlendShapeCountJs(result: JsFaceLandmarkerResult, faceIndex: Int): Int =
    js("(result.faceBlendshapes && result.faceBlendshapes[faceIndex] ? result.faceBlendshapes[faceIndex].length : 0)")

/**
 * Returns the category name of the blend shape at the given index.
 */
internal fun getBlendShapeNameJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    shapeIndex: Int,
): String =
    js("result.faceBlendshapes[faceIndex][shapeIndex].categoryName")

/**
 * Returns the score of the blend shape at the given index.
 */
internal fun getBlendShapeScoreJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    shapeIndex: Int,
): Float =
    js("result.faceBlendshapes[faceIndex][shapeIndex].score")

// -- Landmark accessors --

/**
 * Returns the number of landmarks for the given face.
 */
internal fun getLandmarkCountJs(result: JsFaceLandmarkerResult, faceIndex: Int): Int =
    js("(result.faceLandmarks && result.faceLandmarks[faceIndex] ? result.faceLandmarks[faceIndex].length : 0)")

/**
 * Returns the x coordinate of the landmark at the given index.
 */
internal fun getLandmarkXJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.faceLandmarks[faceIndex][landmarkIndex].x")

/**
 * Returns the y coordinate of the landmark at the given index.
 */
internal fun getLandmarkYJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.faceLandmarks[faceIndex][landmarkIndex].y")

/**
 * Returns the z coordinate of the landmark at the given index.
 */
internal fun getLandmarkZJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    landmarkIndex: Int,
): Float =
    js("result.faceLandmarks[faceIndex][landmarkIndex].z")

// -- Transformation matrix accessors --

/**
 * Returns whether a transformation matrix exists for the given face.
 */
internal fun hasTransformMatrixJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
): Boolean = js(
    "(result.facialTransformationMatrixes && result.facialTransformationMatrixes[faceIndex] != null)"
)

/**
 * Returns a single element from the 4x4 transformation matrix.
 *
 * MediaPipe JS returns the matrix as a `{rows: number, columns: number, data: number[]}` object
 * where data is in row-major order.
 */
internal fun getTransformMatrixElementJs(
    result: JsFaceLandmarkerResult,
    faceIndex: Int,
    elementIndex: Int,
): Float =
    js("result.facialTransformationMatrixes[faceIndex].data[elementIndex]")

// -- Video dimension accessors --

/**
 * Returns the video width in pixels.
 */
internal fun getVideoWidthJs(video: JsVideoElement): Int =
    js("video.videoWidth")

/**
 * Returns the video height in pixels.
 */
internal fun getVideoHeightJs(video: JsVideoElement): Int =
    js("video.videoHeight")
