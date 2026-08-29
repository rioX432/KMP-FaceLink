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
    RECORDING,
    SAVED,
    PAUSED,
    FAILED,
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
    val failure: String? = null,
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
    private var failure: String? = null
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
        check(stage in setOf(
            GuidedCaptureStage.SAVED,
            GuidedCaptureStage.ALIGNING,
            GuidedCaptureStage.COMPLETE,
        )) {
            "next preparation requires a saved or aligning flow"
        }
        stage = GuidedCaptureStage.ALIGNING
        remaining = null
        return snapshot
    }

    fun onFailed(message: String): GuidedCaptureSnapshot {
        require(message.isNotBlank()) { "failure message must not be blank" }
        stage = GuidedCaptureStage.FAILED
        remaining = null
        failure = message
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
        stage = GuidedCaptureStage.COMPLETE
        remaining = null
        return snapshot
    }

    private fun tickCountdown(): Pair<GuidedCaptureSnapshot, GuidedCaptureAction> {
        val next = requireNotNull(remaining) - 1
        return if (next <= 0) {
            remaining = null
            stage = GuidedCaptureStage.RECORDING
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
