package io.github.kmpfacelink.capture

/**
 * The timeline one take runs on, in common code so both platforms mark phases identically.
 *
 * **A study take holds the expression twice.** Neutral, hold, release, hold, release — the second
 * hold is what makes "unstable between repetitions" a statement about the estimator rather than
 * about one attempt, and every clip's evaluation reports both. So phases are not monotonic: going
 * from [CapturePhase.ACTED] back to [CapturePhase.NEUTRAL] mid-take is the script, not an error.
 *
 * The pre-roll is separate from [CaptureScenario.maxSeconds]. It exists because the streaming
 * running modes carry tracking state forward: a frame replayed from a cold start is not in the
 * condition the live engine was in when it produced its result, so the take begins with frames
 * whose only job is to rebuild that state before the first scored one.
 *
 * Scenarios with no neutral prompt ([CaptureScenario.neutralPromptSeconds] == 0) are single-span:
 * they are the older goal-driven takes, which have nothing to baseline against and are scored by
 * their own evaluator instead.
 */
public class TakeScript(public val scenario: CaptureScenario) {

    /** What the subject is being asked to do, so the UI can prompt without knowing the timeline. */
    public enum class Cue { PREPARE, NEUTRAL, HOLD, RELEASE, DONE }

    /** One stretch of the take with a single phase and a single cue. */
    public data class Span(
        val cue: Cue,
        val phase: CapturePhase,
        val startMs: Long,
        val endMs: Long,
    ) {
        public val durationMs: Long get() = endMs - startMs
    }

    public val spans: List<Span> = buildSpans(scenario)

    /** Total length including the pre-roll. */
    public val totalMs: Long = spans.lastOrNull()?.endMs ?: 0L

    /** The span [elapsedMs] falls in, or null once the take is over. */
    public fun spanAt(elapsedMs: Long): Span? = spans.firstOrNull { elapsedMs < it.endMs }

    /** The phase to stamp on a frame captured at [elapsedMs], or null once the take is over. */
    public fun phaseAt(elapsedMs: Long): CapturePhase? = spanAt(elapsedMs)?.phase

    /**
     * The span that follows the one [elapsedMs] falls in, or null at the end of the take.
     *
     * The UI announces it before it arrives: the timing is pre-registered and cannot move, so a
     * subject who only learns the cue when the span starts spends the first of its frames still
     * forming the expression, inside the window those frames are scored in.
     */
    public fun nextSpanAt(elapsedMs: Long): Span? {
        val index = spans.indexOfFirst { elapsedMs < it.endMs }
        return if (index < 0) null else spans.getOrNull(index + 1)
    }

    /** Seconds left in the current span, rounded up, for a countdown. */
    public fun remainingSecondsIn(elapsedMs: Long): Int {
        val span = spanAt(elapsedMs) ?: return 0
        val left = span.endMs - elapsedMs
        return ((left + MS_PER_SEC - 1) / MS_PER_SEC).toInt()
    }

    private companion object {
        const val MS_PER_SEC = 1_000L

        /** Long enough for the streaming modes to have picked the face up before scoring starts. */
        const val PRE_ROLL_SECONDS = 2

        // Neutral, hold, release, hold, release fills the study's 20 s cap exactly at 4 s spans.
        const val HOLD_SECONDS = 4
        const val RELEASE_SECONDS = 4

        fun buildSpans(scenario: CaptureScenario): List<Span> {
            val spans = ArrayList<Span>()
            var at = 0L
            fun add(cue: Cue, phase: CapturePhase, seconds: Int) {
                if (seconds <= 0) return
                val end = at + seconds * MS_PER_SEC
                spans += Span(cue, phase, at, end)
                at = end
            }

            add(Cue.PREPARE, CapturePhase.PRE_ROLL, PRE_ROLL_SECONDS)
            if (scenario.neutralPromptSeconds <= 0) {
                // Goal-driven and free-running takes: one acted span, capped, scored by the
                // scenario's own evaluator rather than against a neutral baseline.
                add(Cue.HOLD, CapturePhase.ACTED, scenario.maxSeconds)
                return spans
            }

            add(Cue.NEUTRAL, CapturePhase.NEUTRAL, scenario.neutralPromptSeconds)
            var scripted = scenario.neutralPromptSeconds
            var holds = 0
            // Two holds if the cap allows them, and never a hold that would be cut short: a
            // truncated hold looks like a weak expression in the numbers.
            while (holds < 2 && scripted + HOLD_SECONDS + RELEASE_SECONDS <= scenario.maxSeconds) {
                add(Cue.HOLD, CapturePhase.ACTED, HOLD_SECONDS)
                add(Cue.RELEASE, CapturePhase.NEUTRAL, RELEASE_SECONDS)
                scripted += HOLD_SECONDS + RELEASE_SECONDS
                holds++
            }
            if (holds == 0) {
                // The cap cannot fit a hold plus its release; spend what is left on one hold.
                add(Cue.HOLD, CapturePhase.ACTED, scenario.maxSeconds - scripted)
            }
            return spans
        }
    }
}
