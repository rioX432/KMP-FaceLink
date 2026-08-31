package io.github.kmpfacelink.capture

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import io.github.kmpfacelink.api.OfflineFrame
import io.github.kmpfacelink.api.OfflineMeasuredFaceResult
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.openOfflineFaceInference
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.MessageDigest

/** Explicit replay condition. A fallback that does not equal [expectedResolvedDelegate] fails. */
internal data class PerformanceBenchmarkConfig(
    val mode: InferenceRunningMode,
    val preferGpu: Boolean,
    val expectedResolvedDelegate: String,
    val evidence: ReviewEvidenceBinding,
    /** Processing configuration; mode/delegate are replaced by the explicit benchmark fields. */
    val processingConfig: FaceTrackerConfig = FaceTrackerConfig(),
    val warmupRepetitions: Int = 1,
    val steadyRepetitions: Int = 3,
    val cooldownMs: Long = 2_000L,
) {
    init {
        require(mode == InferenceRunningMode.IMAGE) {
            "paired benchmark currently supports IMAGE only; VIDEO repetition boundaries carry " +
                "tracker state across an artificial wrap, and LIVE_STREAM is asynchronous"
        }
        require(expectedResolvedDelegate in setOf("CPU", "GPU")) {
            "expectedResolvedDelegate must be CPU or GPU"
        }
        require(warmupRepetitions > 0) { "warmupRepetitions must be positive" }
        require(steadyRepetitions > 0) { "steadyRepetitions must be positive" }
        require(cooldownMs > 0) { "cooldownMs must be positive" }
        require(!processingConfig.calibrationConfig.enabled) {
            "controlled paired benchmark requires calibration disabled; learned state needs a " +
                "separate preregistered warm-up protocol"
        }
        require(!processingConfig.frameSkipConfig.enabled) {
            "offline paired benchmark measures full processing only; interpolation is a live stratum"
        }
        require(processingConfig.detectionConfig.maxFaces == 1) {
            "controlled paired benchmark requires exactly one configured face"
        }
    }
}

/** Aggregate-only file and its independent SHA-256 sidecar. */
public data class PerformanceBenchmarkArtifact(
    val manifest: PerformanceRunManifest,
    val manifestFile: File,
    val manifestSha256: String,
)

/**
 * Replays a hash-checked stored take and writes no pixels, landmarks, transforms, or coefficients.
 * The raw take remains private in its original directory; the produced JSON is public-safe by
 * schema and can be reviewed independently through its SHA-256 sidecar.
 */
