package io.github.kmpfacelink.voice

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.tts.TtsConfig

/**
 * Configuration for the voice pipeline.
 *
 * @property asrConfig ASR engine configuration (null to disable ASR)
 * @property ttsConfig TTS engine configuration
 * @property lipSyncConfig Lip sync animation configuration
 * @property autoPlayAudio Whether to automatically play synthesized audio
 */
public data class VoicePipelineConfig(
    val asrConfig: AsrConfig? = null,
    val ttsConfig: TtsConfig,
    val lipSyncConfig: LipSyncConfig = LipSyncConfig(),
    val autoPlayAudio: Boolean = true,
)
