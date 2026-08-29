package io.github.kmpfacelink.capture.ui

import io.github.kmpfacelink.capture.CaptureScenario
import io.github.kmpfacelink.capture.TakeScript

/**
 * What the subject is told, for each scenario and each cue.
 *
 * Text lives here rather than on [CaptureScenario] so the scenario itself stays free of platform
 * string resources and can be driven identically from either OS. It is deliberately literal: the
 * wording is part of the protocol, and a paraphrase between sessions is a different instruction.
 */
public object CapturePrompts {

    /** The action itself, shown while the expression is meant to be held. */
    public fun action(scenario: CaptureScenario): String = when (scenario) {
        CaptureScenario.CHEEK_PUFF -> "Puff both cheeks"
        CaptureScenario.CHEEK_PUFF_LIGHT -> "Puff both cheeks — lightly"
        CaptureScenario.CHEEK_PUFF_MID -> "Puff both cheeks — medium"
        // Named from the subject's own body, never from the side of the screen. The preview is
        // mirrored, so the puffed cheek appears on the opposite side to the one being named — and
        // getting that backwards is what made two earlier recordings disagree about which cheek had
        // been puffed.
        CaptureScenario.CHEEK_PUFF_LEFT -> "Puff your own LEFT cheek only"
        CaptureScenario.CHEEK_PUFF_RIGHT -> "Puff your own RIGHT cheek only"
        CaptureScenario.PUCKER -> "Purse your lips, as for a kiss"
        CaptureScenario.BLOW -> "Blow out, as if blowing out a candle"
        CaptureScenario.CHEEK_PUFF_YAW_RIGHT -> "Turn your head right, then puff both cheeks"
        CaptureScenario.CHEEK_PUFF_YAW_LEFT -> "Turn your head left, then puff both cheeks"
        CaptureScenario.JAW_OPEN -> "Open your mouth wide"
        CaptureScenario.EYE_WIDEN -> "Open your eyes wide"
        CaptureScenario.NOSE_SNEER -> "Wrinkle your nose"
        CaptureScenario.TONGUE_OUT -> "Stick your tongue out"
        CaptureScenario.JAW_FORWARD -> "Push your jaw forward"
        CaptureScenario.CLENCH -> "Clench your teeth hard, lips closed"
        CaptureScenario.NEUTRAL -> "Rest your face"
        CaptureScenario.BLINK -> "Blink five times, deliberately"
        CaptureScenario.SMILE -> "Smile and hold it"
        CaptureScenario.SQUINT -> "Squint"
        CaptureScenario.SPEECH -> "Read anything aloud, steadily"
        CaptureScenario.HEAD_TURN -> "Turn left, right, up, down"
        CaptureScenario.BROW -> "Raise your eyebrows"
    }

    /** The scenario as it appears in a list. */
    public fun title(scenario: CaptureScenario): String = scenario.id
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    /** What to show for the cue the take is currently on. */
    public fun cue(scenario: CaptureScenario, cue: TakeScript.Cue): String = when (cue) {
        TakeScript.Cue.PREPARE -> "Hold still"
        TakeScript.Cue.NEUTRAL -> "Rest your face"
        TakeScript.Cue.HOLD -> action(scenario)
        TakeScript.Cue.RELEASE -> "Relax"
        TakeScript.Cue.DONE -> "Done"
    }


