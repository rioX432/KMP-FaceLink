package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.FacePipelinePerformanceClock
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FacePipelinePerformanceOutcome
import kotlinx.serialization.Serializable

/** Non-biometric iOS device context for callback-side KMP measurements. */
@Serializable
public data class IosPerformanceDeviceContext(
    val model: String,
    val systemName: String,
    val systemVersion: String,
) {
    init {
        require(model.isNotBlank() && systemName.isNotBlank() && systemVersion.isNotBlank()) {
            "iOS device context must be explicit"
        }
    }
}

/** Aggregate counts for one callback-side run. */
@Serializable
public data class IosProcessedPerformanceCounters(
    val faceAnchorCallbacks: Int,
    val fullOutputs: Int,
    val interpolatedOutputs: Int,
    val noActiveEpochDrops: Int,
    val throttledDrops: Int,
    val interpolationUnavailableDrops: Int,
    val epochChangedDrops: Int,
    val callbackFailures: Int,
    val enqueueAccepted: Int,
    val enqueueRejected: Int,
)

/** Full-output KMP segments. ARKit work before callback entry is absent by construction. */
@Serializable
public data class IosFullOutputLatencySummary(
    val callbackToRawExtraction: PerformanceLatencySummary,
    val rawExtractionToOutputReady: PerformanceLatencySummary,
    val callbackToOutputReady: PerformanceLatencySummary,
    val outputReadyToEnqueueReturn: PerformanceLatencySummary,
    val callbackToEnqueueReturn: PerformanceLatencySummary,
)

/** Interpolated output has no raw ARKit extraction segment. */
@Serializable
public data class IosInterpolatedOutputLatencySummary(
    val callbackToOutputReady: PerformanceLatencySummary,
    val outputReadyToEnqueueReturn: PerformanceLatencySummary,
    val callbackToEnqueueReturn: PerformanceLatencySummary,
)

/**
 * Aggregate-only iOS artifact. It deliberately cannot represent ARKit inference or camera latency,
 * and it contains no coefficient, transform, landmark, frame timestamp, or session identifier.
 */
@Serializable
public data class IosProcessedPerformanceManifest(
    val schemaVersion: Int,
    val runId: String,
    val startedAtIso: String,
    val evidence: ReviewEvidenceBinding,
    val measurementBoundary: String,
    val clock: String,
    val instrumentationScope: String,
    val source: SourceProvenance,
    val device: IosPerformanceDeviceContext,
    val callbackSequenceStart: String?,
    val callbackSequenceEnd: String?,
    val sequenceComplete: Boolean,
    val faceAnchorBatchCount: Int,
    val configurationFingerprint: String?,
    val epochSegmentCount: Int,
    val counters: IosProcessedPerformanceCounters,
    val fullOutput: IosFullOutputLatencySummary,
    val interpolatedOutput: IosInterpolatedOutputLatencySummary,
    val rejectedReasons: Set<String>,
) {
    public companion object {
        public const val SCHEMA_VERSION: Int = 1
        public const val BOUNDARY: String = "ARKIT_CALLBACK_TO_SDK_ENQUEUE_RETURN"
        public const val CLOCK: String = "IOS_MONOTONIC_CALLBACK_CLOCK"
        public const val INSTRUMENTATION_SCOPE: String =
            "INSTRUMENTED_FIRST_FACE_ANCHOR_CALLBACK_PATH"
    }
}

/** Deterministic aggregation and fail-closed accounting for iOS performance events. */
public object IosProcessedPerformanceAggregator {

