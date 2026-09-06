package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source-only controls for the Swift/ARC boundary. The fixture is deterministic noise, never a
 * camera image. These tests exercise the actual iOS owned-payload sink rather than a copied common
 * RawFrame and prove that no-record snapshots need no raw payload.
 */
class IosOwnedFrameBoundaryTest {

    @Test
    fun `owned payload preserves gaps and exact bytes through the iOS writer`() {
        val directory = iosTestDirectory("owned-boundary")
        val first = iosTestPixels(41)
        val second = iosTestPixels(42)
        val sink = openIosRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)

        sink.writeOwned(ownedFrame(sequence = 2, pixels = first))
        sink.writeOwned(ownedFrame(sequence = 9, pixels = second))
        sink.close()

        assertContentEquals(
            first + second,
            readIosTestFile(joinCapturePath(directory, RawFrameFormat.FRAMES_FILE)),
        )
        assertEquals(2, readIosManifest(directory).frameCount)
        removeCapturePath(directory)
    }

    @Test
    fun `wrong length owned payload invalidates rather than writing a partial frame`() {
        val directory = iosTestDirectory("owned-wrong-length")
        val sink = openIosRawFrameSink(directory, IOS_TEST_WIDTH, IOS_TEST_HEIGHT)

        val failure = runCatching {
            sink.writeOwned(ownedFrame(sequence = 0, pixels = ByteArray(7)))
        }.exceptionOrNull()

        assertTrue(failure != null)
        assertTrue(runCatching { sink.close() }.exceptionOrNull() is IllegalStateException)
        removeCapturePath(directory)
    }

    @Test
    fun `no record snapshot advances the real session without raw bytes`() {
        val directory = iosTestDirectory("owned-no-raw")
        val session = IosCaptureSession(
            rootDirectory = directory,
            provenance = testProvenance(),
        )
        try {
            session.start()
            val demand = session.prepareFrame()
            assertEquals(false, demand.rawPixelsRequired)
            session.acceptFrame(
                demand = demand,
                width = IOS_TEST_WIDTH,
                height = IOS_TEST_HEIGHT,
                captureTimestampNs = 1_000L,
                luma = 128f,
                transform = null,
                blendshapes = null,
                rgba = null,
            )
            assertEquals(1, session.uiState.value.resultsObserved)
            assertEquals(null, session.uiState.value.landmarks)
        } finally {
            session.release()
            removeCapturePath(directory)
        }
    }

    private fun ownedFrame(sequence: Int, pixels: ByteArray): IosOwnedRawFrame = IosOwnedRawFrame(
        width = IOS_TEST_WIDTH,
        height = IOS_TEST_HEIGHT,
        analysisFrameSeq = sequence,
        captureTimestampNs = sequence * 1_000L,
        inferenceTimestampMs = sequence.toLong(),
        phase = CapturePhase.ACTED,
        pixels = iosOwnedTestData(pixels),
    )

    private fun testProvenance() = SourceProvenance(
        repo = "KMP-FaceLink",
        branch = "test",
        gitSha = "0".repeat(40),
        commitTimeIso = "2026-08-30T00:00:00Z",
        dirty = true,
    )
}
