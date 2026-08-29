package io.github.kmpfacelink.capture

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Writes frames and their index on one background thread.
 *
 * One thread, not a pool: the payload is a flat concatenation, so frames must land in submission
 * order. The queue is bounded — an unbounded one lets a writer that cannot keep up accumulate
 * hundreds of 300 KB buffers until the process dies, and the crash arrives long after the cause.
 *
 * On overflow the take is invalidated rather than the frame dropped. Blocking the analyzer would
 * change LIVE_STREAM's frame-dropping behaviour, which is one of the things being measured, so a
 * take that cannot be recorded faithfully is failed instead of quietly altered.
 *
 * **Admission and shutdown share one lock, and shutdown enters the queue behind the frames already
 * admitted.** Testing a flag and then enqueuing are two steps: [write] could read "still open" and
 * enqueue just after [close] had drained and sealed the take, and then the frame is reported
 * written, is absent from the take, and the sidecar next to it says the take is whole. The lock is
 * held only for those handovers — never across the join or any I/O — so the analyzer thread never
 * waits on the disk.
 *
 * Every failure path ends with the take failed and no completion marker on disk. The asymmetry is
 * deliberate: a take wrongly failed costs one re-record, while a take wrongly marked complete
 * becomes numbers in a result table weeks later.
 */
