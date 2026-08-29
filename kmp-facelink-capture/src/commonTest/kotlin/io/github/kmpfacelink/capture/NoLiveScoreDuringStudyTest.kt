package io.github.kmpfacelink.capture

import io.github.kmpfacelink.capture.ui.studyProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The study takes must show the operator nothing about how well the expression was detected.
 *
 * A re-take is legitimate when the subject knows they made a mistake and illegitimate when the
 * number came out low, and the only reliable way to keep those apart is for the number not to be
 * on the screen. This is a property of the protocol, not a UI preference: if a live readout is
 * added later, every take after it becomes a take that could have been selected on its outcome.
 */
class NoLiveScoreDuringStudyTest {

    private val study = studyProtocol()

    @Test
    fun noScenarioThatCouldConfirmTheHypothesisShowsALiveScore() {
        // The asymmetry is deliberate. A score shown on a cheek-puff take invites a re-take of the
        // weak ones, which biases towards the hypothesis and is fatal. A score shown on a negative
        // invites a re-take of the weak ones too, but a stronger smile is a harder negative — that
        // bias runs against the hypothesis, so it is allowed to stand rather than be papered over.
        val scored = study.filter { it.softValidation != SoftValidationKind.TIME_ONLY }
        val confirming = scored.filter { it.id.startsWith("cheek_puff") }
        assertTrue(confirming.isEmpty(), "a live score on a positive: ${confirming.map { it.id }}")
        assertEquals(
            listOf("smile_hold_10s"),
            scored.map { it.id },
            "a new scored scenario appeared; check which way its re-takes would bias the result",
        )
    }

    @Test
    fun aTimeOnlyEvaluatorReportsNoProgress() {
        val evaluator = LiveEvaluator(SoftValidationKind.TIME_ONLY)
        assertEquals(LiveProgress.None, evaluator.progress())
    }

    @Test
    fun aTimeOnlyOutcomeCarriesNoMeasurement() {
        val outcome = LiveEvaluator(SoftValidationKind.TIME_ONLY).outcome()
        // The reason string is written into meta.json and shown to the operator. A number in it
        // would be a score by another name.
        assertTrue(outcome.reason.none { it.isDigit() }, "reason leaks a measurement: ${outcome.reason}")
    }
}
