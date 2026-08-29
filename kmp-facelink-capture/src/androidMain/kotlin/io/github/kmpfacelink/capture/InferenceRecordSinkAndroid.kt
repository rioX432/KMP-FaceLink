// Same file-naming convention for actuals as the rest of this source set.
@file:Suppress("Filename", "MatchingDeclarationName")

package io.github.kmpfacelink.capture

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Writes the four position-aligned result streams for one take.
 *
 * Records arrive on the inference callback thread, one at a time, and are written straight through:
 * unlike the pixel payload there is no queue, because a record is 6 KB rather than 300 KB and the
 * callback thread is not the analyzer thread.
 *
 * **Zero-filling is enforced here, from the flags, rather than trusted from the caller.** The
 * streams are fixed-stride so that frame *n* is at offset *n × stride*; a record written to three of
 * the four streams would shift every later frame's landmarks by one and still read as a valid file.
 *
 * Not compressed, for the same reason the pixels are not: compression during capture competes for
 * CPU and thermal budget with the pipeline being measured. Integrity comes from the hashes instead,
 * which [hashes] produces once the streams are closed.
 */
@Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException") // fail-closed by design
internal class AndroidInferenceRecordSink(directory: File) : InferenceRecordSink {

    private val index = open(directory, InferenceRecordFormat.INDEX_FILE)
    private val landmarks = open(directory, InferenceRecordFormat.LANDMARKS_FILE)
    private val blendshapes = open(directory, InferenceRecordFormat.BLENDSHAPES_FILE)
    private val transform = open(directory, InferenceRecordFormat.TRANSFORM_FILE)

    private val files = listOf(
        File(directory, InferenceRecordFormat.INDEX_FILE),
        File(directory, InferenceRecordFormat.LANDMARKS_FILE),
        File(directory, InferenceRecordFormat.BLENDSHAPES_FILE),
        File(directory, InferenceRecordFormat.TRANSFORM_FILE),
    )

    private val indexRecord = ByteBuffer
        .allocate(InferenceRecordFormat.INDEX_RECORD_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    private val landmarkBytes = floatBuffer(InferenceRecordFormat.LANDMARK_FLOATS)
    private val blendshapeBytes = floatBuffer(InferenceRecordFormat.BLENDSHAPE_COUNT)
    private val transformBytes = floatBuffer(InferenceRecordFormat.TRANSFORM_FLOATS)

    private val lock = ReentrantLock()
    private var failure: Throwable? = null
    private var closed = false
    private var count = 0

    // Under the lock, like everything else here: what the records accepted so far declared.
    private val order = TakeOrderGuard(TakeOrderGuard.LaterClock.RESULT_ARRIVAL)

    override val recordCount: Int get() = lock.withLock { count }

    override fun write(record: InferenceRecord) {
        lock.withLock {
            failure?.let { throw IllegalStateException("result writing already failed", it) }
            check(!closed) { "result sink is closed" }
            checkOrderLocked(record)
            try {
                writeIndex(record)
                val usable = record.hasFace && !record.inferenceFailed
                writeFloats(landmarks, landmarkBytes, if (usable) record.landmarks else null)
                writeFloats(blendshapes, blendshapeBytes, if (usable) record.blendshapes else null)
                writeFloats(transform, transformBytes, if (usable) record.transform else null)
                count++
            } catch (t: Throwable) {
                if (t is Error) throw t
                // One failed record leaves the four streams out of step with each other, so the
                // take is over: every later record would be attributed to the wrong frame.
                failure = t
                throw IllegalStateException("result writing failed at record $count", t)
            }
        }
    }

    override fun close() {
        lock.withLock {
            if (closed) {
                failure?.let { throw IllegalStateException("take is incomplete", it) }
                return
            }
            closed = true
            listOf(
                index to InferenceRecordFormat.INDEX_FILE,
                landmarks to InferenceRecordFormat.LANDMARKS_FILE,
                blendshapes to InferenceRecordFormat.BLENDSHAPES_FILE,
                transform to InferenceRecordFormat.TRANSFORM_FILE,
            ).forEach { (stream, name) -> closeStream(stream, name) }
            failure?.let { throw IllegalStateException("take is incomplete", it) }
        }
    }

    override fun hashes(): List<String> {
        lock.withLock { check(closed) { "hash the streams after closing them, not before" } }
        return files.map { sha256Of(it) }
    }

    /**
     * Refuses a record that cannot belong to this take, before it reaches the disk.
     *
     * The results are what the frames are joined to, so a repeated or backwards sequence here is
     * the same fault as one in the payload: the study would pair a result with another frame's
     * pixels and never know. Refusing it invalidates the take rather than only the call, because a
     * take whose results skip a frame the engine did score is not the take that was performed —
     * and a re-record costs one clip, while a wrong join costs a number in a result table.
     *
     * What counts as well-formed lives in [TakeOrderGuard], shared with the frame sink.
     */
    private fun checkOrderLocked(record: InferenceRecord) {
        val rejection = order.admit(
            seq = record.analysisFrameSeq,
            captureTimestampNs = record.captureTimestampNs,
            laterTimestamp = record.resultElapsedNs,
            phase = record.phase,
        ) ?: return
        val refusal = IllegalArgumentException(
            "result refused at record $count: $rejection. The take is invalid; re-record it.",
        )
        keep(refusal)
        throw refusal
    }

    private fun writeIndex(record: InferenceRecord) {
        var flags = 0
        if (record.hasFace) flags = flags or InferenceRecordFormat.FLAG_HAS_FACE
        if (record.inferenceFailed) flags = flags or InferenceRecordFormat.FLAG_INFERENCE_FAILED
        indexRecord.clear()
        indexRecord.putInt(record.analysisFrameSeq)
        indexRecord.putInt(record.phase.code)
        indexRecord.putInt(flags)
        indexRecord.putInt(0) // reserved, so the record stays 8-byte aligned
        indexRecord.putLong(record.captureTimestampNs)
        indexRecord.putLong(record.resultElapsedNs)
        index.write(indexRecord.array())
    }

    private fun writeFloats(out: OutputStream, buffer: ByteBuffer, values: FloatArray?) {
        buffer.clear()
        val floats = buffer.capacity() / Float.SIZE_BYTES
        repeat(floats) { i -> buffer.putFloat(values?.get(i) ?: 0f) }
        out.write(buffer.array())
    }

    private fun closeStream(stream: OutputStream, name: String) {
        runCatching { stream.flush() }.exceptionOrNull()?.let { keep(IOException("could not flush $name", it)) }
        runCatching { stream.close() }.exceptionOrNull()?.let { keep(IOException("could not close $name", it)) }
    }

    private fun keep(cause: Throwable) {
        val first = failure
        when {
            first == null -> failure = cause
            first !== cause -> first.addSuppressed(cause)
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    private fun floatBuffer(floats: Int): ByteBuffer =
        ByteBuffer.allocate(floats * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)

    private fun open(directory: File, name: String): OutputStream =
        BufferedOutputStream(FileOutputStream(File(directory, name)), BUFFER_BYTES)

    private companion object {
        const val BUFFER_BYTES = 1 shl 16
        const val HASH_BUFFER_BYTES = 1 shl 16
        const val BYTE_MASK = 0xFF
    }
}

public actual fun openInferenceRecordSink(directory: String): InferenceRecordSink {
    val dir = File(directory)
    if (!dir.isDirectory && !dir.mkdirs()) {
        throw IOException("could not create $directory to record results into")
    }
    return AndroidInferenceRecordSink(dir)
}