@Suppress(
    // Streams, claim and publisher are injected so the failure paths are reachable from a test.
    "LongParameterList",
    "TooManyFunctions",
)
internal class AndroidRawFrameSink(
    private val directory: File,
    private val claim: DirectoryClaim,
    private val marker: MarkerPublisher,
    private val width: Int,
    private val height: Int,
    private val payload: OutputStream,
    private val index: OutputStream,
) : RawFrameSink {

    /** Frames and the stop signal travel the same queue, which is what keeps them ordered. */
    private sealed interface Item {
        class Frame(val frame: RawFrame) : Item
        object Stop : Item
    }

    /**
     * [OPEN] accepts frames. [INVALID] has recorded a failure and accepts none. [CLOSING] is a
     * shutdown in progress. [DONE] is terminal, and reports the same verdict however many times
     * [close] is called.
     */
    private enum class Phase { OPEN, INVALID, CLOSING, DONE }

    private val lock = ReentrantLock()
    private val shutdownFinished = lock.newCondition()

    private var phase = Phase.OPEN
    private var failure: Throwable? = null
    private var stopEnqueued = false

    // One slot past what write() will ever fill, kept for the stop signal. A full queue therefore
    // cannot refuse the shutdown, and the shutdown cannot overtake an admitted frame.
    private val queue = ArrayBlockingQueue<Item>(FRAME_CAPACITY + 1)

    // Worker-thread only. Read by the closer only once the worker has terminated, which is what
    // publishes them.
    private var written = 0
    private var offset = 0L

    // Admission-thread only, under the lock: what the frames accepted so far declared, so the
    // stream can be checked as it arrives rather than by a reader weeks later. Shared with the
    // result sink so both streams of one take agree on what well-formed means.
    private val order = TakeOrderGuard(TakeOrderGuard.LaterClock.INFERENCE_SUBMISSION)

    private val worker = Thread({ drain() }, "RawFrameSink").apply {
        isDaemon = true
        start()
    }

    /**
     * The invariant is on the exit, not on the write: **the only way out of here that is not a
     * failure is taking the stop signal.**
     *
     * Guarding each write was not enough. A thread that died before or between writes — its buffer
     * allocation failing, or any later edit adding an early return — left nothing recorded, and
     * then `close()` joined a dead writer, found no failure, and published a marker over however
     * many frames had made it.
     */
    @Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException") // fail-closed by design
    private fun drain() {
        var tookStopSignal = false
        var exit: Throwable? = null
        try {
            val record = ByteBuffer.allocate(RawFrameFormat.INDEX_RECORD_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                val item = queue.take()
                if (item !is Item.Frame) {
                    tookStopSignal = true
                    return
                }
                writeFrame(record, item.frame)
            }
        } catch (t: Throwable) {
            // Stop at the first failure: carrying on would append frames after a hole, and every
            // later index offset would then point at the wrong pixels.
            exit = t
            if (t is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            if (!tookStopSignal) {
                recordFailure(
                    exit ?: IllegalStateException(
                        "the frame writer stopped before the take was closed",
                    ),
                )
            }
        }
    }

    private fun writeFrame(record: ByteBuffer, frame: RawFrame) {
        record.clear()
        record.putInt(frame.analysisFrameSeq)
        record.putInt(frame.phase.code)
        record.putLong(frame.captureTimestampNs)
        record.putLong(frame.inferenceTimestampMs)
        record.putLong(offset)
        index.write(record.array())

        val pixels = frame.pixelsForWriting
        payload.write(pixels)
        offset += pixels.size
        written++
    }

    override fun write(frame: RawFrame) {
        lock.withLock {
            failure?.let { throw IllegalStateException("frame writing already failed", it) }
            check(phase == Phase.OPEN) { "sink is closed" }
            if (frame.width != width || frame.height != height) {
                // Invalidates the take, not just this call. The sink cannot show that inference
                // did not see the frame it is turning away, so a take that quietly lacks a frame
                // the engine scored would otherwise still close as complete.
                rejectTakeLocked(
                    "frame ${frame.width}x${frame.height} does not match the take's " +
                        "${width}x$height; the payload has a fixed stride, so the geometry cannot " +
                        "change mid-take",
                )
            }
            checkOrderLocked(frame)
            if (queue.size >= FRAME_CAPACITY) {
                failLocked(
                    IllegalStateException(
                        "frame writer fell behind: the $FRAME_CAPACITY-frame queue is full. The " +
                            "take is invalid — dropping frames here would change the very " +
                            "frame-dropping behaviour being measured.",
                    ),
                )
                error("frame writing fell behind; take invalidated")
            }
            check(queue.offer(Item.Frame(frame))) { "the queue refused an admitted frame" }
        }
    }

    /**
     * Refuses a stream that cannot be what it claims to be, before it reaches the disk.
     *
     * A reader can find these afterwards, but afterwards is too late: the performance cannot be
     * acted again. What counts as well-formed lives in [TakeOrderGuard], shared with the result
     * sink and with both iOS sinks, so one take cannot have two definitions of it.
     */
    private fun checkOrderLocked(frame: RawFrame) {
        order.admit(
            seq = frame.analysisFrameSeq,
            captureTimestampNs = frame.captureTimestampNs,
            laterTimestamp = frame.inferenceTimestampMs,
            phase = frame.phase,
        )?.let { rejectTakeLocked("frame refused: $it. The take is invalid.") }
    }

    /** Fails the take and the call with one message. Only valid while holding [lock]. */
    private fun rejectTakeLocked(message: String): Nothing {
        failLocked(IllegalArgumentException(message))
        throw IllegalArgumentException(message)
    }

    @Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException") // fail-closed by design
    override fun close() {
        val shutdownIsMine = lock.withLock {
            // A concurrent closer is finishing: wait for its verdict rather than reporting a
            // different one.
            while (phase == Phase.CLOSING) shutdownFinished.awaitUninterruptibly()
            if (phase == Phase.DONE) {
                false
            } else {
                phase = Phase.CLOSING
                enqueueStopLocked()
                true
            }
        }
        if (shutdownIsMine) {
            try {
                finish()
            } catch (t: Throwable) {
                // Anything escaping here — an interrupted join above all — left the take unsealed.
                // Recording it before the phase goes terminal is what keeps a closer waiting behind
                // us from being told the take is fine. It is then reported through the verdict
                // below rather than rethrown, so every caller of close() learns the same thing in
                // the same shape, with the interruption as the cause. A VM-level Error is not a
                // take verdict and goes straight up.
                if (t is InterruptedException) Thread.currentThread().interrupt()
                recordFailure(t)
                if (t is Error) throw t
            } finally {
                lock.withLock {
                    phase = Phase.DONE
                    shutdownFinished.signalAll()
                }
            }
        }
        currentFailure()?.let {
            throw IllegalStateException("take is incomplete and must not be used", it)
        }
    }

    private fun finish() {
        worker.join(JOIN_TIMEOUT_MS)
        if (worker.isAlive) {
            // The streams are deliberately left alone: the writer may be inside a blocking write,
            // and flushing a BufferedOutputStream from a second thread corrupts its buffer. The
            // claim is deliberately not released either, because that abandoned thread still holds
            // a descriptor into this payload, and recording a fresh take here while it can still
            // wake up would interleave two takes in one file.
            recordFailure(
                IllegalStateException(
                    "the frame writer did not finish within $JOIN_TIMEOUT_MS ms, so the take " +
                        "cannot be shown to be complete and $directory stays unusable until this " +
                        "process exits",
                ),
            )
            return
        }
        try {
            closeStream(payload, RawFrameFormat.FRAMES_FILE)
            closeStream(index, RawFrameFormat.INDEX_FILE)
            if (currentFailure() != null) return
            finalizing({ IOException("could not write ${RawFrameFormat.FRAMES_META_FILE}", it) }) {
                // Hashed here, from the closed files rather than from what was meant to be written,
                // and before the marker goes down. Hashing during capture would have competed with
                // the pipeline being measured, and hashing later would bless whatever happened in
                // between as the original.
                marker.publish(rawFrameJson.encodeToString(manifest()))
            }
        } finally {
            // Released last, after the marker has been published or given up on. The claim has to
            // cover every file this take still touches, not just the open descriptors: released
            // before publication, another take could claim the directory, truncate the payload, and
            // then have this take's completion marker land on top of its half-written files.
            claim.release()
        }
    }

    /**
     * Flush and close are attempted separately and every failure is kept.
     *
     * A full disk surfaces at flush, and discarding that failure is how an unwritable take still
     * got its completion marker. Close is attempted either way, since it is what releases the
     * descriptor.
     */
    private fun closeStream(stream: OutputStream, name: String) {
        finalizing({ IOException("could not flush $name", it) }) { stream.flush() }
        finalizing({ IOException("could not close $name", it) }) { stream.close() }
    }

    /**
     * Runs one finalization step, keeping its failure as the take's verdict instead of letting it
     * escape and skip the steps after it.
     *
     * `runCatching` was doing this, but it swallows the two cases that are not take verdicts: an
     * interruption has to leave the flag restored for the caller, and a VM-level [Error] belongs to
     * the caller rather than to the take. Both matter now that publication is injectable and will
     * grow a hashing step.
     */
    @Suppress("TooGenericExceptionCaught", "InstanceOfCheckForException") // fail-closed by design
    private inline fun finalizing(describe: (Throwable) -> Throwable, step: () -> Unit) {
        try {
            step()
        } catch (t: Throwable) {
            if (t is Error) throw t
            if (t is InterruptedException) Thread.currentThread().interrupt()
            recordFailure(describe(t))
        }
    }

    /** What the finished take says about itself. Putting it in place is [marker]'s job. */
    private fun manifest(): RawFrameManifest = RawFrameManifest(
        schemaVersion = RawFrameFormat.SCHEMA_VERSION,
        pixelFormat = RawFrameFormat.PIXEL_FORMAT,
        bytesPerPixel = RawFrameFormat.BYTES_PER_PIXEL,
        width = width,
        height = height,
        frameCount = written,
        bytesPerFrame = frameByteSize(width, height).toInt(),
        indexRecordBytes = RawFrameFormat.INDEX_RECORD_BYTES,
        compressed = false,
        payloadSha256 = sha256Of(File(directory, RawFrameFormat.FRAMES_FILE)),
        indexSha256 = sha256Of(File(directory, RawFrameFormat.INDEX_FILE)),
        phaseCodes = capturePhaseCodes(),
    )

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
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }
    }

    private fun recordFailure(cause: Throwable) = lock.withLock { failLocked(cause) }

    private fun failLocked(cause: Throwable) {
        val first = failure
        when {
            first == null -> failure = cause
            // Later ones are kept too. A disk that fails the payload flush usually fails the index
            // flush as well, and the second report is what tells those two apart afterwards.
            first !== cause -> first.addSuppressed(cause)
        }
        if (phase == Phase.OPEN) {
            phase = Phase.INVALID
            enqueueStopLocked()
        }
    }

    private fun enqueueStopLocked() {
        if (!stopEnqueued) {
            stopEnqueued = true
            check(queue.offer(Item.Stop)) { "the reserved stop slot was taken" }
        }
    }

    private fun currentFailure(): Throwable? = lock.withLock { failure }

    private companion object {
        // ~30 frames at 240x320 is about 9 MB held at worst, which absorbs a disk hiccup of a
        // second at 30 fps without letting a genuinely stuck writer grow without bound.
        const val FRAME_CAPACITY = 30
        const val JOIN_TIMEOUT_MS = 10_000L
        const val HASH_BUFFER_BYTES = 1 shl 16
        const val BYTE_MASK = 0xFF
        const val PARTIAL_SUFFIX = ".partial"
    }
}

