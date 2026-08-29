package io.github.kmpfacelink.capture

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class LiveEvaluatorTest {

    private fun blinkFrame(evaluator: LiveEvaluator, ear: Float, ts: Long) {
        evaluator.onFrame(
            ear = ear,
            mouthRatio = 0f,
            frontal = false,
            neutralPhase = false,
            yawSin = null,
            pitchSin = null,
            captureTimestampNs = ts,
        )
    }

    /** Feeds [count] frames at [ear] and returns the next timestamp. */
    private fun feed(evaluator: LiveEvaluator, ear: Float, count: Int, start: Long): Long {
        var ts = start
        repeat(count) { blinkFrame(evaluator, ear, ts++) }
        return ts
    }

    private fun blinkCount(evaluator: LiveEvaluator): Int =
        (evaluator.progress() as LiveProgress.Blink).count

    @Test
    fun `five long half-second closures count five`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = feed(evaluator, 0.30f, 4, 0L) // establish open baseline
        repeat(5) {
            ts = feed(evaluator, 0.03f, 15, ts) // ~500ms sustained closure
            ts = feed(evaluator, 0.30f, 4, ts) // reopen
        }
        assertEquals(5, blinkCount(evaluator))
        assertTrue(evaluator.outcome().passed)
    }

    @Test
    fun `five rapid blinks count five`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = feed(evaluator, 0.30f, 4, 0L)
        repeat(5) {
            ts = feed(evaluator, 0.05f, 2, ts) // brief dip
            ts = feed(evaluator, 0.30f, 2, ts) // quick reopen
        }
        assertEquals(5, blinkCount(evaluator))
    }

    @Test
    fun `two blinks with only a partial reopen between them count two`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = feed(evaluator, 0.40f, 6, 0L) // baseline 0.40 -> close 0.22, rearm 0.30
        ts = feed(evaluator, 0.05f, 3, ts) // blink 1
        ts = feed(evaluator, 0.33f, 3, ts) // partial reopen (~close x1.5), never fully open
        ts = feed(evaluator, 0.05f, 3, ts) // blink 2
        feed(evaluator, 0.40f, 3, ts)
        assertEquals(2, blinkCount(evaluator))
    }

    @Test
    fun `single-frame noise dip is not counted`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = feed(evaluator, 0.30f, 10, 0L)
        ts = feed(evaluator, 0.10f, 1, ts) // one-frame dip only
        feed(evaluator, 0.30f, 10, ts)
        assertEquals(0, blinkCount(evaluator))
    }

    @Test
    fun `blink counter streams to five and passes`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = 0L
        repeat(5) {
            repeat(6) { blinkFrame(evaluator, 0.30f, ts++) } // open
            repeat(3) { blinkFrame(evaluator, 0.10f, ts++) } // dip
        }
        repeat(6) { blinkFrame(evaluator, 0.30f, ts++) }

        val progress = evaluator.progress()
        assertTrue((progress) is LiveProgress.Blink)
        assertEquals(5, (progress as LiveProgress.Blink).count)
        assertTrue(evaluator.outcome().passed)
    }

    @Test
    fun `isGoalReached flips true once five blinks are counted`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        var ts = feed(evaluator, 0.30f, 4, 0L)
        repeat(4) {
            ts = feed(evaluator, 0.05f, 2, ts)
            ts = feed(evaluator, 0.30f, 2, ts)
        }
        assertFalse(evaluator.isGoalReached()) // only 4 so far
        ts = feed(evaluator, 0.05f, 2, ts)
        feed(evaluator, 0.30f, 2, ts)
        assertTrue(evaluator.isGoalReached())
    }

    @Test
    fun `steady open eyes count zero blinks and fail`() {
        val evaluator = LiveEvaluator(SoftValidationKind.BLINK_COUNT)
        repeat(40) { blinkFrame(evaluator, 0.30f, it.toLong()) }
        assertEquals(0, (evaluator.progress() as LiveProgress.Blink).count)
        assertFalse(evaluator.outcome().passed)
    }

    @Test
    fun `smile passes when acted spread exceeds neutral baseline and is held`() {
        val evaluator = LiveEvaluator(SoftValidationKind.SMILE)
        // neutral baseline phase (frontal frames)
        repeat(5) { evaluator.onFrame(0f, 0.90f, frontal = true, neutralPhase = true, null, null, it.toLong()) }
        // acted phase: 5 frames spaced 1s apart, wider mouth, held -> 4s accumulated hold
        for (k in 0..4) {
            val ts = 1_000_000_000L * (k + 1)
            evaluator.onFrame(0f, 1.10f, frontal = true, neutralPhase = false, null, null, ts)
        }
        val progress = evaluator.progress() as LiveProgress.Smile
        assertTrue((progress.holdSeconds) >= (3))
        assertTrue(evaluator.outcome().passed)
    }

    @Test
    fun `head turn passes only after all four directions are covered`() {
        val evaluator = LiveEvaluator(SoftValidationKind.HEAD_TURN)
        evaluator.onFrame(0f, 0f, frontal = true, neutralPhase = false, yawSin = 0.5f, pitchSin = 0f, 0L) // left
        evaluator.onFrame(0f, 0f, frontal = true, neutralPhase = false, yawSin = -0.5f, pitchSin = 0f, 1L) // right
        evaluator.onFrame(0f, 0f, frontal = true, neutralPhase = false, yawSin = 0f, pitchSin = 0.5f, 2L) // up
        assertFalse(evaluator.outcome().passed) // down still missing
        assertFalse(evaluator.isGoalReached())
        evaluator.onFrame(0f, 0f, frontal = true, neutralPhase = false, yawSin = 0f, pitchSin = -0.5f, 3L) // down
        assertTrue(evaluator.outcome().passed)
        assertTrue(evaluator.isGoalReached())
    }
}
