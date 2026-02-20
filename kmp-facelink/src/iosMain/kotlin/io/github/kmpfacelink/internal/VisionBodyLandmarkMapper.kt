@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.BodyJoint
import io.github.kmpfacelink.model.BodyLandmarkPoint
import kotlinx.cinterop.useContents
import platform.Vision.VNHumanBodyPoseObservation
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftAnkle
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftEar
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftElbow
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftEye
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftHip
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftKnee
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftShoulder
import platform.Vision.VNHumanBodyPoseObservationJointNameLeftWrist
import platform.Vision.VNHumanBodyPoseObservationJointNameNose
import platform.Vision.VNHumanBodyPoseObservationJointNameRightAnkle
import platform.Vision.VNHumanBodyPoseObservationJointNameRightEar
import platform.Vision.VNHumanBodyPoseObservationJointNameRightElbow
import platform.Vision.VNHumanBodyPoseObservationJointNameRightEye
import platform.Vision.VNHumanBodyPoseObservationJointNameRightHip
import platform.Vision.VNHumanBodyPoseObservationJointNameRightKnee
import platform.Vision.VNHumanBodyPoseObservationJointNameRightShoulder
import platform.Vision.VNHumanBodyPoseObservationJointNameRightWrist
import platform.Vision.VNRecognizedPointKey

/**
 * Maps Apple Vision VNHumanBodyPoseObservation to KMP-FaceLink model types.
 *
 * Vision provides 19 joints (17 mapped to BodyJoint + neck/root which have no
 * direct BodyJoint equivalent). Unmapped MediaPipe joints are filled with
 * visibility = 0.
 */
internal object VisionBodyLandmarkMapper {

    /**
     * Mapping from Vision joint names to [BodyJoint].
     * Only the 17 joints that have a direct correspondence are included.
     */
    private val jointMapping: List<Pair<VNRecognizedPointKey, BodyJoint>> = listOf(
        VNHumanBodyPoseObservationJointNameNose to BodyJoint.NOSE,
        VNHumanBodyPoseObservationJointNameLeftEye to BodyJoint.LEFT_EYE,
        VNHumanBodyPoseObservationJointNameRightEye to BodyJoint.RIGHT_EYE,
        VNHumanBodyPoseObservationJointNameLeftEar to BodyJoint.LEFT_EAR,
        VNHumanBodyPoseObservationJointNameRightEar to BodyJoint.RIGHT_EAR,
        VNHumanBodyPoseObservationJointNameLeftShoulder to BodyJoint.LEFT_SHOULDER,
        VNHumanBodyPoseObservationJointNameRightShoulder to BodyJoint.RIGHT_SHOULDER,
        VNHumanBodyPoseObservationJointNameLeftElbow to BodyJoint.LEFT_ELBOW,
        VNHumanBodyPoseObservationJointNameRightElbow to BodyJoint.RIGHT_ELBOW,
        VNHumanBodyPoseObservationJointNameLeftWrist to BodyJoint.LEFT_WRIST,
        VNHumanBodyPoseObservationJointNameRightWrist to BodyJoint.RIGHT_WRIST,
        VNHumanBodyPoseObservationJointNameLeftHip to BodyJoint.LEFT_HIP,
        VNHumanBodyPoseObservationJointNameRightHip to BodyJoint.RIGHT_HIP,
        VNHumanBodyPoseObservationJointNameLeftKnee to BodyJoint.LEFT_KNEE,
        VNHumanBodyPoseObservationJointNameRightKnee to BodyJoint.RIGHT_KNEE,
        VNHumanBodyPoseObservationJointNameLeftAnkle to BodyJoint.LEFT_ANKLE,
        VNHumanBodyPoseObservationJointNameRightAnkle to BodyJoint.RIGHT_ANKLE,
    )

    /**
     * Extract body landmarks from a Vision body pose observation.
     *
     * Returns all 33 [BodyJoint] entries. Joints that Vision supports are populated
     * with coordinates (Y-flipped to top-left origin); unsupported joints are filled
     * with (0, 0, 0, visibility=0).
     */
    fun mapLandmarks(observation: VNHumanBodyPoseObservation): List<BodyLandmarkPoint> {
        // First, collect Vision-detected points into a map
        val detectedPoints = mutableMapOf<BodyJoint, BodyLandmarkPoint>()

        for ((visionJoint, bodyJoint) in jointMapping) {
            val point = try {
                observation.recognizedPointForJointName(visionJoint, null)
            } catch (_: Exception) {
                null
            }

            if (point != null && point.confidence > 0f) {
                val (px, py) = point.location.useContents { x.toFloat() to y.toFloat() }
                detectedPoints[bodyJoint] = BodyLandmarkPoint(
                    joint = bodyJoint,
                    x = px,
                    y = 1f - py, // Flip Y: bottom-left → top-left
                    z = 0f,
                    visibility = point.confidence.toFloat(),
                )
            }
        }

        // Build the full 33-point list, filling unmapped joints with zeros
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
}
