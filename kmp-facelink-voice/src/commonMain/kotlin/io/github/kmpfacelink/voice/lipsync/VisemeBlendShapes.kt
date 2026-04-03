package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping from [Viseme] to ARKit [BlendShape] values.
 *
 * Values are based on the TalkingHead project, tuned for VTuber mouth animation.
 * Each viseme maps to a sparse set of mouth-related blend shapes with float weights (0.0–1.0).
 */
public object VisemeBlendShapes {

    private const val WEIGHT_MINIMAL = 0.1f
    private const val WEIGHT_SUBTLE = 0.15f
    private const val WEIGHT_LOW = 0.2f
    private const val WEIGHT_LOW_MID = 0.25f
    private const val WEIGHT_MEDIUM = 0.3f
    private const val WEIGHT_MID_HIGH = 0.35f
    private const val WEIGHT_MODERATE = 0.4f
    private const val WEIGHT_HALF = 0.5f
    private const val WEIGHT_STRONG = 0.6f
    private const val WEIGHT_HIGH = 0.7f

    private val mappings: Map<Viseme, Map<BlendShape, Float>> = mapOf(
        Viseme.SIL to emptyMap(),
        Viseme.PP to mapOf(
            BlendShape.MOUTH_CLOSE to WEIGHT_HIGH,
            BlendShape.MOUTH_PRESS_LEFT to WEIGHT_HALF,
            BlendShape.MOUTH_PRESS_RIGHT to WEIGHT_HALF,
            BlendShape.MOUTH_PUCKER to WEIGHT_MEDIUM,
        ),
        Viseme.FF to mapOf(
            BlendShape.MOUTH_FUNNEL to WEIGHT_MEDIUM,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to WEIGHT_LOW,
            BlendShape.MOUTH_UPPER_UP_LEFT to WEIGHT_SUBTLE,
            BlendShape.MOUTH_UPPER_UP_RIGHT to WEIGHT_SUBTLE,
        ),
        Viseme.TH to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_SUBTLE,
            BlendShape.MOUTH_UPPER_UP_LEFT to WEIGHT_MINIMAL,
            BlendShape.MOUTH_UPPER_UP_RIGHT to WEIGHT_MINIMAL,
            BlendShape.TONGUE_OUT to WEIGHT_MEDIUM,
        ),
        Viseme.DD to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_LOW,
            BlendShape.MOUTH_CLOSE to WEIGHT_SUBTLE,
            BlendShape.MOUTH_PRESS_LEFT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_PRESS_RIGHT to WEIGHT_MEDIUM,
        ),
        Viseme.KK to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_LOW_MID,
            BlendShape.MOUTH_STRETCH_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_STRETCH_RIGHT to WEIGHT_LOW,
        ),
        Viseme.CH to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_LOW,
            BlendShape.MOUTH_FUNNEL to WEIGHT_HALF,
            BlendShape.MOUTH_PUCKER to WEIGHT_MEDIUM,
        ),
        Viseme.SS to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_MINIMAL,
            BlendShape.MOUTH_SMILE_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_SMILE_RIGHT to WEIGHT_LOW,
            BlendShape.MOUTH_STRETCH_LEFT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_STRETCH_RIGHT to WEIGHT_MEDIUM,
        ),
        Viseme.NN to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_MINIMAL,
            BlendShape.MOUTH_CLOSE to WEIGHT_MEDIUM,
            BlendShape.MOUTH_PRESS_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_PRESS_RIGHT to WEIGHT_LOW,
        ),
        Viseme.RR to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_SUBTLE,
            BlendShape.MOUTH_FUNNEL to WEIGHT_MODERATE,
            BlendShape.MOUTH_PUCKER to WEIGHT_LOW,
        ),
        Viseme.AA to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_HIGH,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_UPPER_UP_LEFT to WEIGHT_SUBTLE,
            BlendShape.MOUTH_UPPER_UP_RIGHT to WEIGHT_SUBTLE,
        ),
        Viseme.E to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_MID_HIGH,
            BlendShape.MOUTH_SMILE_LEFT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_SMILE_RIGHT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_STRETCH_LEFT to WEIGHT_LOW,
            BlendShape.MOUTH_STRETCH_RIGHT to WEIGHT_LOW,
        ),
        Viseme.IH to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_LOW,
            BlendShape.MOUTH_SMILE_LEFT to WEIGHT_MODERATE,
            BlendShape.MOUTH_SMILE_RIGHT to WEIGHT_MODERATE,
            BlendShape.MOUTH_STRETCH_LEFT to WEIGHT_MEDIUM,
            BlendShape.MOUTH_STRETCH_RIGHT to WEIGHT_MEDIUM,
        ),
        Viseme.OH to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_HALF,
            BlendShape.MOUTH_FUNNEL to WEIGHT_HALF,
            BlendShape.MOUTH_PUCKER to WEIGHT_LOW,
        ),
        Viseme.OU to mapOf(
            BlendShape.JAW_OPEN to WEIGHT_LOW_MID,
            BlendShape.MOUTH_FUNNEL to WEIGHT_STRONG,
            BlendShape.MOUTH_PUCKER to WEIGHT_HALF,
        ),
    )

    /**
     * Returns the blend shape weights for the given viseme.
     * Returns an empty map for [Viseme.SIL].
     */
    public fun blendShapesFor(viseme: Viseme): Map<BlendShape, Float> =
        mappings[viseme] ?: emptyMap()

    /**
     * Set of all [BlendShape] values that lip sync may modify.
     * Useful for knowing which face tracking shapes to override.
     */
    public val mouthBlendShapes: Set<BlendShape> by lazy {
        mappings.values.flatMap { it.keys }.toSet()
    }
}
