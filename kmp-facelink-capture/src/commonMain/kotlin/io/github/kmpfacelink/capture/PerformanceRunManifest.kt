package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/** Measurement layers; paired does not permit their boundary summaries to be merged. */
@Serializable
public enum class PerformanceOutputLayer {
    /** MediaPipe output before SDK transforms. */
    RAW_ESTIMATOR,

    /** Normal KMP-FaceLink output, including its declared transforms and provenance. */
    PROCESSED_SDK_OUTPUT,

    /** Both layers came from each exact same estimator invocation and stored frame. */
    PAIRED_RAW_AND_PROCESSED,
}

/**
 * Exact source and binary review decisions bound into a device-local aggregate.
 *
 * Source authorization permits creating a candidate binary only. [runAuthorizationSha256] names
 * a separately signed authorization that is issued after the exact [binarySha256] and its binary
 * review packet have passed review. Keeping both stages in the result prevents a source-only
 * candidate from presenting itself as an executable-reviewed observation.
 */
@Serializable
public data class ReviewEvidenceBinding(
    val sourcePacketId: String,
    val sourcePacketManifestSha256: String,
    val sourceManifestSha256: String,
    /** SHA-256 of the reviewed build-authorization properties file. */
    val sourceAuthorizationSha256: String,
    val binaryPacketId: String,
    val binaryPacketManifestSha256: String,
    /**
     * Android APK SHA-256 or the canonical complete iOS app-bundle content SHA-256, as declared
     * by the signed run authorization. The iOS value is not only the main executable because a
     * debug launcher can load reviewed Kotlin/Swift code from separate bundle files.
     */
    val binarySha256: String,
    /** SHA-256 of the complete signed run-authorization properties file. */
    val runAuthorizationSha256: String,
    /** SHA-256 of the uncompressed P-256 public key used to verify the authorization. */
    val runAuthorizationSigningKeyId: String,
) {
    init {
        require(sourcePacketId.isNotBlank()) { "source review packet ID must not be blank" }
        require(sourcePacketManifestSha256.isSha256Hex()) {
            "source review packet manifest must be a lowercase SHA-256"
        }
        require(sourceManifestSha256.isSha256Hex()) {
            "review source manifest must be a lowercase SHA-256"
        }
        require(sourceAuthorizationSha256.isSha256Hex()) {
            "source build authorization must be a lowercase SHA-256"
        }
        require(binaryPacketId.isNotBlank()) { "binary review packet ID must not be blank" }
        require(binaryPacketManifestSha256.isSha256Hex()) {
            "binary review packet manifest must be a lowercase SHA-256"
        }
        require(binarySha256.isSha256Hex()) {
            "reviewed binary must be a lowercase SHA-256"
        }
        require(runAuthorizationSha256.isSha256Hex()) {
            "signed run authorization must be a lowercase SHA-256"
        }
        require(runAuthorizationSigningKeyId.isSha256Hex()) {
            "run-authorization signing key ID must be a lowercase SHA-256"
        }
    }
}

/** Named monotonic clock domain for Android offline performance intervals. */
@Serializable
public enum class PerformanceMeasurementClock {
    ANDROID_ELAPSED_REALTIME_NANOS,
}

/** Processing outcome retained as a separate aggregate stratum. */
@Serializable
public enum class PerformanceSampleOutcome {
    FACE_OUTPUT,
    NO_FACE_OUTPUT,
    PROCESSING_FAILURE,
}

/** Fixed benchmark phases. Reports retain them separately to expose warm-up and thermal drift. */
@Serializable
public enum class PerformanceRunPhase {
    WARMUP,
    STEADY,
    COOLDOWN,
}

/** Timestamp treatment is explicit because VIDEO inference is stateful in frame time. */
@Serializable
public enum class ReplayTimestampPolicy {
    /** Keep recorded inter-frame deltas while rebasing each repeated stream monotonically. */
    ORIGINAL_DELTAS_REBASED_MONOTONIC,
}

