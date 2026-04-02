package io.github.kmpfacelink.internal.onnx

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.util.FaceLinkLogger
import io.github.kmpfacelink.util.GazeEstimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

private const val TAG = "TrackingLoop"
private const val NUM_LANDMARKS = 66
private const val FRAME_DELAY_MS = 33L // ~30 FPS target

/**
 * Main tracking loop for the OnnxFaceTracker.
 *
 * Captures frames, runs detection + landmark inference, and emits FaceTrackingData.
 */
internal suspend fun OnnxFaceTracker.runTrackingLoop() {
    val cam = getCameraManager() ?: return
    val det = getDetector() ?: return
    val lm = getLandmarkInference() ?: return
    val frameW = cam.frameWidth
    val frameH = cam.frameHeight

    while (coroutineContext.isActive) {
        try {
            val frame = cam.grab() ?: continue
            val image = ImagePreprocessor.frameToBufferedImage(frame) ?: continue

            val detectionInput = ImagePreprocessor.preprocessForDetection(image, DETECTION_SIZE)
            val faces = det.detect(detectionInput, frameW, frameH)

            val trackingData = if (faces.isNotEmpty()) {
                processDetectedFace(faces.first(), image, lm, frameW, frameH)
            } else {
                FaceTrackingData.notTracking(System.currentTimeMillis())
            }
            emitTrackingData(trackingData)
        } catch (expected: Exception) {
            if (expected is kotlinx.coroutines.CancellationException) throw expected
            FaceLinkLogger.w(TAG, "Frame processing error: ${expected.message}")
        }
        delay(FRAME_DELAY_MS)
    }
}

private fun processDetectedFace(
    face: FaceBox,
    image: java.awt.image.BufferedImage,
    lm: LandmarkInference,
    frameW: Int,
    frameH: Int,
): FaceTrackingData {
    val lmInput = ImagePreprocessor.preprocessForLandmark(image, face, LANDMARK_SIZE)
    val result = lm.infer(lmInput, frameW, frameH)
        ?: return FaceTrackingData.notTracking(System.currentTimeMillis())

    val blendShapes = LandmarkToBlendShapeMapper.map(result.landmarks)
    val headTransform = HeadPoseEstimator.estimate(result.landmarks)
    val gazeData = GazeEstimator.estimate(blendShapes)
    val faceLandmarks = toLandmarkList(result)

    val trackedFace = TrackedFace(
        faceIndex = 0,
        blendShapes = blendShapes,
        headTransform = headTransform,
        gazeData = gazeData,
        landmarks = faceLandmarks,
        confidence = face.confidence,
    )

    return FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = headTransform,
        gazeData = gazeData,
        landmarks = faceLandmarks,
        sourceImageWidth = frameW,
        sourceImageHeight = frameH,
        timestampMs = System.currentTimeMillis(),
        isTracking = true,
        faces = listOf(trackedFace),
    )
}

private fun toLandmarkList(result: LandmarkResult): List<FaceLandmark> {
    return List(NUM_LANDMARKS) { i ->
        FaceLandmark(
            x = result.landmarks[i * 2],
            y = result.landmarks[i * 2 + 1],
            visibility = result.confidences[i],
            presence = result.confidences[i],
        )
    }
}

private const val DETECTION_SIZE = 224
private const val LANDMARK_SIZE = 224
