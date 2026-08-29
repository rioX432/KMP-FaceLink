package io.github.kmpfacelink.capture

/**
 * The order one take's writes have to arrive in, decided as each one arrives.
 *
 * **A reader can find these too, but afterwards is too late: the performance cannot be acted
 * again.** A take whose sequence repeats or goes backwards cannot be joined to the other stream,
 * and a take whose clocks go backwards cannot be replayed at the cadence it was recorded at. Both
 * are cheap to refuse at the microphone and impossible to repair at the desk.
 *
 * Common rather than per-platform because it is a property of the take format, not of a
 * filesystem: the two sinks on each platform have to agree about what a well-formed take is, or
 * the same recording session yields data that only one of them would have accepted.
 *
 * O(1) and allocation-free on the accepting path. It runs on the analyzer and inference callback
 * threads for every frame and every result, and the pipeline it guards is the thing being measured.
 *
 * Not thread-safe on its own: each sink already serializes admission under its own lock, and a
 * second lock here would only add a hand-off on the hot path.
 *
 * @property laterClock which second clock this stream carries, so a refusal names it
 */
internal class TakeOrderGuard(private val laterClock: LaterClock) {

    /**
     * The clock a stream carries beside capture time.
     *
     * The two streams keep different ones — a frame records the stamp it handed the engine, a
     * result records when it came back — and a refusal that did not say which was meant would send
     * the operator looking at the wrong one.
     */
    enum class LaterClock(val what: String, val unit: String, val why: String) {
        /** The stamp handed to the inference engine, which a VIDEO-mode replay has to reuse. */
        INFERENCE_SUBMISSION("inference time", "ms", "a VIDEO-mode replay reuses these"),

        /** When a result came back; ordered independently from the camera timestamp clock. */
        RESULT_ARRIVAL(
            "result arrival time",
            "ns",
            "it orders callbacks but is not a latency origin unless clock equivalence is proved",
        ),
    }

    private var lastSeq = -1
    private var lastCaptureNs = -1L
    private var lastLater = -1L
    private var pastPreRoll = false

    /**
     * Takes one write into the take, or says why it cannot be part of it.
     *
     * Gaps in [seq] are allowed. The streams are joined by sequence rather than by position, and a
     * sink cannot tell a frame the analyzer never admitted from one the engine declined to run.
     * What cannot happen is the same sequence twice, or a lower one after a higher one: both make
     * the join key ambiguous, and the reader would silently pair a result with another frame's
     * pixels.
     *
     * Phases are deliberately not monotonic. A take is neutral, hold, release, hold, release, so
     * [CapturePhase.ACTED] back to [CapturePhase.NEUTRAL] is the script rather than an error — see
     * [TakeScript]. What cannot happen is pre-roll resuming once the take proper has started, since
     * pre-roll exists only to rebuild tracking state ahead of the first scored write.
     *
     * @return null when the write belongs to the take, otherwise why it does not and what that
     *   means for the take. State is left untouched when it is refused.
     */
    @Suppress("ReturnCount") // each refusal is a different fault and keeps its own message
    fun admit(seq: Int, captureTimestampNs: Long, laterTimestamp: Long, phase: CapturePhase): String? {
        if (captureTimestampNs < 0) {
            return "capture time ${captureTimestampNs}ns is negative; it comes from the camera's " +
                "monotonic clock and cannot be"
        }
        if (laterTimestamp < 0) {
            return "${laterClock.what} $laterTimestamp${laterClock.unit} is negative; " +
                "${laterClock.why}"
        }
        // A negative sequence lands here too, because the take starts at -1: it is not the
        // analyzer's counter, so it is not a sequence this take can carry.
        if (seq <= lastSeq) {
            val how = if (seq == lastSeq) "repeated $seq" else "went from $lastSeq to $seq"
            return "sequence $how; sequences are the join key between the frames and the results " +
                "and have to increase"
        }
        if (captureTimestampNs < lastCaptureNs) {
            return "capture time went backwards, ${lastCaptureNs}ns to ${captureTimestampNs}ns"
        }
        if (laterTimestamp < lastLater) {
            return "${laterClock.what} went backwards, $lastLater${laterClock.unit} to " +
                "$laterTimestamp${laterClock.unit}; ${laterClock.why}"
        }
        if (phase == CapturePhase.PRE_ROLL && pastPreRoll) {
            return "pre-roll cannot resume once the take has started; it exists to rebuild " +
                "tracking state before the first scored frame"
        }
        lastSeq = seq
        lastCaptureNs = captureTimestampNs
        lastLater = laterTimestamp
        if (phase != CapturePhase.PRE_ROLL) pastPreRoll = true
        return null
    }
}
