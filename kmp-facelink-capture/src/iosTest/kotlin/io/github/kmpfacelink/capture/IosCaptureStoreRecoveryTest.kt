package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosCaptureStoreRecoveryTest {
    @Test
    fun newStoreRemovesIncompleteTakesButPreservesPublishedTakes() {
        val root = iosTestDirectory("capture-store-recovery")
        try {
            val first = IosCaptureStore(
                rootDirectory = root,
                protocolId = "protocol",
                storageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )
            val incomplete = first.createTmpDir("incomplete")
            publishCaptureText(incomplete, "results.idx", "partial")
            val published = ensureCaptureDirectory(joinCapturePath(first.takesRoot, "complete"))
            publishCaptureText(published, InferenceRecordFormat.TAKE_MANIFEST_FILE, "complete")
            first.close()

            val resumed = IosCaptureStore(
                rootDirectory = root,
                protocolId = "protocol",
                storageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            )

            assertEquals(1, resumed.recoveredIncompleteTakeCount)
            assertFalse(capturePathExists(incomplete))
            assertTrue(capturePathExists(published))
            resumed.close()
        } finally {
            removeCapturePath(root)
        }
    }
}
