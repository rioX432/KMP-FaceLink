package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosRawFrameIntegrityTest {
    @Test
    fun `manifest hashes exactly what was written`() {
        val directory = iosTestDirectory("integrity")
        val sink = openRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)
        repeat(3) { sink.write(iosTestFrame(it)) }
        sink.close()

        val manifest = readIosManifest(directory)
        val payloadPath = joinCapturePath(directory, RawFrameFormat.FRAMES_FILE)
        val indexPath = joinCapturePath(directory, RawFrameFormat.INDEX_FILE)
        assertEquals(sha256OfCaptureFile(payloadPath), manifest.payloadSha256)
        assertEquals(sha256OfCaptureFile(indexPath), manifest.indexSha256)

        val altered = readIosTestFile(payloadPath)
        altered[altered.size / 2] = (altered[altered.size / 2].toInt() xor 1).toByte()
        writeIosTestFile(payloadPath, altered)
        assertNotEquals(manifest.payloadSha256, sha256OfCaptureFile(payloadPath))
        removeCapturePath(directory)
    }
}
