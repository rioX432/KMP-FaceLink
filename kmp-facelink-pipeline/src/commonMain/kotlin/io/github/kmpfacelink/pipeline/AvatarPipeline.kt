package io.github.kmpfacelink.pipeline

import io.github.kmpfacelink.api.Releasable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates the full AI avatar pipeline: ASR → LLM → TTS → lip sync → avatar output.
 *
 * The pipeline manages transitions between states automatically:
 * - [listen] starts ASR, transitions to [AvatarPipelineState.Processing] when speech ends
 * - [processText] skips ASR and directly sends text to the LLM
 * - LLM response triggers TTS and lip sync, transitioning to [AvatarPipelineState.Speaking]
 * - After speech completes, returns to [AvatarPipelineState.Idle]
 *
 * Collect [output] to receive continuous avatar animation frames.
 *
 * ## Related modules
 *
 * - **kmp-facelink-voice**: Provides ASR/TTS/lip sync via [io.github.kmpfacelink.voice.asr.AsrEngine]
 *   and [io.github.kmpfacelink.voice.tts.TtsEngine].
 * - **kmp-facelink-llm**: Provides LLM streaming via [io.github.kmpfacelink.llm.LlmEngine].
 * - **kmp-facelink-avatar**: Maps blend shapes to avatar parameters via
 *   [io.github.kmpfacelink.avatar.AvatarParameterMapper].
 *
 * @see AvatarPipelineBuilder
 */
public interface AvatarPipeline : Releasable {

    /** Current pipeline state. */
    public val state: StateFlow<AvatarPipelineState>

    /** Flow of avatar output frames for driving avatar rendering. */
    public val output: Flow<AvatarPipelineOutput>

    /**
     * Starts the pipeline and initializes all sub-components.
     *
     * Must be called before [listen] or [processText].
     */
    public suspend fun start()

    /**
     * Stops the pipeline and cancels any in-progress operations.
     *
     * The pipeline returns to [AvatarPipelineState.Idle] after stopping.
     */
    public suspend fun stop()

    /**
     * Starts ASR listening.
     *
     * The pipeline transitions to [AvatarPipelineState.Listening] and automatically
     * moves to [AvatarPipelineState.Processing] when speech input is finalized.
     * The recognized text is then sent to the LLM for response generation.
     */
    public suspend fun listen()

    /**
     * Processes text directly, skipping ASR.
     *
     * Sends the given text to the LLM and produces avatar output
     * (speech + lip sync + emotion) from the response.
     *
     * @param text Input text to process through the LLM
     */
    public suspend fun processText(text: String)

    /** Releases all resources held by the pipeline and its sub-components. */
    public override fun release()
}
