package io.github.kmpfacelink.capture

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The point of recording frames is that a take can be re-inferred later on the exact pixels the
 * device saw, and joined back to what the device produced from them. So the tests here are the
 * round trip and the identity — not "the writer uses a lossless format", which would prove nothing.
 *
 * The lifecycle — everything about how a take can be reported complete when it is not — is in
 * [RawFrameSinkLifecycleTest].
 */
class RawFrameSinkTest {

    @Test
    fun `frames survive the round trip byte for byte, with their identity`() {
        val dir = tempDir("roundtrip")
        // Non-contiguous sequences. The recorder itself numbers at analyzer admission and stores
        // every admitted frame, so its own output is contiguous; the format must not depend on
        // that, since the reader joins by sequence rather than by position.
        val seqs = listOf(0, 1, 4, 7)
        val written = seqs.map { frame(it) }

        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        written.forEach(sink::write)
        sink.close()

        val read = RawFrameReader(dir.absolutePath).frames().toList()
        assertEquals(written.size, read.size)
        written.zip(read).forEachIndexed { i, (w, r) ->
            assertContentEquals(w.copyPixels(), r.copyPixels(), "frame $i pixels differ")
            assertEquals(w.analysisFrameSeq, r.analysisFrameSeq, "frame $i seq differs")
            assertEquals(w.captureTimestampNs, r.captureTimestampNs)
            assertEquals(w.inferenceTimestampMs, r.inferenceTimestampMs)
            assertEquals(w.phase, r.phase)
        }
        dir.deleteRecursively()
    }

    @Test
    fun `a producer reusing its buffer cannot corrupt a stored frame`() {
        // This is the bug the first implementation had: the caller's array was handed to the writer
        // thread, and a camera analyzer reuses its buffer. The result was valid output holding a
        // later frame's pixels, which no checksum or length check can catch.
        val dir = tempDir("aliasing-in")
        val original = pixels(seed = 42)
        val reused = original.copyOf()

        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(
            RawFrame(WIDTH, HEIGHT, 0, 0L, 0L, CapturePhase.ACTED, reused),
        )
        // Overwrite immediately, as the next camera frame would.
        Random(999).nextBytes(reused)
        sink.close()

        val stored = RawFrameReader(dir.absolutePath).frames().single()
        assertContentEquals(original, stored.copyPixels(), "the sink stored the overwritten buffer")
        dir.deleteRecursively()
    }

    @Test
    fun `an accepted frame cannot be repainted through anything the caller still holds`() {
        // Copying on construction closed only the producer's door. The frame itself stayed
        // reachable, so a caller could repaint a frame the writer thread had not yet put on disk —
        // and the test above could not see that, because it overwrote the caller's array rather
        // than the frame. Everything still reachable from a written frame is exercised here.
        val dir = tempDir("aliasing-out")
        val original = pixels(seed = 42)
        val written = frame(0, seed = 42)

        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(written)
        Random(999).nextBytes(written.copyPixels())
        sink.close()

        val stored = RawFrameReader(dir.absolutePath).frames().single()
        assertEquals(original.size, written.pixelByteCount)
        assertContentEquals(original, stored.copyPixels(), "the sink stored repainted pixels")
        dir.deleteRecursively()
    }

    @Test
    fun `phases survive so pre-roll can be replayed and excluded from scoring`() {
        val dir = tempDir("phases")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0, phase = CapturePhase.PRE_ROLL))
        sink.write(frame(1, phase = CapturePhase.NEUTRAL))
        sink.write(frame(2, phase = CapturePhase.ACTED))
        sink.close()

        assertEquals(
            listOf(CapturePhase.PRE_ROLL, CapturePhase.NEUTRAL, CapturePhase.ACTED),
            RawFrameReader(dir.absolutePath).frames().map { it.phase }.toList(),
        )
        dir.deleteRecursively()
    }

    @Test
    fun `the sidecar records geometry and totals`() {
        val dir = tempDir("meta")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { sink.write(frame(it)) }
        sink.close()

        val meta = File(dir, RawFrameFormat.FRAMES_META_FILE).readText()
        assertTrue(meta.contains("\"schemaVersion\": ${RawFrameFormat.SCHEMA_VERSION}"), meta)
        assertTrue(meta.contains("\"frameCount\": 3"), meta)
        assertTrue(meta.contains("\"compressed\": false"), meta)
        dir.deleteRecursively()
    }

    @Test
    fun `a frame whose geometry changes mid-take invalidates the whole take`() {
        // Refusing the call alone is not enough: the sink cannot show that inference did not see
        // the frame it turned away, so a caller that swallowed the rejection would close a take
        // that silently lacks a scored frame.
        val dir = tempDir("mismatch")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))

        val rejected = runCatching {
            sink.write(
                RawFrame(
                    HEIGHT, WIDTH, 1, 0L, 0L, CapturePhase.ACTED,
                    ByteArray(WIDTH * HEIGHT * RawFrameFormat.BYTES_PER_PIXEL),
                ),
            )
        }.exceptionOrNull()
        val closed = runCatching { sink.close() }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException, "got $rejected")
        assertTrue(closed is IllegalStateException, "close() completed a take with a lost frame")
        assertFalse(File(dir, RawFrameFormat.FRAMES_META_FILE).exists())
        dir.deleteRecursively()
    }

    @Test
    fun `a take with no sidecar is refused rather than guessed at`() {
        val dir = tempDir("nosidecar")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()
        File(dir, RawFrameFormat.FRAMES_META_FILE).delete()

        val failure = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
        dir.deleteRecursively()
    }

    @Test
    fun `pixel count must match the declared geometry`() {
        val failure = runCatching {
            RawFrame(4, 4, 0, 0L, 0L, CapturePhase.ACTED, ByteArray(4 * 4 * 3))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }
}
