package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosInferenceRecordOrderTest {
    @Test
    fun `sequence gaps and alternating phases are accepted`() {
        val directory = iosTestDirectory("result-order")
        val sink = openInferenceRecordSink(directory)
        val records = listOf(
            iosTestInferenceRecord(0, phase = CapturePhase.PRE_ROLL),
            iosTestInferenceRecord(3, phase = CapturePhase.NEUTRAL),
            iosTestInferenceRecord(4, phase = CapturePhase.ACTED),
            iosTestInferenceRecord(11, phase = CapturePhase.NEUTRAL),
        )
        records.forEach(sink::write)
        sink.close()

        assertEquals(records.size, sink.recordCount)
        removeCapturePath(directory)
    }

    @Test
    fun `repeated result sequence invalidates the take`() {
        assertRejected("result-sequence") { sink ->
            sink.write(iosTestInferenceRecord(7))
            sink.write(iosTestInferenceRecord(7))
        }
    }

    @Test
    fun `result arrival moving backwards invalidates the take`() {
        assertRejected("result-time") { sink ->
            sink.write(iosTestInferenceRecord(0, resultNs = 9_000L))
            sink.write(iosTestInferenceRecord(1, resultNs = 8_000L))
        }
    }

    private fun assertRejected(name: String, writes: (InferenceRecordSink) -> Unit) {
        val directory = iosTestDirectory(name)
        val sink = openInferenceRecordSink(directory)
        assertTrue(runCatching { writes(sink) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { sink.close() }.exceptionOrNull() is IllegalStateException)
        removeCapturePath(directory)
    }
}