/** Stateful objects that intentionally carry from performance warm-up into steady measurement. */
@Serializable
public enum class OfflineStateCarryPolicy {
    /** One runner; estimator, calibration, and smoother state remain continuous across phases. */
    CONTIGUOUS_WARMUP_TO_STEADY,
}

/** Exact start and end of one latency sample. */
@Serializable
public enum class PerformanceLatencyBoundary {
    /**
     * Immediately before calling the estimator until its synchronous result returns. Stored-frame
     * reads, RGBA-to-platform-image materialization, SDK transforms, and artifact writes are out of
     * scope. Both stamps come from the same monotonic clock.
     */
    ESTIMATOR_CALL_ONLY,

    /** Raw result available until immutable SDK output and per-face provenance are ready. */
    SDK_PROCESS_TO_OUTPUT_READY,

    /** Sum of the two adjacent segments above for the same invocation. */
    ESTIMATOR_TO_SDK_OUTPUT_READY,

    /** Raw result to copied raw arrays; performed only after processed output is ready. */
    RAW_OUTPUT_MATERIALIZATION,
}

/** Replay protocol required to reproduce load duration and delegate selection. */
@Serializable
public data class PerformanceReplayProtocol(
    val warmupRepetitions: Int,
    val steadyRepetitions: Int,
    val cooldownMs: Long,
    val requestedDelegate: String,
    val expectedResolvedDelegate: String,
    val latencyBoundaries: Set<PerformanceLatencyBoundary>,
    val stateCarryPolicy: OfflineStateCarryPolicy,
) {
    init {
        require(warmupRepetitions > 0) { "warmupRepetitions must be positive" }
        require(steadyRepetitions > 0) { "steadyRepetitions must be positive" }
        require(cooldownMs > 0) { "cooldownMs must be positive" }
        require(requestedDelegate.isNotBlank()) { "requestedDelegate must not be blank" }
        require(expectedResolvedDelegate.isNotBlank()) { "expectedResolvedDelegate must not be blank" }
        require(latencyBoundaries.isNotEmpty()) { "latencyBoundaries must not be empty" }
    }
}

/** Explicit availability state for telemetry that Android versions and devices may not expose. */
@Serializable
public sealed interface TelemetryValue<out T> {
    @Serializable
    public data class Available<T>(val value: T) : TelemetryValue<T>

    @Serializable
    public data class Unavailable(val reason: String) : TelemetryValue<Nothing> {
        init {
            require(reason.isNotBlank()) { "unavailable telemetry requires a reason" }
        }
    }
}

