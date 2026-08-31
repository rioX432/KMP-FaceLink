package io.github.kmpfacelink.capture

/** Timing-only settings for the participant-facing guided workflow. */
internal data class GuidedCaptureSettings(
    val gateCountdownSeconds: Int = 3,
    val savedConfirmationSeconds: Int = 2,
) {
    init {
        require(gateCountdownSeconds > 0) { "gateCountdownSeconds must be positive" }
        require(savedConfirmationSeconds > 0) { "savedConfirmationSeconds must be positive" }
    }
}

/** Participant-visible workflow stages; estimator values are deliberately absent. */
internal enum class GuidedCaptureStage {
    INTRO,
    ALIGNING,
    COUNTDOWN,
    START_PENDING,
    RECORDING,
    SAVED,
    PAUSED,
    FAILED,
    TERMINATED,
    COMPLETE,
}

internal sealed interface GuidedCaptureAction {
    data object None : GuidedCaptureAction
    data object StartTake : GuidedCaptureAction
    data object Advance : GuidedCaptureAction
}

internal data class GuidedCaptureSnapshot(
    val stage: GuidedCaptureStage,
    val secondsRemaining: Int? = null,
    val failure: CaptureFailurePresentation? = null,
)

/**
 * Deterministic guided-flow state machine shared by Android and iOS UI.
 *
 * Only framing-gate state and terminal recorder outcomes enter this machine. Blendshapes,
 * landmarks, soft-validation scores, and live expression progress cannot influence start,
 * advance, retry, or pause decisions through this API.
 */
