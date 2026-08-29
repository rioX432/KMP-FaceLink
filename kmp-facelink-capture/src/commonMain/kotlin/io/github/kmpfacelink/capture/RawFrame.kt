package io.github.kmpfacelink.capture

/**
 * One analysis frame, exactly as it was handed to the inference engine, plus the identity needed
 * to join it back to that engine's output.
 *
 * Recording these is what lets a single take be scored under several conditions. Running mode,
 * delegate and downsample resolution can all be varied afterwards over the same pixels, so the
 * comparison isolates the condition instead of confounding it with a different performance of the
 * expression, different lighting and a different day.
 *
 * **[analysisFrameSeq] counts frames at analyzer admission, before any decision that depends on the
 * condition being varied, and every admitted frame is recorded.** That includes the ones
 * LIVE_STREAM then declines to run: those do not update tracking state themselves, but their
 * arrival shapes the scheduling that decides which later frames do. Numbering at submission instead
 * would make the recording sparse in a way that depends on the very condition under test, and two
 * takes would no longer be comparable. The results are the sparse stream, keyed back by this
 * sequence.
 *
 * **The identity fields are not optional.** Stored without [analysisFrameSeq] the frames are an
 * anonymous pile that cannot be joined to those results, and without [captureTimestampNs] a
 * VIDEO-mode replay cannot reproduce the original cadence.
 *
 * The pixels are stored raw rather than through a platform image codec. Cross-platform equality is
 * then checkable as the hash of the decoded payload, with no codec in the way.
 *
 * @property analysisFrameSeq the analyzer's frame counter at admission, shared with the inference
 *   record stream. This is the join key
 * @property captureTimestampNs camera capture time
 * @property inferenceTimestampMs the timestamp handed to the inference engine, which VIDEO mode
 *   replay must reuse
 * @property phase which part of the scripted take this frame belongs to
 * @param pixels [RawFrameFormat.BYTES_PER_PIXEL] bytes per pixel, row-major, no padding
 */
public class RawFrame(
    public val width: Int,
    public val height: Int,
    public val analysisFrameSeq: Int,
    public val captureTimestampNs: Long,
    public val inferenceTimestampMs: Long,
    public val phase: CapturePhase,
    pixels: ByteArray,
) {
    /**
     * The frame's own bytes, which never leave this object.
     *
     * Both directions have to be closed. The producer is a camera analyzer that reuses its
     * buffer, so the array is copied on the way in. And a sink writes these bytes later on
     * another thread, so a caller must not be able to reach them afterwards either: repainting an
     * already-accepted frame produces a well-formed take holding the wrong pixels, which no
     * checksum or length check can catch. Copying in alone left that second door open.
     */
    private val storedPixels: ByteArray = pixels.copyOf()

    /** Size of the pixel payload in bytes. */
    public val pixelByteCount: Int get() = storedPixels.size

    /** A fresh copy of the pixels, which the caller owns and may modify. */
    public fun copyPixels(): ByteArray = storedPixels.copyOf()

    /**
     * Read access for the sinks in this module, which write these bytes straight out and would
     * otherwise copy 300 KB per frame to do it.
     *
     * Internal, which closes the door for every Kotlin caller outside this module. On the JVM it
     * survives as a name-mangled public method, so Java could still reach the array by that name —
     * that is sabotage rather than the buffer-reuse mistake this guards against, and the recorders
     * are Kotlin.
     */
    internal val pixelsForWriting: ByteArray get() = storedPixels

    init {
        require(width > 0 && height > 0) { "frame must be non-empty, was ${width}x$height" }
        val expected = width.toLong() * height.toLong() * RawFrameFormat.BYTES_PER_PIXEL
        require(storedPixels.size.toLong() == expected) {
            "expected $expected bytes for ${width}x$height ${RawFrameFormat.PIXEL_FORMAT}, " +
                "got ${storedPixels.size}"
        }
        require(analysisFrameSeq >= 0) { "analysisFrameSeq must be >= 0, was $analysisFrameSeq" }
        require(captureTimestampNs >= 0) {
            "captureTimestampNs must be >= 0, was $captureTimestampNs"
        }
        require(inferenceTimestampMs >= 0) {
            "inferenceTimestampMs must be >= 0, was $inferenceTimestampMs"
        }
    }
}

