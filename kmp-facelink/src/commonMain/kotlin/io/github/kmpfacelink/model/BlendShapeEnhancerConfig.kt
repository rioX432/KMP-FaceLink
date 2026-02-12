package io.github.kmpfacelink.model

/**
 * Configuration for [io.github.kmpfacelink.util.BlendShapeEnhancer].
 *
 * Controls geometric blend shape enhancement for MediaPipe output.
 * On iOS (ARKit), blend shapes are already high-quality and this enhancement is not needed.
 */
public sealed class BlendShapeEnhancerConfig {

    /** No enhancement applied. */
    public data object None : BlendShapeEnhancerConfig()

    /**
     * Default enhancement using geometric landmark solvers.
     *
     * @property sensitivityOverrides Per-shape sensitivity multipliers.
     *           Values > 1 amplify weak signals. Applied after geometric blending.
     * @property deadZoneOverrides Per-shape dead zone thresholds.
     *           Values below the threshold snap to 0 (noise removal).
     * @property geometricBlendWeight Weight for geometric value in weighted blending (0.0–1.0).
     *           Only used for low-accuracy shapes (jaw, mouthDimple).
     *           Near-zero shapes (cheekPuff, eyeWide, noseSneer) use max(geometric, ml).
     */
    public data class Default(
        val sensitivityOverrides: Map<BlendShape, Float> = defaultSensitivityMap,
        val deadZoneOverrides: Map<BlendShape, Float> = defaultDeadZoneMap,
        val geometricBlendWeight: Float = 0.7f,
    ) : BlendShapeEnhancerConfig() {
        init {
            require(geometricBlendWeight in 0f..1f) {
                "geometricBlendWeight must be in 0.0..1.0, was $geometricBlendWeight"
            }
        }
    }

    public companion object {
        /** Default sensitivity multipliers for enhanced shapes. */
        public val defaultSensitivityMap: Map<BlendShape, Float> = mapOf(
            BlendShape.CHEEK_PUFF to 3.0f,
            BlendShape.EYE_WIDE_LEFT to 2.5f,
            BlendShape.EYE_WIDE_RIGHT to 2.5f,
            BlendShape.NOSE_SNEER_LEFT to 2.5f,
            BlendShape.NOSE_SNEER_RIGHT to 2.5f,
            BlendShape.JAW_FORWARD to 2.0f,
            BlendShape.JAW_LEFT to 2.0f,
            BlendShape.JAW_RIGHT to 2.0f,
            BlendShape.MOUTH_DIMPLE_LEFT to 2.0f,
            BlendShape.MOUTH_DIMPLE_RIGHT to 2.0f,
        )

        /** Default dead zone thresholds for enhanced shapes. */
        public val defaultDeadZoneMap: Map<BlendShape, Float> = mapOf(
            BlendShape.CHEEK_PUFF to 0.03f,
            BlendShape.EYE_WIDE_LEFT to 0.02f,
            BlendShape.EYE_WIDE_RIGHT to 0.02f,
            BlendShape.NOSE_SNEER_LEFT to 0.03f,
            BlendShape.NOSE_SNEER_RIGHT to 0.03f,
            BlendShape.JAW_FORWARD to 0.02f,
            BlendShape.JAW_LEFT to 0.02f,
            BlendShape.JAW_RIGHT to 0.02f,
            BlendShape.MOUTH_DIMPLE_LEFT to 0.02f,
            BlendShape.MOUTH_DIMPLE_RIGHT to 0.02f,
        )
    }
}
