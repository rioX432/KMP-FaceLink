@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import platform.Vision.VNHumanBodyPose3DObservation
import platform.Vision.VNHumanBodyPose3DObservationJointNameCenterHead
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftAnkle
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftElbow
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftHip
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftKnee
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftShoulder
import platform.Vision.VNHumanBodyPose3DObservationJointNameLeftWrist
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightAnkle
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightElbow
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightHip
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightKnee
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightShoulder
import platform.Vision.VNHumanBodyPose3DObservationJointNameRightWrist
import platform.Vision.VNRecognizedPointKey

/**
 * Maps Apple Vision VNHumanBodyPose3DObservation to KMP-FaceLink model types.
 *
 * Vision 3D provides 17 joints with positions in meters relative to the root
 * joint (center of hips). The position is stored as a simd_float4x4 matrix
 * where the translation (x, y, z) is in the 4th column (indices 12, 13, 14).
 *
 * Since the 3D API does not provide nose/eye/ear joints directly, we map
 * centerHead to NOSE for the closest approximation. The remaining head
 * joints (eyes, ears, mouth) are filled with visibility = 0.
 */
internal object VisionBodyLandmarkMapper3D {

    /**
     * Mapping from Vision 3D joint names to [BodyJoint].
     *
     * Vision 3D has 17 joints; some map directly to MediaPipe joints,
     * others (centerHead, centerShoulder, spine, root) map to their
     * closest MediaPipe equivalents.
     */
    private val jointMapping: List<Pair<VNRecognizedPointKey, BodyJoint>> = listOf(
        VNHumanBodyPose3DObservationJointNameCenterHead to BodyJoint.NOSE,
        VNHumanBodyPose3DObservationJointNameLeftShoulder to BodyJoint.LEFT_SHOULDER,
        VNHumanBodyPose3DObservationJointNameRightShoulder to BodyJoint.RIGHT_SHOULDER,
        VNHumanBodyPose3DObservationJointNameLeftElbow to BodyJoint.LEFT_ELBOW,
        VNHumanBodyPose3DObservationJointNameRightElbow to BodyJoint.RIGHT_ELBOW,
        VNHumanBodyPose3DObservationJointNameLeftWrist to BodyJoint.LEFT_WRIST,
        VNHumanBodyPose3DObservationJointNameRightWrist to BodyJoint.RIGHT_WRIST,
        VNHumanBodyPose3DObservationJointNameLeftHip to BodyJoint.LEFT_HIP,
        VNHumanBodyPose3DObservationJointNameRightHip to BodyJoint.RIGHT_HIP,
        VNHumanBodyPose3DObservationJointNameLeftKnee to BodyJoint.LEFT_KNEE,
        VNHumanBodyPose3DObservationJointNameRightKnee to BodyJoint.RIGHT_KNEE,
        VNHumanBodyPose3DObservationJointNameLeftAnkle to BodyJoint.LEFT_ANKLE,
        VNHumanBodyPose3DObservationJointNameRightAnkle to BodyJoint.RIGHT_ANKLE,
    )

    // Column-major indices for translation in simd_float4x4
    private const val TRANSLATION_X_INDEX = 12
    private const val TRANSLATION_Y_INDEX = 13
    private const val TRANSLATION_Z_INDEX = 14

    /**
     * Extract 3D body landmarks from a Vision body pose 3D observation.
     *
     * Returns all 33 [BodyJoint] entries. Joints that Vision 3D supports are
     * populated with x/y/z in meters relative to root; unsupported joints are
     * filled with (0, 0, 0, visibility=0).
     */
    fun mapLandmarks(observation: VNHumanBodyPose3DObservation): List<BodyLandmarkPoint> {
        val detectedPoints = mutableMapOf<BodyJoint, BodyLandmarkPoint>()

        for ((visionJoint, bodyJoint) in jointMapping) {
            val point = extractPoint(observation, visionJoint, bodyJoint)
            if (point != null) {
                detectedPoints[bodyJoint] = point
            }
        }

        return BodyJoint.entries.map { joint ->
            detectedPoints[joint] ?: BodyLandmarkPoint(
                joint = joint,
                x = 0f,
                y = 0f,
                z = 0f,
                visibility = 0f,
            )
        }
    }

    /**
     * Extract a single 3D point from the observation.
     *
     * The position property is a simd_float4x4 matrix (column-major) where
     * the translation vector (x, y, z) is in the 4th column at indices 12-14.
     */
    private fun extractPoint(
        observation: VNHumanBodyPose3DObservation,
        visionJoint: VNRecognizedPointKey,
        bodyJoint: BodyJoint,
    ): BodyLandmarkPoint? {
        val point = try {
            observation.recognizedPointForJointName(visionJoint, null)
        } catch (_: Exception) {
            // Vision framework may throw for unsupported joints on some devices
            null
        } ?: return null

        // Extract translation from the simd_float4x4 position matrix
        val (px, py, pz) = point.position.useContents {
            val floatPtr = this.ptr.reinterpret<FloatVar>()
            Triple(
                floatPtr[TRANSLATION_X_INDEX],
                floatPtr[TRANSLATION_Y_INDEX],
                floatPtr[TRANSLATION_Z_INDEX],
            )
        }

        return BodyLandmarkPoint(
            joint = bodyJoint,
            x = px,
            y = py,
            z = pz,
            visibility = 1f, // 3D API does not provide per-joint confidence
        )
    }
}