/** Non-biometric device facts needed to interpret one run. */
@Serializable
public data class PerformanceDeviceContext(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val androidApi: Int,
    val abi: String,
    val buildFingerprintSha256: String,
) {
    init {
        require(manufacturer.isNotBlank()) { "manufacturer must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(androidRelease.isNotBlank()) { "androidRelease must not be blank" }
        require(androidApi > 0) { "androidApi must be positive" }
        require(abi.isNotBlank()) { "abi must not be blank" }
        require(buildFingerprintSha256.isSha256Hex()) { "build fingerprint must be a lowercase SHA-256" }
    }
}

/** Submission-to-callback latency from one monotonic clock domain. */
@Serializable
public data class PerformanceLatencySummary(
    val sampleCount: Int,
    val p50Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
) {
    init {
        require(sampleCount >= 0) { "sampleCount must not be negative" }
        require(p50Ms >= 0.0) { "p50Ms must not be negative" }
        require(p50Ms <= p95Ms && p95Ms <= p99Ms && p99Ms <= maxMs) {
            "latency quantiles must be monotonic"
        }
    }
}

/** Aggregate-only result for one benchmark phase. */
@Serializable
public data class PerformancePhaseSummary(
    val phase: PerformanceRunPhase,
    val startElapsedMs: Long,
    val endElapsedMs: Long,
    val inferenceCalls: Int,
    val faceOutputs: Int,
    val noFaceOutputs: Int,
    val processingFailures: Int,
    val analyzerArrivals: TelemetryValue<Int>,
    val analyzerAdmissions: TelemetryValue<Int>,
    val asynchronousCallbacks: TelemetryValue<Int>,
    val enqueueAttempts: TelemetryValue<Int>,
    val writerFailures: TelemetryValue<Int>,
    val latencies: Map<PerformanceLatencyBoundary, PerformanceLatencySummary>,
    val latenciesByOutcome: Map<
        PerformanceSampleOutcome,
        Map<PerformanceLatencyBoundary, PerformanceLatencySummary>,
        >,
    val thermalStatus: TelemetryValue<String>,
    val batteryLevelFraction: TelemetryValue<Double>,
) {
    init {
        require(startElapsedMs >= 0) { "phase start must not be negative" }
        require(endElapsedMs > startElapsedMs) { "phase end must follow start" }
        listOf(
            inferenceCalls,
            faceOutputs,
            noFaceOutputs,
            processingFailures,
        ).forEach { require(it >= 0) { "performance counters must not be negative" } }
        require(latencies.keys.isNotEmpty()) { "each phase must declare latency boundaries" }
    }
}

/** Aggregate proof that processed samples retained the capability/provenance boundary. */
@Serializable
public data class ProcessedOutputSummary(
    val configurationFingerprint: String,
    val epochSegmentCount: Int,
    val outputsWithProvenance: Int,
    val outputsWithoutProvenance: Int,
    val pipelineKinds: Set<String>,
    val materializations: Set<String>,
    val calibrationPhases: Set<String>,
    val calibrationStateRevisionStart: String?,
    val calibrationStateRevisionEnd: String?,
    val processingStateRevisionStart: String?,
    val processingStateRevisionEnd: String?,
    val appliedTransformFingerprints: Set<String>,
) {
    init {
        require(configurationFingerprint.isSha256Hex()) {
            "processed configuration fingerprint must be a lowercase SHA-256"
        }
        require(epochSegmentCount > 0) { "epochSegmentCount must be positive" }
        require(outputsWithProvenance >= 0 && outputsWithoutProvenance >= 0) {
            "provenance counters must not be negative"
        }
    }
}

/**
 * Public-safe performance artifact. It contains aggregate counters and non-biometric provenance;
 * raw pixels, landmarks, transforms, per-frame values, and tracker session IDs are deliberately
 * absent from this schema.
 */
@Serializable
public data class PerformanceRunManifest(
    val schemaVersion: Int,
    val runId: String,
    val startedAtIso: String,
    val evidence: ReviewEvidenceBinding,
    val outputLayer: PerformanceOutputLayer,
    val clock: PerformanceMeasurementClock,
    val conditionFingerprint: String,
    val sourceTakeManifestSha256: String?,
    val sourceFrameManifestSha256: String?,
    val exactStoredPixelReplay: Boolean,
    val replayTimestampPolicy: ReplayTimestampPolicy?,
    val replayProtocol: PerformanceReplayProtocol?,
    val expandedReplayTimelineSha256: String?,
    val saveFrames: Boolean,
    val inputArtifactsRetainedLocally: Boolean,
    val device: PerformanceDeviceContext,
    val condition: CaptureCondition,
    val phases: List<PerformancePhaseSummary>,
    val processedOutput: ProcessedOutputSummary?,
    val abortedReason: String?,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "unsupported performance schema $schemaVersion" }
        require(runId.isNotBlank()) { "runId must not be blank" }
        require(startedAtIso.isNotBlank()) { "startedAtIso must not be blank" }
        require(conditionFingerprint.isSha256Hex()) { "condition fingerprint must be a lowercase SHA-256" }
        require(sourceTakeManifestSha256 == null || sourceTakeManifestSha256.isSha256Hex()) {
            "source take manifest must be a lowercase SHA-256"
        }
        require(sourceFrameManifestSha256 == null || sourceFrameManifestSha256.isSha256Hex()) {
            "source frame manifest must be a lowercase SHA-256"
        }
        require(!exactStoredPixelReplay || sourceTakeManifestSha256 != null && sourceFrameManifestSha256 != null) {
            "exact stored-pixel replay requires hash-bound take and frame manifests"
        }
        require(!exactStoredPixelReplay || replayTimestampPolicy != null) {
            "stored-pixel replay requires an explicit timestamp policy"
        }
        require(!exactStoredPixelReplay || replayProtocol != null) {
            "stored-pixel replay requires an explicit replay protocol"
        }
        require(expandedReplayTimelineSha256 == null || expandedReplayTimelineSha256.isSha256Hex()) {
            "expanded replay timeline must be a lowercase SHA-256"
        }
        require(!exactStoredPixelReplay || expandedReplayTimelineSha256 != null) {
            "stored-pixel replay requires a hash-bound expanded timeline"
        }
    }

    public companion object {
        public const val SCHEMA_VERSION: Int = 2
    }
}

