package io.github.kmpfacelink.capture

/**
 * Cap and neutral-phase length shared by the cheek-puff study scenarios.
 *
 * Every span is four seconds long. Only the hold spans are scored, so the neutral and release
 * lengths cost nothing in analysis and buy the subject time to settle and to breathe out between
 * repetitions — a rushed release showed up as an unfinished expression at the next hold.
 */
private const val STUDY_MAX_SECONDS = 20
private const val STUDY_NEUTRAL_SECONDS = 4

/**
 * The takes a capture session walks through.
 *
 * [id] is the stable, language-independent value written to the session metadata and read by the
 * analysis. Display text is deliberately absent: it belongs to the UI layer, which has the
 * platform's string resources. Keeping it out is what lets this enum live in common code and be
 * driven identically from Android and iOS.
 *
 * [maxSeconds] caps the recording. [neutralPromptSeconds] > 0 splits the take into an initial
 * neutral phase and then the acted phase — the evaluation baselines every clip against its own
 * neutral, so a scenario without one cannot be scored.
 */
public enum class CaptureScenario(
    public val id: String,
    public val maxSeconds: Int,
    public val softValidation: SoftValidationKind,
    public val neutralPromptSeconds: Int = 0,
) {
    NEUTRAL("neutral_10s", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    BLINK("blink_x5", 30, SoftValidationKind.BLINK_COUNT),
    SMILE("smile_hold_10s", STUDY_MAX_SECONDS, SoftValidationKind.SMILE, STUDY_NEUTRAL_SECONDS),
    SQUINT("squint", 8, SoftValidationKind.TIME_ONLY),
    SPEECH("speech", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    HEAD_TURN("head_turn", 30, SoftValidationKind.HEAD_TURN),
    BROW("brow", 8, SoftValidationKind.TIME_ONLY),

    // Cheek-puff study protocol. pucker and blow are the hard negatives: without them there is no
    // false-positive rate, and a detection rate on its own says nothing.
    CHEEK_PUFF("cheek_puff", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    CHEEK_PUFF_LIGHT("cheek_puff_light", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    CHEEK_PUFF_MID("cheek_puff_mid", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),

    // Left and right are the subject's own, not the side of the screen. The preview is mirrored,
    // so the puffed cheek appears opposite the side being named. Getting this backwards is what
    // made two earlier recordings disagree about which cheek had been puffed.
    CHEEK_PUFF_LEFT("cheek_puff_left", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    CHEEK_PUFF_RIGHT("cheek_puff_right", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),

    PUCKER("pucker", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    BLOW("blow", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    CHEEK_PUFF_YAW_RIGHT(
        "cheek_puff_yaw_right",
        STUDY_MAX_SECONDS,
        SoftValidationKind.TIME_ONLY,
        STUDY_NEUTRAL_SECONDS,
    ),
    CHEEK_PUFF_YAW_LEFT("cheek_puff_yaw_left", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    JAW_OPEN("jaw_open", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    EYE_WIDEN("eye_widen", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    NOSE_SNEER("nose_sneer", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    TONGUE_OUT("tongue_out", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    JAW_FORWARD("jaw_forward", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),

    // Clenching with the lips closed raises the masseter, which can widen the jaw-height contour —
    // the quantity the geometric rule keys on. It is not in the pre-registered negative list, so it
    // is recorded as an extra and cannot count towards the registered judgement; it is here because
    // the literature names it as a confound and finding one's own is worth more than being told.
    CLENCH("clench", STUDY_MAX_SECONDS, SoftValidationKind.TIME_ONLY, STUDY_NEUTRAL_SECONDS),
    ;

    /** Unscripted countable scenarios may end once their goal is met. */
    public val goalDriven: Boolean
        get() = neutralPromptSeconds == 0 && softValidation != SoftValidationKind.TIME_ONLY

    public companion object {
        public fun byId(id: String): CaptureScenario? = entries.firstOrNull { it.id == id }
    }
}
