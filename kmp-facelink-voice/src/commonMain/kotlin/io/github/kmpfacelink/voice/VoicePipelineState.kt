package io.github.kmpfacelink.voice

/**
 * State of the voice pipeline.
 */
public sealed class VoicePipelineState : EngineState {
    /** Pipeline is idle and ready. */
    public data object Idle : VoicePipelineState(), EngineState.Idle

    /** Pipeline is recording microphone input. */
    public data object Listening : VoicePipelineState(), EngineState.Active

    /** Pipeline is processing ASR or TTS. */
    public data object Processing : VoicePipelineState(), EngineState.Active

    /** Pipeline is playing synthesized speech with lip sync. */
    public data object Speaking : VoicePipelineState(), EngineState.Active

    /** An error occurred in the pipeline. */
    public data class Error(override val message: String) : VoicePipelineState(), EngineState.Error
}
