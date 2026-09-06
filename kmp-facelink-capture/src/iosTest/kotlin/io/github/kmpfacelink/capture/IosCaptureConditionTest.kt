package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosCaptureConditionTest {

    @Test
    fun guidedTakeConditionSerializesNativePreviewRatherThanHistoricalNone() {
        val productionCondition = iosGuidedCaptureCondition(
            nativeWidth = 1920,
            nativeHeight = 1440,
            provenance = testProvenance(),
        )

        assertEquals(5, TakeManifest.SCHEMA_VERSION)
        assertEquals(ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE, productionCondition.participantPreview)
        assertTrue(
            "\"participantPreview\": \"NATIVE_CAMERA_WITH_GUIDE\"" in
                rawFrameJson.encodeToString(manifestWith(productionCondition)),
        )

        // Negative control is derived from the exact production value, not a copied condition.
        assertFailsWith<AssertionError> {
            assertEquals(
                ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE,
                productionCondition.copy(participantPreview = ParticipantPreview.NONE).participantPreview,
            )
        }
    }

    private fun testProvenance() = SourceProvenance(
        repo = "KMP-FaceLink",
        branch = "test",
        gitSha = "0".repeat(40),
        commitTimeIso = "2026-08-29T00:00:00Z",
        dirty = true,
    )

    /** Uses the real iOS condition inside the current schema-5 artifact that capture writes last. */
    private fun manifestWith(condition: CaptureCondition) = TakeManifest(
        schemaVersion = TakeManifest.SCHEMA_VERSION,
        takeId = "take",
        scenarioId = CaptureScenario.NEUTRAL.id,
        attempt = 1,
        startedAtIso = "2026-08-29T00:00:00Z",
        condition = condition,
        analysisFramesAdmitted = 0,
        inferenceResults = 0,
        noFaceResults = 0,
        inferenceFailedResults = 0,
        inferenceErrors = 0,
        framesStored = 0,
        softValidationPassed = true,
        softValidationReason = "test",
        medianIod = 0f,
        outOfDistanceBandFraction = 0f,
        spans = emptyList(),
        blendshapeNames = emptyList(),
        resultIndexSha256 = "",
        landmarksSha256 = "",
        blendshapesSha256 = "",
        transformSha256 = "",
        captureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
        storageReceipt = CaptureStorageReceipt.coefficientsOnly(240, 320),
        studyRun = StudyRunIdentity(
            StudyRunIdentity.SCHEMA_VERSION,
            "run-test",
            StudyRunIdentity.GUIDED_PROTOCOL_ID,
            CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            "2026-08-29T00:00:00Z",
        ),
    )
}
