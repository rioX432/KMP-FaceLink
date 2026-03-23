package io.github.kmpfacelink.pipeline

/**
 * State of the avatar pipeline.
 */
public sealed class AvatarPipelineState {
    /** Pipeline is idle and ready. */
    public data object Idle : AvatarPipelineState()

    /** Pipeline is recording microphone input via ASR. */
    public data object Listening : AvatarPipelineState()

    /** Pipeline is processing (ASR transcription or LLM generation). */
    public data object Processing : AvatarPipelineState()

    /** Pipeline is playing synthesized speech with lip sync. */
    public data object Speaking : AvatarPipelineState()

    /** An error occurred in the pipeline. */
    public data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : AvatarPipelineState()
}
