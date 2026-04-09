package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import io.github.kmpfacelink.model.TrackedBody

/**
 * Maps MediaPipe JS PoseLandmarkerResult data to KMP domain model types.
 *
 * Analogous to [WasmResultMapper] but for body/pose tracking results.
 */
internal object WasmBodyResultMapper {

    private val indexToJoint: Array<BodyJoint> by lazy {
        Array(BodyJoint.entries.size) { i ->
            BodyJoint.entries.first { it.index == i }
        }
    }

    /**
     * Extracts body landmarks for the given body from a JS result.
     */
    fun extractLandmarks(
        result: JsPoseLandmarkerResult,
        bodyIndex: Int,
    ): List<BodyLandmarkPoint> {
        val count = getBodyLandmarkCountJs(result, bodyIndex)
        if (count == 0) return emptyList()

        return List(count) { i ->
            BodyLandmarkPoint(
                joint = if (i < indexToJoint.size) indexToJoint[i] else BodyJoint.NOSE,
                x = getBodyLandmarkXJs(result, bodyIndex, i),
                y = getBodyLandmarkYJs(result, bodyIndex, i),
                z = getBodyLandmarkZJs(result, bodyIndex, i),
                visibility = getBodyLandmarkVisibilityJs(result, bodyIndex, i),
            )
        }
    }

    /**
     * Builds a [TrackedBody] from the JS result for the given body index.
     */
    fun buildTrackedBody(
        result: JsPoseLandmarkerResult,
        bodyIndex: Int,
    ): TrackedBody = TrackedBody(
        landmarks = extractLandmarks(result, bodyIndex),
    )
}
