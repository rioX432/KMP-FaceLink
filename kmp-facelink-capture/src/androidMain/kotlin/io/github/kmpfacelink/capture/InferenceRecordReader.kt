package io.github.kmpfacelink.capture

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads back the four result streams of a take.
 *
 * The index is read whole — it is 32 bytes a frame — and checked before anything is handed out; the
 * float streams are read a record at a time, because the landmarks alone are 6 KB a frame.
 *
 * The same lengths-must-agree rule as the frame payload: the four streams are position-aligned, so a
 * file that is one record short would silently shift every later frame's landmarks by one and still
 * parse.
 */
public class InferenceRecordReader(directory: String) {

    /** What the index says about one result, without the arrays. */
    public class Header(
        public val analysisFrameSeq: Int,
        public val phase: CapturePhase,
        public val hasFace: Boolean,
        public val inferenceFailed: Boolean,
        public val captureTimestampNs: Long,
        public val resultElapsedNs: Long,
    )

    private val dir = File(directory)
    private val landmarkFile = File(dir, InferenceRecordFormat.LANDMARKS_FILE)
    private val blendshapeFile = File(dir, InferenceRecordFormat.BLENDSHAPES_FILE)
    private val transformFile = File(dir, InferenceRecordFormat.TRANSFORM_FILE)

    public val headers: List<Header>

    init {
        val indexFile = File(dir, InferenceRecordFormat.INDEX_FILE)
        require(indexFile.isFile) { "${indexFile.path} is missing" }
        val bytes = indexFile.readBytes()
        require(bytes.size % InferenceRecordFormat.INDEX_RECORD_BYTES == 0) {
            "${indexFile.path} ends in a partial record: ${bytes.size} bytes is not a multiple of " +
                "${InferenceRecordFormat.INDEX_RECORD_BYTES}"
        }
        val count = bytes.size / InferenceRecordFormat.INDEX_RECORD_BYTES
        expectLength(landmarkFile, count, InferenceRecordFormat.LANDMARK_FLOATS)
        expectLength(blendshapeFile, count, InferenceRecordFormat.BLENDSHAPE_COUNT)
        expectLength(transformFile, count, InferenceRecordFormat.TRANSFORM_FLOATS)

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        headers = List(count) {
            val seq = buffer.int
            val code = buffer.int
            val flags = buffer.int
            buffer.int // reserved
            val captureNs = buffer.long
            val resultNs = buffer.long
            Header(
                analysisFrameSeq = seq,
                phase = requireNotNull(CapturePhase.ofCode(code)) { "unknown phase code $code" },
                hasFace = flags and InferenceRecordFormat.FLAG_HAS_FACE != 0,
                inferenceFailed = flags and InferenceRecordFormat.FLAG_INFERENCE_FAILED != 0,
                captureTimestampNs = captureNs,
                resultElapsedNs = resultNs,
            )
        }
    }

    /** The 52 raw scores of result [index], or null when that result had no usable face. */
    public fun blendshapesAt(index: Int): FloatArray? =
        floatsAt(blendshapeFile, index, InferenceRecordFormat.BLENDSHAPE_COUNT)

    /** The 478 * 3 landmarks of result [index], or null when that result had no usable face. */
    public fun landmarksAt(index: Int): FloatArray? =
        floatsAt(landmarkFile, index, InferenceRecordFormat.LANDMARK_FLOATS)

    /** The 4x4 transform of result [index], or null when that result had no usable face. */
    public fun transformAt(index: Int): FloatArray? =
        floatsAt(transformFile, index, InferenceRecordFormat.TRANSFORM_FLOATS)

    private fun floatsAt(file: File, index: Int, floats: Int): FloatArray? {
        val header = headers[index]
        // The streams are zero-filled for results without a usable face. Returning null rather than
        // those zeros is what stops "nothing detected" being read as "detected at the origin".
        if (!header.hasFace || header.inferenceFailed) return null
        val bytes = ByteArray(floats * Float.SIZE_BYTES)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(index.toLong() * bytes.size)
            raf.readFully(bytes)
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(floats) { buffer.float }
    }

    private fun expectLength(file: File, records: Int, floats: Int) {
        require(file.isFile) { "${file.path} is missing" }
        val expected = records.toLong() * floats * Float.SIZE_BYTES
        require(file.length() == expected) {
            "${file.path} holds ${file.length()} bytes; $records records of $floats floats is " +
                "$expected. The four streams are position-aligned, so a mismatch shifts every " +
                "later frame's arrays."
        }
    }
}
