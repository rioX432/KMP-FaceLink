package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape

/**
 * Data received from a VMC Protocol sender.
 *
 * Contains the parsed blend shape values, head bone transform,
 * status information, and sender timestamp from a single frame of VMC messages.
 *
 * @property blendShapes Blend shape values keyed by [BlendShape] enum
 * @property headPosition Head bone position (x, y, z) or null if not received
 * @property headRotation Head bone rotation as quaternion (x, y, z, w) or null if not received
 * @property isLoaded Whether the sender reports an avatar is loaded
 * @property senderTime Relative time reported by the sender, or null if not received
 */
public data class VmcReceivedData(
    val blendShapes: Map<BlendShape, Float>,
    val headPosition: FloatArray?,
    val headRotation: FloatArray?,
    val isLoaded: Boolean,
    val senderTime: Float?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VmcReceivedData) return false
        return blendShapes == other.blendShapes &&
            headPosition.contentEquals(other.headPosition) &&
            headRotation.contentEquals(other.headRotation) &&
            isLoaded == other.isLoaded &&
            senderTime == other.senderTime
    }

    override fun hashCode(): Int {
        var result = blendShapes.hashCode()
        result = 31 * result + (headPosition?.contentHashCode() ?: 0)
        result = 31 * result + (headRotation?.contentHashCode() ?: 0)
        result = 31 * result + isLoaded.hashCode()
        result = 31 * result + (senderTime?.hashCode() ?: 0)
        return result
    }

    public companion object {
        /** Empty received data when no VMC messages have been parsed. */
        public val EMPTY: VmcReceivedData = VmcReceivedData(
            blendShapes = emptyMap(),
            headPosition = null,
            headRotation = null,
            isLoaded = false,
            senderTime = null,
        )
    }
}

private fun FloatArray?.contentEquals(other: FloatArray?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return this == other
    return this.contentEquals(other)
}