/** Fail-closed validation for benchmark comparison and public export. */
public object PerformanceRunValidator {

    /** Returns every reason the manifest is not a valid aggregate-only benchmark artifact. */
    public fun rejectionReasons(
        manifest: PerformanceRunManifest,
        requireReproducibleSource: Boolean,
        expectedEvidence: ReviewEvidenceBinding? = null,
    ): Set<String> = buildSet {
        if (manifest.phases.map { it.phase } != PerformanceRunPhase.entries.toList()) add("phase order")
        manifest.phases.zipWithNext().forEach { (first, second) ->
            if (first.endElapsedMs > second.startElapsedMs) add("overlapping phases")
        }
        manifest.phases.forEach { phase ->
            if (phase.faceOutputs + phase.noFaceOutputs + phase.processingFailures != phase.inferenceCalls) {
                add("offline inference accounting")
            }
            phase.latencies.forEach { (boundary, latency) ->
                val expected = if (phase.phase == PerformanceRunPhase.COOLDOWN) 0 else phase.inferenceCalls
                if (latency.sampleCount != expected) add("latency sample count for $boundary")
            }
            if (phase.latenciesByOutcome.keys != PerformanceSampleOutcome.entries.toSet()) {
                add("outcome strata set")
            }
            phase.latenciesByOutcome.forEach { (outcome, latencies) ->
                if (latencies.keys != phase.latencies.keys) add("outcome boundary set for $outcome")
                val expected = when (outcome) {
                    PerformanceSampleOutcome.FACE_OUTPUT -> phase.faceOutputs
                    PerformanceSampleOutcome.NO_FACE_OUTPUT -> phase.noFaceOutputs
                    PerformanceSampleOutcome.PROCESSING_FAILURE -> phase.processingFailures
                }
                latencies.forEach { (boundary, latency) ->
                    if (latency.sampleCount != expected) {
                        add("outcome latency sample count for $outcome/$boundary")
                    }
                }
            }
            listOf(
                phase.analyzerArrivals,
                phase.analyzerAdmissions,
                phase.asynchronousCallbacks,
                phase.enqueueAttempts,
                phase.writerFailures,
            ).forEach { value ->
                if (value !is TelemetryValue.Unavailable) add("offline live counter must be unavailable")
            }
        }
        val requiredBoundaries = manifest.replayProtocol?.latencyBoundaries.orEmpty()
        if (manifest.phases.any { it.latencies.keys != requiredBoundaries }) add("latency boundary set")
        if (manifest.outputLayer == PerformanceOutputLayer.PAIRED_RAW_AND_PROCESSED) {
            if (manifest.processedOutput == null) add("missing processed provenance")
            if (!manifest.exactStoredPixelReplay) add("paired run requires exact stored-pixel replay")
            if (manifest.sourceTakeManifestSha256 == null) add("paired run requires take manifest hash")
            if (manifest.sourceFrameManifestSha256 == null) add("paired run requires frame manifest hash")
            if (manifest.replayTimestampPolicy == null) add("paired run requires timestamp policy")
            if (manifest.replayProtocol == null) add("paired run requires replay protocol")
            if (manifest.expandedReplayTimelineSha256 == null) {
                add("paired run requires expanded replay timeline hash")
            }
            if (manifest.condition.layer != MeasurementLayer.DEVICE_OFFLINE) {
                add("paired run requires device-offline layer")
            }
            if (manifest.saveFrames) add("paired run must not duplicate stored frames")
            if (!manifest.inputArtifactsRetainedLocally) {
                add("paired run requires local input retention")
            }
        }
        val processed = manifest.processedOutput
        if (processed?.configurationFingerprint != manifest.conditionFingerprint) {
            add("processed configuration fingerprint mismatch")
        }
        if (processed?.outputsWithoutProvenance != 0) add("processed output without provenance")
        if (processed != null) {
            val faceOutputs = manifest.phases.sumOf { it.faceOutputs }
            if (processed.outputsWithProvenance != faceOutputs) add("processed provenance accounting")
            val expectedPopulated = faceOutputs > 0
            if ((processed.pipelineKinds.isNotEmpty()) != expectedPopulated) add("processed pipeline kinds")
            if ((processed.materializations.isNotEmpty()) != expectedPopulated) add("processed materializations")
            if ((processed.calibrationPhases.isNotEmpty()) != expectedPopulated) add("processed calibration phases")
            if (expectedPopulated && processed.pipelineKinds != setOf("PRIMARY")) {
                add("processed pipeline kind")
            }
            if (expectedPopulated && processed.materializations != setOf("FULL_INFERENCE")) {
                add("processed materialization")
            }
            if (expectedPopulated && processed.calibrationPhases != setOf("DISABLED")) {
                add("processed calibration phase")
            }
        }
        if (manifest.clock != PerformanceMeasurementClock.ANDROID_ELAPSED_REALTIME_NANOS) {
            add("clock domain")
        }
        if (manifest.condition.runningMode != "IMAGE") add("offline primary running mode")
        if (manifest.condition.numFaces != 1) add("offline primary face count")
        if (manifest.abortedReason != null) add("aborted run")
        if (manifest.saveFrames != manifest.inputArtifactsRetainedLocally && !manifest.exactStoredPixelReplay) {
            add("frame retention state")
        }
        if (expectedEvidence != null && manifest.evidence != expectedEvidence) {
            add("review evidence binding mismatch")
        }
        if (requireReproducibleSource && !manifest.condition.provenance.reproducible && expectedEvidence == null) {
            add("source provenance is not reproducible and no expected review binding was supplied")
        }
    }

