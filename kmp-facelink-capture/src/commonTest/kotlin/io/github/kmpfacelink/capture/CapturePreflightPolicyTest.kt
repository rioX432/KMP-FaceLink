package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapturePreflightPolicyTest {
    @Test
    fun acceptsBothReplayModesWhenEveryLiveResultJoinsAndFacePresenceAgrees() {
        assertTrue(CapturePreflightPolicy.passes(passingObservations()))
    }

    @Test
    fun rejectsMissingOrDuplicateReplayMode() {
        val passing = passingObservations()
        assertFalse(CapturePreflightPolicy.passes(passing.take(1)))
        assertFalse(CapturePreflightPolicy.passes(listOf(passing[0], passing[0])))
    }

    @Test
    fun rejectsZeroJoinDroppedJoinMissingStoredFrameOrFaceDisagreement() {
        val passing = passingObservations()
        assertFalse(CapturePreflightPolicy.passes(passing.replaceImage { copy(joined = 0) }))
        assertFalse(CapturePreflightPolicy.passes(passing.replaceImage { copy(joined = liveResults - 1) }))
        assertFalse(CapturePreflightPolicy.passes(passing.replaceImage { copy(storedFrames = liveResults - 1) }))
        assertFalse(CapturePreflightPolicy.passes(passing.replaceImage { copy(faceAgreement = 0.99f) }))
    }

    private fun passingObservations(): List<CapturePreflightObservation> = listOf(
        CapturePreflightObservation("IMAGE", storedFrames = 120, liveResults = 100, joined = 100, faceAgreement = 1f),
        CapturePreflightObservation("VIDEO", storedFrames = 120, liveResults = 100, joined = 100, faceAgreement = 1f),
    )

    private fun List<CapturePreflightObservation>.replaceImage(
        transform: CapturePreflightObservation.() -> CapturePreflightObservation,
    ): List<CapturePreflightObservation> = map { if (it.mode == "IMAGE") it.transform() else it }
}
