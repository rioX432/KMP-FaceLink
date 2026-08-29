package io.github.kmpfacelink.capture

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order the frames claim to have arrived in.
 *
 * These are checked on admission rather than left to a reader, because a reader finds them out too
 * late: the performance cannot be acted again. Both sides check them — the sink guards what it was
 * handed, the reader guards what came back off the disk.
 */
class RawFrameOrderTest {

    @Test
    fun `a take alternating hold and neutral is accepted, because that is the script`() {
        // Each take is neutral, hold, back to neutral, hold again. A phase order that only ever
        // moved forwards would refuse the protocol this study actually records.
        val dir = tempDir("script-order")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        val script = listOf(
            CapturePhase.PRE_ROLL,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
        )
        script.forEachIndexed { i, phase -> sink.write(frame(i, phase = phase)) }
        sink.close()

        assertEquals(script, RawFrameReader(dir.absolutePath).frames().map { it.phase }.toList())
        dir.deleteRecursively()
    }

    @Test
    fun `a repeated sequence number invalidates the take`() {
        assertRejected("seq-repeat") { sink ->
            sink.write(frame(7))
            sink.write(frame(7))
        }
    }

    @Test
    fun `a sequence number that goes backwards invalidates the take`() {
        assertRejected("seq-regress") { sink ->
            sink.write(frame(7))
            sink.write(frame(6))
        }
    }

    @Test
    fun `a capture time that goes backwards invalidates the take`() {
        assertRejected("capture-regress") { sink ->
            sink.write(RawFrame(WIDTH, HEIGHT, 0, 2_000L, 0L, CapturePhase.ACTED, pixels(0)))
            sink.write(RawFrame(WIDTH, HEIGHT, 1, 1_000L, 1L, CapturePhase.ACTED, pixels(1)))
        }
    }

    @Test
    fun `an inference time that goes backwards invalidates the take`() {
        assertRejected("inference-regress") { sink ->
            sink.write(RawFrame(WIDTH, HEIGHT, 0, 1_000L, 9L, CapturePhase.ACTED, pixels(0)))
            sink.write(RawFrame(WIDTH, HEIGHT, 1, 2_000L, 8L, CapturePhase.ACTED, pixels(1)))
        }
    }

    @Test
    fun `pre-roll resuming after the take has started invalidates the take`() {
        // Pre-roll exists to rebuild tracking state ahead of the first scored frame, so it cannot
        // reappear in the middle.
        assertRejected("preroll-resumed") { sink ->
            sink.write(frame(0, phase = CapturePhase.PRE_ROLL))
            sink.write(frame(1, phase = CapturePhase.NEUTRAL))
            sink.write(frame(2, phase = CapturePhase.PRE_ROLL))
        }
    }

    @Test
    fun `negative timestamps are refused by the frame itself`() {
        assertTrue(
            runCatching {
                RawFrame(WIDTH, HEIGHT, 0, -1L, 0L, CapturePhase.ACTED, pixels(0))
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                RawFrame(WIDTH, HEIGHT, 0, 0L, -1L, CapturePhase.ACTED, pixels(0))
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `an index offset that does not follow the frames before it is refused`() {
        val dir = tempDir("bad-offset")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { sink.write(frame(it)) }
        sink.close()
        patchIndex(dir, record = 2, byteOffsetInRecord = 24) { putLong(0L) }

        // Hash checking is off so this is the offset check answering, not the hash.
        val failure = runCatching { RawFrameReader(dir.absolutePath, verifyHashes = false) }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
        dir.deleteRecursively()
    }

    @Test
    fun `an unknown phase code is refused rather than guessed at`() {
        val dir = tempDir("bad-phase")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(2) { sink.write(frame(it)) }
        sink.close()
        patchIndex(dir, record = 1, byteOffsetInRecord = 4) { putInt(99) }

        val failure = runCatching { RawFrameReader(dir.absolutePath, verifyHashes = false) }
            .exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
        dir.deleteRecursively()
    }

    @Test
    fun `a zero phase code does not decode as pre-roll`() {
        // What a never-written or zero-filled index record reads as. Codes start at 1 so this is
        // nothing rather than the first enum entry.
        assertEquals(null, CapturePhase.ofCode(0))
        assertEquals(CapturePhase.PRE_ROLL, CapturePhase.ofCode(1))
    }

    /** Writes through [take], then requires that both the call and the take were refused. */
    private fun assertRejected(name: String, take: (RawFrameSink) -> Unit) {
        val dir = tempDir(name)
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)

        val rejected = runCatching { take(sink) }.exceptionOrNull()
        val closed = runCatching { sink.close() }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException, "the frame was accepted: $rejected")
        assertTrue(closed is IllegalStateException, "close() completed a take with a lost frame")
        assertFalse(
            File(dir, RawFrameFormat.FRAMES_META_FILE).exists(),
            "an invalidated take was marked complete",
        )
        dir.deleteRecursively()
    }

    private fun patchIndex(
        dir: File,
        record: Int,
        byteOffsetInRecord: Int,
        write: ByteBuffer.() -> Unit,
    ) {
        val file = File(dir, RawFrameFormat.INDEX_FILE)
        val bytes = file.readBytes()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            position(record * RawFrameFormat.INDEX_RECORD_BYTES + byteOffsetInRecord)
            write()
        }
        file.writeBytes(bytes)
    }
}
