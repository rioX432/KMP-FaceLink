@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import platform.Foundation.NSCondition
import platform.Foundation.NSLock
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_sync

/**
 * Writes uncompressed frames on one serial queue.
 *
 * Compression is deliberately absent: it would compete for CPU, memory bandwidth and thermal
 * budget with ARKit while the measurement is running. The bounded hand-off also refuses a take
 * instead of silently dropping a frame when storage cannot keep up.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "InstanceOfCheckForException")
internal class IosRawFrameSink(
    private val directory: String,
    private val width: Int,
    private val height: Int,
    private val payload: NativeOutput,
    private val index: NativeOutput,
    private val releaseClaim: () -> Unit,
) : RawFrameSink {
    private enum class Phase { OPEN, INVALID, CLOSING, DONE }

    private val lock = NSCondition()
    private val writerQueue = dispatch_queue_create("io.github.kmpfacelink.capture.frames", null)
    private var phase = Phase.OPEN
    private var failure: Throwable? = null
    private var pending = 0
    private var written = 0
    private var offset = 0L
    private val order = TakeOrderGuard(TakeOrderGuard.LaterClock.INFERENCE_SUBMISSION)

    override fun write(frame: RawFrame) {
        lock.withCaptureLock {
            failure?.let { throw IllegalStateException("frame writing already failed", it) }
            check(phase == Phase.OPEN) { "sink is closed" }
            validateFrame(frame)
            if (pending >= FRAME_CAPACITY) {
                failLocked(
                    IllegalStateException(
                        "frame writer fell behind: the $FRAME_CAPACITY-frame queue is full; the " +
                            "take is invalid because dropping a frame would change the measurement",
                    ),
                )
                error("frame writing fell behind; take invalidated")
            }
            pending++
        }
        dispatch_async(writerQueue) {
            try {
                if (lock.withCaptureLock { failure == null }) writeFrame(frame)
            } catch (t: Throwable) {
                if (t is Error) throw t
                lock.withCaptureLock { failLocked(t) }
            } finally {
                lock.withCaptureLock { pending-- }
            }
        }
    }

    private fun validateFrame(frame: RawFrame) {
        if (frame.width != width || frame.height != height) {
            rejectLocked(
                "frame ${frame.width}x${frame.height} does not match the take's ${width}x$height",
            )
        }
        order.admit(
            seq = frame.analysisFrameSeq,
            captureTimestampNs = frame.captureTimestampNs,
            laterTimestamp = frame.inferenceTimestampMs,
            phase = frame.phase,
        )?.let(::rejectLocked)
    }

    private fun rejectLocked(message: String): Nothing {
        failLocked(IllegalArgumentException(message))
        throw IllegalArgumentException(message)
    }

    private fun writeFrame(frame: RawFrame) {
        val record = ByteArray(RawFrameFormat.INDEX_RECORD_BYTES)
        record.putIntLittleEndian(SEQUENCE_OFFSET, frame.analysisFrameSeq)
        record.putIntLittleEndian(PHASE_OFFSET, frame.phase.code)
        record.putLongLittleEndian(CAPTURE_TIME_OFFSET, frame.captureTimestampNs)
        record.putLongLittleEndian(INFERENCE_TIME_OFFSET, frame.inferenceTimestampMs)
        record.putLongLittleEndian(PAYLOAD_OFFSET, offset)
        index.write(record)
        payload.write(frame.pixelsForWriting)
        offset += frame.pixelByteCount
        written++
    }

    override fun close() {
        val ownsClose = lock.withCaptureLock {
            when (phase) {
                Phase.OPEN, Phase.INVALID -> {
                    phase = Phase.CLOSING
                    true
                }
                Phase.CLOSING -> false
                Phase.DONE -> false
            }
        }
        if (ownsClose) {
            try {
                dispatch_sync(writerQueue) {}
                finish()
            } catch (t: Throwable) {
                lock.withCaptureLock { failLocked(t) }
                if (t is Error) throw t
            } finally {
                lock.withCaptureLock {
                    phase = Phase.DONE
                    lock.broadcast()
                }
            }
        } else {
            lock.withCaptureLock {
                while (phase == Phase.CLOSING) lock.wait()
            }
        }
        lock.withCaptureLock { failure }?.let {
            throw IllegalStateException("take is incomplete and must not be used", it)
        }
    }

    private fun finish() {
        try {
            closeFile(payload)
            closeFile(index)
            if (lock.withCaptureLock { failure } == null) publishManifest()
        } finally {
            releaseClaim()
        }
    }

    private fun closeFile(file: NativeOutput) {
        try {
            file.closeAndSync()
        } catch (t: Throwable) {
            if (t is Error) throw t
            lock.withCaptureLock { failLocked(t) }
            file.closeAfterFailure()
        }
    }

    private fun publishManifest() {
        val manifest = RawFrameManifest(
            schemaVersion = RawFrameFormat.SCHEMA_VERSION,
            pixelFormat = RawFrameFormat.PIXEL_FORMAT,
            bytesPerPixel = RawFrameFormat.BYTES_PER_PIXEL,
            width = width,
            height = height,
            frameCount = written,
            bytesPerFrame = width * height * RawFrameFormat.BYTES_PER_PIXEL,
            indexRecordBytes = RawFrameFormat.INDEX_RECORD_BYTES,
            compressed = false,
            payloadSha256 = sha256OfCaptureFile(joinCapturePath(directory, RawFrameFormat.FRAMES_FILE)),
            indexSha256 = sha256OfCaptureFile(joinCapturePath(directory, RawFrameFormat.INDEX_FILE)),
            phaseCodes = capturePhaseCodes(),
        )
        publishCaptureText(
            directory,
            RawFrameFormat.FRAMES_META_FILE,
            rawFrameJson.encodeToString(manifest),
        )
    }

    private fun failLocked(cause: Throwable) {
        if (failure == null) failure = cause else if (failure !== cause) failure?.addSuppressed(cause)
        if (phase == Phase.OPEN) phase = Phase.INVALID
    }

    private companion object {
        const val FRAME_CAPACITY = 30
        const val SEQUENCE_OFFSET = 0
        const val PHASE_OFFSET = 4
        const val CAPTURE_TIME_OFFSET = 8
        const val INFERENCE_TIME_OFFSET = 16
        const val PAYLOAD_OFFSET = 24
    }
}

private val rawFrameDirectoryLock = NSLock()
private val rawFrameDirectories = mutableSetOf<String>()

public actual fun openRawFrameSink(directory: String, width: Int, height: Int): RawFrameSink {
    require(width > 0 && height > 0) { "take geometry must be non-empty, was ${width}x$height" }
    val pixels = width.toLong() * height.toLong()
    require(pixels <= Int.MAX_VALUE / RawFrameFormat.BYTES_PER_PIXEL) {
        "a ${width}x$height ${RawFrameFormat.PIXEL_FORMAT} frame is too large"
    }
    val path = ensureCaptureDirectory(directory)
    check(rawFrameDirectoryLock.withCaptureLock { rawFrameDirectories.add(path) }) {
        "a sink is already recording into $path"
    }
    val release = { rawFrameDirectoryLock.withCaptureLock { rawFrameDirectories.remove(path) }; Unit }
    var payload: NativeOutput? = null
    var index: NativeOutput? = null
    try {
        val marker = joinCapturePath(path, RawFrameFormat.FRAMES_META_FILE)
        check(removeCapturePath(marker)) { "could not remove stale completion marker $marker" }
        payload = NativeOutput.create(joinCapturePath(path, RawFrameFormat.FRAMES_FILE))
        index = NativeOutput.create(joinCapturePath(path, RawFrameFormat.INDEX_FILE))
        return IosRawFrameSink(path, width, height, payload, index, release)
    } catch (t: Throwable) {
        payload?.closeAfterFailure()
        index?.closeAfterFailure()
        release()
        throw t
    }
}
