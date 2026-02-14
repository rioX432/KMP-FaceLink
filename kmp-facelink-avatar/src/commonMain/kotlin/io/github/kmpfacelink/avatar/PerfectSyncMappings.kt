package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape

/**
 * Perfect Sync configuration for VTubeStudio-compatible Live2D models.
 *
 * Maps all 52 ARKit blend shapes as [ParameterMapping.Direct] pass-through
 * parameters using camelCase ARKit names (e.g. "eyeBlinkLeft").
 * These are added via [Live2DMapperConfig.customMappings], so the 19 standard
 * Live2D parameters are preserved alongside the 52 Perfect Sync parameters.
 *
 * Usage:
 * ```
 * val mapper = Live2DParameterMapper(PerfectSyncMappings.config())
 * ```
 */
public object PerfectSyncMappings {

    /**
     * Creates a [Live2DMapperConfig] that adds all 52 ARKit blend shapes
     * as direct pass-through custom mappings.
     */
    public fun config(): Live2DMapperConfig = Live2DMapperConfig(
        customMappings = BlendShape.entries.associate { shape ->
            shape.arKitName to ParameterMapping.Direct(shape)
        },
    )
}
