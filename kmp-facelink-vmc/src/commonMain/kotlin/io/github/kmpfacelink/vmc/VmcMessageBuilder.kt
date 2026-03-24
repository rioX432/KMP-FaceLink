package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.vmc.osc.OscArgument
import io.github.kmpfacelink.vmc.osc.OscMessage

/**
 * Builds VMC Protocol OSC messages from KMP-FaceLink tracking data.
 *
 * Converts [BlendShapeData] and [HeadTransform] into the OSC message format
 * specified by the VMC Protocol.
 */
internal object VmcMessageBuilder {

    /**
     * Builds `/VMC/Ext/Blend/Val` messages for all blend shapes,
     * followed by a `/VMC/Ext/Blend/Apply` message.
     */
    fun buildBlendShapeMessages(blendShapes: BlendShapeData): List<OscMessage> {
        val messages = ArrayList<OscMessage>(blendShapes.size + 1)
        for ((shape, value) in blendShapes) {
            messages.add(
                OscMessage(
                    address = VmcAddress.BLEND_VAL,
                    arguments = listOf(
                        OscArgument.StringArg(VmcBoneMapping.blendShapeToVmcName(shape)),
                        OscArgument.FloatArg(value),
                    ),
                ),
            )
        }
        messages.add(OscMessage(address = VmcAddress.BLEND_APPLY, arguments = emptyList()))
        return messages
    }

    /**
     * Builds `/VMC/Ext/Blend/Val` messages for pre-mapped expression parameters,
     * followed by a `/VMC/Ext/Blend/Apply` message.
     *
     * Use this when sending VRM expression parameters (e.g. from [VrmParameterMapper])
     * where the keys are already version-specific expression IDs (e.g. "happy", "aa", "blinkLeft").
     * Head rotation keys (yaw/pitch/roll) are excluded since they are sent via bone messages.
     *
     * @param expressions pre-mapped expression ID to value map
     * @param excludeKeys parameter keys to exclude (e.g. head transform keys)
     */
    fun buildExpressionMessages(
        expressions: Map<String, Float>,
        excludeKeys: Set<String> = emptySet(),
    ): List<OscMessage> {
        val filtered = if (excludeKeys.isEmpty()) expressions else expressions - excludeKeys
        val messages = ArrayList<OscMessage>(filtered.size + 1)
        for ((name, value) in filtered) {
            messages.add(
                OscMessage(
                    address = VmcAddress.BLEND_VAL,
                    arguments = listOf(
                        OscArgument.StringArg(name),
                        OscArgument.FloatArg(value),
                    ),
                ),
            )
        }
        messages.add(OscMessage(address = VmcAddress.BLEND_APPLY, arguments = emptyList()))
        return messages
    }

    /**
     * Builds a `/VMC/Ext/Bone/Pos` message for head tracking.
     *
     * VMC uses Unity's coordinate system with quaternion rotation.
     * The head transform's Euler angles are converted to a quaternion (x, y, z, w).
     */
    fun buildHeadBoneMessage(head: HeadTransform): OscMessage {
        val quaternion = eulerToQuaternion(head.pitch, head.yaw, head.roll)
        return OscMessage(
            address = VmcAddress.BONE_POS,
            arguments = listOf(
                OscArgument.StringArg(VmcBoneMapping.HEAD_BONE),
                OscArgument.FloatArg(head.positionX),
                OscArgument.FloatArg(head.positionY),
                OscArgument.FloatArg(head.positionZ),
                OscArgument.FloatArg(quaternion.x),
                OscArgument.FloatArg(quaternion.y),
                OscArgument.FloatArg(quaternion.z),
                OscArgument.FloatArg(quaternion.w),
            ),
        )
    }

    /**
     * Builds a `/VMC/Ext/OK` status message.
     */
    fun buildStatusMessage(loaded: Int, calibrationState: Int = 0, calibrationMode: Int = 0): OscMessage =
        OscMessage(
            address = VmcAddress.OK,
            arguments = listOf(
                OscArgument.IntArg(loaded),
                OscArgument.IntArg(calibrationState),
                OscArgument.IntArg(calibrationMode),
            ),
        )

    /**
     * Builds a `/VMC/Ext/T` timestamp message.
     */
    fun buildTimestampMessage(time: Float): OscMessage =
        OscMessage(
            address = VmcAddress.TIME,
            arguments = listOf(OscArgument.FloatArg(time)),
        )
}
