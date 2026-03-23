package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.actions.emotion.Emotion
import io.github.kmpfacelink.model.BlendShape

/**
 * Output frame from the avatar pipeline.
 *
 * Contains all data needed to drive an avatar renderer for a single frame:
 * blend shapes for facial animation, detected emotion, current text chunk,
 * audio playback state, and a timestamp.
 *
 * @property blendShapes Current blend shape values for avatar animation
 * @property emotion Detected emotion from the current context, or null if unavailable
 * @property text Current LLM response text chunk, or null between responses
 * @property isAudioPlaying Whether TTS audio is currently being played
 * @property timestampMs Frame timestamp in milliseconds
 */
public data class AvatarPipelineOutput(
    public val blendShapes: Map<BlendShape, Float>,
    public val emotion: Emotion?,
    public val text: String?,
    public val isAudioPlaying: Boolean,
    public val timestampMs: Long,
)
