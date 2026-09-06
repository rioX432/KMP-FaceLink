package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosRawFrameSinkLifecycleTest {
    @Test
    fun `close is idempotent and later writes fail`() {
        val directory = iosTestDirectory("close")
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        sink.write(iosTestFrame(0))
        sink.close()
        sink.close()
        assertTrue(runCatching { sink.write(iosTestFrame(1)) }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, readIosManifest(directory).frameCount)
        removeCapturePath(directory)
    }

    @Test
    fun `two sinks cannot truncate one directory`() {
        val directory = iosTestDirectory("claim")
        val first = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        assertTrue(
            runCatching {
                openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
            }.exceptionOrNull() is IllegalStateException,
        )
        first.close()
        val second = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        second.close()
        removeCapturePath(directory)
    }

    @Test
    fun `geometry mismatch fails this call and the whole take`() {
        val directory = iosTestDirectory("geometry")
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        val mismatch = RawFrame(
            IOS_TEST_HEIGHT,
            IOS_TEST_WIDTH,
            0,
            0L,
            0L,
            CapturePhase.ACTED,
            ByteArray(IOS_TEST_WIDTH * IOS_TEST_HEIGHT * RawFrameFormat.BYTES_PER_PIXEL),
        )
        assertTrue(runCatching { sink.write(mismatch) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { sink.close() }.exceptionOrNull() is IllegalStateException)
        val retry = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        retry.close()
        removeCapturePath(directory)
    }

    @Test
    fun `opening a new take removes an earlier completion marker before truncation`() {
        val directory = iosTestDirectory("stale-marker")
        val first = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        first.write(iosTestFrame(0))
        first.close()

        val second = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        val marker = joinCapturePath(directory, RawFrameFormat.FRAMES_META_FILE)
        assertTrue(!platform.Foundation.NSFileManager.defaultManager.fileExistsAtPath(marker))
        second.close()
        assertEquals(0, readIosManifest(directory).frameCount)
        removeCapturePath(directory)
    }
}
