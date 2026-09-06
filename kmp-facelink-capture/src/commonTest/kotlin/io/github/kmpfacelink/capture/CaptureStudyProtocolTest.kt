package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureStudyProtocolTest {
    @Test
    fun iosRegisteredCoefficientRunStopsAtTheFrozenSeventeenthSlot() {
        val protocol = CaptureStudyProtocol.iosRegisteredCoefficient17()

        assertEquals(CaptureStudyProtocol.IOS_REGISTERED_COEFFICIENT_17_ID, protocol.id)
        assertEquals(CaptureStorageProfile.COEFFICIENTS_ONLY_V1, protocol.storageProfile)
        assertEquals(17, protocol.scenarios.size)
        assertEquals(CaptureScenario.NEUTRAL, protocol.scenarios.first())
        assertEquals(CaptureScenario.CHEEK_PUFF, protocol.scenarios.last())
        assertEquals(
            listOf(CaptureScenario.CHEEK_PUFF_LIGHT, CaptureScenario.CHEEK_PUFF_MID, CaptureScenario.CHEEK_PUFF),
            protocol.scenarios.takeLast(3),
        )
    }

    @Test
    fun sessionCIdentityStorageAndRepeatedSlotsAreFrozenTogether() {
        val protocol = CaptureStudyProtocol.learnedPatchSessionC12()

        assertEquals(CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID, protocol.id)
        assertEquals(CaptureStorageProfile.CANONICAL_RGBA_240X320_V1, protocol.storageProfile)
        assertEquals(
            listOf(
                CaptureScenario.NEUTRAL,
                CaptureScenario.JAW_OPEN,
                CaptureScenario.CHEEK_PUFF_LIGHT,
                CaptureScenario.CHEEK_PUFF_MID,
                CaptureScenario.CHEEK_PUFF,
                CaptureScenario.BLOW,
                CaptureScenario.PUCKER,
                CaptureScenario.CHEEK_PUFF_LIGHT,
                CaptureScenario.CHEEK_PUFF_MID,
                CaptureScenario.CHEEK_PUFF,
                CaptureScenario.BLOW,
                CaptureScenario.PUCKER,
            ),
            protocol.scenarios,
        )
        assertEquals(listOf(1, 2), studyTakeSlots(protocol.scenarios).filter {
            it.scenario == CaptureScenario.BLOW
        }.map { it.attempt })
        assertEquals(listOf(1, 2), studyTakeSlots(protocol.scenarios).filter {
            it.scenario == CaptureScenario.PUCKER
        }.map { it.attempt })
    }

    @Test
    fun invalidProtocolCannotBecomeAnActiveBoundary() {
        assertFailsWith<IllegalArgumentException> {
            CaptureStudyProtocol("", CaptureStorageProfile.COEFFICIENTS_ONLY_V1, listOf(CaptureScenario.NEUTRAL))
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureStudyProtocol(
                "valid",
                CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
                listOf(CaptureScenario.NEUTRAL),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureStudyProtocol("valid", CaptureStorageProfile.COEFFICIENTS_ONLY_V1, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureStudyProtocol(
                CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                listOf(CaptureScenario.NEUTRAL),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CaptureStudyProtocol(
                CaptureStudyProtocol.IOS_REGISTERED_COEFFICIENT_17_ID,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                listOf(CaptureScenario.NEUTRAL),
            )
        }
    }

    @Test
    fun attemptAllocationCannotEscapeTheFrozenPlan() {
        val protocol = CaptureStudyProtocol.learnedPatchSessionC12()
        val run = StudyRunIdentity.legacyUnscoped()
        val blow1 = StudyTakeRecord("blow-1", CaptureScenario.BLOW, 1, run)
        val blow2 = StudyTakeRecord("blow-2", CaptureScenario.BLOW, 2, run)

        assertEquals(1, nextProtocolAttempt(emptyList(), protocol, CaptureScenario.BLOW))
        assertEquals(2, nextProtocolAttempt(listOf(blow1), protocol, CaptureScenario.BLOW))
        assertFailsWith<IllegalArgumentException> {
            nextProtocolAttempt(listOf(blow1, blow2), protocol, CaptureScenario.BLOW)
        }
        assertFailsWith<IllegalArgumentException> {
            nextProtocolAttempt(emptyList(), protocol, CaptureScenario.SMILE)
        }
    }

    @Test
    fun callerOwnedScenarioListCannotMutateTheActivePlan() {
        val callerOwned = mutableListOf(CaptureScenario.NEUTRAL)
        val protocol = CaptureStudyProtocol(
            "COPY_TEST_V1",
            CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            callerOwned,
        )

        callerOwned += CaptureScenario.SMILE

        assertEquals(listOf(CaptureScenario.NEUTRAL), protocol.scenarios)
    }
}
