package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.lipsync.internal.PhonemeAnimator
import io.github.kmpfacelink.voice.tts.PhonemeEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PhonemeAnimatorTest {

    @Test
    fun emptyPhonemeEventsProducesNoFrames() = runTest {
        val frames = PhonemeAnimator.animate(
            phonemeEvents = emptyList(),
            config = LipSyncConfig(targetFps = 30),
        ).toList()

        assertTrue(frames.isEmpty())
    }

    @Test
    fun singlePhonemeProducesFrames() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 200),
        )

        val frames = PhonemeAnimator.animate(
            phonemeEvents = events,
            config = LipSyncConfig(targetFps = 30),
        ).toList()

        assertTrue(frames.isNotEmpty())
        // First frame should have AA viseme blend shapes (JAW_OPEN)
        val firstActive = frames.first { it.blendShapes.isNotEmpty() }
        assertTrue(firstActive.blendShapes.containsKey(BlendShape.JAW_OPEN))
    }

    @Test
    fun lastFrameIsSilence() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 100),
        )

        val frames = PhonemeAnimator.animate(
            phonemeEvents = events,
            config = LipSyncConfig(targetFps = 60),
        ).toList()

        assertTrue(frames.isNotEmpty())
        val lastFrame = frames.last()
        assertTrue(lastFrame.blendShapes.isEmpty(), "Last frame should be silence")
    }

    @Test
    fun multiplePhonemeTransitions() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 200),
            PhonemeEvent(phoneme = "u", startMs = 200, endMs = 400),
        )

        val frames = PhonemeAnimator.animate(
            phonemeEvents = events,
            config = LipSyncConfig(targetFps = 30),
        ).toList()

        assertTrue(frames.size > 2, "Should produce multiple frames for 400ms at 30fps")
    }

    @Test
    fun framesHaveIncreasingTimestamps() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 300),
        )

        val frames = PhonemeAnimator.animate(
            phonemeEvents = events,
            config = LipSyncConfig(targetFps = 30),
        ).toList()

        for (i in 1 until frames.size) {
            assertTrue(
                frames[i].timestampMs >= frames[i - 1].timestampMs,
                "Timestamps should be non-decreasing",
            )
        }
    }

    @Test
    fun blendShapeValuesAreInRange() = runTest {
        val events = listOf(
            PhonemeEvent(phoneme = "a", startMs = 0, endMs = 150),
            PhonemeEvent(phoneme = "i", startMs = 150, endMs = 300),
            PhonemeEvent(phoneme = "u", startMs = 300, endMs = 450),
        )

        val frames = PhonemeAnimator.animate(
            phonemeEvents = events,
            config = LipSyncConfig(targetFps = 30),
        ).toList()

        frames.forEach { frame ->
            frame.blendShapes.forEach { (_, value) ->
                assertTrue(value in -0.01f..1.01f, "Blend shape value $value out of range")
            }
        }
    }
}
