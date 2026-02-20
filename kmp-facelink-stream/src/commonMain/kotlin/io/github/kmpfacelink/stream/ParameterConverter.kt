package io.github.kmpfacelink.stream

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.valueOf

/**
 * Converts [FaceTrackingData] into a flat parameter map suitable for streaming.
 *
 * Produces 52 ARKit blend shape parameters (using their camelCase names)
 * plus 3 head rotation angles (FaceAngleX, FaceAngleY, FaceAngleZ).
 */
public object ParameterConverter {

    private const val FACE_ANGLE_X = "FaceAngleX"
    private const val FACE_ANGLE_Y = "FaceAngleY"
    private const val FACE_ANGLE_Z = "FaceAngleZ"

    /**
     * Converts face tracking data to a parameter map.
     *
     * @param data The face tracking data to convert
     * @return Map of parameter ID strings to float values (55 entries total)
     */
    public fun convert(data: FaceTrackingData): Map<String, Float> {
        val params = LinkedHashMap<String, Float>(EXPECTED_PARAM_COUNT)

        for (shape in BlendShape.entries) {
            params[shape.arKitName] = data.blendShapes.valueOf(shape)
        }

        params[FACE_ANGLE_X] = data.headTransform.pitch
        params[FACE_ANGLE_Y] = data.headTransform.yaw
        params[FACE_ANGLE_Z] = data.headTransform.roll

        return params
    }

    private const val EXPECTED_PARAM_COUNT = 55
}
