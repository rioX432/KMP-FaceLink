package io.github.kmpfacelink.voice

/**
 * State of the voice pipeline.
 */
public sealed class VoicePipelineState {
    /** Pipeline is idle and ready. */
    public data object Idle : VoicePipelineState()

    /** Pipeline is recording microphone input. */
    public data object Listening : VoicePipelineState()

    /** Pipeline is processing ASR or TTS. */
    public data object Processing : VoicePipelineState()

    /** Pipeline is playing synthesized speech with lip sync. */
    public data object Speaking : VoicePipelineState()

    /** An error occurred in the pipeline. */
    public data class Error(val message: String) : VoicePipelineState()
}
