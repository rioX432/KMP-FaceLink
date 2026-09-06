package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateReadiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalFaceLinkApi::class)
class C0NativeCheekPuffControlTest {
    @Test
    fun readsOnlyEstimatorPlaneWithoutCreatingAlternative() {
        val receipt = observation(mapOf(BlendShape.CHEEK_PUFF to 0.044f))
        val control = C0NativeCheekPuffControl.read(receipt)

        assertEquals(0.044f, control.value)
        assertEquals(CandidateReadiness.READY, control.readiness)
        assertTrue(receipt.alternatives.isEmpty())
        assertEquals(receipt.estimator, receipt.selected)
    }

    @Test
    fun missingNonFiniteAndOutOfRangeRemainNonNumeric() {
        val cases = listOf(
            observation(emptyMap()) to CandidateReadiness.MISSING_INPUT,
            observation(mapOf(BlendShape.CHEEK_PUFF to Float.NaN)) to CandidateReadiness.NON_FINITE,
            observation(mapOf(BlendShape.CHEEK_PUFF to 1.01f)) to CandidateReadiness.REJECTED,
        )

        cases.forEach { (receipt, readiness) ->
            val control = C0NativeCheekPuffControl.read(receipt)
            assertNull(control.value)
            assertEquals(readiness, control.readiness)
        }
    }

    private fun observation(estimator: Map<BlendShape, Float>) = CandidateObservationSession().observe(
        CandidateObservationInput(
            identity = CandidateInputIdentity(1u, 0u, 0, 10L),
            estimator = estimator,
            landmarks = emptyList(),
            sourceImageWidth = 320,
            sourceImageHeight = 240,
        ),
    )
}