    /**
     * The same instruction in Japanese, shown above the English one.
     *
     * The subject reads this while holding the phone at arm's length, and an instruction they have
     * to translate is an instruction they act on late. The English line stays because it is what
     * earlier sessions were recorded against and what the protocol document quotes.
     */
    public fun actionJa(scenario: CaptureScenario): String = when (scenario) {
        CaptureScenario.CHEEK_PUFF -> "両方のほっぺを ふくらませる"
        CaptureScenario.CHEEK_PUFF_LIGHT -> "両方のほっぺを 軽く ふくらませる"
        CaptureScenario.CHEEK_PUFF_MID -> "両方のほっぺを 中くらいに ふくらませる"
        CaptureScenario.CHEEK_PUFF_LEFT -> "自分から見て 左 のほっぺだけ ふくらませる"
        CaptureScenario.CHEEK_PUFF_RIGHT -> "自分から見て 右 のほっぺだけ ふくらませる"
        CaptureScenario.PUCKER -> "くちびるを すぼめる"
        CaptureScenario.BLOW -> "ろうそくを 吹き消すように 息を吹く"
        CaptureScenario.CHEEK_PUFF_YAW_RIGHT -> "顔を 右 に向けてから ほっぺをふくらませる"
        CaptureScenario.CHEEK_PUFF_YAW_LEFT -> "顔を 左 に向けてから ほっぺをふくらませる"
        CaptureScenario.JAW_OPEN -> "口を 大きく開ける"
        CaptureScenario.EYE_WIDEN -> "目を 大きく開く"
        CaptureScenario.NOSE_SNEER -> "鼻に しわを寄せる"
        CaptureScenario.TONGUE_OUT -> "舌を 出す"
        CaptureScenario.JAW_FORWARD -> "あごを 前に出す"
        CaptureScenario.CLENCH -> "口を閉じたまま 歯を強く 食いしばる"
        CaptureScenario.NEUTRAL -> "顔の力を抜く"
        CaptureScenario.BLINK -> "はっきりと 5回 まばたき"
        CaptureScenario.SMILE -> "笑顔にして そのまま保つ"
        CaptureScenario.SQUINT -> "目を細める"
        CaptureScenario.SPEECH -> "何でもよいので 声に出して読む"
        CaptureScenario.HEAD_TURN -> "左・右・上・下 に顔を向ける"
        CaptureScenario.BROW -> "眉を 上げ下げする"
    }

    /** The cue in Japanese. */
    public fun cueJa(scenario: CaptureScenario, cue: TakeScript.Cue): String = when (cue) {
        TakeScript.Cue.PREPARE -> "そのまま 動かないで"
        TakeScript.Cue.NEUTRAL -> "顔の力を抜く"
        TakeScript.Cue.HOLD -> actionJa(scenario)
        TakeScript.Cue.RELEASE -> "もとに もどす"
        TakeScript.Cue.DONE -> "おわり"
    }

    /**
     * What is coming next, so the subject can start forming the expression before it is scored.
     *
     * The take's timing is pre-registered and cannot move, so the only way to stop transition
     * frames landing inside a scored span is to give the subject a run-up into it.
     */
    public fun nextCueJa(scenario: CaptureScenario, next: TakeScript.Cue?): String? = when (next) {
        null -> null
        // Every line says what is coming and that it has not started. Moving early is worse than
        // moving late: the span before the first hold is what the analysis calibrates against, so
        // an expression that begins during it is measured as if it were this subject's rest face.
        TakeScript.Cue.HOLD -> "つぎ: " + actionJa(scenario) + "（合図まで動かない）"
        TakeScript.Cue.RELEASE -> "つぎ: もとに もどす（合図まで そのまま）"
        TakeScript.Cue.NEUTRAL -> "つぎ: 顔の力を抜く（合図まで そのまま）"
        TakeScript.Cue.DONE -> "まもなく おわり"
        TakeScript.Cue.PREPARE -> null
    }

    /**
     * Why the take is waiting, when it is.
     *
     * The gate holds the take until distance, framing and light are in band. Telling the subject
     * which one is failing is what makes that a two-second correction rather than a mystery.
     */
    public fun gateHint(
        hasFace: Boolean,
        centered: Boolean,
        sized: Boolean,
        bright: Boolean,
        distanced: Boolean,
    ): String = when {
        !hasFace -> "No face detected"
        !centered -> "Move to the centre of the frame"
        !distanced || !sized -> "Adjust your distance"
        !bright -> "Too dark or too bright"
        else -> "Ready"
    }
}
