package io.github.kmpfacelink.internal

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.util.TransformUtils

/**
 * Extracts and maps raw [FaceLandmarkerResult] data into domain model types.
 *
 * Isolates the MediaPipe → domain-model conversion from [MediaPipeFaceTracker]
 * so that each concern has a single place to change.
 */
internal object FaceLandmarkerResultMapper {

    /**
     * Maps MediaPipe landmarks for the given face index to a list of [FaceLandmark].
     *
     * Returns an empty list when [faceIndex] is out of range.
     */
    fun extractLandmarks(result: FaceLandmarkerResult, faceIndex: Int): List<FaceLandmark> {
        val allLandmarks = result.faceLandmarks()
        if (faceIndex >= allLandmarks.size) return emptyList()
        return allLandmarks[faceIndex].map { landmark ->
            FaceLandmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
                presence = landmark.presence().orElse(0f),
            )
        }
    }

    /**
     * Maps MediaPipe blend shape categories for the given face index to [BlendShapeData].
     *
     * The returned map is a scratch buffer from [BlendShapeMapper] — copy it if you need to
     * hold a reference across the next call.
     */
    fun extractBlendShapes(result: FaceLandmarkerResult, faceIndex: Int): BlendShapeData {
        val categories = result.faceBlendshapes().get()[faceIndex].map { category ->
            category.categoryName() to category.score()
        }
        return BlendShapeMapper.mapFromMediaPipe(categories)
    }

    /**
     * Extracts the head transform matrix for the given face index.
     *
     * Returns a default [HeadTransform] when the result contains no transformation matrices
     * or [faceIndex] is out of range.
     */
    fun extractHeadTransform(result: FaceLandmarkerResult, faceIndex: Int): HeadTransform {
        if (!result.facialTransformationMatrixes().isPresent) return HeadTransform()
        val matrices = result.facialTransformationMatrixes().get()
        if (faceIndex >= matrices.size) return HeadTransform()
        // MediaPipe returns flat column-major float array (16 elements)
        return TransformUtils.fromMatrix(matrices[faceIndex])
    }
}
