@file:Suppress(
    "FunctionNaming", // Backtick names identify the exact tamper case.
    "MagicNumber", // Byte offsets and mutations are integrity-test fixtures.
)

package io.github.kmpfacelink.capture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What tells a take that is still the take from one that only looks like it.
 *
 * Lengths and offsets cannot do it alone: a flipped bit leaves every length lining up, and the frame
 * it produces is a plausible face. Nor can a marker "written last", since a marker cut off part way
 * through used to satisfy a reader that picked its fields out of the text.
 */
class RawFrameIntegrityTest {

    @Test
    fun `the manifest records the hashes of what was actually written`() {
        val dir = tempDir("manifest")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { sink.write(frame(it)) }
        sink.close()

        val manifest = RawFrameReader(dir.absolutePath).manifest
        assertEquals(RawFrameFormat.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(3, manifest.frameCount)
        assertEquals(WIDTH * HEIGHT * RawFrameFormat.BYTES_PER_PIXEL, manifest.bytesPerFrame)
        assertEquals(false, manifest.compressed)
        // Carried in the file so the offline tooling does not have to hardcode this build's enum.
        assertEquals(mapOf("PRE_ROLL" to 1, "NEUTRAL" to 2, "ACTED" to 3), manifest.phaseCodes)
        assertEquals(64, manifest.payloadSha256.length, manifest.payloadSha256)
        assertEquals(64, manifest.indexSha256.length, manifest.indexSha256)
        dir.deleteRecursively()
    }

    @Test
    fun `a single flipped byte in the payload is refused`() {
        // The reason the hashes are there. Every length and offset still lines up afterwards, so
        // this take would otherwise be re-inferred and produce ordinary-looking numbers.
        val dir = tempDir("flipped-byte")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(2) { sink.write(frame(it)) }
        sink.close()

        val payload = File(dir, RawFrameFormat.FRAMES_FILE)
        val bytes = payload.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        payload.writeBytes(bytes)

        val failure = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "the altered payload was accepted: $failure")
        // And the same take still reads if integrity is deliberately not being relied on, so the
        // refusal above is the hash check rather than something structural.
        assertEquals(2, RawFrameReader(dir.absolutePath, verifyHashes = false).frameCount)
        dir.deleteRecursively()
    }

    @Test
    fun `a truncated payload is refused before a single frame is handed out`() {
        // Deferring this into the frame sequence meant a caller that looked at the first few frames
        // never reached it — which is exactly what one does when eyeballing a take.
        val dir = tempDir("truncated")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(3) { sink.write(frame(it)) }
        sink.close()

        val payload = File(dir, RawFrameFormat.FRAMES_FILE)
        payload.writeBytes(payload.readBytes().dropLast(100).toByteArray())

        assertRefusedBothWays(dir, "a truncated payload")
        dir.deleteRecursively()
    }

    @Test
    fun `a payload longer than the manifest accounts for is refused`() {
        val dir = tempDir("overlong")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()

        val payload = File(dir, RawFrameFormat.FRAMES_FILE)
        payload.writeBytes(payload.readBytes() + ByteArray(16))

        assertRefusedBothWays(dir, "a payload with bytes the manifest does not account for")
        dir.deleteRecursively()
    }

    @Test
    fun `an index with a partial trailing record is refused`() {
        // Dividing the index length by the record size accepted N whole records plus a fragment as
        // N records.
        val dir = tempDir("index-fragment")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        repeat(2) { sink.write(frame(it)) }
        sink.close()

        val index = File(dir, RawFrameFormat.INDEX_FILE)
        index.writeBytes(index.readBytes() + ByteArray(RawFrameFormat.INDEX_RECORD_BYTES / 2))

        assertRefusedBothWays(dir, "an index ending in a partial record")
        dir.deleteRecursively()
    }

    @Test
    fun `a manifest missing a field is refused rather than half-read`() {
        val dir = tempDir("manifest-partial")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()

        val marker = File(dir, RawFrameFormat.FRAMES_META_FILE)
        val cut = marker.readText().lines().filterNot { it.contains("payloadSha256") }
        marker.writeText(cut.joinToString("\n"))

        val failure = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(
            failure is IllegalArgumentException,
            "a manifest with no payload hash was accepted: $failure",
        )
        dir.deleteRecursively()
    }

    @Test
    fun `a manifest carrying fields this build does not know is refused`() {
        // A take written by a later build. Reading it as if the extra fields did not matter is how a
        // format change turns into wrong numbers instead of an error.
        val dir = tempDir("manifest-newer")
        val sink = openRawFrameSink(dir.absolutePath, WIDTH, HEIGHT)
        sink.write(frame(0))
        sink.close()

        val marker = File(dir, RawFrameFormat.FRAMES_META_FILE)
        marker.writeText(marker.readText().replaceFirst("{", "{\n  \"downsampleFilter\": \"area\","))

        val failure = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
        dir.deleteRecursively()
    }

    /**
     * Refused with the hashes checked, and refused again without them.
     *
     * The second half is what keeps the structural checks honest. Any change to the bytes also
     * changes a hash, so a test that only looks at the default path passes even if the lengths are
     * never checked — and the lengths are all that stands behind a deliberate quick look.
     */
    private fun assertRefusedBothWays(dir: File, what: String) {
        val hashed = runCatching { RawFrameReader(dir.absolutePath) }.exceptionOrNull()
        assertTrue(hashed is IllegalArgumentException, "$what was accepted: $hashed")
        val structural = runCatching { RawFrameReader(dir.absolutePath, verifyHashes = false) }
            .exceptionOrNull()
        assertTrue(
            structural is IllegalArgumentException,
            "$what was accepted once the hashes were not being checked: $structural",
        )
    }
}
