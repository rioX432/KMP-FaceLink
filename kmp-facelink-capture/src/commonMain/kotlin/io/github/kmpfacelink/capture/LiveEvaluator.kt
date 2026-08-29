package io.github.kmpfacelink.capture

/** Live progress for the recording overlay, produced by the same evaluator as the final outcome. */
public sealed interface LiveProgress {
    public data object None : LiveProgress
    public data class Blink(val count: Int, val target: Int) : LiveProgress
    public data class Smile(val holding: Boolean, val holdSeconds: Int, val targetSeconds: Int, val neutral: Boolean) :
        LiveProgress

    public data class HeadTurn(val left: Boolean, val right: Boolean, val up: Boolean, val down: Boolean) : LiveProgress
}

/**
 * Single stateful soft-validation evaluator. It is fed one frame at a time during recording and is
 * the ONE source of truth: [progress] renders the live on-screen counter and [outcome] is the value
 * saved to meta.json — there is no separate end-of-take pass to drift from the live display.
 *
 * These are soft (non-blocking) UX checks; the authoritative offline metrics live in the reader.
 * Blink uses a running-max "open" baseline (streaming) rather than a whole-take percentile so the
 * live count can update in real time.
 */
public class LiveEvaluator(private val kind: SoftValidationKind) {

    // blink
    private var openBaseline = 0f
    private var blinkClosed = false
    private var framesBelow = 0
    private var blinkCount = 0

    // smile
    private val neutralMouth = ArrayList<Float>()
    private var smileBaseline = 0f
    private var smileHoldNs = 0L
    private var lastHoldTs = -1L
    private var holdingNow = false
    private var neutralPhase = false

    // head turn
    private var turnedLeft = false
    private var turnedRight = false
    private var turnedUp = false
    private var turnedDown = false

    @Suppress("LongParameterList")
    public fun onFrame(
        ear: Float,
        mouthRatio: Float,
        frontal: Boolean,
        neutralPhase: Boolean,
        yawSin: Float?,
        pitchSin: Float?,
        captureTimestampNs: Long,
    ) {
        this.neutralPhase = neutralPhase
        when (kind) {
            SoftValidationKind.BLINK_COUNT -> updateBlink(ear)
            SoftValidationKind.SMILE -> updateSmile(mouthRatio, frontal, neutralPhase, captureTimestampNs)
            SoftValidationKind.HEAD_TURN -> updateHeadTurn(yawSin, pitchSin)
            SoftValidationKind.TIME_ONLY -> Unit
        }
    }

    /**
     * Counts a blink on the down-crossing of [CLOSE_RATIO] and re-arms on a PARTIAL reopen
     * ([REARM_RATIO], not a full open). Real intentional blinks stay closed for hundreds of ms and
     * consecutive blinks often reopen only part-way; requiring a full reopen to re-arm merged them.
     * Closure duration is irrelevant (one dip = one blink); only a 2-frame floor guards noise.
     */
    private fun updateBlink(ear: Float) {
        if (ear <= 0f) return
        // Never let a closed-eye (low) frame lower the open baseline (running max, open frames only).
        if (!blinkClosed && ear > openBaseline) openBaseline = ear
        if (openBaseline <= 0f) return
        val closeThreshold = openBaseline * CLOSE_RATIO
        val rearmThreshold = openBaseline * REARM_RATIO
        when {
            blinkClosed && ear >= rearmThreshold -> {
                blinkClosed = false
                framesBelow = 0
            }
            blinkClosed -> Unit // still closed; already counted once, duration does not matter
            ear >= closeThreshold -> framesBelow = 0
            else -> {
                framesBelow++
                if (framesBelow >= MIN_DIP_FRAMES) {
                    blinkClosed = true
                    blinkCount++
                }
            }
        }
    }

