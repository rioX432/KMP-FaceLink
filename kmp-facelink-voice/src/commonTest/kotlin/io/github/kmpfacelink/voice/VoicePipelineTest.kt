package io.github.kmpfacelink.voice

import io.github.kmpfacelink.voice.asr.FakeAsrEngine
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.audio.FakeAudioPlayer
import io.github.kmpfacelink.voice.audio.FakeAudioRecorder
import io.github.kmpfacelink.voice.lipsync.LipSyncConfig
import io.github.kmpfacelink.voice.tts.FakeTtsEngine
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import io.github.kmpfacelink.voice.tts.TtsConfig
import io.github.kmpfacelink.voice.tts.TtsResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val FAKE_DURATION_MS = 300L

class VoicePipelineTest {

    private val fakeTts = FakeTtsEngine()
    private val fakeAsr = FakeAsrEngine()
    private val fakeRecorder = FakeAudioRecorder()
    private val fakePlayer = FakeAudioPlayer()

    private fun createPipeline(autoPlayAudio: Boolean = false): VoicePipeline = VoicePipeline(
        config = VoicePipelineConfig(
            ttsConfig = TtsConfig.Voicevox(), // Not actually used since we inject fake
            lipSyncConfig = LipSyncConfig(targetFps = 30),
            autoPlayAudio = autoPlayAudio,
        ),
        audioRecorder = fakeRecorder,
        audioPlayer = fakePlayer,
        ttsEngine = fakeTts,
        asrEngine = fakeAsr,
    )

    @Test
    fun speakReturnsLipSyncFlow() = runTest {
        val pipeline = createPipeline()

        // Set up fake TTS with phoneme events
        fakeTts.resultToReturn = TtsResult(
            audio = AudioData(ByteArray(16000), AudioFormat(), FAKE_DURATION_MS),
            phonemeEvents = listOf(
                PhonemeEvent("a", 0, 100),
                PhonemeEvent("i", 100, 200),
                PhonemeEvent("u", 200, 300),
            ),
            durationMs = FAKE_DURATION_MS,
        )

        val flow = pipeline.speak("hello")
        val firstFrame = flow.first()

        assertNotNull(firstFrame)
        assertEquals(1, fakeTts.synthesizeCount)

        pipeline.release()
    }

    @Test
    fun speakWithAmplitudeFallback() = runTest {
        val pipeline = createPipeline()

        // No phoneme events → amplitude fallback
        fakeTts.resultToReturn = TtsResult(
            audio = AudioData(
                bytes = ByteArray(16000) { 0x40.toByte() },
                format = AudioFormat(),
                durationMs = FAKE_DURATION_MS,
            ),
            phonemeEvents = emptyList(),
            durationMs = FAKE_DURATION_MS,
        )

        val flow = pipeline.speak("hello")
        val firstFrame = flow.first()

        assertNotNull(firstFrame)

        pipeline.release()
    }

    @Test
    fun stopListeningReturnsTranscription() = runTest {
        val pipeline = createPipeline()

        fakeRecorder.audioDataToReturn = AudioData(
            bytes = ByteArray(16000),
            format = AudioFormat(),
            durationMs = FAKE_DURATION_MS,
        )

        pipeline.startListening()
        assertTrue(fakeRecorder.isRecording.value)

        val result = pipeline.stopListening()
        assertNotNull(result)
        assertEquals("Hello, world!", result.text)
        assertEquals(1, fakeAsr.transcribeCount)

        pipeline.release()
    }

    @Test
    fun initialStateIsIdle() {
        val pipeline = createPipeline()
        assertEquals(VoicePipelineState.Idle, pipeline.state.value)
        pipeline.release()
    }
}
