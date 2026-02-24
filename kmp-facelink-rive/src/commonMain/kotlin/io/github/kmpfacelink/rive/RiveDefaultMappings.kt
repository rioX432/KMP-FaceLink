package io.github.kmpfacelink.rive

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.valueOf

/**
 * Default ARKit blend shape → Rive input mappings.
 *
 * All 52 ARKit blend shapes are mapped as direct [RiveInput.Number] pass-through (0.0–1.0).
 * Head rotation is mapped as normalized Number inputs (headYaw, headPitch, headRoll).
 *
 * Use [RiveMapperConfig] to rename, exclude, or extend inputs.
 */
@ExperimentalFaceLinkApi
public object RiveDefaultMappings {

    private const val HEAD_ROTATION_NORMALIZE = 30f

    /**
     * Creates a [RiveParameterMapper] with default ARKit pass-through mappings.
     *
     * @param config optional configuration for input name overrides and exclusions
     */
    public fun createMapper(config: RiveMapperConfig = RiveMapperConfig()): RiveParameterMapper =
        RiveParameterMapper { data -> mapData(data, config) }

    internal fun mapData(
        data: FaceTrackingData,
        config: RiveMapperConfig,
    ): Map<String, RiveInput> {
        if (!data.isTracking) return emptyMap()

        val result = LinkedHashMap<String, RiveInput>(BlendShape.entries.size + HEAD_INPUT_COUNT)

        // Map all 52 ARKit blend shapes as Number inputs
        for (shape in BlendShape.entries) {
            val defaultName = shape.arKitName
            if (defaultName in config.excludedInputs) continue
            val inputName = config.inputNameOverrides[defaultName] ?: defaultName
            result[inputName] = RiveInput.Number(data.blendShapes.valueOf(shape))
        }

        // Map head rotation as normalized Number inputs
        if (config.includeHeadRotation) {
            addHeadRotation(data, config, result)
        }

        return result
    }

    private fun addHeadRotation(
        data: FaceTrackingData,
        config: RiveMapperConfig,
        result: MutableMap<String, RiveInput>,
    ) {
        val head = data.headTransform
        putIfNotExcluded(result, config, HEAD_YAW, head.yaw / HEAD_ROTATION_NORMALIZE)
        putIfNotExcluded(result, config, HEAD_PITCH, head.pitch / HEAD_ROTATION_NORMALIZE)
        putIfNotExcluded(result, config, HEAD_ROLL, head.roll / HEAD_ROTATION_NORMALIZE)
    }

    private fun putIfNotExcluded(
        result: MutableMap<String, RiveInput>,
        config: RiveMapperConfig,
        defaultName: String,
        value: Float,
    ) {
        if (defaultName in config.excludedInputs) return
        val inputName = config.inputNameOverrides[defaultName] ?: defaultName
        result[inputName] = RiveInput.Number(value.coerceIn(-1f, 1f))
    }

    internal const val HEAD_YAW = "headYaw"
    internal const val HEAD_PITCH = "headPitch"
    internal const val HEAD_ROLL = "headRoll"
    private const val HEAD_INPUT_COUNT = 3
}
