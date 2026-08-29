package io.github.kmpfacelink.capture

import io.github.kmpfacelink.capture.ui.studyProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyProtocolTest {
    @Test
    fun takeOrderMatchesTheRegisteredProtocolThenAppendsExtras() {
        val expected = listOf(
            CaptureScenario.NEUTRAL,
            CaptureScenario.JAW_OPEN,
            CaptureScenario.CHEEK_PUFF_LIGHT,
            CaptureScenario.CHEEK_PUFF_MID,
            CaptureScenario.CHEEK_PUFF,
            CaptureScenario.CHEEK_PUFF_LEFT,
            CaptureScenario.CHEEK_PUFF_RIGHT,
            CaptureScenario.BLOW,
            CaptureScenario.BLOW,
            CaptureScenario.PUCKER,
            CaptureScenario.SMILE,
            CaptureScenario.TONGUE_OUT,
            CaptureScenario.NOSE_SNEER,
            CaptureScenario.SPEECH,
            CaptureScenario.CHEEK_PUFF_LIGHT,
            CaptureScenario.CHEEK_PUFF_MID,
            CaptureScenario.CHEEK_PUFF,
            CaptureScenario.CHEEK_PUFF_YAW_RIGHT,
            CaptureScenario.CHEEK_PUFF_YAW_LEFT,
            CaptureScenario.EYE_WIDEN,
            CaptureScenario.JAW_FORWARD,
            CaptureScenario.CLENCH,
        )

        assertEquals(22, studyProtocol().size)
        assertEquals(expected, studyProtocol())
    }

    @Test
    fun registeredRepeatsAreSeparatedByTheNegativePass() {
        val slots = studyTakeSlots(studyProtocol())
        val speechIndex = slots.indexOfFirst { it.scenario == CaptureScenario.SPEECH }
        val repeated = listOf(
            CaptureScenario.CHEEK_PUFF_LIGHT,
            CaptureScenario.CHEEK_PUFF_MID,
            CaptureScenario.CHEEK_PUFF,
        )

        repeated.forEach { scenario ->
            val indices = slots.mapIndexedNotNull { index, slot -> index.takeIf { slot.scenario == scenario } }
            assertEquals(2, indices.size, scenario.id)
            assertTrue(indices.first() < slots.indexOfFirst { it.scenario == CaptureScenario.BLOW }, scenario.id)
            assertTrue(indices.last() > speechIndex, scenario.id)
        }
    }

    @Test
    fun protocolAndAdditionalTakesAreVisiblySeparableInThePlan() {
        val slots = studyTakeSlots(studyProtocol())
        assertEquals(17, slots.count { it.group == StudyTakeGroup.PROTOCOL })
        assertEquals(5, slots.count { it.group == StudyTakeGroup.EXTRA })
        assertTrue(slots.take(17).all { it.group == StudyTakeGroup.PROTOCOL })
        assertTrue(slots.drop(17).all { it.group == StudyTakeGroup.EXTRA })
    }
}
