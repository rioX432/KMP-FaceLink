package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape

/**
 * A single frame of lip sync animation.
 *
 * @property blendShapes Mouth-related blend shape values for this frame
 * @property timestampMs Timestamp in milliseconds relative to audio start
 */
public data class LipSyncFrame(
    val blendShapes: Map<BlendShape, Float>,
    val timestampMs: Long,
)
