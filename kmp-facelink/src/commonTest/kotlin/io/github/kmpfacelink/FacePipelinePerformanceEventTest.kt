package io.github.kmpfacelink

import io.github.kmpfacelink.model.FacePipelinePerformanceClock
import io.github.kmpfacelink.model.FacePipelinePerformanceEvent
import io.github.kmpfacelink.model.FacePipelinePerformanceOutcome
import io.github.kmpfacelink.model.InferenceMaterialization
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FacePipelinePerformanceEventTest {

    @Test
    fun fullOutputRequiresRawAndProcessedSegments() {
        assertFailsWith<IllegalArgumentException> {
            outputEvent(
                outcome = FacePipelinePerformanceOutcome.FULL_OUTPUT,
                materialization = InferenceMaterialization.FULL_INFERENCE,
                callbackToRawNs = null,
                rawToOutputNs = 2,
            )
        }
    }

    @Test
    fun interpolatedOutputCannotPretendToHaveRawExtraction() {
        assertFailsWith<IllegalArgumentException> {
            outputEvent(
                outcome = FacePipelinePerformanceOutcome.INTERPOLATED_OUTPUT,
                materialization = InferenceMaterialization.INTERPOLATED,
                callbackToRawNs = 1,
                rawToOutputNs = 2,
            )
        }
    }

    @Test
    fun dropCannotClaimOutputLatency() {
        assertFailsWith<IllegalArgumentException> {
            outputEvent(
                outcome = FacePipelinePerformanceOutcome.DROPPED_THROTTLED,
                materialization = null,
                callbackToRawNs = null,
                rawToOutputNs = null,
                enqueueAccepted = null,
            )
        }
    }

    private fun outputEvent(
        outcome: FacePipelinePerformanceOutcome,
        materialization: InferenceMaterialization?,
        callbackToRawNs: Long?,
        rawToOutputNs: Long?,
        enqueueAccepted: Boolean? = true,
    ): FacePipelinePerformanceEvent = FacePipelinePerformanceEvent(
        callbackSequence = 1u,
        anchorBatchSequence = 1u,
        clock = FacePipelinePerformanceClock.IOS_MONOTONIC_CALLBACK_CLOCK,
        outcome = outcome,
        materialization = materialization,
        configurationFingerprint = "a".repeat(64),
        epochFingerprint = "b".repeat(64),
        callbackToRawExtractionNs = callbackToRawNs,
        rawExtractionToOutputReadyNs = rawToOutputNs,
        callbackToOutputReadyNs = 3,
        outputReadyToEnqueueReturnNs = 1,
        callbackToEnqueueReturnNs = 4,
        enqueueAccepted = enqueueAccepted,
    )
}
