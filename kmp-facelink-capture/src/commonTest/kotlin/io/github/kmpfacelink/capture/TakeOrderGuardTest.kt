package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a well-formed take looks like, tested where both platforms share it.
 *
 * The sink tests cover the same faults through a real sink on each platform. This one covers the
 * decision itself, including the sequence bound that the record types happen to enforce first: the
 * guard is the last place that has to hold if a future caller builds the streams by another route.
 */
class TakeOrderGuardTest {

    @Test
    fun acceptsAScriptedTakeWithGaps() {
        val guard = guard()
        assertNull(guard.admit(0, 0L, 0L, CapturePhase.PRE_ROLL))
        assertNull(guard.admit(1, 10L, 10L, CapturePhase.NEUTRAL))
        // The engine ran neither 2 nor 3. The join is by sequence, not by position.
        assertNull(guard.admit(4, 40L, 40L, CapturePhase.ACTED))
        assertNull(guard.admit(5, 50L, 50L, CapturePhase.NEUTRAL))
        assertNull(guard.admit(6, 60L, 60L, CapturePhase.ACTED))
    }

    @Test
    fun acceptsRepeatedTimestampsAtAHigherSequence() {
        // Two frames inside one clock tick are the camera's resolution, not a fault. Only the
        // sequence has to be strictly increasing.
        val guard = guard()
        assertNull(guard.admit(0, 10L, 10L, CapturePhase.ACTED))
        assertNull(guard.admit(1, 10L, 10L, CapturePhase.ACTED))
    }

    @Test
    fun refusesARepeatedSequence() {
        val guard = guard()
        guard.admit(7, 70L, 70L, CapturePhase.ACTED)
        assertNotNull(guard.admit(7, 71L, 71L, CapturePhase.ACTED))
    }

    @Test
    fun refusesASequenceThatGoesBackwards() {
        val guard = guard()
        guard.admit(7, 70L, 70L, CapturePhase.ACTED)
        assertNotNull(guard.admit(6, 71L, 71L, CapturePhase.ACTED))
    }

    @Test
    fun refusesANegativeSequence() {
        assertNotNull(guard().admit(-1, 0L, 0L, CapturePhase.ACTED))
    }

    @Test
    fun refusesACaptureTimeThatGoesBackwards() {
        val guard = guard()
        guard.admit(0, 2_000L, 0L, CapturePhase.ACTED)
        assertNotNull(guard.admit(1, 1_000L, 1L, CapturePhase.ACTED))
    }

    @Test
    fun refusesANegativeCaptureTime() {
        assertNotNull(guard().admit(0, -1L, 0L, CapturePhase.ACTED))
    }

    @Test
    fun refusesALaterClockThatGoesBackwards() {
        val guard = guard()
        guard.admit(0, 0L, 9_000L, CapturePhase.ACTED)
        assertNotNull(guard.admit(1, 1L, 8_000L, CapturePhase.ACTED))
    }

    @Test
    fun refusesANegativeLaterClock() {
        assertNotNull(guard().admit(0, 0L, -1L, CapturePhase.ACTED))
    }

    @Test
    fun refusesPreRollResumingMidTake() {
        val guard = guard()
        guard.admit(0, 0L, 0L, CapturePhase.PRE_ROLL)
        guard.admit(1, 1L, 1L, CapturePhase.NEUTRAL)
        assertNotNull(guard.admit(2, 2L, 2L, CapturePhase.PRE_ROLL))
    }

    @Test
    fun aRefusedWriteDoesNotMoveTheTakeForward() {
        // The refusal invalidates the take, but nothing here may depend on that: a guard that
        // recorded the bad values would then refuse the write that should have followed.
        val guard = guard()
        guard.admit(5, 50L, 50L, CapturePhase.ACTED)
        assertNotNull(guard.admit(4, 40L, 40L, CapturePhase.ACTED))
        assertNull(guard.admit(6, 60L, 60L, CapturePhase.ACTED))
    }

    @Test
    fun namesTheClockItRefused() {
        // One message per stream, so the operator is not sent to the wrong clock.
        val frames = guard(TakeOrderGuard.LaterClock.INFERENCE_SUBMISSION)
        frames.admit(0, 0L, 9L, CapturePhase.ACTED)
        val results = guard(TakeOrderGuard.LaterClock.RESULT_ARRIVAL)
        results.admit(0, 0L, 9L, CapturePhase.ACTED)

        val frameRefusal = assertNotNull(frames.admit(1, 1L, 8L, CapturePhase.ACTED))
        val resultRefusal = assertNotNull(results.admit(1, 1L, 8L, CapturePhase.ACTED))
        assertTrue(frameRefusal.contains("inference time"), frameRefusal)
        assertTrue(resultRefusal.contains("result arrival time"), resultRefusal)
    }

    private fun guard(
        clock: TakeOrderGuard.LaterClock = TakeOrderGuard.LaterClock.RESULT_ARRIVAL,
    ) = TakeOrderGuard(clock)
}
