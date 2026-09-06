package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scenario ids end up in session metadata and in every downstream analysis, so they are a
 * data contract rather than an implementation detail. These tests pin the parts that would break
 * already-recorded takes if they changed.
 */
class CaptureScenarioTest {

    @Test
    fun `ids are unique and stable`() {
        val ids = CaptureScenario.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate id in $ids")
        // The pilot takes were recorded under these; renaming one orphans that data.
        assertEquals("neutral_10s", CaptureScenario.NEUTRAL.id)
        assertEquals("cheek_puff", CaptureScenario.CHEEK_PUFF.id)
    }

    @Test
    fun `byId round-trips and rejects the unknown`() {
        CaptureScenario.entries.forEach { assertEquals(it, CaptureScenario.byId(it.id)) }
        assertNull(CaptureScenario.byId("no_such_scenario"))
    }

    @Test
    fun `every cheek-puff study scenario carries its own neutral phase`() {
        // The evaluation baselines each clip against its own neutral, so a study scenario without
        // one produces a take that cannot be scored at all.
        val study = CaptureScenario.entries.filter {
            it.id.startsWith("cheek_puff") || it.id in setOf(
                "pucker", "blow", "jaw_open", "eye_widen", "nose_sneer", "tongue_out",
                "jaw_forward",
            )
        }
        assertTrue(study.isNotEmpty())
        study.forEach {
            assertTrue(it.neutralPromptSeconds > 0, "${it.id} has no neutral phase")
        }
    }

    @Test
    fun `the hard negatives are present`() {
        // Without pucker and blow there is no false-positive rate, and a detection rate on its
        // own says nothing about whether the detector is usable.
        assertTrue(CaptureScenario.byId("pucker") != null)
        assertTrue(CaptureScenario.byId("blow") != null)
    }

    @Test
    fun `goalDriven matches the validation kind`() {
        assertTrue(CaptureScenario.BLINK.goalDriven)
        assertTrue(CaptureScenario.HEAD_TURN.goalDriven)
        assertTrue(!CaptureScenario.SMILE.goalDriven)
        assertTrue(!CaptureScenario.CHEEK_PUFF.goalDriven)
    }
}
