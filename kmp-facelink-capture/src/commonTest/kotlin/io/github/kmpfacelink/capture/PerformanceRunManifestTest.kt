package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceRunManifestTest {

    @Test
    fun latencyUsesSubmissionAndCallbackFromOneClockDomain() {
        val summary = PerformanceLatencyAggregator.summarize(
            listOf(1_000_000L, 2_000_000L, 3_000_000L, 100_000_000L),
        )
        assertEquals(4, summary.sampleCount)
        assertEquals(3.0, summary.p50Ms)
        assertEquals(100.0, summary.p95Ms)
        assertEquals(100.0, summary.p99Ms)
        assertEquals(100.0, summary.maxMs)
    }

    @Test
    fun phaseMixingAndCounterContradictionsFailClosed() {
        val reversed = manifest().copy(phases = manifest().phases.reversed())
        assertTrue("phase order" in PerformanceRunValidator.rejectionReasons(reversed, false))

        val invalidCounts = manifest().let { run ->
            run.copy(
                phases = run.phases.mapIndexed { index, phase ->
                    if (index == 1) phase.copy(faceOutputs = 11) else phase
                },
            )
        }
        val reasons = PerformanceRunValidator.rejectionReasons(invalidCounts, false)
        assertTrue("offline inference accounting" in reasons)
    }

    @Test
    fun dirtyRunsRemainUsableLocallyButCannotBecomeFinalEvidence() {
        val dirty = manifest(dirty = true)
        assertTrue(PerformanceRunValidator.isValid(dirty, requireReproducibleSource = false))
        assertFalse(PerformanceRunValidator.isValid(dirty, requireReproducibleSource = true))
        assertTrue(
            PerformanceRunValidator.isValid(
                dirty,
                requireReproducibleSource = true,
                expectedEvidence = dirty.evidence,
            ),
        )
    }

    @Test
    fun publicManifestSchemaContainsNoPerFrameOrBiometricPayload() {
        val encoded = Json.encodeToString(manifest())
        listOf("landmarks", "blendshapes", "transform", "pixels", "rgba", "sessionInstanceId").forEach { forbidden ->
            assertFalse(forbidden in encoded, forbidden)
        }
        assertTrue("PAIRED_RAW_AND_PROCESSED" in encoded)
    }

    @Test
    fun pairedRunFailsClosedWithoutProcessedProvenanceOrWithLiveCounters() {
        val missing = manifest().copy(processedOutput = null)
        assertTrue("missing processed provenance" in PerformanceRunValidator.rejectionReasons(missing, false))

        val liveCounter = manifest().let { run ->
            run.copy(
                phases = run.phases.mapIndexed { index, phase ->
                    if (index == 1) phase.copy(analyzerArrivals = TelemetryValue.Available(10)) else phase
                },
            )
        }
        assertTrue(
            "offline live counter must be unavailable" in
                PerformanceRunValidator.rejectionReasons(liveCounter, false),
        )
    }

    @Test
    fun pairedRunFailsClosedWhenStoredReplayEvidenceOrOfflineLayerIsRemoved() {
        val stripped = manifest().copy(
            exactStoredPixelReplay = false,
            sourceTakeManifestSha256 = null,
            sourceFrameManifestSha256 = null,
            replayTimestampPolicy = null,
            replayProtocol = null,
            expandedReplayTimelineSha256 = null,
            inputArtifactsRetainedLocally = false,
            condition = manifest().condition.copy(layer = MeasurementLayer.DEVICE_REALTIME),
        )
        val reasons = PerformanceRunValidator.rejectionReasons(stripped, false)
        assertTrue("paired run requires exact stored-pixel replay" in reasons)
        assertTrue("paired run requires take manifest hash" in reasons)
        assertTrue("paired run requires frame manifest hash" in reasons)
        assertTrue("paired run requires timestamp policy" in reasons)
        assertTrue("paired run requires replay protocol" in reasons)
        assertTrue("paired run requires expanded replay timeline hash" in reasons)
        assertTrue("paired run requires device-offline layer" in reasons)
        assertTrue("paired run requires local input retention" in reasons)
    }

    @Test
    fun processedProvenanceContradictionsFailClosed() {
        val zeroProvenance = manifest().let { run ->
            run.copy(
                processedOutput = run.processedOutput!!.copy(
                    outputsWithProvenance = 0,
                    pipelineKinds = emptySet(),
                    materializations = emptySet(),
                    calibrationPhases = emptySet(),
                ),
            )
        }
        val zeroReasons = PerformanceRunValidator.rejectionReasons(zeroProvenance, false)
        assertTrue("processed provenance accounting" in zeroReasons)
        assertTrue("processed pipeline kinds" in zeroReasons)

        val wrongFingerprint = manifest().let { run ->
            run.copy(processedOutput = run.processedOutput!!.copy(configurationFingerprint = "9".repeat(64)))
        }
        assertTrue(
            "processed configuration fingerprint mismatch" in
                PerformanceRunValidator.rejectionReasons(wrongFingerprint, false),
        )
    }

    @Test
    fun outcomeStrataAndEvidenceBindingFailClosed() {
        val badOutcomeCount = manifest().let { run ->
            run.copy(
                phases = run.phases.mapIndexed { index, phase ->
                    if (index == 1) {
                        phase.copy(
                            latenciesByOutcome = phase.latenciesByOutcome.toMutableMap().apply {
                                this[PerformanceSampleOutcome.FACE_OUTPUT] =
                                    phase.latencies.mapValues { PerformanceLatencySummary(0, 0.0, 0.0, 0.0, 0.0) }
                            },
                        )
                    } else {
                        phase
                    }
                },
            )
        }
        assertTrue(
            PerformanceRunValidator.rejectionReasons(badOutcomeCount, false)
                .any { it.startsWith("outcome latency sample count") },
        )

        val wrongEvidence = ReviewEvidenceBinding(
            "other",
            "7".repeat(64),
            "8".repeat(64),
            "9".repeat(64),
            "other-binary",
            "a".repeat(64),
            "b".repeat(64),
            "c".repeat(64),
            "d".repeat(64),
        )
        assertTrue(
            "review evidence binding mismatch" in PerformanceRunValidator.rejectionReasons(
                manifest(),
                requireReproducibleSource = false,
                expectedEvidence = wrongEvidence,
            ),
        )
    }

    private fun manifest(dirty: Boolean = false): PerformanceRunManifest = PerformanceRunManifest(
        schemaVersion = PerformanceRunManifest.SCHEMA_VERSION,
        runId = "run-1",
        startedAtIso = "2026-08-29T00:00:00Z",
        evidence = evidence(),
        outputLayer = PerformanceOutputLayer.PAIRED_RAW_AND_PROCESSED,
        clock = PerformanceMeasurementClock.ANDROID_ELAPSED_REALTIME_NANOS,
        conditionFingerprint = "a".repeat(64),
        sourceTakeManifestSha256 = "b".repeat(64),
        sourceFrameManifestSha256 = "e".repeat(64),
        exactStoredPixelReplay = true,
        replayTimestampPolicy = ReplayTimestampPolicy.ORIGINAL_DELTAS_REBASED_MONOTONIC,
        replayProtocol = PerformanceReplayProtocol(
            warmupRepetitions = 1,
            steadyRepetitions = 3,
            cooldownMs = 2_000L,
            requestedDelegate = "CPU",
            expectedResolvedDelegate = "CPU",
            latencyBoundaries = PerformanceLatencyBoundary.entries.toSet(),
            stateCarryPolicy = OfflineStateCarryPolicy.CONTIGUOUS_WARMUP_TO_STEADY,
        ),
        expandedReplayTimelineSha256 = "f".repeat(64),
        saveFrames = false,
        inputArtifactsRetainedLocally = true,
        device = PerformanceDeviceContext(
            manufacturer = "test",
            model = "test",
            androidRelease = "test",
            androidApi = 35,
            abi = "arm64-v8a",
            buildFingerprintSha256 = "c".repeat(64),
        ),
        condition = CaptureCondition(
            nativeResolution = "240x320",
            runningMode = "IMAGE",
            delegate = "CPU",
            numFaces = 1,
            minDetectionConfidence = 0.5f,
            minTrackingConfidence = 0.5f,
            minPresenceConfidence = 0.5f,
            mediapipeVersion = "0.10.35",
            modelSha256 = "d".repeat(64),
            mirrored = true,
            layer = MeasurementLayer.DEVICE_OFFLINE,
            provenance = SourceProvenance("repo", "branch", "sha", "time", dirty),
        ),
        phases = PerformanceRunPhase.entries.mapIndexed { index, phase ->
            val count = if (phase == PerformanceRunPhase.COOLDOWN) 0 else 10
            PerformancePhaseSummary(
                phase = phase,
                startElapsedMs = index * 1_000L,
                endElapsedMs = (index + 1) * 1_000L,
                inferenceCalls = count,
                faceOutputs = if (count == 0) 0 else 9,
                noFaceOutputs = if (count == 0) 0 else 1,
                processingFailures = 0,
                analyzerArrivals = TelemetryValue.Unavailable("offline fixture"),
                analyzerAdmissions = TelemetryValue.Unavailable("offline fixture"),
                asynchronousCallbacks = TelemetryValue.Unavailable("offline fixture"),
                enqueueAttempts = TelemetryValue.Unavailable("offline fixture"),
                writerFailures = TelemetryValue.Unavailable("offline fixture"),
                latencies = PerformanceLatencyBoundary.entries.associateWith {
                    PerformanceLatencySummary(count, 1.0, 2.0, 3.0, 4.0)
                },
                latenciesByOutcome = PerformanceSampleOutcome.entries.associateWith { outcome ->
                    val outcomeCount = when (outcome) {
                        PerformanceSampleOutcome.FACE_OUTPUT -> if (count == 0) 0 else 9
                        PerformanceSampleOutcome.NO_FACE_OUTPUT -> if (count == 0) 0 else 1
                        PerformanceSampleOutcome.PROCESSING_FAILURE -> 0
                    }
                    PerformanceLatencyBoundary.entries.associateWith {
                        if (outcomeCount == 0) {
                            PerformanceLatencySummary(0, 0.0, 0.0, 0.0, 0.0)
                        } else {
                            PerformanceLatencySummary(outcomeCount, 1.0, 2.0, 3.0, 4.0)
                        }
                    }
                },
                thermalStatus = TelemetryValue.Unavailable("host fixture"),
                batteryLevelFraction = TelemetryValue.Unavailable("host fixture"),
            )
        },
        processedOutput = ProcessedOutputSummary(
            configurationFingerprint = "a".repeat(64),
            epochSegmentCount = 1,
            outputsWithProvenance = 18,
            outputsWithoutProvenance = 0,
            pipelineKinds = setOf("PRIMARY"),
            materializations = setOf("FULL_INFERENCE"),
            calibrationPhases = setOf("DISABLED"),
            calibrationStateRevisionStart = "0",
            calibrationStateRevisionEnd = "0",
            processingStateRevisionStart = "0",
            processingStateRevisionEnd = "0",
            appliedTransformFingerprints = setOf("2".repeat(64)),
        ),
        abortedReason = null,
    )

    private fun evidence() = ReviewEvidenceBinding(
        sourcePacketId = "kmp-performance-boundaries-v2",
        sourcePacketManifestSha256 = "7".repeat(64),
        sourceManifestSha256 = "8".repeat(64),
        sourceAuthorizationSha256 = "9".repeat(64),
        binaryPacketId = "binary-packet-v1",
        binaryPacketManifestSha256 = "a".repeat(64),
        binarySha256 = "b".repeat(64),
        runAuthorizationSha256 = "c".repeat(64),
        runAuthorizationSigningKeyId = "d".repeat(64),
    )
}
