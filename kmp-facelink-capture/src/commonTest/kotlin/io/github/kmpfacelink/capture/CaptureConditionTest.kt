package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CaptureConditionTest {

    private fun provenance(dirty: Boolean = false, sha: String = "27971d62b4") = SourceProvenance(
        repo = "KMP-FaceLink",
        branch = "feature/capture-harness",
        gitSha = sha,
        commitTimeIso = "2026-08-17T18:58:09+09:00",
        dirty = dirty,
    )

    private fun condition(
        downsampleTo: String? = null,
        downsampleAlgorithm: String? = null,
        modelSha256: String = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
    ) = CaptureCondition(
        nativeResolution = "240x320",
        downsampleTo = downsampleTo,
        downsampleAlgorithm = downsampleAlgorithm,
        runningMode = "LIVE_STREAM",
        delegate = "GPU",
        numFaces = 1,
        minDetectionConfidence = 0.5f,
        minTrackingConfidence = 0.5f,
        minPresenceConfidence = 0.5f,
        mediapipeVersion = "0.10.35",
        modelSha256 = modelSha256,
        mirrored = true,
        layer = MeasurementLayer.DEVICE_REALTIME,
        provenance = provenance(),
    )

    @Test
    fun `a take from a clean commit is reproducible`() {
        assertTrue(condition().provenance.reproducible)
    }

    @Test
    fun `a dirty working tree makes a take unreproducible`() {
        // The sha no longer describes what actually ran, so the take cannot be rebuilt.
        assertFalse(provenance(dirty = true).reproducible)
    }

    @Test
    fun `a missing sha makes a take unreproducible`() {
        assertFalse(provenance(sha = "").reproducible)
    }

    @Test
    fun `the model hash is required`() {
        // Two takes are only comparable if the same weights produced them.
        val failure = runCatching { condition(modelSha256 = "  ") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }

    @Test
    fun `a downsample algorithm without a target resolution is rejected`() {
        // "downsampled with bilinear" says nothing without the size, and different filters give
        // different landmarks, so a half-specified downsample is not a condition.
        val failure = runCatching { condition(downsampleAlgorithm = "bilinear") }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }

    @Test
    fun `a fully specified downsample is accepted`() {
        val c = condition(downsampleTo = "120x160", downsampleAlgorithm = "bilinear")
        assertEquals("120x160", c.downsampleTo)
        assertEquals("bilinear", c.downsampleAlgorithm)
    }

    @Test
    fun `the three layers stay distinct`() {
        // They describe non-exchangeable contexts. Distinct labels prevent a downstream aggregate
        // from silently pooling them; they do not authorize subtraction.
        assertEquals(3, MeasurementLayer.entries.size)
    }

    @Test
    fun `older takes default to no participant camera preview`() {
        assertEquals(ParticipantPreview.NONE, condition().participantPreview)
    }
}
