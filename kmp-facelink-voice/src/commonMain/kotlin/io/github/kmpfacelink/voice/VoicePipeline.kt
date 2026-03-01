package io.github.kmpfacelink.voice

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.asr.AsrEngine
import io.github.kmpfacelink.voice.asr.TranscriptionResult
import io.github.kmpfacelink.voice.asr.internal.AsrEngineFactory
import io.github.kmpfacelink.voice.audio.AudioPlayer
import io.github.kmpfacelink.voice.audio.AudioRecorder
import io.github.kmpfacelink.voice.lipsync.LipSyncEngine
import io.github.kmpfacelink.voice.lipsync.internal.DefaultLipSyncEngine
import io.github.kmpfacelink.voice.tts.TtsEngine
import io.github.kmpfacelink.voice.tts.internal.TtsEngineFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Voice pipeline orchestrator.
 *
 * Wires together ASR, TTS, lip sync, and audio I/O into a single pipeline.
 * Produces `Map<BlendShape, Float>` output compatible with the core SDK.
 *
 * @param config Pipeline configuration
 * @param audioRecorder Platform audio recorder (null to disable recording)
 * @param audioPlayer Platform audio player (null to disable playback)
 * @param ttsEngine Optional pre-built TTS engine (overrides config.ttsConfig)
 * @param asrEngine Optional pre-built ASR engine (overrides config.asrConfig)
 * @param lipSyncEngine Optional pre-built lip sync engine
 */
public class VoicePipeline(
    private val config: VoicePipelineConfig,
    private val audioRecorder: AudioRecorder? = null,
    private val audioPlayer: AudioPlayer? = null,
    ttsEngine: TtsEngine? = null,
    asrEngine: AsrEngine? = null,
    lipSyncEngine: LipSyncEngine? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<VoicePipelineState>(VoicePipelineState.Idle)

    /** Current pipeline state. */
    public val state: StateFlow<VoicePipelineState> = _state.asStateFlow()

    private val tts: TtsEngine = ttsEngine ?: TtsEngineFactory.create(config.ttsConfig)
    private val asr: AsrEngine? = asrEngine ?: config.asrConfig?.let { AsrEngineFactory.create(it) }
    private val lipSync: LipSyncEngine = lipSyncEngine ?: DefaultLipSyncEngine()

    private val _lipSyncOutput = MutableSharedFlow<Map<BlendShape, Float>>(extraBufferCapacity = 16)

    /** Flow of lip sync blend shape values. Emits during [speak]. */
    public val lipSyncOutput: Flow<Map<BlendShape, Float>> = _lipSyncOutput.asSharedFlow()

    private val _transcriptions = MutableSharedFlow<TranscriptionResult>(extraBufferCapacity = 16)

    /** Flow of transcription results from ASR. */
    public val transcriptions: Flow<TranscriptionResult> = _transcriptions.asSharedFlow()

    private var transcriptionCollectorJob: kotlinx.coroutines.Job? = null

    /**
     * Synthesizes speech from text and generates lip sync animation.
     *
     * If [VoicePipelineConfig.autoPlayAudio] is true, audio is played via [AudioPlayer].
     * Lip sync frames are emitted to [lipSyncOutput] during playback.
     *
     * @param text Text to speak
     * @return Flow of lip sync blend shape maps
     */
    public suspend fun speak(text: String): Flow<Map<BlendShape, Float>> {
        _state.value = VoicePipelineState.Processing

        val result = tts.synthesize(text)

        _state.value = VoicePipelineState.Speaking

        // Choose lip sync strategy based on available phoneme data
        val lipSyncFlow = if (result.phonemeEvents.isNotEmpty()) {
            lipSync.animateFromPhonemes(result.phonemeEvents, config.lipSyncConfig)
        } else {
            lipSync.animateFromAudio(result.audio, config.lipSyncConfig)
        }

        val outputFlow = lipSyncFlow.map { frame ->
            _lipSyncOutput.tryEmit(frame.blendShapes)
            frame.blendShapes
        }

        // Play audio in background if configured
        if (config.autoPlayAudio && audioPlayer != null) {
            scope.launch {
                try {
                    audioPlayer.play(result.audio)
                    _state.value = VoicePipelineState.Idle
                } catch (e: Exception) {
                    _state.value = VoicePipelineState.Error("Audio playback failed: ${e.message}")
                }
            }
        } else {
            // No auto-play — reset state immediately since no playback will trigger the reset
            _state.value = VoicePipelineState.Idle
        }

        return outputFlow
    }

    /**
     * Starts continuous listening for speech input via ASR.
     *
     * Transcriptions are emitted to [transcriptions].
     */
    public suspend fun startListening() {
        val engine = asr ?: error("ASR not configured")
        val recorder = audioRecorder ?: error("AudioRecorder not provided")

        try {
            recorder.start()
            engine.startListening()
            _state.value = VoicePipelineState.Listening
        } catch (e: Exception) {
            _state.value = VoicePipelineState.Error("Failed to start listening: ${e.message}")
            throw e
        }

        transcriptionCollectorJob?.cancel()
        transcriptionCollectorJob = scope.launch {
            engine.transcriptions.collect { result ->
                _transcriptions.tryEmit(result)
            }
        }
    }

    /**
     * Stops listening and returns the latest transcription.
     */
    public suspend fun stopListening(): TranscriptionResult? {
        val engine = asr ?: return null
        val recorder = audioRecorder ?: return null

        engine.stopListening()
        val audioData = recorder.stop() ?: return null

        _state.value = VoicePipelineState.Processing
        val result = engine.transcribe(audioData)
        _state.value = VoicePipelineState.Idle

        return result
    }

    /** Releases all resources held by the pipeline. */
    public fun release() {
        scope.cancel()
        tts.release()
        asr?.release()
        audioRecorder?.release()
        audioPlayer?.release()
    }
}
