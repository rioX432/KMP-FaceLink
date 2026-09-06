package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParticipantQualificationTest {
    @Test
    fun qualificationUsesTheFrozen12SlotsInOrder() {
        val protocol = CaptureStudyProtocol.learnedPatchSessionC12()

        assertEquals(12, qualificationSlotKeys().size)
        assertEquals(studyTakeSlots(protocol.scenarios).map(StudyTakeSlot::key), qualificationSlotKeys())
        assertEquals(12, qualificationSlotKeys().distinct().size)
    }

    @Test
    fun oneUnpublishedStreamOrIntegrityReplacementIsBoundedPerSlot() {
        val slot = qualificationSlotKeys().first()
        var state = freshState()
        state = beginParticipantQualificationAttempt(state, slot)
        val first = failParticipantQualificationAttempt(
            state,
            slot,
            CaptureFailureCategory.STREAM_WRITE.code,
        )
        assertEquals(ParticipantQualificationFailureDecision.RETRY_ONCE, first.second)
        assertEquals(setOf(slot), first.first.replacementUsedSlots)

        state = beginParticipantQualificationAttempt(first.first, slot)
        val second = failParticipantQualificationAttempt(
            state,
            slot,
            CaptureFailureCategory.INTEGRITY.code,
        )
        assertEquals(ParticipantQualificationFailureDecision.TERMINATED, second.second)
        assertEquals(ParticipantQualificationPhase.TERMINATED, second.first.phase)
        assertEquals(2, second.first.failures.last().captureAttempt)
    }

    @Test
    fun nonEligibleFailureAndInterruptedAttemptTerminate() {
        val slot = qualificationSlotKeys().first()
        val active = beginParticipantQualificationAttempt(freshState(), slot)
        val failed = failParticipantQualificationAttempt(
            active,
            slot,
            CaptureFailureCategory.FRAME_SOURCE.code,
        )
        assertEquals(ParticipantQualificationFailureDecision.TERMINATED, failed.second)

        val interrupted = terminateInterruptedParticipantQualification(active)
        assertEquals(ParticipantQualificationPhase.TERMINATED, interrupted.phase)
        assertEquals(INTERRUPTION_CODE, interrupted.terminalCode)
        assertEquals(INTERRUPTION_CODE, interrupted.failures.single().code)
    }

    @Test
    fun reviewedEvidenceRejectsBlankOrNonCanonicalDigests() {
        evidence()
        assertFailsWith<IllegalArgumentException> {
            evidence().copy(reviewPacketSha256 = "")
        }
        assertFailsWith<IllegalArgumentException> {
            evidence().copy(installedApkSha256 = "A".repeat(64))
        }
    }

    private fun freshState() = ParticipantQualificationRunState(
        studyRun = StudyRunIdentity(
            schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
            runId = "qualification-local-1",
            protocolId = CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
            storageProfile = CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
            createdAtIso = "2026-08-30T00:00:00Z",
        ),
        evidence = evidence(),
    )

    private fun evidence() = ParticipantQualificationEvidence(
        reviewPacketSha256 = "1".repeat(64),
        sourceManifestSha256 = "2".repeat(64),
        sourceAuthorizationSha256 = "3".repeat(64),
        installedApkSha256 = "4".repeat(64),
        cheekModelSha256 = "5".repeat(64),
        mouthControlModelSha256 = "6".repeat(64),
        mediaPipeTaskSha256 = "7".repeat(64),
        deviceContractSha256 = "8".repeat(64),
    )
}
