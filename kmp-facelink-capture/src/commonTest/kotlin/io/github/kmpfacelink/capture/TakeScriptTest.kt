package io.github.kmpfacelink.capture

import io.github.kmpfacelink.capture.ui.studyProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The protocol, pinned.
 *
 * The thing worth protecting here is the second hold. Every clip in this study is scored as two
 * attempts, and "unstable between repetitions" is one of its findings — which only means anything if
 * a take actually contains two holds with a return to neutral between them. A phase rule that only
 * moved forwards, or a script that fitted one hold, would quietly change what the numbers describe.
 */
class TakeScriptTest {

    @Test
    fun aStudyTakeHoldsTwiceWithAReturnBetween() {
        val script = TakeScript(CaptureScenario.CHEEK_PUFF)

        assertEquals(
            listOf(
                CapturePhase.PRE_ROLL,
                CapturePhase.NEUTRAL,
                CapturePhase.ACTED,
                CapturePhase.NEUTRAL,
                CapturePhase.ACTED,
                CapturePhase.NEUTRAL,
            ),
            script.spans.map { it.phase },
        )
        assertEquals(2, script.spans.count { it.phase == CapturePhase.ACTED })
        assertEquals(
            listOf(
                TakeScript.Cue.PREPARE,
                TakeScript.Cue.NEUTRAL,
                TakeScript.Cue.HOLD,
                TakeScript.Cue.RELEASE,
                TakeScript.Cue.HOLD,
                TakeScript.Cue.RELEASE,
            ),
            script.spans.map { it.cue },
        )
    }

    @Test
    fun theScriptedPartFitsTheScenarioCap() {
        val script = TakeScript(CaptureScenario.CHEEK_PUFF)
        val preRoll = script.spans.first { it.phase == CapturePhase.PRE_ROLL }
        val scripted = script.totalMs - preRoll.durationMs
        assertEquals(CaptureScenario.CHEEK_PUFF.maxSeconds * 1_000L, scripted)
        // Every hold is full length. A hold cut short by the cap reads as a weak expression.
        script.spans.filter { it.phase == CapturePhase.ACTED }.forEach {
            assertEquals(4_000L, it.durationMs)
        }
    }

    @Test
    fun theSpansCoverTheTakeWithoutGapsOrOverlap() {
        val script = TakeScript(CaptureScenario.CHEEK_PUFF)
        var at = 0L
        script.spans.forEach { span ->
            assertEquals(at, span.startMs)
            assertTrue(span.durationMs > 0)
            at = span.endMs
        }
        assertEquals(script.totalMs, at)
    }

    @Test
    fun aFrameIsStampedWithThePhaseItsClockReadingFallsIn() {
        val script = TakeScript(CaptureScenario.CHEEK_PUFF)
        assertEquals(CapturePhase.PRE_ROLL, script.phaseAt(0))
        assertEquals(CapturePhase.PRE_ROLL, script.phaseAt(1_999))
        // 2 pre-roll, then neutral / hold / release / hold / release at four seconds each.
        assertEquals(CapturePhase.NEUTRAL, script.phaseAt(2_000))
        assertEquals(CapturePhase.ACTED, script.phaseAt(6_000))
        assertEquals(CapturePhase.NEUTRAL, script.phaseAt(10_000))
        assertEquals(CapturePhase.ACTED, script.phaseAt(14_000))
        assertEquals(CapturePhase.NEUTRAL, script.phaseAt(18_000))
        // Past the end there is no phase, which is what stops the take.
        assertNull(script.phaseAt(script.totalMs))
        assertNull(script.phaseAt(script.totalMs + 1))
    }

    @Test
    fun aScenarioWithNoNeutralPromptIsOneActedSpan() {
        // The older goal-driven takes: nothing to baseline against, scored by their own evaluator.
        val script = TakeScript(CaptureScenario.BLINK)
        assertEquals(
            listOf(CapturePhase.PRE_ROLL, CapturePhase.ACTED),
            script.spans.map { it.phase },
        )
        assertEquals(CaptureScenario.BLINK.maxSeconds * 1_000L, script.spans.last().durationMs)
    }

    @Test
    fun theCountdownCountsDownWithinTheCurrentSpan() {
        val script = TakeScript(CaptureScenario.CHEEK_PUFF)
        assertEquals(2, script.remainingSecondsIn(0))
        assertEquals(1, script.remainingSecondsIn(1_500))
        assertEquals(4, script.remainingSecondsIn(2_000))
        assertEquals(0, script.remainingSecondsIn(script.totalMs))
    }

    @Test
    fun everyStudyScenarioGetsBothHolds() {
        // If one of them silently fell back to a single hold, its two-attempt numbers would be
        // reporting something the take never contained.
        studyProtocol().forEach { scenario ->
            val script = TakeScript(scenario)
            val holds = script.spans.count { it.phase == CapturePhase.ACTED }
            assertEquals(22_000L, script.totalMs, "${scenario.id} is not a 22 second take")
            assertEquals(2, holds, "${scenario.id} scripted $holds hold(s)")
            assertEquals(
                listOf(2_000L, 4_000L, 4_000L, 4_000L, 4_000L, 4_000L),
                script.spans.map(TakeScript.Span::durationMs),
                "${scenario.id} does not use the registered study timing",
            )
        }
    }

    @Test
    fun scoredWindowsTrimHalfASecondFromBothHoldEdges() {
        val holds = TakeScript(CaptureScenario.CHEEK_PUFF).spans.filter { it.phase == CapturePhase.ACTED }
        assertEquals(
            listOf(6_500L to 9_500L, 14_500L to 17_500L),
            holds.map { (it.startMs + 500L) to (it.endMs - 500L) },
        )
    }
}
