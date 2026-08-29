// The platform suffix is this module's file-naming convention for actuals, so the reader's file name
// deliberately does not match the class it holds.
@file:Suppress("Filename", "MatchingDeclarationName")

package io.github.kmpfacelink.capture

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Reads back a take written by [openRawFrameSink], so its frames can be re-inferred under a
 * different condition than the one that captured them.
 *
 * Frames come back with their identity attached. That is the point: the recording covers every
 * frame the analyzer admitted, while LIVE_STREAM returns results for only some of them, so the
 * results are a sparse sequence over the same timeline and only [RawFrame.analysisFrameSeq] joins
 * the two. Reading positionally would silently pair frame 4 with result 2.
 *
 * **Everything except the pixels is checked before this constructor returns.** Deferring the checks
 * into the frame sequence meant a caller that looked at the first few frames — which is what one
 * does when eyeballing a take — never reached them. A take that fails any check is refused whole:
 * re-inferring part of a damaged take produces numbers that look ordinary.
 *
 * @param verifyHashes re-reads both files to check them against the manifest. On by default. Pass
 *   false only for a deliberate quick look at a take whose integrity is not being relied on
 */
public class RawFrameReader(
    directory: String,
    verifyHashes: Boolean = true,
) {

    private val dir = File(directory)
    private val payloadFile = File(dir, RawFrameFormat.FRAMES_FILE)
    private val indexFile = File(dir, RawFrameFormat.INDEX_FILE)

    /** What the take says about itself, checked against what is on disk. */
    public val manifest: RawFrameManifest

    public val width: Int get() = manifest.width
    public val height: Int get() = manifest.height
    public val frameCount: Int get() = manifest.frameCount

    private val records: ByteArray

    init {
        manifest = readManifest()
        checkFormat()
        records = readIndexRecords()
        if (verifyHashes) checkHashes()
        checkRecords()
    }

    private fun readManifest(): RawFrameManifest {
        val file = File(dir, RawFrameFormat.FRAMES_META_FILE)
        require(file.isFile) {
            "${file.path} is missing. The take was interrupted before the sink closed, so its " +
                "frame payload cannot be split reliably."
        }
        val parsed = runCatching { rawFrameJson.decodeFromString<RawFrameManifest>(file.readText()) }
        val manifest = parsed.getOrElse {
            // A manifest that will not parse is not a take. It used to be pattern-matched, which
            // accepted one cut off part way through.
            throw IllegalArgumentException("${file.path} is not a readable take manifest", it)
        }
        require(manifest.schemaVersion == RawFrameFormat.SCHEMA_VERSION) {
            "take was written with frame schema ${manifest.schemaVersion}, this reader speaks " +
                "${RawFrameFormat.SCHEMA_VERSION}"
        }
        return manifest
    }

    /** Everything the reader assumes about the bytes, stated as checks rather than as assumptions. */
    private fun checkFormat() {
        require(manifest.pixelFormat == RawFrameFormat.PIXEL_FORMAT) {
            "take holds ${manifest.pixelFormat}, this reader decodes ${RawFrameFormat.PIXEL_FORMAT}"
        }
        require(manifest.bytesPerPixel == RawFrameFormat.BYTES_PER_PIXEL) {
            "take claims ${manifest.bytesPerPixel} bytes per pixel, the format is " +
                "${RawFrameFormat.BYTES_PER_PIXEL}"
        }
        require(!manifest.compressed) { "take is compressed; this reader reads raw payloads only" }
        require(manifest.indexRecordBytes == RawFrameFormat.INDEX_RECORD_BYTES) {
            "take has ${manifest.indexRecordBytes}-byte index records, this reader reads " +
                "${RawFrameFormat.INDEX_RECORD_BYTES}"
        }
        require(manifest.width > 0 && manifest.height > 0) {
            "take geometry is ${manifest.width}x${manifest.height}"
        }
        require(manifest.frameCount >= 0) { "take claims ${manifest.frameCount} frames" }
        val expectedFrameBytes =
            manifest.width.toLong() * manifest.height.toLong() * manifest.bytesPerPixel
        require(manifest.bytesPerFrame.toLong() == expectedFrameBytes) {
            "take claims ${manifest.bytesPerFrame} bytes per frame, but " +
                "${manifest.width}x${manifest.height} at ${manifest.bytesPerPixel} is " +
                "$expectedFrameBytes"
        }
        require(manifest.phaseCodes == capturePhaseCodes()) {
            "take was written with phase codes ${manifest.phaseCodes}, this build uses " +
                "${capturePhaseCodes()}"
        }

        require(payloadFile.isFile) { "${payloadFile.path} is missing" }
        require(indexFile.isFile) { "${indexFile.path} is missing" }
        // Exact lengths, not minimums: a payload longer than the manifest accounts for means the
        // files do not describe one take, and a partial trailing index record used to be accepted
        // as a whole one because the count was computed by dividing.
        val expectedPayload = manifest.frameCount.toLong() * manifest.bytesPerFrame
        require(payloadFile.length() == expectedPayload) {
            "${payloadFile.path} holds ${payloadFile.length()} bytes, the manifest accounts for " +
                "$expectedPayload"
        }
        val expectedIndex = manifest.frameCount.toLong() * manifest.indexRecordBytes
        require(indexFile.length() == expectedIndex) {
            "${indexFile.path} holds ${indexFile.length()} bytes, the manifest accounts for " +
                "$expectedIndex"
        }
    }

    private fun readIndexRecords(): ByteArray = indexFile.readBytes()

    /**
     * The hashes are what tell a flipped bit from a different frame.
     *
     * Without them a payload can be altered into something that still reads as a well-formed take,
     * since every length and offset would still line up.
     */
    private fun checkHashes() {
        val payloadSha = sha256Of(payloadFile)
        require(payloadSha == manifest.payloadSha256) {
            "${payloadFile.path} hashes to $payloadSha, the manifest recorded " +
                "${manifest.payloadSha256}"
        }
        val indexSha = sha256(records)
        require(indexSha == manifest.indexSha256) {
            "${indexFile.path} hashes to $indexSha, the manifest recorded ${manifest.indexSha256}"
        }
    }

    /**
     * The index as a whole, before any pixels are handed out.
     *
     * These are the same invariants the sink refuses on admission. Checking them again here is not
     * redundant: the sink guards what it was handed, this guards what came back off the disk.
     */
    private fun checkRecords() {
        val buffer = ByteBuffer.wrap(records).order(ByteOrder.LITTLE_ENDIAN)
        var expectedOffset = 0L
        var lastSeq = -1
        var lastCaptureNs = -1L
        var lastInferenceMs = -1L
        var pastPreRoll = false
        repeat(manifest.frameCount) { i ->
            val seq = buffer.int
            val code = buffer.int
            val captureNs = buffer.long
            val inferenceMs = buffer.long
            val offset = buffer.long

            val phase = CapturePhase.ofCode(code)
            requireNotNull(phase) { "frame $i carries unknown phase code $code" }
            require(offset == expectedOffset) {
                "frame $i (seq $seq) points at byte $offset, the frames before it end at " +
                    "$expectedOffset"
            }
            require(seq > lastSeq) {
                "frame $i went from sequence $lastSeq to $seq; sequences are the join key to the " +
                    "inference records and have to increase"
            }
            require(captureNs >= lastCaptureNs) {
                "frame $i (seq $seq) captured at ${captureNs}ns, after ${lastCaptureNs}ns"
            }
            require(inferenceMs >= lastInferenceMs) {
                "frame $i (seq $seq) was submitted at ${inferenceMs}ms, after ${lastInferenceMs}ms"
            }
            require(!(phase == CapturePhase.PRE_ROLL && pastPreRoll)) {
                "frame $i (seq $seq) is pre-roll, but the take had already started"
            }

            expectedOffset += manifest.bytesPerFrame
            lastSeq = seq
            lastCaptureNs = captureNs
            lastInferenceMs = inferenceMs
            if (phase != CapturePhase.PRE_ROLL) pastPreRoll = true
        }
    }

    /**
     * Every stored frame, in write order, each carrying the sequence and timestamps it was captured
     * with.
     *
     * Reading is sequential and holds one frame at a time: a 450-frame 240x320 take is ~132 MB of
     * pixels, which does not want to be resident at once.
     */
    public fun frames(): Sequence<RawFrame> = sequence {
        val buffer = ByteBuffer.wrap(records).order(ByteOrder.LITTLE_ENDIAN)
        BufferedInputStream(FileInputStream(payloadFile)).use { stream ->
            val pixels = ByteArray(manifest.bytesPerFrame)
            repeat(manifest.frameCount) { i ->
                val seq = buffer.int
                val code = buffer.int
                val captureNs = buffer.long
                val inferenceMs = buffer.long
                buffer.long // payload offset; checked against the running total in checkRecords()

                // Read in a loop rather than with readNBytes: that one is API 33 and this module
                // ships at minSdk 24, so a device between the two would take a NoSuchMethodError
                // here instead of reading the take.
                var read = 0
                while (read < pixels.size) {
                    val n = stream.read(pixels, read, pixels.size - read)
                    if (n < 0) break
                    read += n
                }
                check(read == pixels.size) {
                    "frame $i (seq $seq) is truncated: $read of ${pixels.size} bytes"
                }
                yield(
                    RawFrame(
                        width = manifest.width,
                        height = manifest.height,
                        analysisFrameSeq = seq,
                        captureTimestampNs = captureNs,
                        inferenceTimestampMs = inferenceMs,
                        phase = checkNotNull(CapturePhase.ofCode(code)),
                        pixels = pixels,
                    ),
                )
            }
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().hex()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).hex()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private companion object {
        const val HASH_BUFFER_BYTES = 1 shl 16
        const val BYTE_MASK = 0xFF
    }
}