private const val BUFFER_BYTES = 1 shl 20

/**
 * Directories with a sink recording into them, by canonical path.
 *
 * Two sinks on one directory truncate each other's payload and then both report success. This is a
 * claim inside one classloader, not a lock on the filesystem — it says nothing about another process
 * writing the same path.
 */
private val openDirectories = ConcurrentHashMap.newKeySet<String>()

/**
 * What a sink was given and must hand back, so that it can only release a claim it holds.
 *
 * A sink that merely knew the path could remove an entry another sink was relying on, and then two
 * takes would be recording into one directory again.
 */
internal fun interface DirectoryClaim {
    fun release()
}

/** For sinks built directly in tests, which never took a claim and so have none to give back. */
internal val NO_DIRECTORY_CLAIM: DirectoryClaim = DirectoryClaim {}

/**
 * Puts the take's completion marker in place.
 *
 * Separate from the sink because the ordering that matters — the directory claim is still held while
 * the marker is going down — is only checkable if publication can be held open.
 *
 * **Publication has to be synchronous.** Returning means no further filesystem work will happen,
 * because the claim on the directory is released as soon as this returns and another take may then
 * truncate the files this marker describes. The sink owns what the marker says; this owns only
 * getting it into place in one piece.
 */
internal fun interface MarkerPublisher {
    fun publish(json: String)
}

