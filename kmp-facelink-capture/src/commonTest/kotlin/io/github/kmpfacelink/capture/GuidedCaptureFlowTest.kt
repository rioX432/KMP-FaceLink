package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals

class GuidedCaptureFlowTest {

    @Test
    fun guidedWorkflowDoesNotChangeTheRegisteredTwentyTwoSlotProtocol() {
        assertEquals(
            listOf(
                "neutral_10s",
                "jaw_open",
                "cheek_puff_light",
                "cheek_puff_mid",
                "cheek_puff",
                "cheek_puff_left",
                "cheek_puff_right",
                "blow",
                "blow",
                "pucker",
                "smile_hold_10s",
                "tongue_out",
                "nose_sneer",
                "speech",
                "cheek_puff_light",
                "cheek_puff_mid",
                "cheek_puff",
                "cheek_puff_yaw_right",
                "cheek_puff_yaw_left",
                "eye_widen",
                "jaw_forward",
                "clench",
            ),
            io.github.kmpfacelink.capture.ui.studyProtocol().map(CaptureScenario::id),
        )
    }

    @Test
    fun everyRegisteredSlotKeepsTheFrozenTwentyTwoSecondScript() {
        val expected = listOf(
            TakeScript.Span(TakeScript.Cue.PREPARE, CapturePhase.PRE_ROLL, 0L, 2_000L),
            TakeScript.Span(TakeScript.Cue.NEUTRAL, CapturePhase.NEUTRAL, 2_000L, 6_000L),
            TakeScript.Span(TakeScript.Cue.HOLD, CapturePhase.ACTED, 6_000L, 10_000L),
            TakeScript.Span(TakeScript.Cue.RELEASE, CapturePhase.NEUTRAL, 10_000L, 14_000L),
            TakeScript.Span(TakeScript.Cue.HOLD, CapturePhase.ACTED, 14_000L, 18_000L),
            TakeScript.Span(TakeScript.Cue.RELEASE, CapturePhase.NEUTRAL, 18_000L, 22_000L),
        )

        val scripts = io.github.kmpfacelink.capture.ui.studyProtocol().map(::TakeScript)
        assertEquals(22, scripts.size)
        scripts.forEach { script ->
            assertEquals(expected, script.spans, script.scenario.id)
            assertEquals(22_000L, script.totalMs, script.scenario.id)
        }
    }

    @Test
    fun gateMustStayPassedForTheWholeVisibleCountdownAndStartsOnlyOnce() {
        val flow = GuidedCaptureFlow(GuidedCaptureSettings(gateCountdownSeconds = 3))
        flow.confirmIntroduction()
        assertEquals(GuidedCaptureStage.COUNTDOWN, flow.onGateChanged(true).stage)
        assertEquals(2, flow.tick().first.secondsRemaining)

        assertEquals(GuidedCaptureStage.ALIGNING, flow.onGateChanged(false).stage)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)

        flow.onGateChanged(true)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
        assertEquals(GuidedCaptureAction.StartTake, flow.tick().second)
        assertEquals(GuidedCaptureStage.RECORDING, flow.snapshot.stage)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
    }

    @Test
    fun saveAdvancesAutomaticallyButPauseNeverChangesTheProtocolSlot() {
        val flow = recordingFlow()
        flow.onSaved()
        assertEquals(GuidedCaptureStage.PAUSED, flow.pause().stage)
        assertEquals(GuidedCaptureStage.ALIGNING, flow.resume().stage)

        val uninterrupted = recordingFlow()
        uninterrupted.onSaved()
        assertEquals(GuidedCaptureAction.None, uninterrupted.tick().second)
        assertEquals(GuidedCaptureAction.Advance, uninterrupted.tick().second)
        assertEquals(GuidedCaptureStage.ALIGNING, uninterrupted.snapshot.stage)
    }

    @Test
    fun recorderFailureRequiresAnExplicitRetryAndCannotLoopAutomatically() {
        val flow = recordingFlow()
        assertEquals(GuidedCaptureStage.FAILED, flow.onFailed("データは保存されていません").stage)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
        assertEquals(GuidedCaptureStage.ALIGNING, flow.retry().stage)
    }

    private fun recordingFlow(): GuidedCaptureFlow = GuidedCaptureFlow().apply {
        confirmIntroduction()
        onGateChanged(true)
        repeat(3) { tick() }
        assertEquals(GuidedCaptureStage.RECORDING, snapshot.stage)
    }
}
