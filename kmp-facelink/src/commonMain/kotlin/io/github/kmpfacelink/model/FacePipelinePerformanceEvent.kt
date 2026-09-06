package io.github.kmpfacelink.model

/** Named clock domain for face-pipeline duration samples. */
public enum class FacePipelinePerformanceClock {
    /** A monotonic iOS host clock sampled only inside the ARKit delegate callback. */
    IOS_MONOTONIC_CALLBACK_CLOCK,
}

/** What happened to one face-anchor callback after it entered KMP-FaceLink. */
public enum class FacePipelinePerformanceOutcome {
    FULL_OUTPUT,
    INTERPOLATED_OUTPUT,
    DROPPED_NO_ACTIVE_EPOCH,
    DROPPED_THROTTLED,
    DROPPED_INTERPOLATION_UNAVAILABLE,
    DROPPED_EPOCH_CHANGED,

    /** Callback processing threw after a face-anchor sequence was admitted; aggregate must reject. */
    CALLBACK_FAILURE,
}

/**
 * Callback-side measurement for one iOS ARKit face anchor.
 *
 * This does not include camera capture or ARKit's internal work before callback entry. Durations
 * are computed from one named monotonic clock. Full outputs have all four durations; interpolated
 * outputs have only callback-to-output/enqueue durations. Drop outcomes have no output duration.
 * Sequence gaps mean the diagnostics collector did not receive every event and invalidate an
 * aggregate run.
 */
public data class FacePipelinePerformanceEvent(
    val callbackSequence: ULong,
    val anchorBatchSequence: ULong,
    val clock: FacePipelinePerformanceClock,
    val outcome: FacePipelinePerformanceOutcome,
    val materialization: InferenceMaterialization?,
    val configurationFingerprint: String?,
    val epochFingerprint: String?,
    val callbackToRawExtractionNs: Long?,
    val rawExtractionToOutputReadyNs: Long?,
    val callbackToOutputReadyNs: Long?,
    val outputReadyToEnqueueReturnNs: Long?,
    val callbackToEnqueueReturnNs: Long?,
    val enqueueAccepted: Boolean?,
) {
    init {
        listOfNotNull(
            callbackToRawExtractionNs,
            rawExtractionToOutputReadyNs,
            callbackToOutputReadyNs,
            outputReadyToEnqueueReturnNs,
            callbackToEnqueueReturnNs,
        ).forEach { require(it >= 0) { "performance durations must not be negative" } }

        val output = outcome == FacePipelinePerformanceOutcome.FULL_OUTPUT ||
            outcome == FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT
        require(output == (materialization != null)) {
            "only output events have a materialization"
        }
        require(output == (enqueueAccepted != null)) {
            "only output events have enqueue accounting"
        }
        if (output) {
            require(
                callbackToOutputReadyNs != null &&
                    outputReadyToEnqueueReturnNs != null &&
                    callbackToEnqueueReturnNs != null,
            ) { "output events require output-ready and enqueue-return segments" }
        }
        if (outcome == FacePipelinePerformanceOutcome.FULL_OUTPUT) {
            require(callbackToRawExtractionNs != null && rawExtractionToOutputReadyNs != null) {
                "full output requires raw and processed SDK segments"
            }
            require(callbackToOutputReadyNs == callbackToRawExtractionNs + rawExtractionToOutputReadyNs) {
                "full output segments must close at output-ready"
            }
        }
        if (outcome == FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT) {
            require(callbackToRawExtractionNs == null && rawExtractionToOutputReadyNs == null) {
                "interpolated output has no raw ARKit extraction segment"
            }
        }
        if (!output) {
            require(
                callbackToRawExtractionNs == null &&
                    rawExtractionToOutputReadyNs == null &&
                    callbackToOutputReadyNs == null &&
                    outputReadyToEnqueueReturnNs == null &&
                    callbackToEnqueueReturnNs == null,
            ) { "drop events cannot claim output latency" }
        } else {
            require(callbackToEnqueueReturnNs == callbackToOutputReadyNs!! + outputReadyToEnqueueReturnNs!!) {
                "output and enqueue segments must close"
            }
        }
    }
}