/**
 * Writes under a staging name and renames into place, so no reader can meet a half-written marker.
 *
 * Writing it last is not enough by itself: `writeText` can fail part way through, and the reader
 * picks fields out of the text rather than parsing whole JSON, so a marker cut off after
 * `bytesPerFrame` would still read as a complete take.
 */
internal class AtomicMarkerPublisher(private val directory: File) : MarkerPublisher {

    @Suppress("TooGenericExceptionCaught") // any failure has to leave the staging file cleaned up
    override fun publish(json: String) {
        val target = File(directory, RawFrameFormat.FRAMES_META_FILE)
        val staging = File(directory, RawFrameFormat.FRAMES_META_FILE + PARTIAL_SUFFIX)
        try {
            staging.writeText(json)
            if (!staging.renameTo(target)) {
                throw IOException("could not publish ${target.path} from ${staging.path}")
            }
        } catch (t: Throwable) {
            // Covers a write that failed part way through as well as a failed rename. The staging
            // name is never a completion marker, so this is hygiene rather than safety, and a
            // deletion that also fails changes nothing about how the take reads.
            staging.delete()
            throw t
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".partial"
    }
}

private fun frameByteSize(width: Int, height: Int): Long =
    width.toLong() * height.toLong() * RawFrameFormat.BYTES_PER_PIXEL

public actual fun openRawFrameSink(directory: String, width: Int, height: Int): RawFrameSink =
    openRawFrameSink(directory, width, height, ::AtomicMarkerPublisher)

// Hidden from Java as well as from other Kotlin modules: a caller who could pass a publisher that
// does nothing would get a take that closes as complete with no marker at all.
@JvmSynthetic
@Suppress(
    // A half-opened take must not keep its claim or its descriptors.
    "TooGenericExceptionCaught",
    // Each throw is a different reason a take cannot be recorded here, and collapsing them would
    // lose which one it was.
    "ThrowsCount",
)
internal fun openRawFrameSink(
    directory: String,
    width: Int,
    height: Int,
    publisher: (File) -> MarkerPublisher,
): RawFrameSink {
    require(width > 0 && height > 0) { "take geometry must be non-empty, was ${width}x$height" }
    // Bounded as a pixel count, before the multiplication: a frame has to fit one buffer and the
    // reader reads bytesPerFrame as an Int, and checking the product instead lets a big enough
    // geometry overflow to a negative that passes the check and produces an unreadable sidecar.
    val pixels = width.toLong() * height.toLong()
    require(pixels <= Int.MAX_VALUE / RawFrameFormat.BYTES_PER_PIXEL) {
        "a ${width}x$height ${RawFrameFormat.PIXEL_FORMAT} frame is past what one frame buffer " +
            "can hold"
    }

    // Checked rather than assumed: mkdirs returns false instead of throwing, and swallowing that
    // turned "this directory cannot be created" into an ENOENT on the payload file several frames
    // later, which reads like a corrupt take rather than a bad path.
    val dir = File(directory)
    if (!dir.isDirectory && !dir.mkdirs()) {
        throw IOException("could not create $directory to record a take into")
    }
    val path = dir.canonicalPath
    check(openDirectories.add(path)) {
        "a sink is already recording into $path; two takes in one directory would truncate each " +
            "other's payload and both report success"
    }
    val claim = DirectoryClaim { openDirectories.remove(path) }

    var payload: OutputStream? = null
    var index: OutputStream? = null
    try {
        // Any earlier take's completion marker goes before the streams below truncate the payload
        // it describes, and the take is refused if it will not go. Leaving it would let a crash
        // during this recording present that stale marker over a half-written stream, and the
        // marker is exactly what tells a reader the take is whole.
        val marker = File(dir, RawFrameFormat.FRAMES_META_FILE)
        if (marker.exists() && !marker.delete()) {
            throw IOException(
                "${marker.path} belongs to an earlier take and could not be removed, so this " +
                    "directory cannot be recorded into",
            )
        }
        payload = BufferedOutputStream(
            FileOutputStream(File(dir, RawFrameFormat.FRAMES_FILE)),
            BUFFER_BYTES,
        )
        index = BufferedOutputStream(
            FileOutputStream(File(dir, RawFrameFormat.INDEX_FILE)),
            BUFFER_BYTES,
        )
        return AndroidRawFrameSink(dir, claim, publisher(dir), width, height, payload, index)
    } catch (t: Throwable) {
        runCatching { payload?.close() }
        runCatching { index?.close() }
        claim.release()
        throw t
    }
}
