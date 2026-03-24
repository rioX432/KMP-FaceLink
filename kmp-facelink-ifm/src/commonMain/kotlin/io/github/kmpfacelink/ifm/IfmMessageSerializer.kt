package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform

/**
 * Serializes face tracking data into the iFacialMocap wire format.
 *
 * The output format is a pipe-separated string containing:
 * 1. Blendshape name-value pairs (52 entries, values 0-100)
 * 2. Head pose: `=head#rotX,rotY,rotZ,posX,posY,posZ`
 * 3. Eye rotations: `rightEye#rotX,rotY,rotZ` and `leftEye#rotX,rotY,rotZ`
 *
 * V1 uses `-` between name and value; V2 uses `&`.
 */
internal object IfmMessageSerializer {

    /**
     * Serializes a complete tracking frame into the iFacialMocap string format.
     */
    fun serialize(
        blendShapes: BlendShapeData,
        headTransform: HeadTransform,
        gazeData: GazeData?,
        dataVersion: DataVersion,
    ): String {
        val sb = StringBuilder(INITIAL_CAPACITY)
        appendBlendShapes(sb, blendShapes, dataVersion)
        appendHeadTransform(sb, headTransform)
        appendEyeRotations(sb, gazeData)
        appendDataVersion(sb, dataVersion)
        return sb.toString()
    }

    private fun appendBlendShapes(
        sb: StringBuilder,
        blendShapes: BlendShapeData,
        dataVersion: DataVersion,
    ) {
        val separator = when (dataVersion) {
            DataVersion.V1 -> V1_NAME_VALUE_SEPARATOR
            DataVersion.V2 -> V2_NAME_VALUE_SEPARATOR
        }
        for ((blendShape, ifmName) in IfmBlendShapeNames.entries) {
            val rawValue = blendShapes[blendShape] ?: 0f
            val scaledValue = (rawValue * BLEND_SHAPE_SCALE).toInt().coerceIn(0, BLEND_SHAPE_MAX)
            sb.append(ifmName).append(separator).append(scaledValue).append(FIELD_SEPARATOR)
        }
    }

    private fun appendHeadTransform(sb: StringBuilder, head: HeadTransform) {
        sb.append("=head#")
        appendFloat(sb, head.pitch)
        sb.append(',')
        appendFloat(sb, head.yaw)
        sb.append(',')
        appendFloat(sb, head.roll)
        sb.append(',')
        appendFloat(sb, head.positionX)
        sb.append(',')
        appendFloat(sb, head.positionY)
        sb.append(',')
        appendFloat(sb, head.positionZ)
        sb.append(FIELD_SEPARATOR)
    }

    private fun appendEyeRotations(sb: StringBuilder, gazeData: GazeData?) {
        val gaze = gazeData ?: GazeData()
        sb.append("rightEye#")
        appendFloat(sb, gaze.rightEyePitch)
        sb.append(',')
        appendFloat(sb, gaze.rightEyeYaw)
        sb.append(",0.000000")
        sb.append(FIELD_SEPARATOR)
        sb.append("leftEye#")
        appendFloat(sb, gaze.leftEyePitch)
        sb.append(',')
        appendFloat(sb, gaze.leftEyeYaw)
        sb.append(",0.000000")
        sb.append(FIELD_SEPARATOR)
    }

    private fun appendDataVersion(sb: StringBuilder, dataVersion: DataVersion) {
        if (dataVersion == DataVersion.V2) {
            sb.append("sendDataVersion=v2").append(FIELD_SEPARATOR)
        }
    }

    /**
     * Appends a float in the format used by iFacialMocap: `%3.6f`.
     */
    private fun appendFloat(sb: StringBuilder, value: Float) {
        // Format similar to C's %3.6f — 6 decimal places
        val formatted = formatFloat(value)
        sb.append(formatted)
    }

    private const val FIELD_SEPARATOR = '|'
    private const val V1_NAME_VALUE_SEPARATOR = '-'
    private const val V2_NAME_VALUE_SEPARATOR = '&'
    private const val BLEND_SHAPE_SCALE = 100f
    private const val BLEND_SHAPE_MAX = 100
    private const val INITIAL_CAPACITY = 2048
}

/**
 * Formats a float with 6 decimal places, matching iFacialMocap's `%3.6f` convention.
 * Uses pure Kotlin to avoid platform-specific String.format dependencies.
 */
@Suppress("MagicNumber")
internal fun formatFloat(value: Float): String {
    val negative = value < 0f
    val abs = if (negative) -value else value
    val intPart = abs.toLong()
    val fracPart = ((abs - intPart) * 1_000_000f + 0.5f).toLong().coerceAtMost(999_999L)
    val fracStr = fracPart.toString().padStart(6, '0')
    return if (negative) "-$intPart.$fracStr" else "$intPart.$fracStr"
}
