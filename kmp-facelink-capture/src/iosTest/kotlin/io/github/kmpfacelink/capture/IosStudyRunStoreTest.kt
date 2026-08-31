package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IosStudyRunStoreTest {
    @Test
    fun activeManifestCreatesResumesAndRotatesOnMismatch() {
        val root = iosTestDirectory("study-run")
        try {
            val firstStore = IosCaptureStore(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )
            val first = firstStore.studyRun
            firstStore.close()
            val resumedStore = IosCaptureStore(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )
            val resumed = resumedStore.studyRun
            resumedStore.close()
            val rotatedStore = IosCaptureStore(
                root,
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            )
            val rotated = rotatedStore.studyRun
            rotatedStore.close()

            assertEquals(first, resumed)
            assertNotEquals(first.runId, rotated.runId)
            assertEquals(CaptureStorageProfile.CANONICAL_RGBA_240X320_V1, rotated.storageProfile)
        } finally {
            removeCapturePath(root)
        }
    }
}
