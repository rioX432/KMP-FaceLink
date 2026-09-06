package io.github.kmpfacelink.capture

import io.github.kmpfacelink.capture.ui.nextIncompleteTake
import io.github.kmpfacelink.capture.ui.studyProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StudyTakeHistoryTest {
    @Test
    fun removingARecordedTakeImmediatelyMakesItsScenarioIncomplete() {
        val slots = studyTakeSlots(studyProtocol())
        val neutral = StudyTakeRecord("neutral", CaptureScenario.NEUTRAL, 1)
        val jaw = StudyTakeRecord("jaw", CaptureScenario.JAW_OPEN, 1)
        val firstPuff = StudyTakeRecord("puff", CaptureScenario.CHEEK_PUFF_LIGHT, 1)

        assertEquals(
            CaptureScenario.CHEEK_PUFF_MID,
            nextIncompleteTake(slots, listOf(neutral, jaw, firstPuff))?.scenario,
        )
        assertEquals(
            CaptureScenario.CHEEK_PUFF_LIGHT,
            nextIncompleteTake(slots, listOf(neutral, jaw))?.scenario,
        )
    }

    @Test
    fun anyPublishedTakeCanBeSelectedForReplacement() {
        val ids = listOf("20260823-010000_neutral_10s_1", "20260823-010100_cheek_puff_light_1")
        assertTrue(containsPublishedTake(ids, ids.first()))
        assertTrue(containsPublishedTake(ids, ids.last()))
        assertFalse(containsPublishedTake(ids, "missing"))
    }

    @Test
    fun runGuardRejectsASecondEntryUntilTheFirstLeaves() {
        val guard = CaptureRunGuard()
        assertTrue(guard.tryEnter())
        assertFalse(guard.tryEnter())
        guard.leave()
        assertTrue(guard.tryEnter())
        guard.leave()
    }

    @Test
    fun publishAndDeleteUpdateTheInMemoryHistoryImmediately() {
        val take = StudyTakeRecord("take-1", CaptureScenario.NEUTRAL, 1)
        val published = withPublishedStudyTake(emptyList(), take)
        assertEquals(listOf(take), published)
        assertTrue(withoutStudyTake(published, take.takeId).isEmpty())
    }

    @Test
    fun replacingAnArbitraryTakeRestoresTheCompletedCount() {
        val slots = studyTakeSlots(studyProtocol())
        val original = slots.mapIndexed { index, slot ->
            StudyTakeRecord("take-$index", slot.scenario, slot.attempt)
        }
        val target = original.first { it.scenario == CaptureScenario.CHEEK_PUFF_LIGHT && it.attempt == 1 }

        val afterDelete = withoutStudyTake(original, target.takeId)
        assertEquals(21, completedStudyTakeCount(slots, afterDelete))
        assertEquals(
            StudyTakeSlot(CaptureScenario.CHEEK_PUFF_LIGHT, 1, StudyTakeGroup.PROTOCOL),
            nextIncompleteTake(slots, afterDelete),
        )

        val replacement = StudyTakeRecord("replacement", target.scenario, target.attempt)
        val afterReplacement = withPublishedStudyTake(afterDelete, replacement)
        assertEquals(22, completedStudyTakeCount(slots, afterReplacement))
        assertEquals(null, nextIncompleteTake(slots, afterReplacement))
    }

    @Test
    fun repeatedScenarioAttemptsAreCountedAsDifferentSlots() {
        val slots = studyTakeSlots(studyProtocol())
        val first = StudyTakeRecord("light-1", CaptureScenario.CHEEK_PUFF_LIGHT, 1)
        val secondSlot = slots.first {
            it.scenario == CaptureScenario.CHEEK_PUFF_LIGHT && it.attempt == 2
        }

        assertEquals(1, completedStudyTakeCount(slots, listOf(first)))
        assertEquals(null, acceptedStudyTake(listOf(first), secondSlot))
    }

    @Test
    fun recordingFillsTheLowestMissingAttemptWhenReplacing() {
        val first = StudyTakeRecord("light-1", CaptureScenario.CHEEK_PUFF_LIGHT, 1)
        val second = StudyTakeRecord("light-2", CaptureScenario.CHEEK_PUFF_LIGHT, 2)
        assertEquals(1, nextStudyAttempt(emptyList(), CaptureScenario.CHEEK_PUFF_LIGHT))
        assertEquals(2, nextStudyAttempt(listOf(first), CaptureScenario.CHEEK_PUFF_LIGHT))
        assertEquals(1, nextStudyAttempt(listOf(second), CaptureScenario.CHEEK_PUFF_LIGHT))
    }

    @Test
    fun stoppedOrFailedScriptsAreNeverPublished() {
        assertTrue(shouldPublishTake(ScriptEnd.COMPLETED, hasFailure = false))
        assertFalse(shouldPublishTake(ScriptEnd.STOPPED, hasFailure = false))
        assertFalse(shouldPublishTake(ScriptEnd.FAILED, hasFailure = true))
        assertFalse(shouldPublishTake(ScriptEnd.COMPLETED, hasFailure = true))
    }
}
