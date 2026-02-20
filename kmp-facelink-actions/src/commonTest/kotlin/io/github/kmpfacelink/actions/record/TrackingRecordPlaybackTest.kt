package io.github.kmpfacelink.actions.record

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingRecordPlaybackTest {

    // --- TrackingSession ---

    @Test
    fun emptySessionHasZeroDuration() {
        val session = TrackingSession(emptyList())
        assertEquals(0L, session.durationMs)
        assertEquals(0, session.frameCount)
        assertTrue(session.isEmpty)
        assertEquals(0f, session.averageFps)
    }

    @Test
    fun singleFrameSessionHasZeroDuration() {
        val session = TrackingSession(listOf(frame(0L)))
        assertEquals(0L, session.durationMs)
        assertEquals(1, session.frameCount)
        assertFalse(session.isEmpty)
    }

    @Test
    fun sessionDurationIsLastMinusFirst() {
        val session = TrackingSession(listOf(frame(100L), frame(200L), frame(300L)))
        assertEquals(200L, session.durationMs)
        assertEquals(3, session.frameCount)
        assertEquals(100L, session.startTimestampMs)
        assertEquals(300L, session.endTimestampMs)
    }

    @Test
    fun averageFpsCalculation() {
        // 3 frames over 100ms = 2 intervals in 0.1s = 20 fps
        val session = TrackingSession(listOf(frame(0L), frame(50L), frame(100L)))
        assertEquals(20f, session.averageFps, 0.01f)
    }

    // --- TrackingRecorder ---

    @Test
    fun recorderStartsInactive() {
        val recorder = TrackingRecorder()
        assertFalse(recorder.isRecording)
        assertEquals(0, recorder.currentFrameCount)
    }

    @Test
    fun recorderRecordsFrames() = runTest {
        val recorder = TrackingRecorder()
        recorder.start()
        assertTrue(recorder.isRecording)

        recorder.record(frame(0L))
        recorder.record(frame(100L))
        assertEquals(2, recorder.currentFrameCount)

        val session = recorder.stop()
        assertFalse(recorder.isRecording)
        assertEquals(2, session.frameCount)
    }

    @Test
    fun recorderSkipsNotTrackingFrames() = runTest {
        val recorder = TrackingRecorder()
        recorder.start()

        recorder.record(frame(0L))
        recorder.record(FaceTrackingData.notTracking(50L))
        recorder.record(frame(100L))

        val session = recorder.stop()
        assertEquals(2, session.frameCount)
    }

    @Test
    fun recorderRespectsMaxFrames() = runTest {
        val recorder = TrackingRecorder(maxFrames = 2)
        recorder.start()

        recorder.record(frame(0L))
        recorder.record(frame(100L))
        recorder.record(frame(200L)) // should be dropped

        val session = recorder.stop()
        assertEquals(2, session.frameCount)
    }

    @Test
    fun recorderDoesNotRecordWhenStopped() = runTest {
        val recorder = TrackingRecorder()
        recorder.record(frame(0L))
        assertEquals(0, recorder.currentFrameCount)
    }

    @Test
    fun recorderClearRemovesFrames() = runTest {
        val recorder = TrackingRecorder()
        recorder.start()
        recorder.record(frame(0L))
        recorder.record(frame(100L))
        assertEquals(2, recorder.currentFrameCount)

        recorder.clear()
        assertEquals(0, recorder.currentFrameCount)
    }

    @Test
    fun flowRecordExtension() = runTest {
        val recorder = TrackingRecorder()
        recorder.start()

        val frames = listOf(frame(0L), frame(100L), frame(200L))
        val collected = flowOf(*frames.toTypedArray())
            .record(recorder)
            .toList()

        // Flow passes data through unchanged
        assertEquals(3, collected.size)
        // Recorder captured the frames
        val session = recorder.stop()
        assertEquals(3, session.frameCount)
    }

    // --- TrackingPlayer ---

    @Test
    fun playerEmitsAllFrames() = runTest {
        val session = TrackingSession(listOf(frame(0L), frame(10L), frame(20L)))
        val played = session.play(speed = 100f).toList() // fast playback for test
        assertEquals(3, played.size)
        assertEquals(0L, played[0].timestampMs)
        assertEquals(10L, played[1].timestampMs)
        assertEquals(20L, played[2].timestampMs)
    }

    @Test
    fun playerEmitsNothingForEmptySession() = runTest {
        val session = TrackingSession(emptyList())
        val played = session.play().toList()
        assertTrue(played.isEmpty())
    }

    @Test
    fun playedDataMatchesRecordedData() = runTest {
        val original = listOf(
            frame(0L, BlendShape.MOUTH_SMILE_LEFT to 0.8f),
            frame(10L, BlendShape.JAW_OPEN to 0.5f),
        )
        val session = TrackingSession(original)
        val played = session.play(speed = 100f).toList()

        assertEquals(original.size, played.size)
        for (i in original.indices) {
            assertEquals(original[i].blendShapes, played[i].blendShapes)
            assertEquals(original[i].timestampMs, played[i].timestampMs)
        }
    }

    // --- Helpers ---

    private fun frame(
        timestampMs: Long,
        vararg blendShapes: Pair<BlendShape, Float>,
    ): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = true,
        )
    }
}
