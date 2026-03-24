package io.github.kmpfacelink.vts

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform

/**
 * Converts KMP-FaceLink tracking data to VTube Studio parameter values.
 *
 * Maps [BlendShapeData] and [HeadTransform] to VTS-compatible [ParameterValue] lists
 * for use with `InjectParameterDataRequest`.
 */
internal object VtsParameterConverter {

    /**
     * Converts full face tracking data to VTS parameter values.
     *
     * @param data Face tracking data with blendshapes and head transform
     * @return List of VTS parameter values ready for injection
     */
    fun convert(data: FaceTrackingData): List<ParameterValue> {
        val params = mutableListOf<ParameterValue>()
        params.addAll(convertBlendShapes(data.blendShapes))
        params.addAll(convertHeadTransform(data.headTransform))
        return params
    }

    /**
     * Converts blendshape data to VTS parameter values.
     *
     * Eye blink values are inverted: ARKit uses 0=open, 1=closed,
     * but VTS EyeOpen uses 0=closed, 1=open.
     *
     * @param blendShapes Source blendshape data
     * @return List of VTS parameter values for blendshapes
     */
    fun convertBlendShapes(blendShapes: BlendShapeData): List<ParameterValue> {
        val vtsParams = mutableMapOf<String, Float>()

        for ((blendShape, value) in blendShapes) {
            val vtsName = VtsParameterNames.mapping[blendShape] ?: continue
            val converted = convertValue(blendShape, value)
            // Use max value when multiple ARKit params map to same VTS param
            val existing = vtsParams[vtsName]
            if (existing == null || kotlin.math.abs(converted) > kotlin.math.abs(existing)) {
                vtsParams[vtsName] = converted
            }
        }

        return vtsParams.map { (name, value) -> ParameterValue(id = name, value = value) }
    }

    /**
     * Converts head transform to VTS parameter values.
     *
     * VTS uses: FaceAngleX = yaw, FaceAngleY = pitch, FaceAngleZ = roll.
     *
     * @param head Head transform data
     * @return List of VTS parameter values for head position/rotation
     */
    fun convertHeadTransform(head: HeadTransform): List<ParameterValue> = listOf(
        ParameterValue(id = VtsHeadParameters.FACE_ANGLE_X, value = head.yaw),
        ParameterValue(id = VtsHeadParameters.FACE_ANGLE_Y, value = head.pitch),
        ParameterValue(id = VtsHeadParameters.FACE_ANGLE_Z, value = head.roll),
        ParameterValue(id = VtsHeadParameters.FACE_POSITION_X, value = head.positionX),
        ParameterValue(id = VtsHeadParameters.FACE_POSITION_Y, value = head.positionY),
        ParameterValue(id = VtsHeadParameters.FACE_POSITION_Z, value = head.positionZ),
    )

    private fun convertValue(blendShape: BlendShape, value: Float): Float =
        when (blendShape) {
            // Invert eye blink: ARKit 1.0=closed -> VTS 0.0=closed
            BlendShape.EYE_BLINK_LEFT, BlendShape.EYE_BLINK_RIGHT -> 1f - value.coerceIn(0f, 1f)
            // Frown maps to negative smile
            BlendShape.MOUTH_FROWN_LEFT, BlendShape.MOUTH_FROWN_RIGHT -> -value.coerceIn(0f, 1f)
            else -> value.coerceIn(0f, 1f)
        }
}
