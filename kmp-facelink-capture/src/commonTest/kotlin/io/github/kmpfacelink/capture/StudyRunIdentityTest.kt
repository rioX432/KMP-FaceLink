package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class StudyRunIdentityTest {
    @Test
    fun matchingProtocolAndProfileResumeTheExactRun() {
        val restored = run("existing")
        val selected = activeOrNewStudyRun(
            restored,
            restored.protocolId,
            restored.storageProfile,
        ) { run("unexpected") }

        assertEquals(restored, selected)
    }

    @Test
    fun protocolOrProfileChangeCreatesANewRun() {
        val restored = run("existing")
        val protocolChanged = activeOrNewStudyRun(
            restored,
            "CHEEK_PUFF_CONFIRMATORY_22_V1",
            restored.storageProfile,
        ) { run("protocol-new", "CHEEK_PUFF_CONFIRMATORY_22_V1") }
        val profileChanged = activeOrNewStudyRun(
            restored,
            restored.protocolId,
            CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
        ) { run("profile-new", storageProfile = CaptureStorageProfile.CANONICAL_RGBA_240X320_V1) }

        assertNotEquals(restored.runId, protocolChanged.runId)
        assertNotEquals(restored.runId, profileChanged.runId)
    }

    @Test
    fun onlyTheActiveRunContributesToProgressAndAttemptAllocation() {
        val active = run("active")
        val other = run("other")
        val records = listOf(
            StudyTakeRecord("legacy", CaptureScenario.CHEEK_PUFF_LIGHT, 1),
            StudyTakeRecord("other", CaptureScenario.CHEEK_PUFF_LIGHT, 1, other),
            StudyTakeRecord("active", CaptureScenario.CHEEK_PUFF_LIGHT, 1, active),
        )

        val scoped = scopedStudyTakeRecords(records, active)
        assertEquals(listOf("active"), scoped.map(StudyTakeRecord::takeId))
        assertEquals(2, nextStudyAttempt(scoped, CaptureScenario.CHEEK_PUFF_LIGHT))
    }

    @Test
    fun duplicateScenarioAttemptWithinOneRunIsRejected() {
        val active = run("active")
        assertFailsWith<IllegalArgumentException> {
            scopedStudyTakeRecords(
                listOf(
                    StudyTakeRecord("first", CaptureScenario.NEUTRAL, 1, active),
                    StudyTakeRecord("second", CaptureScenario.NEUTRAL, 1, active),
                ),
                active,
            )
        }
    }

    private fun run(
        id: String,
        protocolId: String = StudyRunIdentity.GUIDED_PROTOCOL_ID,
        storageProfile: CaptureStorageProfile = CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
    ): StudyRunIdentity = StudyRunIdentity(
        schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
        runId = id,
        protocolId = protocolId,
        storageProfile = storageProfile,
        createdAtIso = "2026-08-30T00:00:00Z",
    )
}
