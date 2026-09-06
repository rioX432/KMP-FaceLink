package io.github.kmpfacelink.capture.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.capture.CapturePhase
import io.github.kmpfacelink.capture.InferenceRecord
import io.github.kmpfacelink.capture.InferenceRecordFormat
import io.github.kmpfacelink.capture.InferenceRecordReader
import io.github.kmpfacelink.capture.RawFrame
import io.github.kmpfacelink.capture.RawFrameFormat
import io.github.kmpfacelink.capture.RawFrameReader
import io.github.kmpfacelink.capture.openInferenceRecordSink
import io.github.kmpfacelink.capture.openRawFrameSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * The recording format, on the device that will record with it.
 *
 * Runs without the camera on purpose. What it exercises is the part that has to be right before a
 * face is ever in front of the phone: that the frames come back byte for byte, that the four result
 * streams stay aligned with them, and that the join key survives frames the engine declined to run.
 * A sparse result stream is the normal case in LIVE_STREAM, so it is what the test builds.
 */
@RunWith(AndroidJUnit4::class)
class TakeFormatOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val width = 32
    private val height = 24

    @Test
    fun aSparseTakeSurvivesTheRoundTripAndStaysJoinable() {
        val dir = File(context.cacheDir, "take-format-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val framesDir = File(dir, "frames").apply { mkdirs() }

        // Ten admitted frames; the engine answered for four of them.
        val admitted = (0 until 10).toList()
        val answered = listOf(0, 1, 4, 9)
        val pixels = admitted.associateWith { seq ->
            Random(seq).nextBytes(width * height * RawFrameFormat.BYTES_PER_PIXEL)
        }

        val frames = openRawFrameSink(framesDir.absolutePath, width, height)
        val results = openInferenceRecordSink(dir.absolutePath)
        admitted.forEach { seq ->
            val phase = if (seq < 2) CapturePhase.PRE_ROLL else CapturePhase.ACTED
            frames.write(
                RawFrame(
                    width = width,
                    height = height,
                    analysisFrameSeq = seq,
                    captureTimestampNs = 1_000_000L * seq,
                    inferenceTimestampMs = seq.toLong(),
                    phase = phase,
                    pixels = pixels.getValue(seq),
                ),
            )
            if (seq !in answered) return@forEach
            val hasFace = seq != 4 // one result with no face, which the streams zero-fill
            results.write(
                InferenceRecord(
                    analysisFrameSeq = seq,
                    captureTimestampNs = 1_000_000L * seq,
                    resultElapsedNs = 2_000_000L * seq,
                    phase = phase,
                    hasFace = hasFace,
                    inferenceFailed = false,
                    landmarks = if (hasFace) FloatArray(InferenceRecordFormat.LANDMARK_FLOATS) { it * 0.001f } else null,
                    blendshapes = if (hasFace) FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT) { it * 0.01f } else null,
                    transform = if (hasFace) FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS) { it.toFloat() } else null,
                ),
            )
        }
        frames.close()
        results.close()

        val readFrames = RawFrameReader(framesDir.absolutePath).frames().toList()
        assertEquals(admitted.size, readFrames.size)
        readFrames.forEach { frame ->
            assertTrue(
                "frame ${frame.analysisFrameSeq} came back with different pixels",
                pixels.getValue(frame.analysisFrameSeq).contentEquals(frame.copyPixels()),
            )
        }

        val readResults = InferenceRecordReader(dir.absolutePath)
        assertEquals(answered, readResults.headers.map { it.analysisFrameSeq })
        // Joining by position instead of by sequence would pair frame 4 with result 2.
        answered.forEachIndexed { index, seq ->
            assertEquals(seq, readResults.headers[index].analysisFrameSeq)
            if (seq == 4) {
                assertNull("a result with no face must not hand back its zero-fill", readResults.blendshapesAt(index))
            } else {
                assertNotNull(readResults.blendshapesAt(index))
                assertEquals(
                    InferenceRecordFormat.LANDMARK_FLOATS,
                    readResults.landmarksAt(index)!!.size,
                )
            }
        }
        dir.deleteRecursively()
    }
}
