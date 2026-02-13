package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.valueOf

/**
 * Maps [FaceTrackingData] to Live2D Cubism avatar parameters.
 *
 * Mappings are resolved at construction time for fast per-frame evaluation.
 * Use [Live2DMapperConfig] to override or extend default mappings.
 *
 * @param config optional configuration for overrides and custom mappings
 */
public class Live2DParameterMapper(
    config: Live2DMapperConfig = Live2DMapperConfig(),
) : AvatarParameterMapper {

    private val resolvedMappings: Map<String, ParameterMapping>

    init {
        val mappings = Live2DDefaultMappings.buildMappings().toMutableMap()

        // Apply overrides: null removes the mapping, non-null replaces it
        for ((key, mapping) in config.blendShapeOverrides) {
            if (mapping == null) {
                mappings.remove(key)
            } else {
                mappings[key] = mapping
            }
        }

        // Apply custom mappings (last-write-wins over defaults)
        mappings.putAll(config.customMappings)

        resolvedMappings = mappings
    }

    override fun map(data: FaceTrackingData): Map<String, Float> {
        if (!data.isTracking) return emptyMap()

        val result = LinkedHashMap<String, Float>(resolvedMappings.size)
        for ((paramId, mapping) in resolvedMappings) {
            result[paramId] = applyMapping(mapping, data)
        }
        return result
    }

    private fun applyMapping(
        mapping: ParameterMapping,
        data: FaceTrackingData,
    ): Float = when (mapping) {
        is ParameterMapping.Direct -> data.blendShapes.valueOf(mapping.blendShape)
        is ParameterMapping.Inverted -> 1f - data.blendShapes.valueOf(mapping.blendShape)
        is ParameterMapping.Scaled -> {
            val raw = data.blendShapes.valueOf(mapping.blendShape)
            (raw * mapping.scale + mapping.offset).coerceIn(mapping.min, mapping.max)
        }
        is ParameterMapping.Custom -> mapping.compute(data)
    }
}
