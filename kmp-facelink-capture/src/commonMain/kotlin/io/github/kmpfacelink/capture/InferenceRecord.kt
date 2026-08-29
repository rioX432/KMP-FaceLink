package io.github.kmpfacelink.capture

/**
 * One inference result, as the engine produced it, joined to its frame by [analysisFrameSeq].
 *
 * Raw on purpose. The product runs its results through smoothing, a blendshape enhancer and an
 * expression correlator before anything sees them; a measurement of the estimator has to be taken
 * before all of that, or it measures the correction instead.
 *
 * Arrays are null when there was no usable face rather than zero-filled, so "nothing detected"
 * cannot be read as "detected at the origin". The sink is what zero-fills the streams, from the
 * flags, so the file layout stays fixed-stride.
 *
 * @property analysisFrameSeq the analyzer's counter at admission — the same value the stored frame
 *   carries. In LIVE_STREAM the engine returns results for only some admitted frames, so this is
 *   the only thing that pairs a result with the pixels that produced it
 * @property captureTimestampNs camera capture time of the frame this result came from
 * @property resultElapsedNs when the result arrived, on the device's elapsed-realtime clock.
 *   This timestamp is retained for ordering and joins. It must not be subtracted from
 *   [captureTimestampNs] unless a platform has separately proved that the camera timestamp uses
 *   the same clock domain. Performance benchmarks therefore stamp submission and completion with
 *   one explicitly declared monotonic clock instead of deriving latency from these two fields.
 * @property inferenceFailed a face was detected but the result was malformed. Distinct from no
 *   face: one is the subject, the other is the estimator
 */
public class InferenceRecord(
    public val analysisFrameSeq: Int,
    public val captureTimestampNs: Long,
    public val resultElapsedNs: Long,
    public val phase: CapturePhase,
    public val hasFace: Boolean,
    public val inferenceFailed: Boolean,
    public val landmarks: FloatArray?,
    public val blendshapes: FloatArray?,
    public val transform: FloatArray?,
) {
    init {
        require(analysisFrameSeq >= 0) { "analysisFrameSeq must be >= 0, was $analysisFrameSeq" }
        landmarks?.let {
            require(it.size == InferenceRecordFormat.LANDMARK_FLOATS) {
                "expected ${InferenceRecordFormat.LANDMARK_FLOATS} landmark floats, got ${it.size}"
            }
        }
        blendshapes?.let {
            require(it.size == InferenceRecordFormat.BLENDSHAPE_COUNT) {
                "expected ${InferenceRecordFormat.BLENDSHAPE_COUNT} blendshapes, got ${it.size}"
            }
        }
        transform?.let {
            require(it.size == InferenceRecordFormat.TRANSFORM_FLOATS) {
                "expected ${InferenceRecordFormat.TRANSFORM_FLOATS} transform floats, got ${it.size}"
            }
        }
    }
}

/** Constants the record reader depends on. Changing any of them is a format change. */
public object InferenceRecordFormat {

    public const val LANDMARK_COUNT: Int = 478
    public const val LANDMARK_FLOATS: Int = LANDMARK_COUNT * 3
    public const val BLENDSHAPE_COUNT: Int = 52
    public const val TRANSFORM_FLOATS: Int = 16

    /**
     * Index record layout, little endian:
     * `0 int32 analysisFrameSeq | 4 int32 CapturePhase.code | 8 int32 flags | 12 int32 reserved |
     *  16 int64 captureTimestampNs | 24 int64 resultElapsedNs`
     */
    public const val INDEX_RECORD_BYTES: Int = 32

    public const val FLAG_HAS_FACE: Int = 0x1
    public const val FLAG_INFERENCE_FAILED: Int = 0x2

    public const val INDEX_FILE: String = "results.idx"
    public const val LANDMARKS_FILE: String = "landmarks.f32"
    public const val BLENDSHAPES_FILE: String = "blendshapes.f32"
    public const val TRANSFORM_FILE: String = "transform.f32"

    /** Written last. Its presence is what marks the take complete. */
    public const val TAKE_MANIFEST_FILE: String = "take.json"
}

/**
 * Destination for [InferenceRecord]s.
 *
 * Results arrive on the inference callback thread, so implementations must not block it. The four
 * streams are position-aligned and fixed-stride, so a record that is written to one has to be
 * written to all of them: a partial record shifts every later frame's landmarks by one.
 */
public interface InferenceRecordSink {

    /** How many records have been accepted. */
    public val recordCount: Int

    /** Appends one record. Throws once the take has been invalidated. */
    public fun write(record: InferenceRecord)

    /**
     * Flushes and releases. Idempotent in the strict sense: every call reports the same verdict.
     *
     * @throws IllegalStateException if any record failed to be written, so a caller cannot treat a
     *   partial take as complete
     */
    public fun close()

    /**
     * SHA-256 of the four streams, in index, landmarks, blendshapes, transform order.
     *
     * Taken from the closed files rather than from what was meant to be written, and only after
     * [close]: hashing during a take would compete for CPU with the pipeline being measured, and
     * hashing later would bless whatever happened in between as the original.
     */
    public fun hashes(): List<String>
}

/**
 * Opens an [InferenceRecordSink] writing the four streams inside [directory].
 *
 * @throws NotImplementedError on platforms where recording is not implemented yet. Failing loudly
 *   beats writing nothing: a take that looks complete and cannot be joined to its frames would only
 *   surface once the session was long over.
 */
public expect fun openInferenceRecordSink(directory: String): InferenceRecordSink
