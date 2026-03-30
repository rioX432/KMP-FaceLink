package io.github.kmpfacelink.stream.livekit

import io.github.kmpfacelink.stream.FaceFrame
import io.github.kmpfacelink.stream.HandFrame
import io.github.kmpfacelink.stream.StreamFrame
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level message published to a LiveKit data channel.
 *
 * This format is designed for consumption by LiveKit AI agent frameworks
 * that drive avatar embodiment from tracking data. The structure uses
 * flat arrays and quaternions for efficient parsing on the receiver side.
 *
 * @property version Protocol version for forward compatibility.
 * @property type Message type discriminator.
 * @property timestampMs Frame timestamp in milliseconds (monotonic clock).
 * @property source Identity of the tracking data source.
 * @property face Face tracking payload, or null if unavailable.
 * @property hands Hand tracking payloads, or null if unavailable.
 */
@Serializable
public data class LiveKitTrackingMessage(
    @SerialName("v") val version: Int = PROTOCOL_VERSION,
    @SerialName("type") val type: String = MESSAGE_TYPE_TRACKING,
    @SerialName("ts") val timestampMs: Long,
    @SerialName("src") val source: String,
    @SerialName("face") val face: LiveKitFaceData? = null,
    @SerialName("hands") val hands: List<LiveKitHandData>? = null,
) {
    public companion object {
        /** Current protocol version. */
        public const val PROTOCOL_VERSION: Int = 1

        /** Message type for tracking data. */
        public const val MESSAGE_TYPE_TRACKING: String = "tracking"
    }
}

/**
 * Face tracking data in LiveKit-compatible format.
 *
 * Uses ordered float arrays instead of maps for compact serialization.
 * Blend shape ordering follows ARKit convention (alphabetical by ARKit name).
 *
 * @property blendShapes Ordered array of blend shape values (0.0-1.0).
 *           Index mapping is defined by [LiveKitBlendShapeOrder].
 * @property headPose Head orientation as euler angles [pitch, yaw, roll]
 *           followed by position [x, y, z]. Total length: 6.
 */
@Serializable
public data class LiveKitFaceData(
    @SerialName("bs") val blendShapes: List<Float>,
    @SerialName("head") val headPose: List<Float>,
)

/**
 * Hand tracking data in LiveKit-compatible format.
 *
 * @property side Hand side: "left", "right", or "unknown".
 * @property landmarks Flat array of [x, y, z] triples for 21 hand joints.
 *           Total length: 63.
 */
@Serializable
public data class LiveKitHandData(
    @SerialName("side") val side: String,
    @SerialName("lm") val landmarks: List<Float>,
)

/**
 * Defines the ordered blend shape indices for [LiveKitFaceData.blendShapes].
 *
 * The ordering matches ARKit blend shape names sorted alphabetically.
 * Use [orderedNames] to get the name-to-index mapping.
 */
public object LiveKitBlendShapeOrder {
    /**
     * ARKit blend shape names in the canonical order used by
     * [LiveKitFaceData.blendShapes] array indices.
     */
    public val orderedNames: List<String> = listOf(
        "browDownLeft", "browDownRight", "browInnerUp",
        "browOuterUpLeft", "browOuterUpRight",
        "cheekPuff", "cheekSquintLeft", "cheekSquintRight",
        "eyeBlinkLeft", "eyeBlinkRight",
        "eyeLookDownLeft", "eyeLookDownRight",
        "eyeLookInLeft", "eyeLookInRight",
        "eyeLookOutLeft", "eyeLookOutRight",
        "eyeLookUpLeft", "eyeLookUpRight",
        "eyeSquintLeft", "eyeSquintRight",
        "eyeWideLeft", "eyeWideRight",
        "jawForward", "jawLeft", "jawOpen", "jawRight",
        "mouthClose", "mouthDimpleLeft", "mouthDimpleRight",
        "mouthFrownLeft", "mouthFrownRight",
        "mouthFunnel", "mouthLeft",
        "mouthLowerDownLeft", "mouthLowerDownRight",
        "mouthPressLeft", "mouthPressRight",
        "mouthPucker", "mouthRight",
        "mouthRollLower", "mouthRollUpper",
        "mouthShrugLower", "mouthShrugUpper",
        "mouthSmileLeft", "mouthSmileRight",
        "mouthStretchLeft", "mouthStretchRight",
        "mouthUpperUpLeft", "mouthUpperUpRight",
        "noseSneerLeft", "noseSneerRight",
        "tongueOut",
    )

    /** Total number of blend shapes. */
    public const val COUNT: Int = 52

    private val nameToIndex: Map<String, Int> = orderedNames.withIndex()
        .associate { (index, name) -> name to index }

    /** Returns the array index for a given ARKit blend shape name, or -1 if unknown. */
    public fun indexOf(name: String): Int = nameToIndex[name] ?: -1
}

/**
 * Converts a [StreamFrame] to a [LiveKitTrackingMessage].
 *
 * @param source Participant identity for the message source field.
 */
public fun StreamFrame.toLiveKitMessage(source: String): LiveKitTrackingMessage =
    LiveKitTrackingMessage(
        timestampMs = timestampMs,
        source = source,
        face = face?.toLiveKitFaceData(),
        hands = hands?.map { it.toLiveKitHandData() },
    )

private fun FaceFrame.toLiveKitFaceData(): LiveKitFaceData {
    val orderedValues = FloatArray(LiveKitBlendShapeOrder.COUNT)
    for ((name, value) in blendShapes) {
        val index = LiveKitBlendShapeOrder.indexOf(name)
        if (index >= 0) orderedValues[index] = value
    }
    return LiveKitFaceData(
        blendShapes = orderedValues.toList(),
        headPose = listOf(head.pitch, head.yaw, head.roll, head.x, head.y, head.z),
    )
}

private fun HandFrame.toLiveKitHandData(): LiveKitHandData = LiveKitHandData(
    side = side,
    landmarks = landmarks,
)
