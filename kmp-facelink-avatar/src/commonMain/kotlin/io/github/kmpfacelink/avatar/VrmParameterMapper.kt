package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.FaceTrackingData

/**
 * Maps [FaceTrackingData] to VRM expression parameters.
 *
 * Supports both VRM 0.x (BlendShapeProxy) and VRM 1.0 (ExpressionManager) specifications.
 * Output keys use the version-appropriate naming convention (PascalCase for 0.x, camelCase for 1.0).
 *
 * Mapped parameter categories:
 * - **Visemes**: A/I/U/E/O mouth shapes for lip sync
 * - **Emotions**: Joy, Angry, Sorrow, Surprise, Neutral, Relaxed/Fun
 * - **Eyes**: Blink (both), BlinkLeft, BlinkRight
 * - **Gaze**: LookUp, LookDown, LookLeft, LookRight
 * - **Head**: Yaw, Pitch, Roll (degrees from HeadTransform)
 *
 * @param config optional configuration for VRM version, weight overrides, and custom mappings
 */
public class VrmParameterMapper(
    config: VrmConfig = VrmConfig(),
) : AvatarParameterMapper {

    private val resolvedMappings: Map<String, ParameterMapping>
    private val weightOverrides: Map<String, Float> = config.expressionWeightOverrides
    private val headParamIds: Set<String>

    init {
        val mappings = VrmDefaultMappings.buildMappings(config.version).toMutableMap()
        mappings.putAll(config.customMappings)

        val headKeys = VrmIdResolver.headIds(config.version)
        headParamIds = setOf(headKeys.yaw, headKeys.pitch, headKeys.roll)

        if (config.headTransformSensitivity != 1f) {
            wrapHeadSensitivity(mappings, headKeys, config.headTransformSensitivity)
        }
        resolvedMappings = mappings
    }

    override fun map(data: FaceTrackingData): Map<String, Float> {
        if (!data.isTracking) return emptyMap()

        val result = LinkedHashMap<String, Float>(resolvedMappings.size)
        for ((paramId, mapping) in resolvedMappings) {
            val raw = applyMapping(mapping, data)
            val weight = weightOverrides[paramId] ?: 1f
            result[paramId] = if (paramId in headParamIds) {
                raw * weight
            } else {
                (raw * weight).coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun applyMapping(
        mapping: ParameterMapping,
        data: FaceTrackingData,
    ): Float = when (mapping) {
        is ParameterMapping.Direct -> data.blendShapes.valueOrRestPose(mapping.blendShape)
        is ParameterMapping.Inverted -> 1f - data.blendShapes.valueOrRestPose(mapping.blendShape)
        is ParameterMapping.Scaled -> {
            val raw = data.blendShapes.valueOrRestPose(mapping.blendShape)
            (raw * mapping.scale + mapping.offset).coerceIn(mapping.min, mapping.max)
        }
        is ParameterMapping.Custom -> mapping.compute(data)
    }

    private fun wrapHeadSensitivity(
        mappings: MutableMap<String, ParameterMapping>,
        headKeys: HeadIds,
        sensitivity: Float,
    ) {
        listOf(headKeys.yaw, headKeys.pitch, headKeys.roll).forEach { key ->
            val original = mappings[key]
            if (original is ParameterMapping.Custom) {
                mappings[key] = ParameterMapping.Custom { data ->
                    original.compute(data) * sensitivity
                }
            }
        }
    }
}
