package io.github.kmpfacelink.voice

import io.github.kmpfacelink.voice.asr.AsrConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.lipsync.LipSyncLanguage
import io.github.kmpfacelink.voice.tts.TtsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
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

    // --- Copy semantics ---

    @Test
    fun copyChangingTtsConfig() {
        val original = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        val newTts = TtsConfig.OpenAiTts(apiKey = "key")
        val copied = original.copy(ttsConfig = newTts)
        assertIs<TtsConfig.OpenAiTts>(copied.ttsConfig)
        assertNull(copied.asrConfig)
        assertTrue(copied.autoPlayAudio)
    }

    @Test
    fun copyChangingAutoPlayAudio() {
        val original = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        val copied = original.copy(autoPlayAudio = false)
        assertFalse(copied.autoPlayAudio)
        assertEquals(original.ttsConfig, copied.ttsConfig)
    }

    @Test
    fun copyAddingAsrConfig() {
        val original = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertNull(original.asrConfig)
        val copied = original.copy(asrConfig = AsrConfig.OpenAiWhisper(apiKey = "k"))
        assertNotNull(copied.asrConfig)
        assertIs<AsrConfig.OpenAiWhisper>(copied.asrConfig)
    }

    @Test
    fun copyRemovingAsrConfig() {
        val original = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(),
            asrConfig = AsrConfig.OpenAiWhisper(apiKey = "k"),
        )
        val copied = original.copy(asrConfig = null)
        assertNull(copied.asrConfig)
    }

    // --- Different TTS provider configs in pipeline ---

    @Test
    fun pipelineConfigWithOpenAiTts() {
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.OpenAiTts(apiKey = "key", voice = "nova"),
        )
        assertIs<TtsConfig.OpenAiTts>(config.ttsConfig)
    }

    @Test
    fun pipelineConfigWithElevenLabsTts() {
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "voice-1"),
        )
        assertIs<TtsConfig.ElevenLabs>(config.ttsConfig)
    }

    @Test
    fun pipelineConfigWithVoicevoxTts() {
        val config = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        assertIs<TtsConfig.Voicevox>(config.ttsConfig)
    }

    // --- Full config combinations ---

    @Test
    fun fullConfigWithOpenAiTtsAndWhisperAsr() {
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.OpenAiTts(apiKey = "tts-key"),
            asrConfig = AsrConfig.OpenAiWhisper(apiKey = "asr-key"),
            lipSyncConfig = LipSyncConfig(language = LipSyncLanguage.JAPANESE),
            autoPlayAudio = true,
        )
        assertIs<TtsConfig.OpenAiTts>(config.ttsConfig)
        assertIs<AsrConfig.OpenAiWhisper>(config.asrConfig)
        assertEquals(LipSyncLanguage.JAPANESE, config.lipSyncConfig.language)
        assertTrue(config.autoPlayAudio)
    }

    @Test
    fun fullConfigWithElevenLabsAndWhisperCpp() {
        val config = VoicePipelineConfig(
            ttsConfig = TtsConfig.ElevenLabs(apiKey = "key", voiceId = "v"),
            asrConfig = AsrConfig.WhisperCpp(modelPath = "/models/whisper.bin"),
            lipSyncConfig = LipSyncConfig(targetFps = 30),
            autoPlayAudio = false,
        )
        assertIs<TtsConfig.ElevenLabs>(config.ttsConfig)
        assertIs<AsrConfig.WhisperCpp>(config.asrConfig)
        assertEquals(30, config.lipSyncConfig.targetFps)
        assertFalse(config.autoPlayAudio)
    }

    // --- Inequality ---

    @Test
    fun configsWithDifferentTtsAreNotEqual() {
        val a = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox())
        val b = VoicePipelineConfig(ttsConfig = TtsConfig.OpenAiTts(apiKey = "key"))
        assertNotEquals(a, b)
    }

    @Test
    fun configsWithDifferentAutoPlayAreNotEqual() {
        val a = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox(), autoPlayAudio = true)
        val b = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox(), autoPlayAudio = false)
        assertNotEquals(a, b)
    }

    @Test
    fun configsWithDifferentAsrAreNotEqual() {
        val a = VoicePipelineConfig(ttsConfig = TtsConfig.Voicevox(), asrConfig = null)
        val b = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(),
            asrConfig = AsrConfig.OpenAiWhisper(apiKey = "k"),
        )
        assertNotEquals(a, b)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = VoicePipelineConfig(
            ttsConfig = TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"),
            asrConfig = AsrConfig.OpenAiWhisper(apiKey = "asr"),
            autoPlayAudio = false,
        )
        val b = VoicePipelineConfig(
            ttsConfig = TtsConfig.ElevenLabs(apiKey = "k", voiceId = "v"),
            asrConfig = AsrConfig.OpenAiWhisper(apiKey = "asr"),
            autoPlayAudio = false,
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
