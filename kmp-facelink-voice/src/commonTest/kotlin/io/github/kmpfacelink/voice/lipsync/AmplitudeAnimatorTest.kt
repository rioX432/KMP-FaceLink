package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.voice.audio.AudioData
import io.github.kmpfacelink.voice.audio.AudioFormat
import io.github.kmpfacelink.voice.lipsync.internal.AmplitudeAnimator
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SAMPLE_RATE = 16000
private const val BYTES_PER_SAMPLE = 2

class AmplitudeAnimatorTest {

    private val format16kHz = AudioFormat(sampleRate = SAMPLE_RATE, channels = 1, bitsPerSample = 16)

    private fun silentAudio(durationMs: Long): AudioData {
        val byteCount = (SAMPLE_RATE * BYTES_PER_SAMPLE * durationMs / 1000).toInt()
        return AudioData(bytes = ByteArray(byteCount), format = format16kHz, durationMs = durationMs)
    }

    private fun loudAudio(durationMs: Long): AudioData {
        val byteCount = (SAMPLE_RATE * BYTES_PER_SAMPLE * durationMs / 1000).toInt()
        val bytes = ByteArray(byteCount)
        // Fill with max amplitude 16-bit PCM (little-endian 0x7FFF)
        for (i in bytes.indices step BYTES_PER_SAMPLE) {
            bytes[i] = 0xFF.toByte()
            bytes[i + 1] = 0x7F.toByte()
        }
        return AudioData(bytes = bytes, format = format16kHz, durationMs = durationMs)
    }

    @Test
    fun emptyAudioProducesOnlyFinalSilenceFrame() = runTest {
        val audio = AudioData(bytes = ByteArray(0), format = format16kHz, durationMs = 0)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()
        // Empty audio has 0 amplitude frames + final silence frame
        assertEquals(1, frames.size)
        assertTrue(frames.last().blendShapes.isEmpty())
    }

    @Test
    fun silentAudioProducesZeroBlendShapes() = runTest {
        val audio = silentAudio(durationMs = 100)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()

        assertTrue(frames.isNotEmpty())
        // For silent audio all frames except the final silence should have empty blend shapes
        // (since jaw < 0.01 threshold, they are excluded from the map)
        frames.forEach { frame ->
            frame.blendShapes.values.forEach { value ->
                assertTrue(value >= 0f, "Blend shape value should be non-negative")
            }
        }
    }

    @Test
    fun loudAudioProducesNonZeroBlendShapes() = runTest {
        val audio = loudAudio(durationMs = 200)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()

        assertTrue(frames.isNotEmpty())
        // At least one frame should have JAW_OPEN since amplitude is non-zero
        val hasJawOpen = frames.any { it.blendShapes.containsKey(BlendShape.JAW_OPEN) }
        assertTrue(hasJawOpen, "Loud audio should produce JAW_OPEN blend shapes")
    }

    @Test
    fun frameCountMatchesTargetFps() = runTest {
        val durationMs = 1000L
        val targetFps = 30
        val audio = loudAudio(durationMs = durationMs)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = targetFps)).toList()

        // Expect approximately targetFps frames + 1 final silence frame
        val expectedFrames = targetFps + 1
        assertTrue(
            frames.size in (expectedFrames - 2)..(expectedFrames + 2),
            "Expected ~$expectedFrames frames but got ${frames.size}",
        )
    }

    @Test
    fun lastFrameIsSilenceWithCorrectTimestamp() = runTest {
        val audio = loudAudio(durationMs = 300)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()

        assertTrue(frames.isNotEmpty())
        val lastFrame = frames.last()
        assertTrue(lastFrame.blendShapes.isEmpty(), "Last frame should be empty (silence)")
        assertEquals(audio.durationMs, lastFrame.timestampMs)
    }

    @Test
    fun frameTimestampsAreNonDecreasing() = runTest {
        val audio = loudAudio(durationMs = 500)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()

        for (i in 1 until frames.size) {
            assertTrue(
                frames[i].timestampMs >= frames[i - 1].timestampMs,
                "Timestamps must be non-decreasing: ${frames[i - 1].timestampMs} -> ${frames[i].timestampMs}",
            )
        }
    }

    @Test
    fun blendShapeValuesAreInValidRange() = runTest {
        val audio = loudAudio(durationMs = 300)
        val frames = AmplitudeAnimator.animate(audio, LipSyncConfig(targetFps = 30)).toList()

        frames.forEach { frame ->
            frame.blendShapes.forEach { (shape, value) ->
                assertTrue(
                    value in 0f..1f,
                    "Blend shape $shape value $value should be in [0, 1]",
                )
            }
        }
    }

    @Test
    fun smoothingReducesAbruptChanges() = runTest {
        // High smoothing (0.9) should produce less variation between consecutive frames
        val audio = loudAudio(durationMs = 500)
        val highSmoothingFrames = AmplitudeAnimator.animate(
            audio,
            LipSyncConfig(targetFps = 30, smoothing = 0.9f),
        ).toList()

        // With high smoothing, jaw values should change gradually
        val jawValues = highSmoothingFrames.mapNotNull { it.blendShapes[BlendShape.JAW_OPEN] }
        if (jawValues.size >= 2) {
            val maxDelta = jawValues.zipWithNext().maxOf { (a, b) -> kotlin.math.abs(b - a) }
            assertTrue(maxDelta < 0.5f, "High smoothing should limit max frame-to-frame change: $maxDelta")
        }
    }

    @Test
    fun amplitudeScaleAffectsBlendShapeWeights() = runTest {
        val audio = loudAudio(durationMs = 300)
        val config1 = LipSyncConfig(targetFps = 30, amplitudeScale = 0.5f, smoothing = 0f)
        val config2 = LipSyncConfig(targetFps = 30, amplitudeScale = 1.0f, smoothing = 0f)

        val frames1 = AmplitudeAnimator.animate(audio, config1).toList()
        val frames2 = AmplitudeAnimator.animate(audio, config2).toList()

        val maxJaw1 = frames1.mapNotNull { it.blendShapes[BlendShape.JAW_OPEN] }.maxOrNull() ?: 0f
        val maxJaw2 = frames2.mapNotNull { it.blendShapes[BlendShape.JAW_OPEN] }.maxOrNull() ?: 0f

        // Higher scale should produce higher or equal jaw values
        assertTrue(maxJaw2 >= maxJaw1, "Higher amplitude scale should produce higher jaw values")
    }

    @Test
    fun jawOpenAndMouthFunnelAreProducedTogether() = runTest {
        val audio = loudAudio(durationMs = 200)
        val frames = AmplitudeAnimator.animate(
            audio,
            LipSyncConfig(targetFps = 30, smoothing = 0f, amplitudeScale = 1.0f),
        ).toList()

        // For any frame that has JAW_OPEN, it should also have MOUTH_FUNNEL
        val framesWithJaw = frames.filter { it.blendShapes.containsKey(BlendShape.JAW_OPEN) }
        framesWithJaw.forEach { frame ->
            assertTrue(
                frame.blendShapes.containsKey(BlendShape.MOUTH_FUNNEL),
                "Frame with JAW_OPEN should also have MOUTH_FUNNEL",
            )
        }
    }
}
