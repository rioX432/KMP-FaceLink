package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureWorkflowTest {

    @Test
    fun currentSchemaRecordsParticipantPreviewAsAnExperimentalCondition() {
        val encoded = rawFrameJson.encodeToString(
            manifest(CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1).copy(
                condition = manifest(CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1).condition.copy(
                    participantPreview = ParticipantPreview.NATIVE_CAMERA_WITH_GUIDE,
                ),
            ),
        )
        assertEquals(5, TakeManifest.SCHEMA_VERSION)
        assertTrue("\"participantPreview\": \"NATIVE_CAMERA_WITH_GUIDE\"" in encoded)
    }

    @Test
    fun guidedWorkflowIsExplicitWhileMissingLegacyFieldRemainsReadable() {
        val guidedJson = rawFrameJson.encodeToString(
            manifest(CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1),
        )
        assertTrue("\"captureWorkflow\": \"GUIDED_GATE_AUTOSTART_V1\"" in guidedJson)

        val legacyJson = rawFrameJson.encodeToString(manifest(CaptureWorkflow.LEGACY_MANUAL_V1))
        assertFalse("captureWorkflow" in legacyJson)
        val decoded = rawFrameJson.decodeFromString<TakeManifest>(legacyJson)
        assertEquals(CaptureWorkflow.LEGACY_MANUAL_V1, decoded.captureWorkflow)
    }

    private fun manifest(workflow: CaptureWorkflow): TakeManifest = TakeManifest(
        schemaVersion = TakeManifest.SCHEMA_VERSION,
        takeId = "take",
        scenarioId = CaptureScenario.NEUTRAL.id,
        attempt = 1,
        startedAtIso = "2026-08-29T00:00:00Z",
        condition = CaptureCondition(
            nativeResolution = "240x320",
            runningMode = "LIVE_STREAM",
            delegate = "CPU",
            numFaces = 1,
            minDetectionConfidence = 0.5f,
            minTrackingConfidence = 0.5f,
            minPresenceConfidence = 0.5f,
            mediapipeVersion = "test",
            modelSha256 = "model",
            mirrored = true,
            layer = MeasurementLayer.DEVICE_REALTIME,
            provenance = SourceProvenance("repo", "branch", "sha", "time", false),
        ),
        analysisFramesAdmitted = 0,
        inferenceResults = 0,
        noFaceResults = 0,
        inferenceFailedResults = 0,
        inferenceErrors = 0,
        framesStored = 0,
        softValidationPassed = true,
        softValidationReason = "time",
        medianIod = 0f,
        outOfDistanceBandFraction = 0f,
        spans = emptyList(),
        blendshapeNames = emptyList(),
        resultIndexSha256 = "",
        landmarksSha256 = "",
        blendshapesSha256 = "",
        transformSha256 = "",
        captureWorkflow = workflow,
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
