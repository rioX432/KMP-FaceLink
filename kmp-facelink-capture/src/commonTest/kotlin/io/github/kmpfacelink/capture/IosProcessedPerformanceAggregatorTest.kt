package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.FacePipelinePerformanceClock
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FacePipelinePerformanceOutcome
import io.github.kmpfacelink.model.InferenceMaterialization
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosProcessedPerformanceAggregatorTest {

    @Test
    fun fullAndInterpolatedOutputsRemainSeparate() {
        val manifest = aggregate(listOf(full(1u), interpolated(2u)))
        assertTrue(manifest.rejectedReasons.isEmpty())
        assertEquals(1, manifest.fullOutput.callbackToRawExtraction.sampleCount)
        assertEquals(1, manifest.interpolatedOutput.callbackToOutputReady.sampleCount)
        assertEquals(2, manifest.counters.enqueueAccepted)
        assertEquals(IosProcessedPerformanceManifest.BOUNDARY, manifest.measurementBoundary)
    }

    @Test
    fun sequenceGapAndMixedConfigurationFailClosed() {
        val manifest = aggregate(
            listOf(
                full(1u),
                full(3u, configurationFingerprint = "c".repeat(64)),
            ),
        )
        assertTrue("callback sequence gap" in manifest.rejectedReasons)
        assertTrue("mixed configuration fingerprint" in manifest.rejectedReasons)
    }

    @Test
    fun mixedEpochAndNonIncreasingBatchFailClosed() {
        val manifest = aggregate(
            listOf(
                full(1u, batch = 2u),
                full(2u, batch = 2u, epochFingerprint = "c".repeat(64)),
            ),
        )
        assertTrue("mixed epoch fingerprint" in manifest.rejectedReasons)
        assertTrue(
            "face-anchor batch sequence not strictly increasing" in manifest.rejectedReasons,
        )
    }

    @Test
    fun malformedRuntimeFingerprintsFailClosed() {
        val manifest = aggregate(
            listOf(
                full(
                    sequence = 1u,
                    configurationFingerprint = "A".repeat(64),
                    epochFingerprint = "not-a-sha",
                ),
            ),
        )
        assertTrue("invalid configuration fingerprint" in manifest.rejectedReasons)
        assertTrue("invalid epoch fingerprint" in manifest.rejectedReasons)
    }

    @Test
    fun emptyRunIsPreservedAsRejectedAggregate() {
        val manifest = aggregate(emptyList())
        assertTrue("no callback events" in manifest.rejectedReasons)
        assertEquals(null, manifest.callbackSequenceStart)
        assertEquals(null, manifest.callbackSequenceEnd)
        assertEquals(0, manifest.counters.faceAnchorCallbacks)
    }

    @Test
    fun terminalCallbackFailureIsRepresentedAndRejected() {
        val manifest = aggregate(listOf(full(1u), callbackFailure(2u)))
        assertTrue("callback processing failure" in manifest.rejectedReasons)
        assertEquals(1, manifest.counters.callbackFailures)
        assertEquals("2", manifest.callbackSequenceEnd)
    }

    @Test
    fun publicManifestContainsNoPerCallbackOrBiometricPayload() {
        val encoded = Json.encodeToString(aggregate(listOf(full(1u))))
        listOf(
            "blendshapes",
            "landmarks",
            "transform",
            "pixels",
            "rgba",
            "sessionInstanceId",
            "callbackToRawExtractionNs",
        ).forEach { forbidden -> assertFalse(forbidden in encoded, forbidden) }
    }

    private fun aggregate(events: List<FacePipelinePerformanceEvent>) =
        IosProcessedPerformanceAggregator.aggregate(
            runId = "run-1",
            startedAtIso = "2026-08-29T00:00:00Z",
            evidence = ReviewEvidenceBinding(
                "packet",
                "7".repeat(64),
                "8".repeat(64),
                "9".repeat(64),
                "binary-packet",
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
            ),
            source = SourceProvenance("repo", "branch", "sha", "time", dirty = true),
            device = IosPerformanceDeviceContext("iPhone", "iOS", "26.0"),
            events = events,
        )

    private fun full(
        sequence: ULong,
        batch: ULong = sequence,
        configurationFingerprint: String = "a".repeat(64),
        epochFingerprint: String = "b".repeat(64),
    ) = FacePipelinePerformanceEvent(
        callbackSequence = sequence,
        anchorBatchSequence = batch,
        clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
        outcome = FacePipelinePerformanceOutcome.FULL_OUTPUT,
        materialization = InferenceMaterialization.FULL_INFERENCE,
        configurationFingerprint = configurationFingerprint,
        epochFingerprint = epochFingerprint,
        callbackToRawExtractionNs = 1_000_000,
        rawExtractionToOutputReadyNs = 2_000_000,
        callbackToOutputReadyNs = 3_000_000,
        outputReadyToEnqueueReturnNs = 1_000,
        callbackToEnqueueReturnNs = 3_001_000,
        enqueueAccepted = true,
    )

    private fun interpolated(sequence: ULong) = FacePipelinePerformanceEvent(
        callbackSequence = sequence,
        anchorBatchSequence = sequence,
        clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
        outcome = FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT,
        materialization = InferenceMaterialization.INTERPOLATED,
        configurationFingerprint = "a".repeat(64),
        epochFingerprint = "b".repeat(64),
        callbackToRawExtractionNs = null,
        rawExtractionToOutputReadyNs = null,
        callbackToOutputReadyNs = 2_000_000,
        outputReadyToEnqueueReturnNs = 1_000,
        callbackToEnqueueReturnNs = 2_001_000,
        enqueueAccepted = true,
    )

    private fun callbackFailure(sequence: ULong) = FacePipelinePerformanceEvent(
        callbackSequence = sequence,
        anchorBatchSequence = sequence,
        clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
        outcome = FacePipelinePerformanceOutcome.CALLBACK_FAILURE,
        materialization = null,
        configurationFingerprint = null,
        epochFingerprint = null,
        callbackToRawExtractionNs = null,
        rawExtractionToOutputReadyNs = null,
        callbackToOutputReadyNs = null,
        outputReadyToEnqueueReturnNs = null,
        callbackToEnqueueReturnNs = null,
        enqueueAccepted = null,
    )
}
