package io.github.kmpfacelink.capture

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosCoefficientOnlyPreflightTest {
    @Test
    fun `published coefficient streams pass the exact structural gate`() {
        val directory = iosTestDirectory("coefficient-preflight")
        val (manifest, _) = writeCoefficientOnlyTake(directory)

        val observation = inspectIosCoefficientOnlyTake(directory, manifest)

        assertEquals(2, observation.resultRecords)
        assertTrue(observation.sequenceAndTimestampStrictlyIncreasing)
        assertTrue(observation.resultStreamHashesMatch)
        assertFalse(observation.framesDirectoryExists)
        assertTrue(CapturePreflightPolicy.passesCoefficientOnly(observation))
        removeCapturePath(directory)
    }

    @Test
    fun `a forbidden pixel artifact blocks coefficient-only progress`() {
        val directory = iosTestDirectory("coefficient-pixels")
        val (manifest, _) = writeCoefficientOnlyTake(directory)
        ensureCaptureDirectory(joinCapturePath(directory, "frames"))

        val observation = inspectIosCoefficientOnlyTake(directory, manifest)

        assertTrue(observation.framesDirectoryExists)
        assertFalse(CapturePreflightPolicy.passesCoefficientOnly(observation))
        removeCapturePath(directory)
    }

    @Test
    fun `a partial result stream is refused before policy evaluation`() {
        val directory = iosTestDirectory("coefficient-partial")
        val (manifest, _) = writeCoefficientOnlyTake(directory)
        publishCaptureText(directory, InferenceRecordFormat.TRANSFORM_FILE, "partial")

        assertFailsWith<IllegalArgumentException> {
            inspectIosCoefficientOnlyTake(directory, manifest)
        }
        removeCapturePath(directory)
    }

    @Test
    fun `one gibibyte free-space boundary is inclusive and fail closed`() {
        requireMinimumIosCaptureCapacity(MINIMUM_IOS_CAPTURE_AVAILABLE_BYTES)
        val failure = assertFailsWith<CaptureFailureException> {
            requireMinimumIosCaptureCapacity(MINIMUM_IOS_CAPTURE_AVAILABLE_BYTES - 1)
        }
        assertEquals(CaptureFailureCategory.CAPACITY, failure.category)
    }

    private fun writeCoefficientOnlyTake(directory: String): Pair<TakeManifest, StudyRunIdentity> {
        val sink = openInferenceRecordSink(directory)
        listOf(
            inferenceRecord(sequence = 7, captureTimestamp = 1_000L, resultTimestamp = 2_000L),
            inferenceRecord(sequence = 9, captureTimestamp = 1_100L, resultTimestamp = 2_100L),
        ).forEach(sink::write)
        sink.close()
        val hashes = sink.hashes()
        val studyRun = StudyRunIdentity(
            schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
            runId = "ios-preflight-test",
            protocolId = CaptureStudyProtocol.IOS_REGISTERED_COEFFICIENT_17_ID,
            storageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            createdAtIso = "2026-08-30T00:00:00Z",
        )
        val manifest = TakeManifest(
            schemaVersion = TakeManifest.SCHEMA_VERSION,
            takeId = "neutral-test",
            scenarioId = CaptureScenario.NEUTRAL.id,
            attempt = 1,
            startedAtIso = "2026-08-30T00:00:00Z",
            condition = iosGuidedCaptureCondition(1920, 1440, testProvenance()),
            analysisFramesAdmitted = 2,
            inferenceResults = 2,
            noFaceResults = 0,
            inferenceFailedResults = 0,
            inferenceErrors = 0,
            framesStored = 0,
            softValidationPassed = true,
            softValidationReason = "test",
            medianIod = 0.2f,
            outOfDistanceBandFraction = 0f,
            spans = emptyList(),
            blendshapeNames = emptyList(),
            resultIndexSha256 = hashes[0],
            landmarksSha256 = hashes[1],
            blendshapesSha256 = hashes[2],
            transformSha256 = hashes[3],
            captureWorkflow = CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
            storageReceipt = CaptureStorageReceipt.coefficientsOnly(1920, 1440),
            studyRun = studyRun,
        )
        publishCaptureText(
            directory,
            InferenceRecordFormat.TAKE_MANIFEST_FILE,
            rawFrameJson.encodeToString(manifest),
        )
        return manifest to studyRun
    }

    private fun inferenceRecord(
        sequence: Int,
        captureTimestamp: Long,
        resultTimestamp: Long,
    ): InferenceRecord = InferenceRecord(
        analysisFrameSeq = sequence,
        captureTimestampNs = captureTimestamp,
        resultElapsedNs = resultTimestamp,
        phase = CapturePhase.NEUTRAL,
        hasFace = true,
        inferenceFailed = false,
        landmarks = null,
        blendshapes = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT),
        transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
    )

    private fun testProvenance(): SourceProvenance = SourceProvenance(
        repo = "KMP-FaceLink",
        branch = "test",
        gitSha = "0".repeat(40),
        commitTimeIso = "2026-08-30T00:00:00Z",
        dirty = true,
    )
}
