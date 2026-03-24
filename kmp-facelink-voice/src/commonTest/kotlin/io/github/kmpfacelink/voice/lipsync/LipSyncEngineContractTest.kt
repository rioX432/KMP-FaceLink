package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

private const val JAW_OPEN_VALUE = 0.6f

/**
 * Contract tests for the [LipSyncEngine] interface.
 */
class LipSyncEngineContractTest {

    private fun createEngine(): FakeLipSyncEngineForContract = FakeLipSyncEngineForContract()

    @Test
    fun animateFromPhonemesProducesFrames() = runTest {
        val engine = createEngine()
        val phonemes = listOf(
            PhonemeEvent("a", 0L, 100L),
            PhonemeEvent("i", 100L, 200L),
        )
        val frames = engine.animateFromPhonemes(phonemes).toList()
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun animateFromPhonemesFramesContainBlendShapes() = runTest {
        val engine = createEngine()
        val phonemes = listOf(PhonemeEvent("a", 0L, 100L))
        val frames = engine.animateFromPhonemes(phonemes).toList()
        assertTrue(frames.all { it.blendShapes.isNotEmpty() })
    }

    @Test
    fun animateFromAudioProducesFrames() = runTest {
        val engine = createEngine()
        val audio = AudioData(ByteArray(16000), AudioFormat(), durationMs = 1000L)
        val frames = engine.animateFromAudio(audio).toList()
        assertTrue(frames.isNotEmpty())
    }

    @Test
    fun animateFromAudioFramesHaveTimestamps() = runTest {
        val engine = createEngine()
        val audio = AudioData(ByteArray(16000), AudioFormat(), durationMs = 1000L)
        val frames = engine.animateFromAudio(audio).toList()
        // Timestamps should be non-negative and ordered
        for (i in 1 until frames.size) {
            assertTrue(frames[i].timestampMs >= frames[i - 1].timestampMs)
        }
    }

    @Test
    fun releaseIsNoOpByDefault() {
        val engine = createEngine()
        engine.release()
        // Default interface method should not throw
    }

    @Test
    fun emptyPhonemeListProducesNoFrames() = runTest {
        val engine = createEngine()
        val frames = engine.animateFromPhonemes(emptyList()).toList()
        assertTrue(frames.isEmpty())
    }
}

private class FakeLipSyncEngineForContract : LipSyncEngine {
    override fun animateFromPhonemes(
        phonemeEvents: List<PhonemeEvent>,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flow {
        for (event in phonemeEvents) {
            emit(
                LipSyncFrame(
                    blendShapes = mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE),
                    timestampMs = event.startMs,
                ),
            )
        }
    }

    override fun animateFromAudio(
        audio: AudioData,
        config: LipSyncConfig,
    ): Flow<LipSyncFrame> = flow {
        val frameCount = 3
        for (i in 0 until frameCount) {
            emit(
                LipSyncFrame(
                    blendShapes = mapOf(BlendShape.JAW_OPEN to JAW_OPEN_VALUE),
                    timestampMs = i.toLong() * 33L,
                ),
            )
        }
    }
}
