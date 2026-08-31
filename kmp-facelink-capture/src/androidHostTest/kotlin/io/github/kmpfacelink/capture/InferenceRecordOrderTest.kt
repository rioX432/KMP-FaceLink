@file:Suppress(
    "FunctionNaming", // Backtick names identify the exact stream-order invariant.
    "MagicNumber", // Sequence, timestamp, and payload sentinels are fixture data.
)

package io.github.kmpfacelink.capture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The order the results claim to have arrived in.
 *
 * Checked on admission rather than left to a reader for the same reason the frames are: a reader
 * finds them out weeks later, and the performance cannot be acted again. The sequence here is the
 * join key back to the pixels, so a repeated or backwards one does not merely disorder the results
 * — it pairs a result with another frame's face.
 */
class InferenceRecordOrderTest {

    @Test
    fun `a take alternating hold and neutral is accepted, because that is the script`() {
        // Neutral, hold, release, hold. A phase order that only moved forwards would refuse the
        // protocol this study records.
        val dir = tempDir("result-script-order")
        val sink = openInferenceRecordSink(dir.absolutePath)
        val script = listOf(
            CapturePhase.PRE_ROLL,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
        )
        script.forEachIndexed { i, phase -> sink.write(record(i, phase = phase)) }
        sink.close()

        assertEquals(script.size, sink.recordCount)
        dir.deleteRecursively()
    }

    @Test
    fun `sequence gaps are accepted, because the engine skips frames`() {
        // LIVE_STREAM returns results for only some admitted frames. The join is by sequence, not
        // by position, so the gaps are the data rather than a fault.
        val dir = tempDir("result-gaps")
        val sink = openInferenceRecordSink(dir.absolutePath)
        listOf(0, 3, 4, 11).forEach { sink.write(record(it)) }
        sink.close()

        assertEquals(4, sink.recordCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a repeated sequence number invalidates the take`() {
        assertRejected("result-seq-repeat") { sink ->
            sink.write(record(7))
            sink.write(record(7))
        }
    }

    @Test
    fun `a sequence number that goes backwards invalidates the take`() {
        assertRejected("result-seq-regress") { sink ->
            sink.write(record(7))
            sink.write(record(6))
        }
    }

    @Test
    fun `a capture time that goes backwards invalidates the take`() {
        assertRejected("result-capture-regress") { sink ->
            sink.write(record(0, captureNs = 2_000L))
            sink.write(record(1, captureNs = 1_000L))
        }
    }

    @Test
    fun `a result arrival time that goes backwards invalidates the take`() {
        assertRejected("result-arrival-regress") { sink ->
            sink.write(record(0, resultNs = 9_000L))
            sink.write(record(1, resultNs = 8_000L))
        }
    }

    @Test
    fun `a negative capture time invalidates the take`() {
        assertRejected("result-capture-negative") { sink -> sink.write(record(0, captureNs = -1L)) }
    }

    @Test
    fun `a negative result arrival time invalidates the take`() {
        assertRejected("result-arrival-negative") { sink -> sink.write(record(0, resultNs = -1L)) }
    }

    @Test
    fun `a negative sequence number is refused by the record itself`() {
        assertTrue(
            runCatching { record(-1) }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `pre-roll resuming after the take has started invalidates the take`() {
        assertRejected("result-preroll-resumed") { sink ->
            sink.write(record(0, phase = CapturePhase.PRE_ROLL))
            sink.write(record(1, phase = CapturePhase.NEUTRAL))
            sink.write(record(2, phase = CapturePhase.PRE_ROLL))
        }
    }

    @Test
    fun `a refused record is not written to any of the four streams`() {
        // The streams are fixed-stride and position-aligned. A refusal that had already written to
        // one of them would shift every later frame's landmarks by one and still read as valid.
        val dir = tempDir("result-refusal-alignment")
        val sink = openInferenceRecordSink(dir.absolutePath)
        sink.write(record(0))
        runCatching { sink.write(record(0)) }
        runCatching { sink.close() }

        assertEquals(1, sink.recordCount)
        assertEquals(
            InferenceRecordFormat.INDEX_RECORD_BYTES.toLong(),
            File(dir, InferenceRecordFormat.INDEX_FILE).length(),
        )
        assertEquals(
            (InferenceRecordFormat.BLENDSHAPE_COUNT * Float.SIZE_BYTES).toLong(),
            File(dir, InferenceRecordFormat.BLENDSHAPES_FILE).length(),
        )
        dir.deleteRecursively()
    }

    /** Writes through [take], then requires that both the call and the take were refused. */
    private fun assertRejected(name: String, take: (InferenceRecordSink) -> Unit) {
        val dir = tempDir(name)
        val sink = openInferenceRecordSink(dir.absolutePath)

        val rejected = runCatching { take(sink) }.exceptionOrNull()
        val closed = runCatching { sink.close() }.exceptionOrNull()

        assertTrue(rejected is IllegalArgumentException, "the record was accepted: $rejected")
        assertTrue(closed is IllegalStateException, "close() completed a take with a lost result")
        dir.deleteRecursively()
    }

    private fun record(
        seq: Int,
        captureNs: Long = 1_000_000L * seq,
        resultNs: Long = 1_000_000L * seq + 1,
        phase: CapturePhase = CapturePhase.ACTED,
    ): InferenceRecord = InferenceRecord(
        analysisFrameSeq = seq,
        captureTimestampNs = captureNs,
        resultElapsedNs = resultNs,
        phase = phase,
        hasFace = true,
        inferenceFailed = false,
        // Synthetic, not recorded geometry: a face's landmarks are biometric data and do not go
        // into a fixture.
        landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS) { it * 1e-4f },
        blendshapes = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT) { it * 1e-2f },
        transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS) { it.toFloat() },
    )
}
