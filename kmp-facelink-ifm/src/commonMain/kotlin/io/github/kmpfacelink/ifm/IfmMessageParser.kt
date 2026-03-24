package io.github.kmpfacelink.ifm

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.GazeData
import io.github.kmpfacelink.model.HeadTransform

/**
 * Parses iFacialMocap protocol strings into structured tracking data.
 *
 * Supports both V1 (dash-separated) and V2 (ampersand-separated) formats.
 */
internal object IfmMessageParser {

    private val ifmNameToBlendShape: Map<String, BlendShape> by lazy {
        IfmBlendShapeNames.entries.associate { (bs, name) -> name to bs }
    }

    /**
     * Parses a raw iFacialMocap message string into an [IfmParsedData] result.
     */
    fun parse(message: String): IfmParsedData {
        val blendShapes = mutableMapOf<BlendShape, Float>()
        var headTransform = HeadTransform()
        var gazeData = GazeData()

        val fields = message.split(FIELD_SEPARATOR)
        for (field in fields) {
            when {
                field.startsWith("=head#") -> headTransform = parseHeadTransform(field)
                field.startsWith("rightEye#") -> gazeData = parseRightEye(field, gazeData)
                field.startsWith("leftEye#") -> gazeData = parseLeftEye(field, gazeData)
                field.startsWith("sendDataVersion=") -> { /* metadata, skip */ }
                field.isNotBlank() -> parseBlendShape(field, blendShapes)
            }
        }

        return IfmParsedData(
            blendShapes = blendShapes,
            headTransform = headTransform,
            gazeData = gazeData,
        )
    }

    private fun parseBlendShape(field: String, result: MutableMap<BlendShape, Float>) {
        // V1: "name-value", V2: "name&value"
        val separatorIndex = findNameValueSeparator(field) ?: return
        val name = field.substring(0, separatorIndex)
        val valueStr = field.substring(separatorIndex + 1)
        val blendShape = ifmNameToBlendShape[name] ?: return
        val intValue = valueStr.toIntOrNull() ?: return
        result[blendShape] = intValue.toFloat() / BLEND_SHAPE_SCALE
    }

    private fun findNameValueSeparator(field: String): Int? {
        // Try ampersand first (V2), then dash (V1)
        val ampIdx = field.indexOf(V2_SEPARATOR)
        if (ampIdx > 0) return ampIdx
        val dashIdx = field.indexOf(V1_SEPARATOR)
        if (dashIdx > 0) return dashIdx
        return null
    }

    private fun parseHeadTransform(field: String): HeadTransform {
        val values = field.removePrefix("=head#").split(",")
        if (values.size < HEAD_VALUE_COUNT) return HeadTransform()
        return HeadTransform(
            pitch = values[0].toFloatOrNull() ?: 0f,
            yaw = values[1].toFloatOrNull() ?: 0f,
            roll = values[2].toFloatOrNull() ?: 0f,
            positionX = values[3].toFloatOrNull() ?: 0f,
            positionY = values[4].toFloatOrNull() ?: 0f,
            positionZ = values[5].toFloatOrNull() ?: 0f,
        )
    }

    private fun parseRightEye(field: String, current: GazeData): GazeData {
        val values = field.removePrefix("rightEye#").split(",")
        if (values.size < EYE_VALUE_COUNT) return current
        return current.copy(
            rightEyePitch = values[0].toFloatOrNull() ?: 0f,
            rightEyeYaw = values[1].toFloatOrNull() ?: 0f,
        )
    }

    private fun parseLeftEye(field: String, current: GazeData): GazeData {
        val values = field.removePrefix("leftEye#").split(",")
        if (values.size < EYE_VALUE_COUNT) return current
        return current.copy(
            leftEyePitch = values[0].toFloatOrNull() ?: 0f,
            leftEyeYaw = values[1].toFloatOrNull() ?: 0f,
        )
    }

    private const val FIELD_SEPARATOR = '|'
    private const val V1_SEPARATOR = '-'
    private const val V2_SEPARATOR = '&'
    private const val BLEND_SHAPE_SCALE = 100f
    private const val HEAD_VALUE_COUNT = 6
    private const val EYE_VALUE_COUNT = 3
}

/**
 * Parsed result from an iFacialMocap protocol message.
 *
 * @property blendShapes Parsed blend shape values (0.0-1.0)
 * @property headTransform Parsed head rotation and position
 * @property gazeData Parsed eye rotation data
 */
internal data class IfmParsedData(
    val blendShapes: BlendShapeData,
    val headTransform: HeadTransform,
    val gazeData: GazeData,
)
