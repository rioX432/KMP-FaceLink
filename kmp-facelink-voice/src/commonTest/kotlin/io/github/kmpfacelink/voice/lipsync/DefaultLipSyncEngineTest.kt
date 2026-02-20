package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.DefaultLipSyncEngine
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultLipSyncEngineTest {

    private val engine = DefaultLipSyncEngine()

    @Test
    fun animateFromPhonemesProducesFrames() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 200),
            PhonemeEvent(phoneme = "i", startMs = 200, endMs = 400),
        )

        val frames = engine.animateFromPhonemes(events).toList()
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun animateFromAudioProducesFrames() = runTest {
        // Create 500ms of non-silent audio
        val bytes = ByteArray(16000) // 500ms at 16kHz 16-bit
        for (i in bytes.indices step 2) {
            bytes[i] = 0x00.toByte()
            bytes[i + 1] = 0x40.toByte()
        }
        val audio = AudioData(
            bytes = bytes,
            format = AudioFormat(sampleRate = 16000, channels = 1, bitsPerSample = 16),
            durationMs = 500,
        )

        val frames = engine.animateFromAudio(audio).toList()
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun animateFromEmptyPhonemesProducesNoFrames() = runTest {
        val frames = engine.animateFromPhonemes(emptyList()).toList()
        assertTrue(frames.isEmpty())
    }
}