/**
 * Where a frame sits in the scripted take.
 *
 * [PRE_ROLL] matters for replay: the streaming running modes carry tracking state forward, so a
 * scored frame replayed from a cold start is not in the same condition the live engine was in
 * when it produced its result. Pre-roll is replayed to rebuild that state, then discarded from
 * scoring.
 */
public enum class CapturePhase(public val code: Int) {
    /** Recorded before the prompt, to rebuild tracking state on replay. Not scored. */
    PRE_ROLL(1),

    /** The scripted neutral, used as the per-clip baseline. */
    NEUTRAL(2),

    /** The acted expression. A take holds it twice, with a return to [NEUTRAL] between. */
    ACTED(3),
    ;

    public companion object {

        /**
         * The phase a stored [code] means, or null for a code this build does not know.
         *
         * Codes start at 1 so that zero — what a truncated or never-written index record reads as —
         * decodes as nothing rather than as pre-roll.
         */
        public fun ofCode(code: Int): CapturePhase? = entries.firstOrNull { it.code == code }
    }
}

/** Constants the reader depends on. Changing any of them is a format change. */
public object RawFrameFormat {

    /**
     * Bumped whenever the on-disk layout changes in a way a reader must know about.
     *
     * 2 replaced the phase ordinal with [CapturePhase.code], which a reordering of the enum cannot
     * silently reinterpret, and added the two payload hashes to the manifest. There is no schema-1
     * data to migrate: nothing was recorded with it.
     */
    public const val SCHEMA_VERSION: Int = 2

    /** Straight (non-premultiplied) RGBA, one byte per channel, top-left origin. */
    public const val PIXEL_FORMAT: String = "RGBA_8888"

    public const val BYTES_PER_PIXEL: Int = 4

    /**
     * Uncompressed, fixed-stride pixel payload.
     *
     * Not compressed while recording: gzip competes for CPU, memory bandwidth and thermal budget
     * with the very pipeline being measured, and LIVE_STREAM's frame dropping is sensitive to
     * exactly that. Compression belongs after the camera and the landmarker have stopped.
     */
    public const val FRAMES_FILE: String = "frames.rgba"

    /** Fixed-size index records, one per stored frame, in write order. */
    public const val INDEX_FILE: String = "frames.idx"

    /** Sidecar describing geometry and totals. Written last, so its presence means completion. */
    public const val FRAMES_META_FILE: String = "frames.json"

    /**
     * Index record layout, little endian:
     * `0 int32 analysisFrameSeq | 4 int32 CapturePhase.code | 8 int64 captureTimestampNs |
     *  16 int64 inferenceTimestampMs | 24 int64 payload byte offset`
     */
    public const val INDEX_RECORD_BYTES: Int = 32
}

/**
 * Destination for [RawFrame]s.
 *
 * Frames arrive on the analysis thread, so implementations must not block it. They must also not
 * silently drop: a take missing frames still looks complete, and the loss only surfaces once the
 * recording session is long over. Overflow invalidates the take instead.
 */
public interface RawFrameSink {

    /**
     * Appends one frame. Throws if the take has already been invalidated by a write failure, or if
     * [close] has begun — a frame accepted here is in the take, so a write that returns normally
     * and then vanishes is the one outcome this must never produce.
     */
    public fun write(frame: RawFrame)

    /**
     * Flushes, writes the index and sidecar, and releases.
     *
     * Idempotent in the strict sense: every call reports the same verdict, so two callers cannot
     * disagree about whether the take completed.
     *
     * @throws IllegalStateException if any frame failed to be written, or if flushing the streams
     *   did not succeed, so a caller cannot treat a partial take as complete
     */
    public fun close()
}
