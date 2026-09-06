package io.github.kmpfacelink.api

import io.github.kmpfacelink.model.FaceLandmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)
class CheekPuffContourObservationSessionTest {

    @Test
    fun requiresTheFrozenMinimumNeutralFrameCount() {
        val session = CheekPuffContourObservationSession(100, 100)
        repeat(CheekPuffContourObservationSession.MINIMUM_NEUTRAL_FRAMES - 1) { index ->
            session.addNeutralFrame(neutralLandmarks(), index.toLong())
        }

        assertFailsWith<IllegalArgumentException> { session.finishNeutralCalibration() }
    }

    @Test
    fun exposesTheFrozenRuleThresholds() {
        assertEquals(3.02, CheekPuffContourObservationSession.MOUTH_WIDTH_MAXIMUM)
        assertEquals(1.54, CheekPuffContourObservationSession.OVAL_JAW_MINIMUM)
        assertEquals(2.68, CheekPuffContourObservationSession.OVAL_CHIN_MAXIMUM)
    }

    private fun neutralLandmarks(): List<FaceLandmark> = List(478) { index ->
        FaceLandmark(
            x = (index % 22) / 22f,
            y = (index / 22) / 22f,
            relativeDepth = 0f,
        )
    }
}
