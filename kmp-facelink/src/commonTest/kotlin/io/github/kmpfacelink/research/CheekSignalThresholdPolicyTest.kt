package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateReadiness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalFaceLinkApi::class)
class CheekSignalThresholdPolicyTest {
    @Test
    fun gridAndHysteresisAreFrozen() {
        assertEquals(
            listOf(0.05f, 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.40f),
            CheekSignalThresholdPolicy.onThresholds,
        )
        assertEquals(0.16f, CheekSignalThresholdPolicy.create(0.20f).offThreshold, 0.000001f)
        assertFailsWith<IllegalArgumentException> { CheekSignalThresholdPolicy.create(0.21f) }
    }

    @Test
    fun exactOnThresholdActivatesOnlyAfterContinuous350ms() {
        val session = CheekSignalThresholdPolicy.create(0.20f)
        assertFalse(session.ready(0L, 0.20f).active)
        assertFalse(session.ready(200L, 0.20f).active)
        assertFalse(session.ready(349L, 0.20f).active)
        assertTrue(session.ready(350L, 0.20f).active)
    }

    @Test
    fun exactOffThresholdRemainsActiveAndStrictlyLowerReleasesAfter200ms() {
        val session = activeSession()
        assertTrue(session.ready(400L, session.offThreshold).active)
        assertTrue(session.ready(450L, 0.159f).active)
        assertTrue(session.ready(649L, 0.159f).active)
        assertFalse(session.ready(650L, 0.159f).active)
    }

    @Test
    fun absenceBreaksDwellWithoutPretendingZero() {
        val session = CheekSignalThresholdPolicy.create(0.20f)
        session.ready(0L, 0.3f)
        val absent = session.observe(identity(200L), null, CandidateReadiness.MISSING_INPUT)
        assertFalse(absent.available)
        assertEquals(CandidateReadiness.MISSING_INPUT, absent.readiness)
        assertFalse(session.ready(400L, 0.3f).active)
        assertFalse(session.ready(600L, 0.3f).active)
        assertTrue(session.ready(750L, 0.3f).active)
    }

    @Test
    fun gapOver250msBreaksDwellButDoesNotClearCompletedActiveState() {
        val activating = CheekSignalThresholdPolicy.create(0.20f)
        activating.ready(0L, 0.3f)
        assertFalse(activating.ready(300L, 0.3f).active)
        assertFalse(activating.ready(650L, 0.3f).active)

        val active = activeSession()
        assertTrue(active.ready(700L, 0.3f).active)
    }

    @Test
    fun epochOrRevisionChangeClearsCompletedState() {
        val session = activeSession()
        val changedEpoch = session.observe(identity(400L, epoch = 2u), 0.3f, CandidateReadiness.READY)
        assertFalse(changedEpoch.active)
        val changedRevision = session.observe(
            identity(800L, epoch = 2u, revision = 1u),
            0.3f,
            CandidateReadiness.READY,
        )
        assertFalse(changedRevision.active)
    }

    private fun activeSession(): CheekSignalThresholdSession =
        CheekSignalThresholdPolicy.create(0.20f).also {
            it.ready(0L, 0.3f)
            it.ready(200L, 0.3f)
            it.ready(350L, 0.3f)
        }

    private fun CheekSignalThresholdSession.ready(
        timestampMs: Long,
        value: Float,
    ): CheekSignalThresholdObservation = observe(identity(timestampMs), value, CandidateReadiness.READY)

    private fun identity(
        timestampMs: Long,
        epoch: ULong = 1u,
        revision: ULong = 0u,
    ): CandidateInputIdentity = CandidateInputIdentity(epoch, revision, 0, timestampMs)
}