internal class GuidedCaptureFlow(
    private val settings: GuidedCaptureSettings = GuidedCaptureSettings(),
) {
    private var stage = GuidedCaptureStage.INTRO
    private var remaining: Int? = null
    private var failure: CaptureFailurePresentation? = null
    private var resumeStage = GuidedCaptureStage.ALIGNING

    val snapshot: GuidedCaptureSnapshot
        get() = GuidedCaptureSnapshot(stage, remaining, failure)

    fun confirmIntroduction(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.INTRO) { "introduction was already confirmed" }
        stage = GuidedCaptureStage.ALIGNING
        return snapshot
    }

    fun onGateChanged(passed: Boolean): GuidedCaptureSnapshot {
        if (stage !in setOf(GuidedCaptureStage.ALIGNING, GuidedCaptureStage.COUNTDOWN)) return snapshot
        if (!passed) {
            stage = GuidedCaptureStage.ALIGNING
            remaining = null
        } else if (stage == GuidedCaptureStage.ALIGNING) {
            stage = GuidedCaptureStage.COUNTDOWN
            remaining = settings.gateCountdownSeconds
        }
        return snapshot
    }

    /** Commits a countdown start only after the UI has accepted the recorder request. */
    fun acceptPendingStart(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.START_PENDING) { "start acceptance requires a pending start" }
        stage = GuidedCaptureStage.RECORDING
        return snapshot
    }

    /** Returns a rejected or gate-lost pending start to a fresh alignment cycle. */
    fun rejectPendingStart(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.START_PENDING) { "start rejection requires a pending start" }
        stage = GuidedCaptureStage.ALIGNING
        remaining = null
        return snapshot
    }

    /** Advances one visible second and emits a one-shot side-effect request. */
    fun tick(): Pair<GuidedCaptureSnapshot, GuidedCaptureAction> = when (stage) {
        GuidedCaptureStage.COUNTDOWN -> tickCountdown()
        GuidedCaptureStage.SAVED -> tickSavedConfirmation()
        else -> snapshot to GuidedCaptureAction.None
    }

    fun onSaved(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.RECORDING) { "save requires an active recording" }
        stage = GuidedCaptureStage.SAVED
        remaining = settings.savedConfirmationSeconds
        return snapshot
    }

    fun prepareNext(): GuidedCaptureSnapshot {
        check(
            stage in setOf(
                GuidedCaptureStage.SAVED,
                GuidedCaptureStage.ALIGNING,
                GuidedCaptureStage.COMPLETE,
            ),
        ) {
            "next preparation requires a saved or aligning flow"
        }
        stage = GuidedCaptureStage.ALIGNING
        remaining = null
        return snapshot
    }

    fun onFailed(presentation: CaptureFailurePresentation): GuidedCaptureSnapshot {
        require(presentation.participantMessageJa.isNotBlank()) { "failure message must not be blank" }
        // STOP_RUN is monotonic. A delayed retryable recorder outcome must never restore a retry
        // action after the frame source or another non-retryable failure terminated the run.
        if (stage == GuidedCaptureStage.TERMINATED) return snapshot
        stage = when (presentation.primaryAction) {
            CaptureFailurePrimaryAction.RETRY_TAKE -> GuidedCaptureStage.FAILED
            CaptureFailurePrimaryAction.STOP_RUN -> GuidedCaptureStage.TERMINATED
        }
        remaining = null
        failure = presentation
        return snapshot
    }

    fun retry(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.FAILED) { "retry requires a failed take" }
        stage = GuidedCaptureStage.ALIGNING
        failure = null
        return snapshot
    }

    fun pause(): GuidedCaptureSnapshot {
        check(stage in PAUSABLE_STAGES) { "cannot pause during $stage" }
        resumeStage = GuidedCaptureStage.ALIGNING
        stage = GuidedCaptureStage.PAUSED
        remaining = null
        return snapshot
    }

    fun resume(): GuidedCaptureSnapshot {
        check(stage == GuidedCaptureStage.PAUSED) { "resume requires a paused flow" }
        stage = resumeStage
        return snapshot
    }

    fun complete(): GuidedCaptureSnapshot {
        check(stage != GuidedCaptureStage.TERMINATED) { "a terminated run cannot complete" }
        stage = GuidedCaptureStage.COMPLETE
        remaining = null
        return snapshot
    }

    private fun tickCountdown(): Pair<GuidedCaptureSnapshot, GuidedCaptureAction> {
        val next = requireNotNull(remaining) - 1
        return if (next <= 0) {
            remaining = null
            stage = GuidedCaptureStage.START_PENDING
            snapshot to GuidedCaptureAction.StartTake
        } else {
            remaining = next
            snapshot to GuidedCaptureAction.None
        }
    }

    private fun tickSavedConfirmation(): Pair<GuidedCaptureSnapshot, GuidedCaptureAction> {
        val next = requireNotNull(remaining) - 1
        return if (next <= 0) {
            remaining = null
            stage = GuidedCaptureStage.ALIGNING
            snapshot to GuidedCaptureAction.Advance
        } else {
            remaining = next
            snapshot to GuidedCaptureAction.None
        }
    }

    private companion object {
        val PAUSABLE_STAGES = setOf(
            GuidedCaptureStage.ALIGNING,
            GuidedCaptureStage.COUNTDOWN,
            GuidedCaptureStage.SAVED,
        )
    }
}

/**
 * Arbitrates the only path from a pending countdown action to a recorder invocation.
 *
 * Keeping this boundary synchronous makes `busy` and the accepted stage visible before the
 * supplied callback can launch asynchronous recorder work. Calls arriving after terminal are
 * ignored, while an ordinary gate loss explicitly returns to alignment.
 */
@Suppress("LongParameterList") // Keep each start invariant and side effect explicit at this boundary.
internal fun startPendingTakeIfEligible(
    flow: GuidedCaptureFlow,
    slotAvailable: Boolean,
    busy: Boolean,
    gatePassed: Boolean,
    onAccepted: (GuidedCaptureSnapshot) -> Unit,
    startRecorder: () -> Unit,
): Boolean {
    if (flow.snapshot.stage != GuidedCaptureStage.START_PENDING) return false
    if (!slotAvailable || busy || !gatePassed) {
        flow.rejectPendingStart()
        return false
    }
    onAccepted(flow.acceptPendingStart())
    startRecorder()
    return true
}