    private fun updateSmile(mouthRatio: Float, frontal: Boolean, neutral: Boolean, captureTs: Long) {
        if (!frontal || mouthRatio <= 0f) {
            holdingNow = false
            lastHoldTs = -1L
            return
        }
        if (neutral) {
            neutralMouth.add(mouthRatio)
            smileBaseline = median(neutralMouth)
            holdingNow = false
            lastHoldTs = -1L
            return
        }
        if (smileBaseline <= 0f) return
        holdingNow = mouthRatio >= smileBaseline * SMILE_GAIN
        if (holdingNow) {
            if (lastHoldTs in 0 until captureTs) smileHoldNs += captureTs - lastHoldTs
            lastHoldTs = captureTs
        } else {
            lastHoldTs = -1L
        }
    }

    private fun updateHeadTurn(yawSin: Float?, pitchSin: Float?) {
        if (yawSin != null) {
            if (yawSin > TURN_SIN_THRESHOLD) turnedLeft = true
            if (yawSin < -TURN_SIN_THRESHOLD) turnedRight = true
        }
        if (pitchSin != null) {
            if (pitchSin > TURN_SIN_THRESHOLD) turnedUp = true
            if (pitchSin < -TURN_SIN_THRESHOLD) turnedDown = true
        }
    }

    public fun progress(): LiveProgress = when (kind) {
        SoftValidationKind.BLINK_COUNT -> LiveProgress.Blink(blinkCount, EXPECTED_BLINKS)
        SoftValidationKind.SMILE ->
            LiveProgress.Smile(holdingNow, holdSeconds(), SMILE_HOLD_TARGET_SEC, neutralPhase)
        SoftValidationKind.HEAD_TURN -> LiveProgress.HeadTurn(turnedLeft, turnedRight, turnedUp, turnedDown)
        SoftValidationKind.TIME_ONLY -> LiveProgress.None
    }

    /**
     * True once the scenario's soft goal is met, so the take can stop early with a pass.
     * TIME_ONLY scenarios have no goal (they always run their full duration).
     */
    public fun isGoalReached(): Boolean = when (kind) {
        SoftValidationKind.BLINK_COUNT -> blinkCount >= EXPECTED_BLINKS
        SoftValidationKind.SMILE -> neutralMouth.size >= MIN_BASELINE_FRAMES && holdSeconds() >= SMILE_HOLD_TARGET_SEC
        SoftValidationKind.HEAD_TURN -> turnedLeft && turnedRight && turnedUp && turnedDown
        SoftValidationKind.TIME_ONLY -> false
    }

    public fun outcome(): Outcome = when (kind) {
        SoftValidationKind.BLINK_COUNT ->
            Outcome(blinkCount >= EXPECTED_BLINKS, "blink_count=$blinkCount")

        SoftValidationKind.SMILE -> {
            val enough = neutralMouth.size >= MIN_BASELINE_FRAMES
            val held = holdSeconds() >= SMILE_HOLD_TARGET_SEC
            Outcome(enough && held, "smile_hold_sec=${holdSeconds()}")
        }

        SoftValidationKind.HEAD_TURN -> Outcome(
            turnedLeft && turnedRight && turnedUp && turnedDown,
            "turn_left=$turnedLeft turn_right=$turnedRight turn_up=$turnedUp turn_down=$turnedDown",
        )

        SoftValidationKind.TIME_ONLY -> Outcome(true, "time_only")
    }

    private fun holdSeconds(): Int = (smileHoldNs / NS_PER_SEC).toInt()

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    public data class Outcome(val passed: Boolean, val reason: String)

    private companion object {
        const val EXPECTED_BLINKS = 5
        const val MIN_DIP_FRAMES = 2
        const val CLOSE_RATIO = 0.55f // EAR below 55% of the open baseline = eye closing
        const val REARM_RATIO = 0.75f // partial reopen (75%) re-arms for the next blink
        const val SMILE_GAIN = 1.12f
        const val SMILE_HOLD_TARGET_SEC = 3
        const val MIN_BASELINE_FRAMES = 3
        const val TURN_SIN_THRESHOLD = 0.30f // ~sin(17deg)
        const val NS_PER_SEC = 1_000_000_000L
    }
}
