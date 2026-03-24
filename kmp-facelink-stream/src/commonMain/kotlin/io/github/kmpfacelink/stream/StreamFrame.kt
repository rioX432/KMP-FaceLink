package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedHand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single streaming frame containing tracking data.
 *
 * This is the top-level message sent over WebSocket. Each frame contains
 * the current face and/or hand tracking state with a monotonic timestamp.
 *
 * @property type Message type discriminator. Always "tracking" for data frames.
 * @property timestampMs Frame timestamp in milliseconds (monotonic clock).
 * @property face Face tracking data, or null if face tracking is inactive.
 * @property hands Hand tracking data list, or null if hand tracking is inactive.
 */
@Serializable
public data class StreamFrame(
    @SerialName("type") val type: String = FRAME_TYPE_TRACKING,
    @SerialName("ts") val timestampMs: Long,
    @SerialName("face") val face: FaceFrame? = null,
    @SerialName("hands") val hands: List<HandFrame>? = null,
) {
    public companion object {
        /** Message type for tracking data frames. */
        public const val FRAME_TYPE_TRACKING: String = "tracking"
    }
}

/**
 * Face tracking data within a [StreamFrame].
 *
 * @property blendShapes Map of ARKit blend shape names to values (0.0–1.0).
 * @property head Head pose (euler angles + position).
 */
@Serializable
public data class FaceFrame(
    @SerialName("bs") val blendShapes: Map<String, Float>,
    @SerialName("head") val head: HeadPose,
)

/**
 * Head pose data within a [FaceFrame].
 *
 * @property pitch Rotation around X-axis in degrees.
 * @property yaw Rotation around Y-axis in degrees.
 * @property roll Rotation around Z-axis in degrees.
 * @property x Position along X-axis.
 * @property y Position along Y-axis.
 * @property z Position along Z-axis.
 */
@Serializable
public data class HeadPose(
    @SerialName("p") val pitch: Float,
    @SerialName("y") val yaw: Float,
    @SerialName("r") val roll: Float,
    @SerialName("x") val x: Float = 0f,
    @SerialName("y_pos") val y: Float = 0f,
    @SerialName("z") val z: Float = 0f,
)

/**
 * Single hand tracking data within a [StreamFrame].
 *
 * @property side "left", "right", or "unknown".
 * @property landmarks Flat array of [x, y, z] triples for each of the 21 hand joints.
 *           Total length is 63 (21 joints x 3 coordinates).
 */
@Serializable
public data class HandFrame(
    @SerialName("side") val side: String,
    @SerialName("lm") val landmarks: List<Float>,
)

/**
 * Converts domain tracking data into a [StreamFrame].
 */
public fun buildStreamFrame(
    faceData: FaceTrackingData?,
    handData: HandTrackingData?,
    timestampMs: Long,
): StreamFrame = StreamFrame(
    timestampMs = timestampMs,
    face = faceData?.toFaceFrame(),
    hands = handData?.toHandFrames(),
)

private fun FaceTrackingData.toFaceFrame(): FaceFrame? {
    if (!isTracking) return null
    return FaceFrame(
        blendShapes = blendShapesToMap(blendShapes),
        head = headTransform.toHeadPose(),
    )
}

private fun blendShapesToMap(data: BlendShapeData): Map<String, Float> =
    data.mapKeys { (shape, _) -> shape.arKitName }

private fun HeadTransform.toHeadPose(): HeadPose = HeadPose(
    pitch = pitch,
    yaw = yaw,
    roll = roll,
    x = positionX,
    y = positionY,
    z = positionZ,
)

private fun HandTrackingData.toHandFrames(): List<HandFrame>? {
    if (!isTracking || hands.isEmpty()) return null
    return hands.map { it.toHandFrame() }
}

private fun TrackedHand.toHandFrame(): HandFrame = HandFrame(
    side = handedness.name.lowercase(),
    landmarks = landmarks.flatMap { listOf(it.x, it.y, it.z) },
)

/**
 * Converts a [StreamFrame] back to a [BlendShapeData] map.
 * Useful on the receiving end to reconstruct model objects.
 */
public fun FaceFrame.toBlendShapeData(): BlendShapeData {
    val result = mutableMapOf<BlendShape, Float>()
    for ((name, value) in blendShapes) {
        val shape = BlendShape.fromArKitName(name) ?: continue
        result[shape] = value
    }
    return result
}
