package io.github.kmpfacelink.voice

/**
 * Shared interface for engine lifecycle states.
 *
 * TTS, ASR, and voice-pipeline states all follow a common pattern:
 * idle → processing → idle (or error). This interface captures that
 * shared pattern so consumers can write generic state-handling logic
 * without depending on a specific engine type.
 *
 * Each concrete state sealed class (`TtsState`, `AsrState`,
 * `VoicePipelineState`) implements this interface while keeping its
 * own domain-specific sub-states.
 */
public interface EngineState {

    /**
     * Marker for the "idle / ready" state.
     *
     * An engine in this state holds no in-flight work and is ready
     * to accept new requests.
     */
    public interface Idle : EngineState

    /**
     * Marker for any "active / working" state.
     *
     * An engine in this state is performing work (synthesizing,
     * transcribing, listening, speaking, etc.).
     */
    public interface Active : EngineState

    /**
     * Marker for the "error" state.
     *
     * @property message Human-readable description of the failure.
     */
    public interface Error : EngineState {
        public val message: String
    }
}
