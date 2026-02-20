package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape

/**
 * Mapping from [Viseme] to ARKit [BlendShape] values.
 *
 * Values are based on the TalkingHead project, tuned for VTuber mouth animation.
 * Each viseme maps to a sparse set of mouth-related blend shapes with float weights (0.0–1.0).
 */
public object VisemeBlendShapes {

    private val mappings: Map<Viseme, Map<BlendShape, Float>> = mapOf(
        Viseme.SIL to emptyMap(),
        Viseme.PP to mapOf(
            BlendShape.MOUTH_CLOSE to 0.7f,
            BlendShape.MOUTH_PRESS_LEFT to 0.5f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.5f,
            BlendShape.MOUTH_PUCKER to 0.3f,
        ),
        Viseme.FF to mapOf(
            BlendShape.MOUTH_FUNNEL to 0.3f,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to 0.2f,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to 0.2f,
            BlendShape.MOUTH_UPPER_UP_LEFT to 0.15f,
            BlendShape.MOUTH_UPPER_UP_RIGHT to 0.15f,
        ),
        Viseme.TH to mapOf(
            BlendShape.JAW_OPEN to 0.15f,
            BlendShape.MOUTH_UPPER_UP_LEFT to 0.1f,
            BlendShape.MOUTH_UPPER_UP_RIGHT to 0.1f,
            BlendShape.TONGUE_OUT to 0.3f,
        ),
        Viseme.DD to mapOf(
            BlendShape.JAW_OPEN to 0.2f,
            BlendShape.MOUTH_CLOSE to 0.15f,
            BlendShape.MOUTH_PRESS_LEFT to 0.3f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.3f,
        ),
        Viseme.KK to mapOf(
            BlendShape.JAW_OPEN to 0.25f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.2f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.2f,
        ),
        Viseme.CH to mapOf(
            BlendShape.JAW_OPEN to 0.2f,
            BlendShape.MOUTH_FUNNEL to 0.5f,
            BlendShape.MOUTH_PUCKER to 0.3f,
        ),
        Viseme.SS to mapOf(
            BlendShape.JAW_OPEN to 0.1f,
            BlendShape.MOUTH_SMILE_LEFT to 0.2f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.2f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.3f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.3f,
        ),
        Viseme.NN to mapOf(
            BlendShape.JAW_OPEN to 0.1f,
            BlendShape.MOUTH_CLOSE to 0.3f,
            BlendShape.MOUTH_PRESS_LEFT to 0.2f,
            BlendShape.MOUTH_PRESS_RIGHT to 0.2f,
        ),
        Viseme.RR to mapOf(
            BlendShape.JAW_OPEN to 0.15f,
            BlendShape.MOUTH_FUNNEL to 0.4f,
            BlendShape.MOUTH_PUCKER to 0.2f,
        ),
        Viseme.AA to mapOf(
            BlendShape.JAW_OPEN to 0.7f,
            BlendShape.MOUTH_LOWER_DOWN_LEFT to 0.3f,
            BlendShape.MOUTH_LOWER_DOWN_RIGHT to 0.3f,
            BlendShape.MOUTH_UPPER_UP_LEFT to 0.15f,
            BlendShape.MOUTH_UPPER_UP_RIGHT to 0.15f,
        ),
        Viseme.E to mapOf(
            BlendShape.JAW_OPEN to 0.35f,
            BlendShape.MOUTH_SMILE_LEFT to 0.3f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.3f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.2f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.2f,
        ),
        Viseme.IH to mapOf(
            BlendShape.JAW_OPEN to 0.2f,
            BlendShape.MOUTH_SMILE_LEFT to 0.4f,
            BlendShape.MOUTH_SMILE_RIGHT to 0.4f,
            BlendShape.MOUTH_STRETCH_LEFT to 0.3f,
            BlendShape.MOUTH_STRETCH_RIGHT to 0.3f,
        ),
        Viseme.OH to mapOf(
            BlendShape.JAW_OPEN to 0.5f,
            BlendShape.MOUTH_FUNNEL to 0.5f,
            BlendShape.MOUTH_PUCKER to 0.2f,
        ),
        Viseme.OU to mapOf(
            BlendShape.JAW_OPEN to 0.25f,
            BlendShape.MOUTH_FUNNEL to 0.6f,
            BlendShape.MOUTH_PUCKER to 0.5f,
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
