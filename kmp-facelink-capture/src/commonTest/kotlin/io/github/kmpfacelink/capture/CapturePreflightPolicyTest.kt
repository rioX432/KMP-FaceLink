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

    @Test
    fun acceptsCoefficientOnlyWhenPixelsAreAbsentAndResultStreamsAreComplete() {
        assertTrue(CapturePreflightPolicy.passesCoefficientOnly(passingCoefficientOnly()))
    }

    @Test
    fun rejectsCoefficientOnlyPixelLeakCountHashOrOrderingFailure() {
        val passing = passingCoefficientOnly()
        assertFalse(CapturePreflightPolicy.passesCoefficientOnly(passing.copy(framesDirectoryExists = true)))
        assertFalse(CapturePreflightPolicy.passesCoefficientOnly(passing.copy(framesStored = 1)))
        assertFalse(CapturePreflightPolicy.passesCoefficientOnly(passing.copy(resultRecords = 0)))
        assertFalse(CapturePreflightPolicy.passesCoefficientOnly(passing.copy(resultStreamHashesMatch = false)))
        assertFalse(
            CapturePreflightPolicy.passesCoefficientOnly(
                passing.copy(sequenceAndTimestampStrictlyIncreasing = false),
            ),
        )
    }

    private fun passingObservations(): List<CapturePreflightObservation> = listOf(
        CapturePreflightObservation("IMAGE", storedFrames = 120, liveResults = 100, joined = 100, faceAgreement = 1f),
        CapturePreflightObservation("VIDEO", storedFrames = 120, liveResults = 100, joined = 100, faceAgreement = 1f),
    )

    private fun passingCoefficientOnly(): CoefficientOnlyPreflightObservation =
        CoefficientOnlyPreflightObservation(
            schemaVersion = TakeManifest.SCHEMA_VERSION,
            profile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            pixelSemantics = CapturePixelSemantics.NONE,
            framesStored = 0,
            framesDirectoryExists = false,
            analysisFramesAdmitted = 102,
            manifestInferenceResults = 100,
            resultRecords = 100,
            sequenceAndTimestampStrictlyIncreasing = true,
            resultStreamHashesMatch = true,
        )

    private fun List<CapturePreflightObservation>.replaceImage(
        transform: CapturePreflightObservation.() -> CapturePreflightObservation,
    ): List<CapturePreflightObservation> = map { if (it.mode == "IMAGE") it.transform() else it }
}
