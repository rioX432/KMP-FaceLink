package io.github.kmpfacelink.vmc

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.vmc.osc.OscArgument
import io.github.kmpfacelink.vmc.osc.OscMessage

/**
 * Parses VMC Protocol OSC messages into [VmcReceivedData].
 *
 * Accumulates blend shape values across multiple `/VMC/Ext/Blend/Val` messages
 * and builds a complete [VmcReceivedData] from a frame's worth of messages.
 */
internal object VmcMessageParser {

    /**
     * Parses a list of OSC messages (typically from one packet or bundle)
     * into a [VmcReceivedData] instance.
     */
    fun parse(messages: List<OscMessage>): VmcReceivedData {
        val blendShapes = mutableMapOf<BlendShape, Float>()
        var headPosition: FloatArray? = null
        var headRotation: FloatArray? = null
        var isLoaded = false
        var senderTime: Float? = null

        for (message in messages) {
            when (message.address) {
                VmcAddress.BLEND_VAL -> parseBlendVal(message, blendShapes)
                VmcAddress.BONE_POS -> {
                    val result = parseBonePos(message)
                    if (result != null) {
                        headPosition = result.first
                        headRotation = result.second
                    }
                }
                VmcAddress.OK -> isLoaded = parseOkStatus(message)
                VmcAddress.TIME -> senderTime = parseTime(message)
                else -> { /* Ignore unknown addresses per VMC spec */ }
            }
        }

        return VmcReceivedData(
            blendShapes = blendShapes,
            headPosition = headPosition,
            headRotation = headRotation,
            isLoaded = isLoaded,
            senderTime = senderTime,
        )
    }

    private fun parseBlendVal(message: OscMessage, target: MutableMap<BlendShape, Float>) {
        val args = message.arguments
        if (args.size < BLEND_VAL_ARG_COUNT) return
        val name = (args[0] as? OscArgument.StringArg)?.value ?: return
        val value = (args[1] as? OscArgument.FloatArg)?.value ?: return
        val shape = BlendShape.fromArKitName(name) ?: return
        target[shape] = value
    }

    /**
     * Parses a Bone/Pos message. Only returns data for the "Head" bone.
     * Returns (position, rotation) or null if not a head bone.
     */
    private fun parseBonePos(message: OscMessage): Pair<FloatArray, FloatArray>? {
        val args = message.arguments
        if (args.size < BONE_POS_ARG_COUNT) return null
        val name = (args[0] as? OscArgument.StringArg)?.value ?: return null
        if (name != VmcBoneMapping.HEAD_BONE) return null

        val floats = extractFloatArgs(args, startIndex = 1, count = BONE_FLOAT_COUNT) ?: return null
        val position = floatArrayOf(floats[0], floats[1], floats[2])
        val rotation = floatArrayOf(
            floats[IDX_QX_OFFSET],
            floats[IDX_QY_OFFSET],
            floats[IDX_QZ_OFFSET],
            floats[IDX_QW_OFFSET],
        )
        return position to rotation
    }

    /**
     * Extracts consecutive float arguments from the argument list.
     * Returns null if any argument is not a FloatArg.
     */
    private fun extractFloatArgs(args: List<OscArgument>, startIndex: Int, count: Int): FloatArray? {
        val result = FloatArray(count)
        for (i in 0 until count) {
            result[i] = (args[startIndex + i] as? OscArgument.FloatArg)?.value ?: return null
        }
        return result
    }

    private fun parseOkStatus(message: OscMessage): Boolean {
        val firstArg = message.arguments.firstOrNull() as? OscArgument.IntArg
        return firstArg?.value == 1
    }

    private fun parseTime(message: OscMessage): Float? {
        return (message.arguments.firstOrNull() as? OscArgument.FloatArg)?.value
    }
}

private const val BLEND_VAL_ARG_COUNT = 2
private const val BONE_POS_ARG_COUNT = 8
private const val BONE_FLOAT_COUNT = 7
private const val IDX_QX_OFFSET = 3
private const val IDX_QY_OFFSET = 4
private const val IDX_QZ_OFFSET = 5
private const val IDX_QW_OFFSET = 6