@Suppress("TooManyFunctions")
internal class AndroidPerformanceBenchmark(
    private val platformContext: PlatformContext,
) {

    private val context: Context get() = platformContext.context

    @JvmSynthetic // reflection reaches this too, so it performs the same raw-byte gate itself.
    @Suppress("LongMethod") // Keep the fail-closed replay protocol and artifact commit in one audit boundary.
    internal fun run(
        takeDir: File,
        authorizationBytes: ByteArray,
    ): PerformanceBenchmarkArtifact {
        // This must remain before take/model reads and before any output directory can be created.
        // Kotlin/JVM visibility annotations are not a reflection-security boundary.
        AndroidRunAuthorizationVerifier.requireAuthorizationBytes(authorizationBytes)
        val authorization = AndroidRunAuthorizationVerifier.verifyInstalled(context, authorizationBytes)
        val config = PerformanceBenchmarkConfig(
            mode = InferenceRunningMode.IMAGE,
            preferGpu = false,
            expectedResolvedDelegate = "CPU",
            evidence = authorization.evidence,
        )
        val takeFile = File(takeDir, InferenceRecordFormat.TAKE_MANIFEST_FILE)
        require(takeFile.isFile) { "${takeFile.path} is missing" }
        val take = rawFrameJson.decodeFromString<TakeManifest>(takeFile.readText())
        val sourceTakeSha = sha256File(takeFile)
        val frameManifestFile = File(
            File(takeDir, AndroidCaptureSession.FRAMES_DIR),
            RawFrameFormat.FRAMES_META_FILE,
        )
        require(frameManifestFile.isFile) { "${frameManifestFile.path} is missing" }
        val sourceFrameManifestSha = sha256File(frameManifestFile)
        val modelSha = modelAssetSha256(context, AndroidCaptureSession.DEFAULT_MODEL_ASSET)
        require(modelSha == take.condition.modelSha256) {
            "replay model $modelSha differs from take model ${take.condition.modelSha256}"
        }

        // Construction verifies both raw-frame hashes before any timing starts.
        val sourceFrames = RawFrameReader(
            File(takeDir, AndroidCaptureSession.FRAMES_DIR).absolutePath,
            storageReceipt = take.storageReceipt,
        )
        require(sourceFrames.frameCount > 0) { "benchmark source take has no stored frames" }

        val trackerConfig = config.processingConfig.copy(
            modelAssetPath = AndroidCaptureSession.DEFAULT_MODEL_ASSET,
            performanceConfig = config.processingConfig.performanceConfig.copy(
                inferenceDelegate = if (config.preferGpu) InferenceDelegate.GPU else InferenceDelegate.CPU,
                runningMode = config.mode,
            ),
        )
        val inference = openOfflineFaceInference(platformContext, trackerConfig)

        val startedAtMs = System.currentTimeMillis()
        val runStartedNs = SystemClock.elapsedRealtimeNanos()
        val timestampRebaser = TimestampRebaser()
        val timelineDigest = MessageDigest.getInstance(SHA_256)
        val processed = ProcessedAccumulator(config.expectedResolvedDelegate)
        val summaries = try {
            listOf(
                measureReplayPhase(
                    takeDir,
                    PerformanceRunPhase.WARMUP,
                    config.warmupRepetitions,
                    runStartedNs,
                    timestampRebaser,
                    timelineDigest,
                    inference,
                    processed,
                    take.condition.mirrored,
                ),
                measureReplayPhase(
                    takeDir,
                    PerformanceRunPhase.STEADY,
                    config.steadyRepetitions,
                    runStartedNs,
                    timestampRebaser,
                    timelineDigest,
                    inference,
                    processed,
                    take.condition.mirrored,
                ),
                measureCooldown(config.cooldownMs, runStartedNs),
            )
        } finally {
            inference.release()
        }
        val processedSummary = processed.toSummary()
        val timelineSha = timelineDigest.digest().toHex()

        val replayCondition = take.condition.copy(
            nativeResolution = "${sourceFrames.width}x${sourceFrames.height}",
            runningMode = config.mode.name,
            delegate = config.expectedResolvedDelegate,
            numFaces = trackerConfig.detectionConfig.maxFaces,
            minDetectionConfidence = trackerConfig.detectionConfig.minDetectionConfidence,
            minTrackingConfidence = trackerConfig.detectionConfig.minTrackingConfidence,
            minPresenceConfidence = trackerConfig.detectionConfig.minPresenceConfidence,
            modelSha256 = modelSha,
            layer = MeasurementLayer.DEVICE_OFFLINE,
            provenance = authorization.provenance,
        )
        val conditionFingerprint = processedSummary.configurationFingerprint
        val manifest = PerformanceRunManifest(
            schemaVersion = PerformanceRunManifest.SCHEMA_VERSION,
            runId = "${take.takeId}-$startedAtMs-paired-${config.mode.name.lowercase()}-" +
                config.expectedResolvedDelegate.lowercase(),
            startedAtIso = isoTimestamp(startedAtMs),
            evidence = config.evidence,
            outputLayer = PerformanceOutputLayer.PAIRED_RAW_AND_PROCESSED,
            clock = PerformanceMeasurementClock.ANDROID_ELAPSED_REALTIME_NANOS,
            conditionFingerprint = conditionFingerprint,
            sourceTakeManifestSha256 = sourceTakeSha,
            sourceFrameManifestSha256 = sourceFrameManifestSha,
            exactStoredPixelReplay = true,
            replayTimestampPolicy = ReplayTimestampPolicy.ORIGINAL_DELTAS_REBASED_MONOTONIC,
            replayProtocol = PerformanceReplayProtocol(
                warmupRepetitions = config.warmupRepetitions,
                steadyRepetitions = config.steadyRepetitions,
                cooldownMs = config.cooldownMs,
                requestedDelegate = if (config.preferGpu) "GPU_THEN_CPU" else "CPU",
                expectedResolvedDelegate = config.expectedResolvedDelegate,
                latencyBoundaries = PAIRED_BOUNDARIES,
                stateCarryPolicy = OfflineStateCarryPolicy.CONTIGUOUS_WARMUP_TO_STEADY,
            ),
            expandedReplayTimelineSha256 = timelineSha,
            retainsSourceFrames = false,
            inputArtifactsRetainedLocally = true,
            device = deviceContext(),
            condition = replayCondition,
            phases = summaries,
            processedOutput = processedSummary,
            abortedReason = null,
        )
        val rejections = PerformanceRunValidator.rejectionReasons(
            manifest,
            requireReproducibleSource = false,
            expectedEvidence = config.evidence,
        )
        check(rejections.isEmpty()) { "invalid performance run: ${rejections.sorted().joinToString()}" }

        val outputDir = File(takeDir, PERFORMANCE_DIR).apply { mkdirs() }
        val output = File(outputDir, "${manifest.runId}.json")
        check(!output.exists()) { "performance run ${manifest.runId} already exists" }
        val bytes = rawFrameJson.encodeToString(manifest).encodeToByteArray()
        output.writeBytes(bytes)
        val outputSha = sha256Bytes(bytes)
        File(outputDir, "${manifest.runId}.sha256").writeText("$outputSha  ${output.name}\n")
        return PerformanceBenchmarkArtifact(manifest, output, outputSha)
    }

    @Suppress(
        "LongMethod", // One loop owns all latency and outcome counts for an exact replay phase.
        "LongParameterList", // The explicit inputs make the measured boundary reviewable.
    )
    private fun measureReplayPhase(
        takeDir: File,
        phase: PerformanceRunPhase,
        repetitions: Int,
        runStartedNs: Long,
        timestamps: TimestampRebaser,
        timelineDigest: MessageDigest,
        inference: io.github.kmpfacelink.api.OfflineFaceInference,
        processed: ProcessedAccumulator,
        mirroredBeforeInference: Boolean,
    ): PerformancePhaseSummary {
        val startNs = SystemClock.elapsedRealtimeNanos()
        var calls = 0
        var faceOutputs = 0
        var noFaceOutputs = 0
        var processingFailures = 0
        val estimatorLatencies = ArrayList<Long>()
        val sdkLatencies = ArrayList<Long>()
        val combinedLatencies = ArrayList<Long>()
        val rawMaterializationLatencies = ArrayList<Long>()
        val outcomeLatencies = PerformanceSampleOutcome.entries.associateWith {
            PAIRED_BOUNDARIES.associateWith { ArrayList<Long>() }
        }
        repeat(repetitions) { repetition ->
            val frames = RawFrameReader(File(takeDir, AndroidCaptureSession.FRAMES_DIR).absolutePath)
            frames.frames().forEach { frame ->
                val timestampMs = timestamps.next(frame.inferenceTimestampMs)
                timelineDigest.update(
                    "${phase.name}|$repetition|${frame.analysisFrameSeq}|$timestampMs\n".encodeToByteArray(),
                )
                val result = inference.inferMeasured(
                    OfflineFrame(
                        width = frame.width,
                        height = frame.height,
                        rgba = frame.copyPixels(),
                        timestampMs = timestampMs,
                        mirroredBeforeInference = mirroredBeforeInference,
                    ),
                )
                processed.add(result)
                estimatorLatencies += result.timing.estimatorCallNs
                sdkLatencies += result.timing.sdkProcessToOutputReadyNs
                combinedLatencies += result.timing.estimatorToSdkOutputReadyNs
                rawMaterializationLatencies += result.timing.rawOutputMaterializationNs
                calls++
                val outcome = when {
                    result.processed != null -> PerformanceSampleOutcome.FACE_OUTPUT.also { faceOutputs++ }
                    result.raw.hasFace -> PerformanceSampleOutcome.PROCESSING_FAILURE.also {
                        processingFailures++
                    }
                    else -> PerformanceSampleOutcome.NO_FACE_OUTPUT.also { noFaceOutputs++ }
                }
                outcomeLatencies.getValue(outcome).getValue(
                    PerformanceLatencyBoundary.ESTIMATOR_CALL_ONLY,
                ) += result.timing.estimatorCallNs
                outcomeLatencies.getValue(outcome).getValue(
                    PerformanceLatencyBoundary.SDK_PROCESS_TO_OUTPUT_READY,
                ) += result.timing.sdkProcessToOutputReadyNs
                outcomeLatencies.getValue(outcome).getValue(
                    PerformanceLatencyBoundary.ESTIMATOR_TO_SDK_OUTPUT_READY,
                ) += result.timing.estimatorToSdkOutputReadyNs
                outcomeLatencies.getValue(outcome).getValue(
                    PerformanceLatencyBoundary.RAW_OUTPUT_MATERIALIZATION,
                ) += result.timing.rawOutputMaterializationNs
            }
        }
        val endNs = SystemClock.elapsedRealtimeNanos()
        return PerformancePhaseSummary(
            phase = phase,
            startElapsedMs = elapsedMs(startNs, runStartedNs),
            endElapsedMs = endAfterStart(endNs, runStartedNs, startNs),
            inferenceCalls = calls,
            faceOutputs = faceOutputs,
            noFaceOutputs = noFaceOutputs,
            processingFailures = processingFailures,
            analyzerArrivals = notApplicableOffline("no CameraX analyzer in stored offline replay"),
            analyzerAdmissions = notApplicableOffline("no CameraX analyzer in stored offline replay"),
            asynchronousCallbacks = notApplicableOffline("IMAGE/VIDEO replay is synchronous"),
            enqueueAttempts = notApplicableOffline("offline output is not enqueued to SDK Flow"),
            writerFailures = notApplicableOffline("artifact writing is outside timed replay"),
            latencies = mapOf(
                PerformanceLatencyBoundary.ESTIMATOR_CALL_ONLY to
                    PerformanceLatencyAggregator.summarize(estimatorLatencies),
                PerformanceLatencyBoundary.SDK_PROCESS_TO_OUTPUT_READY to
                    PerformanceLatencyAggregator.summarize(sdkLatencies),
                PerformanceLatencyBoundary.ESTIMATOR_TO_SDK_OUTPUT_READY to
                    PerformanceLatencyAggregator.summarize(combinedLatencies),
                PerformanceLatencyBoundary.RAW_OUTPUT_MATERIALIZATION to
                    PerformanceLatencyAggregator.summarize(rawMaterializationLatencies),
            ),
            latenciesByOutcome = outcomeLatencies.mapValues { (_, boundaries) ->
                boundaries.mapValues { (_, samples) -> PerformanceLatencyAggregator.summarize(samples) }
            },
            thermalStatus = thermalStatus(),
            batteryLevelFraction = batteryLevel(),
        )
    }

    private fun measureCooldown(durationMs: Long, runStartedNs: Long): PerformancePhaseSummary {
        val startNs = SystemClock.elapsedRealtimeNanos()
        Thread.sleep(durationMs)
        val endNs = SystemClock.elapsedRealtimeNanos()
        return PerformancePhaseSummary(
            phase = PerformanceRunPhase.COOLDOWN,
            startElapsedMs = elapsedMs(startNs, runStartedNs),
            endElapsedMs = endAfterStart(endNs, runStartedNs, startNs),
            inferenceCalls = 0,
            faceOutputs = 0,
            noFaceOutputs = 0,
            processingFailures = 0,
            analyzerArrivals = notApplicableOffline("no CameraX analyzer in stored offline replay"),
            analyzerAdmissions = notApplicableOffline("no CameraX analyzer in stored offline replay"),
            asynchronousCallbacks = notApplicableOffline("IMAGE/VIDEO replay is synchronous"),
            enqueueAttempts = notApplicableOffline("offline output is not enqueued to SDK Flow"),
            writerFailures = notApplicableOffline("artifact writing is outside timed replay"),
            latencies = PAIRED_BOUNDARIES.associateWith {
                PerformanceLatencyAggregator.summarize(emptyList())
            },
            latenciesByOutcome = PerformanceSampleOutcome.entries.associateWith {
                PAIRED_BOUNDARIES.associateWith {
                    PerformanceLatencyAggregator.summarize(emptyList())
                }
            },
            thermalStatus = thermalStatus(),
            batteryLevelFraction = batteryLevel(),
        )
    }

    private fun deviceContext(): PerformanceDeviceContext = PerformanceDeviceContext(
        manufacturer = Build.MANUFACTURER.ifBlank { "unknown" },
        model = Build.MODEL.ifBlank { "unknown" },
        androidRelease = Build.VERSION.RELEASE.ifBlank { "unknown" },
        androidApi = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
        buildFingerprintSha256 = sha256Bytes(Build.FINGERPRINT.encodeToByteArray()),
    )

    private fun thermalStatus(): TelemetryValue<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return TelemetryValue.Unavailable("thermal status requires Android 10/API 29")
        }
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return TelemetryValue.Unavailable("PowerManager unavailable")
        return TelemetryValue.Available(power.currentThermalStatus.toString())
    }

    private fun batteryLevel(): TelemetryValue<Double> {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return TelemetryValue.Unavailable("BatteryManager unavailable")
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 0..PERCENT_MAX) return TelemetryValue.Unavailable("capacity unavailable")
        return TelemetryValue.Available(percent / PERCENT_MAX.toDouble())
    }

    private class TimestampRebaser {
        private var outputMs = 0L
        private var previousInputMs: Long? = null

        fun next(inputMs: Long): Long {
            val previous = previousInputMs
            val delta = if (previous == null) MIN_TIMESTAMP_STEP_MS else (inputMs - previous).coerceAtLeast(1L)
            outputMs += delta
            previousInputMs = inputMs
            return outputMs
        }
    }

    /** Collects only non-biometric provenance categories and revisions. */
    private class ProcessedAccumulator(private val expectedResolvedDelegate: String) {
        private var configurationFingerprint: String? = null
        private val epochFingerprints = linkedSetOf<String>()
        private var outputsWithProvenance: Int = 0
        private var outputsWithoutProvenance: Int = 0
        private val pipelineKinds = linkedSetOf<String>()
        private val materializations = linkedSetOf<String>()
        private val calibrationPhases = linkedSetOf<String>()
        private var calibrationRevisionStart: String? = null
        private var calibrationRevisionEnd: String? = null
        private var processingRevisionStart: String? = null
        private var processingRevisionEnd: String? = null
        private val appliedTransformFingerprints = linkedSetOf<String>()

        fun add(result: OfflineMeasuredFaceResult) {
            val epoch = result.contextEpoch
            val resolved = (epoch.resolvedDelegate as? DimensionValue.Known)?.value
            require(resolved == expectedResolvedDelegate) {
                "resolved delegate $resolved differs from expected $expectedResolvedDelegate"
            }
            val prior = configurationFingerprint
            require(prior == null || prior == epoch.configurationFingerprint) {
                "configuration fingerprint changed within one performance run"
            }
            configurationFingerprint = epoch.configurationFingerprint
            epochFingerprints += epoch.epochFingerprint

            result.processed?.faces.orEmpty().forEach { face ->
                val provenance = face.provenance
                if (provenance == null) {
                    outputsWithoutProvenance++
                    return@forEach
                }
                outputsWithProvenance++
                pipelineKinds += provenance.pipelineKind.name
                materializations += provenance.materialization.name
                calibrationPhases += provenance.calibrationPhase.name
                val calibrationRevision = provenance.calibrationStateRevision.toString()
                val processingRevision = provenance.processingStateRevision.toString()
                if (calibrationRevisionStart == null) calibrationRevisionStart = calibrationRevision
                if (processingRevisionStart == null) processingRevisionStart = processingRevision
                calibrationRevisionEnd = calibrationRevision
                processingRevisionEnd = processingRevision
                provenance.appliedTransforms.forEach { transform ->
                    appliedTransformFingerprints += transform.transformFingerprint
                }
            }
        }

        fun toSummary(): ProcessedOutputSummary = ProcessedOutputSummary(
            configurationFingerprint = checkNotNull(configurationFingerprint) {
                "performance run produced no measured invocation"
            },
            epochSegmentCount = epochFingerprints.size,
            outputsWithProvenance = outputsWithProvenance,
            outputsWithoutProvenance = outputsWithoutProvenance,
            pipelineKinds = pipelineKinds,
            materializations = materializations,
            calibrationPhases = calibrationPhases,
            calibrationStateRevisionStart = calibrationRevisionStart,
            calibrationStateRevisionEnd = calibrationRevisionEnd,
            processingStateRevisionStart = processingRevisionStart,
            processingStateRevisionEnd = processingRevisionEnd,
            appliedTransformFingerprints = appliedTransformFingerprints,
        )
    }

    private fun <T> notApplicableOffline(reason: String): TelemetryValue<T> =
        TelemetryValue.Unavailable(reason)

    private fun elapsedMs(nowNs: Long, startNs: Long): Long = (nowNs - startNs) / NANOS_PER_MS

    private fun endAfterStart(endNs: Long, runStartNs: Long, phaseStartNs: Long): Long =
        elapsedMs(endNs, runStartNs).coerceAtLeast(elapsedMs(phaseStartNs, runStartNs) + 1L)

    private fun sha256File(file: File): String = file.inputStream().use { sha256Bytes(it.readBytes()) }

    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance(SHA_256)
        .digest(bytes)
        .toHex()

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    private companion object {
        const val PERFORMANCE_DIR = "performance"
        const val SHA_256 = "SHA-256"
        const val BYTE_MASK = 0xFF
        const val NANOS_PER_MS = 1_000_000L
        const val MIN_TIMESTAMP_STEP_MS = 1L
        const val PERCENT_MAX = 100
        val PAIRED_BOUNDARIES: Set<PerformanceLatencyBoundary> = setOf(
            PerformanceLatencyBoundary.ESTIMATOR_CALL_ONLY,
            PerformanceLatencyBoundary.SDK_PROCESS_TO_OUTPUT_READY,
            PerformanceLatencyBoundary.ESTIMATOR_TO_SDK_OUTPUT_READY,
            PerformanceLatencyBoundary.RAW_OUTPUT_MATERIALIZATION,
        )
    }
}
