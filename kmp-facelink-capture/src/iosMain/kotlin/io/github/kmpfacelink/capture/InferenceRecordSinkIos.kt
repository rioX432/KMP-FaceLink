@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import platform.Foundation.NSLock

/** Keeps the four fixed-stride result streams position-aligned, as the Android sink does. */
@Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException")
internal class IosInferenceRecordSink(
    private val index: NativeOutput,
    private val landmarks: NativeOutput,
    private val blendshapes: NativeOutput,
    private val transform: NativeOutput,
) : InferenceRecordSink {
    private val lock = NSLock()
    private var failure: Throwable? = null
    private var closed = false
    private var count = 0
    private val order = TakeOrderGuard(TakeOrderGuard.LaterClock.RESULT_ARRIVAL)

    override val recordCount: Int get() = lock.withCaptureLock { count }

    override fun write(record: InferenceRecord) {
        lock.withCaptureLock {
            failure?.let { throw IllegalStateException("result writing already failed", it) }
            check(!closed) { "result sink is closed" }
            checkOrder(record)
            try {
                writeIndex(record)
                val usable = record.hasFace && !record.inferenceFailed
                writeFloats(landmarks, InferenceRecordFormat.LANDMARK_FLOATS, if (usable) record.landmarks else null)
                writeFloats(blendshapes, InferenceRecordFormat.BLENDSHAPE_COUNT, if (usable) record.blendshapes else null)
                writeFloats(transform, InferenceRecordFormat.TRANSFORM_FLOATS, if (usable) record.transform else null)
                count++
            } catch (t: Throwable) {
                if (t is Error) throw t
                failure = t
                throw IllegalStateException("result writing failed at record $count", t)
            }
        }
    }

    private fun checkOrder(record: InferenceRecord) {
        val rejection = order.admit(
            seq = record.analysisFrameSeq,
            captureTimestampNs = record.captureTimestampNs,
            laterTimestamp = record.resultElapsedNs,
            phase = record.phase,
        ) ?: return
        val refusal = IllegalArgumentException("result refused at record $count: $rejection")
        failure = refusal
        throw refusal
    }

    private fun writeIndex(record: InferenceRecord) {
        var flags = 0
        if (record.hasFace) flags = flags or InferenceRecordFormat.FLAG_HAS_FACE
        if (record.inferenceFailed) flags = flags or InferenceRecordFormat.FLAG_INFERENCE_FAILED
        val bytes = ByteArray(InferenceRecordFormat.INDEX_RECORD_BYTES)
        bytes.putIntLittleEndian(SEQUENCE_OFFSET, record.analysisFrameSeq)
        bytes.putIntLittleEndian(PHASE_OFFSET, record.phase.code)
        bytes.putIntLittleEndian(FLAGS_OFFSET, flags)
        bytes.putLongLittleEndian(CAPTURE_TIME_OFFSET, record.captureTimestampNs)
        bytes.putLongLittleEndian(RESULT_TIME_OFFSET, record.resultElapsedNs)
        index.write(bytes)
    }

    private fun writeFloats(output: NativeOutput, count: Int, values: FloatArray?) {
        val bytes = ByteArray(count * Float.SIZE_BYTES)
        repeat(count) { index -> bytes.putFloatLittleEndian(index * Float.SIZE_BYTES, values?.get(index) ?: 0f) }
        output.write(bytes)
    }

    override fun close() {
        lock.withCaptureLock {
            if (!closed) {
                closed = true
                listOf(index, landmarks, blendshapes, transform).forEach(::closeFile)
            }
            failure?.let { throw IllegalStateException("take is incomplete", it) }
        }
    }

    private fun closeFile(file: NativeOutput) {
        try {
            file.closeAndSync()
        } catch (t: Throwable) {
            if (t is Error) throw t
            if (failure == null) failure = t else failure?.addSuppressed(t)
            file.closeAfterFailure()
        }
    }

    override fun hashes(): List<String> = lock.withCaptureLock {
        check(closed) { "hash the streams after closing them, not before" }
        listOf(index, landmarks, blendshapes, transform).map { sha256OfCaptureFile(it.path) }
    }

    private companion object {
        const val SEQUENCE_OFFSET = 0
        const val PHASE_OFFSET = 4
        const val FLAGS_OFFSET = 8
        const val CAPTURE_TIME_OFFSET = 16
        const val RESULT_TIME_OFFSET = 24
    }
}

public actual fun openInferenceRecordSink(directory: String): InferenceRecordSink {
    val path = ensureCaptureDirectory(directory)
    val outputs = ArrayList<NativeOutput>()
    try {
        fun open(name: String): NativeOutput =
            NativeOutput.create(joinCapturePath(path, name)).also(outputs::add)
        return IosInferenceRecordSink(
            index = open(InferenceRecordFormat.INDEX_FILE),
            landmarks = open(InferenceRecordFormat.LANDMARKS_FILE),
            blendshapes = open(InferenceRecordFormat.BLENDSHAPES_FILE),
            transform = open(InferenceRecordFormat.TRANSFORM_FILE),
        )
    } catch (t: Throwable) {
        outputs.forEach(NativeOutput::closeAfterFailure)
        throw t
    }
}