    public fun isValid(
        manifest: PerformanceRunManifest,
        requireReproducibleSource: Boolean,
        expectedEvidence: ReviewEvidenceBinding? = null,
    ): Boolean = rejectionReasons(manifest, requireReproducibleSource, expectedEvidence).isEmpty()
}

/** Builds deterministic percentile summaries without retaining data in the final artifact. */
public object PerformanceLatencyAggregator {
    public fun summarize(submissionToCallbackNs: List<Long>): PerformanceLatencySummary {
        require(submissionToCallbackNs.all { it >= 0 }) { "latencies must not be negative" }
        if (submissionToCallbackNs.isEmpty()) {
            return PerformanceLatencySummary(0, 0.0, 0.0, 0.0, 0.0)
        }
        val values = submissionToCallbackNs.sorted()
        fun percentile(numerator: Int, denominator: Int): Double {
            val rank = ((values.size - 1) * numerator + denominator - 1) / denominator
            return values[rank] / NANOS_PER_MILLISECOND
        }
        return PerformanceLatencySummary(
            sampleCount = values.size,
            p50Ms = percentile(50, 100),
            p95Ms = percentile(95, 100),
            p99Ms = percentile(99, 100),
            maxMs = values.last() / NANOS_PER_MILLISECOND,
        )
    }
}

internal fun String.isSha256Hex(): Boolean =
    length == SHA256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }

private const val SHA256_HEX_LENGTH = 64
private const val NANOS_PER_MILLISECOND = 1_000_000.0
