package io.github.kmpfacelink.voice

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.tts.TtsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoicePipelineConfigTest {

    @Test
    fun defaultAutoPlayAudioIsTrue() {
        val config = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertTrue(config.autoPlayAudio)
    }

    @Test
    fun defaultAsrConfigIsNull() {
        val config = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertNull(config.asrConfig)
    }

    @Test
    fun defaultLipSyncConfigUsesDefaults() {
        val config = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertEquals(LipSyncConfig(), config.lipSyncConfig)
    }

    @Test
    fun ttsConfigIsStored() {
        val tts = TtsConfig.OpenAiTts(apiKey = "key", voice = "nova")
        val config = VoicePipelineConfig(ttsConfig = tts)
        assertEquals(tts, config.ttsConfig)
    }

    @Test
    fun asrConfigIsStored() {
        val asr = AsrConfig.OpenAiWhisper(apiKey = "asr-key")
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(),
            asrConfig = asr,
        )
        assertEquals(asr, config.asrConfig)
    }

    @Test
    fun autoPlayAudioCanBeDisabled() {
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(),
            autoPlayAudio = false,
        )
        assertEquals(false, config.autoPlayAudio)
    }

    @Test
    fun customLipSyncConfigIsStored() {
        val lipSync = LipSyncConfig(smoothing = 0.8f, amplitudeScale = 2.0f, targetFps = 30)
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(),
            lipSyncConfig = lipSync,
        )
        assertEquals(lipSync, config.lipSyncConfig)
    }

    @Test
    fun equalityByValue() {
        val a = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        val b = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertEquals(a, b)
    }
}