    @Suppress(
        "LongParameterList",
        "LongMethod",
        // One fail-closed aggregation pass deliberately accounts for every callback outcome.
        "CyclomaticComplexMethod",
    )
    public fun aggregate(
        runId: String,
        startedAtIso: String,
        evidence: ReviewEvidenceBinding,
        source: SourceProvenance,
        device: IosPerformanceDeviceContext,
        events: List<FacePipelinePerformanceEvent>,
    ): IosProcessedPerformanceManifest {
        require(runId.isNotBlank()) { "runId must not be blank" }
        val ordered = events.sortedBy { it.callbackSequence }
        val sequences = ordered.map { it.callbackSequence }
        val batchSequences = ordered.map { it.anchorBatchSequence }
        val sequenceComplete = sequences.zipWithNext().all { (a, b) -> b == a + 1u }
        val configurationFingerprints = ordered.mapNotNull { it.configurationFingerprint }.toSet()
        val epochFingerprints = ordered.mapNotNull { it.epochFingerprint }.toSet()
        val full = ordered.filter { it.outcome == FacePipelinePerformanceOutcome.FULL_OUTPUT }
        val interpolated = ordered.filter {
            it.outcome == FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT
        }

        val counters = IosProcessedPerformanceCounters(
            faceAnchorCallbacks = ordered.size,
            fullOutputs = full.size,
            interpolatedOutputs = interpolated.size,
            noActiveEpochDrops = ordered.count {
                it.outcome == FacePipelinePerformanceOutcome.DROPPED_NO_ACTIVE_EPOCH
            },
            throttledDrops = ordered.count {
                it.outcome == FacePipelinePerformanceOutcome.DROPPED_THROTTLED
            },
            interpolationUnavailableDrops = ordered.count {
                it.outcome == FacePipelinePerformanceOutcome.DROPPED_INTERPOLATION_UNAVAILABLE
            },
            epochChangedDrops = ordered.count {
                it.outcome == FacePipelinePerformanceOutcome.DROPPED_EPOCH_CHANGED
            },
            callbackFailures = ordered.count {
                it.outcome == FacePipelinePerformanceOutcome.CALLBACK_FAILURE
            },
            enqueueAccepted = ordered.count { it.enqueueAccepted == true },
            enqueueRejected = ordered.count { it.enqueueAccepted == false },
        )
        val accounted = counters.fullOutputs + counters.interpolatedOutputs +
            counters.noActiveEpochDrops + counters.throttledDrops +
            counters.interpolationUnavailableDrops + counters.epochChangedDrops +
            counters.callbackFailures
        val rejected = buildSet {
            if (sequences.isEmpty()) add("no callback events")
            if (sequences.firstOrNull()?.let { it != 1uL } == true) {
                add("snapshot buffer overflow or late measurement start")
            }
            if (!sequenceComplete) add("callback sequence gap")
            if (!batchSequences.zipWithNext().all { (a, b) -> b > a }) {
                add("face-anchor batch sequence not strictly increasing")
            }
            if (ordered.any { it.clock != FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK }) {
                add("clock domain")
            }
            if (configurationFingerprints.size > 1) add("mixed configuration fingerprint")
            if (configurationFingerprints.any { !it.isSha256Hex() }) {
                add("invalid configuration fingerprint")
            }
            if (epochFingerprints.size > 1) add("mixed epoch fingerprint")
            if (epochFingerprints.any { !it.isSha256Hex() }) add("invalid epoch fingerprint")
            if (accounted != ordered.size) add("callback accounting")
            if (counters.callbackFailures > 0) add("callback processing failure")
            if (
                counters.enqueueAccepted + counters.enqueueRejected !=
                counters.fullOutputs + counters.interpolatedOutputs
            ) {
                add("enqueue accounting")
            }
            if (
                (full + interpolated).any { event ->
                    event.configurationFingerprint == null ||
                        event.epochFingerprint == null
                }
            ) {
                add("output without runtime context")
            }
        }

        return IosProcessedPerformanceManifest(
            schemaVersion = IosProcessedPerformanceManifest.SCHEMA_VERSION,
            runId = runId,
            startedAtIso = startedAtIso,
            evidence = evidence,
            measurementBoundary = IosProcessedPerformanceManifest.BOUNDARY,
            clock = IosProcessedPerformanceManifest.CLOCK,
            instrumentationScope = IosProcessedPerformanceManifest.INSTRUMENTATION_SCOPE,
            source = source,
            device = device,
            callbackSequenceStart = sequences.firstOrNull()?.toString(),
            callbackSequenceEnd = sequences.lastOrNull()?.toString(),
            sequenceComplete = sequenceComplete,
            faceAnchorBatchCount = batchSequences.toSet().size,
            configurationFingerprint = configurationFingerprints.singleOrNull(),
            epochSegmentCount = epochFingerprints.size,
            counters = counters,
            fullOutput = IosFullOutputLatencySummary(
                callbackToRawExtraction = summarizeRequired(full) { it.callbackToRawExtractionNs },
                rawExtractionToOutputReady = summarizeRequired(full) {
                    it.rawExtractionToOutputReadyNs
                },
                callbackToOutputReady = summarizeRequired(full) { it.callbackToOutputReadyNs },
                outputReadyToEnqueueReturn = summarizeRequired(full) {
                    it.outputReadyToEnqueueReturnNs
                },
                callbackToEnqueueReturn = summarizeRequired(full) { it.callbackToEnqueueReturnNs },
            ),
            interpolatedOutput = IosInterpolatedOutputLatencySummary(
                callbackToOutputReady = summarizeRequired(interpolated) {
                    it.callbackToOutputReadyNs
                },
                outputReadyToEnqueueReturn = summarizeRequired(interpolated) {
                    it.outputReadyToEnqueueReturnNs
                },
                callbackToEnqueueReturn = summarizeRequired(interpolated) {
                    it.callbackToEnqueueReturnNs
                },
            ),
            rejectedReasons = rejected,
        )
    }

    private fun summarizeRequired(
        events: List<FacePipelinePerformanceEvent>,
        value: (FacePipelinePerformanceEvent) -> Long?,
    ): PerformanceLatencySummary {
        val values = events.mapNotNull(value)
        require(values.size == events.size) { "output event is missing a required duration" }
        return PerformanceLatencyAggregator.summarize(values)
    }
}
