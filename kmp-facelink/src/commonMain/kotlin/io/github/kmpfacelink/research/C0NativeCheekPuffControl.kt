@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateObservationReceipt
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness

/** Exact estimator-plane reader for the shipped MediaPipe `cheekPuff` control. */
@ExperimentalFaceLinkApi
public object C0NativeCheekPuffControl {
    public const val PRODUCER_ID: String = "estimator:c0-native-mediapipe-cheek-puff/v1"

    /**
     * Reads the snapshotted estimator plane without creating an alternative observation.
     * Missing and invalid values remain explicit absence/rejection states rather than numeric zero.
     */
    public fun read(receipt: CandidateObservationReceipt): CandidateProducerOutput {
        val value = receipt.estimator[BlendShape.CHEEK_PUFF]
            ?: return CandidateProducerOutput.absent(CandidateReadiness.MISSING_INPUT, "c0_cheek_puff_missing")
        if (!value.isFinite()) {
            return CandidateProducerOutput.absent(CandidateReadiness.NON_FINITE, "c0_cheek_puff_non_finite")
        }
        if (value !in 0f..1f) {
            return CandidateProducerOutput.absent(CandidateReadiness.REJECTED, "c0_cheek_puff_out_of_range")
        }
        return CandidateProducerOutput.ready(value)
    }
}
