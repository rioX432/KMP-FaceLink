@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosRawFrameOrderTest {
    @Test
    fun `alternating acted and neutral phases are accepted`() {
        val directory = iosTestDirectory("script-order")
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        val phases = listOf(
            CapturePhase.PRE_ROLL,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
            CapturePhase.NEUTRAL,
            CapturePhase.ACTED,
        )
        phases.forEachIndexed { index, phase -> sink.write(iosTestFrame(index, phase = phase)) }
        sink.close()

        val index = readIosTestFile(joinCapturePath(directory, RawFrameFormat.INDEX_FILE))
        val stored = phases.indices.map { position ->
            index.intLittleEndian(position * RawFrameFormat.INDEX_RECORD_BYTES + Int.SIZE_BYTES)
        }
        assertEquals(phases.map(CapturePhase::code), stored)
        removeCapturePath(directory)
    }

    @Test
    fun `repeated sequence invalidates the take`() {
        assertRejected("sequence") { sink ->
            sink.write(iosTestFrame(7))
            sink.write(iosTestFrame(7))
        }
    }

    @Test
    fun `sequence moving backwards invalidates the take`() {
        assertRejected("sequence-backwards") { sink ->
            sink.write(iosTestFrame(7))
            sink.write(iosTestFrame(6))
        }
    }

    @Test
    fun `timestamps moving backwards invalidate the take`() {
        assertRejected("timestamp") { sink ->
            sink.write(
                RawFrame(
                    IOS_TEST_WIDTH,
                    IOS_TEST_HEIGHT,
                    0,
                    2_000L,
                    2L,
                    CapturePhase.ACTED,
                    iosTestPixels(0),
                ),
            )
            sink.write(
                RawFrame(
                    IOS_TEST_WIDTH,
                    IOS_TEST_HEIGHT,
                    1,
                    1_000L,
                    1L,
                    CapturePhase.ACTED,
                    iosTestPixels(1),
                ),
            )
        }
    }

    @Test
    fun `inference timestamps moving backwards invalidate the take`() {
        assertRejected("inference-time") { sink ->
            sink.write(
                RawFrame(
                    IOS_TEST_WIDTH,
                    IOS_TEST_HEIGHT,
                    0,
                    1_000L,
                    9L,
                    CapturePhase.ACTED,
                    iosTestPixels(0),
                ),
            )
            sink.write(
                RawFrame(
                    IOS_TEST_WIDTH,
                    IOS_TEST_HEIGHT,
                    1,
                    2_000L,
                    8L,
                    CapturePhase.ACTED,
                    iosTestPixels(1),
                ),
            )
        }
    }

    @Test
    fun `pre-roll cannot resume after the take begins`() {
        assertRejected("pre-roll") { sink ->
            sink.write(iosTestFrame(0, phase = CapturePhase.PRE_ROLL))
            sink.write(iosTestFrame(1, phase = CapturePhase.NEUTRAL))
            sink.write(iosTestFrame(2, phase = CapturePhase.PRE_ROLL))
        }
    }

    private fun assertRejected(name: String, writes: (RawFrameSink) -> Unit) {
        val directory = iosTestDirectory(name)
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        assertTrue(runCatching { writes(sink) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { sink.close() }.exceptionOrNull() is IllegalStateException)
        assertFalse(
            platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(
                joinCapturePath(directory, RawFrameFormat.FRAMES_META_FILE),
            ),
        )
        removeCapturePath(directory)
    }
}
