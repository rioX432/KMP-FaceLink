package io.github.kmpfacelink.sample

import androidx.compose.runtime.snapshots.SnapshotStateMap
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.VoicePipeline
import io.github.kmpfacelink.voice.VoicePipelineConfig
import io.github.kmpfacelink.voice.VoicePipelineState
import io.github.kmpfacelink.voice.audio.createAudioPlayer
import io.github.kmpfacelink.voice.audio.createAudioRecorder
import io.github.kmpfacelink.voice.tts.TtsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal enum class TtsProvider { VOICEVOX, OPENAI, ELEVENLABS }

/**
 * Holds pipeline state, coroutine jobs, and business logic for VoiceTrackingScreen.
 */
internal class VoiceTrackingStateHolder(private val scope: CoroutineScope) {
    var pipeline: VoicePipeline? = null
    var speakJob: Job? = null
    var stateJob: Job? = null
    var lipSyncJob: Job? = null

    fun startSpeaking(
        config: TtsConfig,
        speakText: String,
        lipSyncShapes: SnapshotStateMap<BlendShape, Float>,
        onStateChange: (VoicePipelineState) -> Unit,
    ) {
        pipeline?.release()
        val newPipeline = VoicePipeline(
            config = VoicePipelineConfig(ttsConfig = config),
            audioRecorder = createAudioRecorder(),
            audioPlayer = createAudioPlayer(),
        )
        pipeline = newPipeline
        stateJob?.cancel()
        stateJob = scope.launch { newPipeline.state.collect { onStateChange(it) } }
        lipSyncJob?.cancel()
        lipSyncJob = scope.launch {
            newPipeline.lipSyncOutput.collect { shapes ->
                lipSyncShapes.clear()
                lipSyncShapes.putAll(shapes)
            }
        }
        speakJob?.cancel()
        speakJob = scope.launch {
            @Suppress("TooGenericExceptionCaught") // UI error handler: voice pipeline
            // can fail with IOException (network TTS), RuntimeException (ASR), etc.
            try {
                newPipeline.speak(speakText).collect { }
            } catch (e: Exception) {
                onStateChange(VoicePipelineState.Error(e.message ?: "Speak failed"))
            }
        }
    }

    fun toggleMic(
        pipelineState: VoicePipelineState,
        onTranscription: (String) -> Unit,
    ) {
        val p = pipeline ?: return
        if (pipelineState == VoicePipelineState.Listening) {
            scope.launch {
                val result = p.stopListening()
                onTranscription(result?.text ?: "(no result)")
            }
        } else {
            scope.launch { p.startListening() }
        }
    }

    fun release() {
        speakJob?.cancel()
        stateJob?.cancel()
        lipSyncJob?.cancel()
        pipeline?.release()
    }
}

internal fun buildTtsConfig(
    provider: TtsProvider,
    apiKey: String,
    voiceId: String,
    voicevoxHost: String,
): TtsConfig = when (provider) {
    TtsProvider.VOICEVOX -> TtsConfig.Voicevox(host = voicevoxHost)
    TtsProvider.OPENAI -> TtsConfig.OpenAiTts(apiKey = apiKey, voice = voiceId)
    TtsProvider.ELEVENLABS -> TtsConfig.ElevenLabs(apiKey = apiKey, voiceId = voiceId)
}
