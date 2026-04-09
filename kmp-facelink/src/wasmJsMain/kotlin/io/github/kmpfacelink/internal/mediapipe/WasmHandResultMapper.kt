package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.TrackedHand

/**
 * Maps MediaPipe JS HandLandmarkerResult data to KMP domain model types.
 *
 * Analogous to [WasmResultMapper] but for hand tracking results.
 */
internal object WasmHandResultMapper {

    private val indexToJoint: Array<HandJoint> by lazy {
        Array(HandJoint.entries.size) { i ->
            HandJoint.entries.first { it.index == i }
        }
    }

    /**
     * Extracts handedness for the given hand from a JS result.
     */
    fun extractHandedness(
        result: JsHandLandmarkerResult,
        handIndex: Int,
    ): Handedness {
        val label = getHandednessLabelJs(result, handIndex)
        return when (label) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> Handedness.UNKNOWN
        }
    }

    /**
     * Extracts hand landmarks for the given hand from a JS result.
     */
    fun extractLandmarks(
        result: JsHandLandmarkerResult,
        handIndex: Int,
    ): List<HandLandmarkPoint> {
        val count = getHandLandmarkCountJs(result, handIndex)
        if (count == 0) return emptyList()

        return List(count) { i ->
            HandLandmarkPoint(
                joint = if (i < indexToJoint.size) indexToJoint[i] else HandJoint.WRIST,
                x = getHandLandmarkXJs(result, handIndex, i),
                y = getHandLandmarkYJs(result, handIndex, i),
                z = getHandLandmarkZJs(result, handIndex, i),
            )
        }
    }

    /**
     * Builds a [TrackedHand] from the JS result for the given hand index.
     */
    fun buildTrackedHand(
        result: JsHandLandmarkerResult,
        handIndex: Int,
    ): TrackedHand = TrackedHand(
        handedness = extractHandedness(result, handIndex),
        landmarks = extractLandmarks(result, handIndex),
    )
}
