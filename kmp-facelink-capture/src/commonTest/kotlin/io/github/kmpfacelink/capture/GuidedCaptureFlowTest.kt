package io.github.kmpfacelink.capture

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
    fun gateMustStayPassedForTheWholeVisibleCountdownAndAcceptedStartRunsOnlyOnce() {
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
        assertEquals(GuidedCaptureStage.START_PENDING, flow.snapshot.stage)
        assertEquals(GuidedCaptureStage.RECORDING, flow.acceptPendingStart().stage)
        assertEquals(GuidedCaptureStage.RECORDING, flow.snapshot.stage)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
    }

    @Test
    fun gateLossBetweenCountdownExpiryAndRecorderAcceptanceReturnsToAlignment() {
        val flow = GuidedCaptureFlow(GuidedCaptureSettings(gateCountdownSeconds = 1))
        flow.confirmIntroduction()
        flow.onGateChanged(true)

        assertEquals(GuidedCaptureAction.StartTake, flow.tick().second)
        assertEquals(GuidedCaptureStage.START_PENDING, flow.snapshot.stage)
        assertEquals(GuidedCaptureStage.ALIGNING, flow.rejectPendingStart().stage)
        assertEquals(null, flow.snapshot.secondsRemaining)

        assertEquals(GuidedCaptureStage.COUNTDOWN, flow.onGateChanged(true).stage)
    }

    @Test
    fun pendingStartCannotBecomeRecordingWithoutExplicitAcceptance() {
        val flow = GuidedCaptureFlow(GuidedCaptureSettings(gateCountdownSeconds = 1))
        flow.confirmIntroduction()
        flow.onGateChanged(true)
        flow.tick()

        assertEquals(GuidedCaptureStage.START_PENDING, flow.snapshot.stage)
        assertFailsWith<IllegalStateException> { flow.onSaved() }
        assertEquals(GuidedCaptureStage.RECORDING, flow.acceptPendingStart().stage)
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
        val presentation = CaptureFailureCategory.STREAM_WRITE.receipt().toPresentation()
        assertEquals(GuidedCaptureStage.FAILED, flow.onFailed(presentation).stage)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
        assertEquals(GuidedCaptureStage.ALIGNING, flow.retry().stage)
    }

    @Test
    fun everyFailureCategoryMapsToItsDeclaredWorkflowAction() {
        CaptureFailureCategory.entries.forEach { category ->
            val expected = if (category.retryable) {
                CaptureFailurePrimaryAction.RETRY_TAKE
            } else {
                CaptureFailurePrimaryAction.STOP_RUN
            }
            assertEquals(expected, category.receipt().toPresentation().primaryAction, category.code)
        }
    }

    @Test
    fun cancellationAndMissingPublicationRemainExplicitlyRetryable() {
        assertEquals(
            CaptureFailurePrimaryAction.RETRY_TAKE,
            cancelledCaptureFailurePresentation().primaryAction,
        )
        assertEquals(
            CaptureFailurePrimaryAction.RETRY_TAKE,
            CaptureFailureCategory.INTEGRITY.receipt().toPresentation().primaryAction,
        )
    }

    @Test
    fun nonRetryableFailureTerminatesTheRunWithoutAnyResumePath() {
        val flow = recordingFlow()
        val presentation = CaptureFailureCategory.CLEANUP.receipt().toPresentation()

        assertEquals(GuidedCaptureStage.TERMINATED, flow.onFailed(presentation).stage)
        assertEquals(presentation, flow.snapshot.failure)
        assertEquals(GuidedCaptureAction.None, flow.tick().second)
        assertFailsWith<IllegalStateException> { flow.retry() }
        assertFailsWith<IllegalStateException> { flow.pause() }
        assertFailsWith<IllegalStateException> { flow.resume() }
        assertFailsWith<IllegalStateException> { flow.prepareNext() }
        assertFailsWith<IllegalStateException> { flow.complete() }
        assertEquals(GuidedCaptureStage.TERMINATED, flow.snapshot.stage)
    }

    @Test
    fun frameSourceFailureUsesTheTerminalPath() {
        val flow = recordingFlow()
        val presentation = CaptureFailureCategory.FRAME_SOURCE.receipt().toPresentation()

        assertEquals(CaptureFailurePrimaryAction.STOP_RUN, presentation.primaryAction)
        assertEquals(GuidedCaptureStage.TERMINATED, flow.onFailed(presentation).stage)
        assertFailsWith<IllegalStateException> { flow.retry() }
    }

    @Test
    fun terminalStopRunAbsorbsADelayedRetryableRecorderOutcome() {
        val flow = recordingFlow()
        val terminal = flow.onFailed(CaptureFailureCategory.FRAME_SOURCE.receipt().toPresentation())

        assertEquals(
            terminal,
            flow.onFailed(CaptureFailureCategory.STREAM_WRITE.receipt().toPresentation()),
        )
        assertEquals(GuidedCaptureStage.TERMINATED, flow.snapshot.stage)
        assertEquals(CaptureFailureCategory.FRAME_SOURCE.participantMessageJa, flow.snapshot.failure?.participantMessageJa)
    }

    @Test
    fun fakeSessionProvesRejectedZeroAcceptedOnceAndTerminalCannotRestart() {
        val fake = CountingCaptureSession()
        val rejected = pendingFlow()
        assertFalse(
            startPendingTakeIfEligible(
                flow = rejected,
                slotAvailable = true,
                busy = false,
                gatePassed = false,
                onAccepted = {},
                startRecorder = fake::recordNow,
            ),
        )
        assertEquals(0, fake.recordCalls)
        assertEquals(GuidedCaptureStage.ALIGNING, rejected.snapshot.stage)

        val accepted = pendingFlow()
        assertTrue(
            startPendingTakeIfEligible(
                flow = accepted,
                slotAvailable = true,
                busy = false,
                gatePassed = true,
                onAccepted = {},
                startRecorder = fake::recordNow,
            ),
        )
        assertEquals(1, fake.recordCalls)
        assertFalse(
            startPendingTakeIfEligible(
                flow = accepted,
                slotAvailable = true,
                busy = false,
                gatePassed = true,
                onAccepted = {},
                startRecorder = fake::recordNow,
            ),
        )
        assertEquals(1, fake.recordCalls)

        val terminal = recordingFlow()
        terminal.onFailed(CaptureFailureCategory.FRAME_SOURCE.receipt().toPresentation())
        terminal.onFailed(CaptureFailureCategory.STREAM_WRITE.receipt().toPresentation())
        assertFalse(
            startPendingTakeIfEligible(
                flow = terminal,
                slotAvailable = true,
                busy = false,
                gatePassed = true,
                onAccepted = {},
                startRecorder = fake::recordNow,
            ),
        )
        assertEquals(1, fake.recordCalls)
        assertEquals(GuidedCaptureStage.TERMINATED, terminal.snapshot.stage)
    }

    @Test
    fun terminalDuringDelayedRetakeDeleteSuppressesEveryCompletionMutation() {
        var terminal = true
        var deleteCalls = 0
        var savedTake = "saved"
        var selected = "selected"
        var prepareNextCalls = 0
        var failureMessages = 0

        assertFalse(
            runImmediate {
                runTerminalAwareRetakeDelete(
                    isTerminal = { terminal },
                    delete = { deleteCalls++ },
                    onSuccess = {
                        savedTake = "cleared"
                        selected = "replaced"
                        prepareNextCalls++
                    },
                    onFailure = { failureMessages++ },
                )
            },
        )
        assertEquals(0, deleteCalls)

        terminal = false
        val delayedDelete = DelayedDelete()
        var completion: Result<Boolean>? = null
        suspend {
            runTerminalAwareRetakeDelete(
                isTerminal = { terminal },
                delete = {
                    deleteCalls++
                    delayedDelete.awaitCompletion()
                },
                onSuccess = {
                    savedTake = "cleared"
                    selected = "replaced"
                    prepareNextCalls++
                },
                onFailure = { failureMessages++ },
            )
        }.startCoroutine(
            Continuation(EmptyCoroutineContext) { result -> completion = result },
        )

        assertEquals(1, deleteCalls)
        assertEquals(null, completion)
        terminal = true
        delayedDelete.complete()

        assertFalse(requireNotNull(completion).getOrThrow())
        assertEquals("saved", savedTake)
        assertEquals("selected", selected)
        assertEquals(0, prepareNextCalls)
        assertEquals(0, failureMessages)
    }

    private fun recordingFlow(): GuidedCaptureFlow = GuidedCaptureFlow().apply {
        confirmIntroduction()
        onGateChanged(true)
        repeat(3) { tick() }
        acceptPendingStart()
        assertEquals(GuidedCaptureStage.RECORDING, snapshot.stage)
    }

    private fun pendingFlow(): GuidedCaptureFlow = GuidedCaptureFlow().apply {
        confirmIntroduction()
        onGateChanged(true)
        repeat(3) { tick() }
        assertEquals(GuidedCaptureStage.START_PENDING, snapshot.stage)
    }

    private class CountingCaptureSession : CaptureSession {
        override val studyProtocol: CaptureStudyProtocol = CaptureStudyProtocol.guided22()
        override val uiState: StateFlow<CaptureUiState> = MutableStateFlow(CaptureUiState())
        override val studyRun: StudyRunIdentity = StudyRunIdentity(
            schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
            runId = "guided-flow-test",
            protocolId = studyProtocol.id,
            storageProfile = studyProtocol.storageProfile,
            createdAtIso = "2026-08-30T00:00:00Z",
        )
        var recordCalls: Int = 0

        override suspend fun record(
            scenario: CaptureScenario,
            storageProfile: CaptureStorageProfile,
        ): TakeOutcome {
            recordCalls++
            return TakeOutcome(scenario, 1, true, "ok")
        }

        override fun stop() = Unit

        override fun release() = Unit

        fun recordNow() {
            runImmediate { record(CaptureScenario.NEUTRAL, studyRun.storageProfile) }
        }
    }

    private class DelayedDelete {
        private var continuation: Continuation<Unit>? = null

        suspend fun awaitCompletion() {
            suspendCoroutine<Unit> { continuation = it }
        }

        fun complete() {
            requireNotNull(continuation).resume(Unit)
        }
    }

    private companion object {
        fun <T> runImmediate(block: suspend () -> T): T {
            var completed: Result<T>? = null
            block.startCoroutine(
                Continuation(EmptyCoroutineContext) { result -> completed = result },
            )
            return checkNotNull(completed) { "fake recorder unexpectedly suspended" }.getOrThrow()
        }
    }
}
