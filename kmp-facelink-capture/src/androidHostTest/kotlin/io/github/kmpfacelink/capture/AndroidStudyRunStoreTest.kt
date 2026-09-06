package io.github.kmpfacelink.capture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class AndroidStudyRunStoreTest {
    @Test
    fun activeManifestCreatesResumesAndRotatesOnMismatch() {
        val root = Files.createTempDirectory("facelink-study-run-").toFile()
        try {
            val first = loadOrCreateAndroidStudyRun(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )
            val resumed = loadOrCreateAndroidStudyRun(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )
            val rotated = loadOrCreateAndroidStudyRun(
                root,
                CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            )

            assertEquals(first, resumed)
            assertNotEquals(first.runId, rotated.runId)
            assertEquals(CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID, rotated.protocolId)
            assertEquals(CaptureStorageProfile.CANONICAL_RGBA_240X320_V1, rotated.storageProfile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun requiredSessionCRunIdCreatesExactlyOnceAndRejectsRestoreDrift() {
        val root = Files.createTempDirectory("facelink-session-c-run-").toFile()
        try {
            val first = loadOrCreateAndroidStudyRun(
                root,
                CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                requiredRunId = "session-c-authorized-run",
            )
            assertEquals("session-c-authorized-run", first.runId)
            val restored = loadOrCreateAndroidStudyRun(
                root,
                CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                requiredRunId = "session-c-authorized-run",
            )
            assertEquals(first, restored)
            assertFailsWith<IllegalArgumentException> {
                loadOrCreateAndroidStudyRun(
                    root,
                    CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                    CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                    requiredRunId = "session-c-other-signed-run",
                )
            }
            val afterRejectedTransition = loadOrCreateAndroidStudyRun(
                root,
                CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                requiredRunId = "session-c-authorized-run",
            )
            assertEquals(first, afterRejectedTransition)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun participantQualificationRefusesToRotateAnExistingMismatchedRun() {
        val root = Files.createTempDirectory("facelink-qualification-run-").toFile()
        try {
            val existing = loadOrCreateAndroidStudyRun(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )

            assertFailsWith<CaptureFailureException> {
                loadOrCreateAndroidStudyRun(
                    root,
                    CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                    CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                    refuseActiveRunRotation = true,
                )
            }
            val stillExisting = loadOrCreateAndroidStudyRun(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                refuseActiveRunRotation = true,
            )
            assertEquals(existing, stillExisting)
        } finally {
            root.deleteRecursively()
        }
    }
}
