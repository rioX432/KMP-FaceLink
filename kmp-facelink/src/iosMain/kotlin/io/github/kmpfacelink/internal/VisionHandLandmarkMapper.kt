@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.HandJoint
import io.github.kmpfacelink.model.HandLandmarkPoint
import io.github.kmpfacelink.model.Handedness
import kotlinx.cinterop.useContents
import platform.Vision.VNChiralityLeft
import platform.Vision.VNChiralityRight
import platform.Vision.VNHumanHandPoseObservation
import platform.Vision.VNHumanHandPoseObservationJointNameIndexDIP
import platform.Vision.VNHumanHandPoseObservationJointNameIndexMCP
import platform.Vision.VNHumanHandPoseObservationJointNameIndexPIP
import platform.Vision.VNHumanHandPoseObservationJointNameIndexTip
import platform.Vision.VNHumanHandPoseObservationJointNameLittleDIP
import platform.Vision.VNHumanHandPoseObservationJointNameLittleMCP
import platform.Vision.VNHumanHandPoseObservationJointNameLittlePIP
import platform.Vision.VNHumanHandPoseObservationJointNameLittleTip
import platform.Vision.VNHumanHandPoseObservationJointNameMiddleDIP
import platform.Vision.VNHumanHandPoseObservationJointNameMiddleMCP
import platform.Vision.VNHumanHandPoseObservationJointNameMiddlePIP
import platform.Vision.VNHumanHandPoseObservationJointNameMiddleTip
import platform.Vision.VNHumanHandPoseObservationJointNameRingDIP
import platform.Vision.VNHumanHandPoseObservationJointNameRingMCP
import platform.Vision.VNHumanHandPoseObservationJointNameRingPIP
import platform.Vision.VNHumanHandPoseObservationJointNameRingTip
import platform.Vision.VNHumanHandPoseObservationJointNameThumbCMC
import platform.Vision.VNHumanHandPoseObservationJointNameThumbIP
import platform.Vision.VNHumanHandPoseObservationJointNameThumbMP
import platform.Vision.VNHumanHandPoseObservationJointNameThumbTip
import platform.Vision.VNHumanHandPoseObservationJointNameWrist
import platform.Vision.VNRecognizedPointKey

/**
 * Maps Apple Vision VNHumanHandPoseObservation to KMP-FaceLink model types.
 */
internal object VisionHandLandmarkMapper {

    /**
     * Ordered mapping from Vision joint names to [HandJoint].
     * Follows the same order as MediaPipe indices (0–20).
     */
    private val jointMapping: List<Pair<VNRecognizedPointKey, HandJoint>> = listOf(
        VNHumanHandPoseObservationJointNameWrist to HandJoint.WRIST,
        VNHumanHandPoseObservationJointNameThumbCMC to HandJoint.THUMB_CMC,
        VNHumanHandPoseObservationJointNameThumbMP to HandJoint.THUMB_MCP,
        VNHumanHandPoseObservationJointNameThumbIP to HandJoint.THUMB_IP,
        VNHumanHandPoseObservationJointNameThumbTip to HandJoint.THUMB_TIP,
        VNHumanHandPoseObservationJointNameIndexMCP to HandJoint.INDEX_FINGER_MCP,
        VNHumanHandPoseObservationJointNameIndexPIP to HandJoint.INDEX_FINGER_PIP,
        VNHumanHandPoseObservationJointNameIndexDIP to HandJoint.INDEX_FINGER_DIP,
        VNHumanHandPoseObservationJointNameIndexTip to HandJoint.INDEX_FINGER_TIP,
        VNHumanHandPoseObservationJointNameMiddleMCP to HandJoint.MIDDLE_FINGER_MCP,
        VNHumanHandPoseObservationJointNameMiddlePIP to HandJoint.MIDDLE_FINGER_PIP,
        VNHumanHandPoseObservationJointNameMiddleDIP to HandJoint.MIDDLE_FINGER_DIP,
        VNHumanHandPoseObservationJointNameMiddleTip to HandJoint.MIDDLE_FINGER_TIP,
        VNHumanHandPoseObservationJointNameRingMCP to HandJoint.RING_FINGER_MCP,
        VNHumanHandPoseObservationJointNameRingPIP to HandJoint.RING_FINGER_PIP,
        VNHumanHandPoseObservationJointNameRingDIP to HandJoint.RING_FINGER_DIP,
        VNHumanHandPoseObservationJointNameRingTip to HandJoint.RING_FINGER_TIP,
        VNHumanHandPoseObservationJointNameLittleMCP to HandJoint.PINKY_MCP,
        VNHumanHandPoseObservationJointNameLittlePIP to HandJoint.PINKY_PIP,
        VNHumanHandPoseObservationJointNameLittleDIP to HandJoint.PINKY_DIP,
        VNHumanHandPoseObservationJointNameLittleTip to HandJoint.PINKY_TIP,
    )

    /**
     * Extract hand landmarks from a Vision hand pose observation.
     * Vision uses a bottom-left origin coordinate system; this method flips Y
     * to match the top-left origin convention used by KMP-FaceLink.
     *
     * Z is always 0 (Vision 2D doesn't provide depth).
     */
    fun mapLandmarks(observation: VNHumanHandPoseObservation): List<HandLandmarkPoint> {
        val landmarks = mutableListOf<HandLandmarkPoint>()

        for ((visionJoint, handJoint) in jointMapping) {
            val point = try {
                observation.recognizedPointForJointName(visionJoint, null)
            } catch (_: Exception) {
                null
            }

            if (point != null && point.confidence > 0f) {
                val (px, py) = point.location.useContents { x.toFloat() to y.toFloat() }
                landmarks.add(
                    HandLandmarkPoint(
                        joint = handJoint,
                        x = px,
                        y = 1f - py, // Flip Y: bottom-left → top-left
                        z = 0f,
                    ),
                )
            } else {
                // Fill with zero if point not available
                landmarks.add(
                    HandLandmarkPoint(joint = handJoint, x = 0f, y = 0f, z = 0f),
                )
            }
        }

        return landmarks
    }

    /**
     * Map Vision chirality to [Handedness].
     */
    fun mapHandedness(chirality: Long): Handedness = when (chirality) {
        VNChiralityLeft -> Handedness.LEFT
        VNChiralityRight -> Handedness.RIGHT
        else -> Handedness.UNKNOWN
    }
}
