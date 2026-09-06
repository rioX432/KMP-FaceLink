package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.FaceTrackingData

/**
 * Maps [FaceTrackingData] to VRCFaceTracking Unified Expressions parameters.
 *
 * Converts ARKit 52 blend shapes into the Unified Expressions standard used by
 * VRCFaceTracking and the VRChat face tracking ecosystem. Output keys follow
 * the `{prefix}{ExpressionName}` format (default prefix is `"v2/"`).
 *
 * Both base shapes (1:1 ARKit mappings) and blended shapes (averaged left/right
 * combinations) are included by default. Use [UnifiedExpressionsConfig] to control
 * prefix, blended shape inclusion, weight overrides, and custom mappings.
 *
 * ## Usage
 *
 * ```kotlin
 * val mapper = UnifiedExpressionsMapper()
 * val params: Map<String, Float> = mapper.map(faceTrackingData)
 * // params["v2/JawOpen"] = 0.8, params["v2/EyeClosedLeft"] = 0.5, etc.
 * ```
 *
 * @param config optional configuration for prefix, weights, and custom mappings
 * @see UnifiedExpression for the complete list of supported expressions
 * @see VrcOscParameterBuilder for sending these parameters to VRChat via OSC
 */
public class UnifiedExpressionsMapper(
    config: UnifiedExpressionsConfig = UnifiedExpressionsConfig(),
) : AvatarParameterMapper {

    private val prefix: String = config.parameterPrefix
    private val weightOverrides: Map<String, Float> = config.weightOverrides
    private val resolvedMappings: Map<String, ParameterMapping>

    init {
        val mappings = buildBaseMappings(config.includeBlendedShapes)
        mappings.putAll(config.customMappings)
        resolvedMappings = mappings
    }

    override fun map(data: FaceTrackingData): Map<String, Float> {
        if (!data.isTracking) return emptyMap()

        val result = LinkedHashMap<String, Float>(resolvedMappings.size)
        for ((paramName, mapping) in resolvedMappings) {
            val raw = applyMapping(mapping, data)
            val weight = weightOverrides[paramName] ?: 1f
            result[prefix + paramName] = (raw * weight).coerceIn(0f, 1f)
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

    private fun buildBaseMappings(
        includeBlended: Boolean,
    ): MutableMap<String, ParameterMapping> {
        val all = UnifiedExpressionMappings.buildMappings()
        if (includeBlended) return all.toMutableMap()

        val blendedNames = BLENDED_EXPRESSIONS.mapTo(mutableSetOf()) { it.parameterName }
        return all.filterKeys { it !in blendedNames }.toMutableMap()
    }

    internal companion object {
        /** Blended expressions that can be excluded via config. */
        internal val BLENDED_EXPRESSIONS: Set<UnifiedExpression> = setOf(
            UnifiedExpression.EYE_CLOSED,
            UnifiedExpression.EYE_WIDE,
            UnifiedExpression.EYE_SQUINT,
            UnifiedExpression.EYE_DILATION,
            UnifiedExpression.EYE_CONSTRICT,
            UnifiedExpression.BROW_DOWN_RIGHT,
            UnifiedExpression.BROW_DOWN_LEFT,
            UnifiedExpression.BROW_DOWN,
            UnifiedExpression.BROW_INNER_UP,
            UnifiedExpression.BROW_UP_RIGHT,
            UnifiedExpression.BROW_UP_LEFT,
            UnifiedExpression.BROW_UP,
            UnifiedExpression.NOSE_SNEER,
            UnifiedExpression.NASAL_DILATION,
            UnifiedExpression.NASAL_CONSTRICT,
            UnifiedExpression.CHEEK_PUFF,
            UnifiedExpression.CHEEK_SUCK,
            UnifiedExpression.CHEEK_SQUINT,
            UnifiedExpression.LIP_SUCK_UPPER,
            UnifiedExpression.LIP_SUCK_LOWER,
            UnifiedExpression.LIP_SUCK,
            UnifiedExpression.LIP_FUNNEL_UPPER,
            UnifiedExpression.LIP_FUNNEL_LOWER,
            UnifiedExpression.LIP_FUNNEL,
            UnifiedExpression.LIP_PUCKER_UPPER,
            UnifiedExpression.LIP_PUCKER_LOWER,
            UnifiedExpression.LIP_PUCKER,
            UnifiedExpression.MOUTH_UPPER_UP,
            UnifiedExpression.MOUTH_LOWER_DOWN,
            UnifiedExpression.MOUTH_OPEN,
            UnifiedExpression.MOUTH_RIGHT,
            UnifiedExpression.MOUTH_LEFT,
            UnifiedExpression.MOUTH_SMILE_RIGHT,
            UnifiedExpression.MOUTH_SMILE_LEFT,
            UnifiedExpression.MOUTH_SMILE,
            UnifiedExpression.MOUTH_SAD_RIGHT,
            UnifiedExpression.MOUTH_SAD_LEFT,
            UnifiedExpression.MOUTH_SAD,
            UnifiedExpression.MOUTH_STRETCH,
            UnifiedExpression.MOUTH_DIMPLE,
            UnifiedExpression.MOUTH_TIGHTENER,
            UnifiedExpression.MOUTH_PRESS,
        )
    }
}
