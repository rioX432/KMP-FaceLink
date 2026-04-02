package io.github.kmpfacelink.internal.mediapipe

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.util.TransformUtils

/**
 * Maps MediaPipe JS FaceLandmarkerResult data to KMP domain model types.
 *
 * Analogous to Android's `FaceLandmarkerResultMapper` but operates on
 * JS objects via the bridge accessor functions.
 */
internal object WasmResultMapper {

    private val nameToBlendShape: Map<String, BlendShape> by lazy {
        BlendShape.entries.associateBy { it.arKitName }
    }

    /**
     * Extracts blend shape data for the given face from a JS result.
     */
    fun extractBlendShapes(
        result: JsFaceLandmarkerResult,
        faceIndex: Int,
    ): BlendShapeData {
        val count = getBlendShapeCountJs(result, faceIndex)
        val map = HashMap<BlendShape, Float>(BlendShape.entries.size)

        for (i in 0 until count) {
            val name = getBlendShapeNameJs(result, faceIndex, i)
            if (name == "_neutral") continue
            val shape = nameToBlendShape[name] ?: continue
            val score = getBlendShapeScoreJs(result, faceIndex, i)
            map[shape] = score.coerceIn(0f, 1f)
        }

        // Fill missing shapes with 0.0
        for (shape in BlendShape.entries) {
            if (shape !in map) {
                map[shape] = 0f
            }
        }
        return map
    }

    /**
     * Extracts face landmarks for the given face from a JS result.
     */
    fun extractLandmarks(
        result: JsFaceLandmarkerResult,
        faceIndex: Int,
    ): List<FaceLandmark> {
        val count = getLandmarkCountJs(result, faceIndex)
        if (count == 0) return emptyList()

        return List(count) { i ->
            FaceLandmark(
                x = getLandmarkXJs(result, faceIndex, i),
                y = getLandmarkYJs(result, faceIndex, i),
                z = getLandmarkZJs(result, faceIndex, i),
            )
        }
    }

    /**
     * Extracts head transform from the facial transformation matrix.
     *
     * MediaPipe JS returns matrices in row-major order as `{rows, columns, data}`.
     * We convert to column-major for [TransformUtils.fromMatrix].
     */
    fun extractHeadTransform(
        result: JsFaceLandmarkerResult,
        faceIndex: Int,
    ): HeadTransform {
        if (!hasTransformMatrixJs(result, faceIndex)) return HeadTransform()

        val matrix = FloatArray(MATRIX_SIZE)
        for (row in 0 until MATRIX_DIM) {
            for (col in 0 until MATRIX_DIM) {
                val rowMajorIndex = row * MATRIX_DIM + col
                val colMajorIndex = col * MATRIX_DIM + row
                matrix[colMajorIndex] = getTransformMatrixElementJs(
                    result, faceIndex, rowMajorIndex,
                )
            }
        }
        return TransformUtils.fromMatrix(matrix)
    }
}

private const val MATRIX_DIM = 4
private const val MATRIX_SIZE = 16
