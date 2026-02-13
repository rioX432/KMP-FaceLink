package io.github.kmpfacelink.effects

/**
 * Output of the effect engine for a single frame.
 *
 * Renderer-agnostic: contains positions, parameters, and active effect metadata
 * that any rendering layer can consume.
 *
 * @property anchors Resolved anchor positions keyed by effect ID
 * @property parameters Float parameters keyed by effect ID (e.g., "$id.intensity")
 * @property activeEffects List of effects with intensity > 0
 * @property timestampMs Frame timestamp in milliseconds
 */
public data class EffectOutput(
    val anchors: Map<String, AnchorData>,
    val parameters: Map<String, Float>,
    val activeEffects: List<ActiveEffect>,
    val timestampMs: Long,
) {
    public companion object {
        /** Empty output with no effects active. */
        public val EMPTY: EffectOutput = EffectOutput(
            anchors = emptyMap(),
            parameters = emptyMap(),
            activeEffects = emptyList(),
            timestampMs = 0L,
        )
    }
}
