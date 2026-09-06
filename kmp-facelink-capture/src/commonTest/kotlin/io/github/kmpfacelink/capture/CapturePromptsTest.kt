package io.github.kmpfacelink.capture

import io.github.kmpfacelink.capture.ui.CapturePrompts
import io.github.kmpfacelink.capture.ui.studyTakeSupplementJa
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the visible Japanese cues because their exact wording is part of the protocol. */
class CapturePromptsTest {
    @Test
    fun currentCuesRemainExact() {
        val scenario = CaptureScenario.CHEEK_PUFF
        assertEquals("そのまま 動かないで", CapturePrompts.cueJa(scenario, TakeScript.Cue.PREPARE))
        assertEquals("顔の力を抜く", CapturePrompts.cueJa(scenario, TakeScript.Cue.NEUTRAL))
        assertEquals("両方のほっぺを ふくらませる", CapturePrompts.cueJa(scenario, TakeScript.Cue.HOLD))
        assertEquals("もとに もどす", CapturePrompts.cueJa(scenario, TakeScript.Cue.RELEASE))
        assertEquals("おわり", CapturePrompts.cueJa(scenario, TakeScript.Cue.DONE))
    }

    @Test
    fun advanceWarningsRemainExactAndForbidEarlyMovement() {
        val scenario = CaptureScenario.CHEEK_PUFF
        assertEquals(
            "つぎ: 両方のほっぺを ふくらませる（合図まで動かない）",
            CapturePrompts.nextCueJa(scenario, TakeScript.Cue.HOLD),
        )
        assertEquals(
            "つぎ: もとに もどす（合図まで そのまま）",
            CapturePrompts.nextCueJa(scenario, TakeScript.Cue.RELEASE),
        )
        assertEquals(
            "つぎ: 顔の力を抜く（合図まで そのまま）",
            CapturePrompts.nextCueJa(scenario, TakeScript.Cue.NEUTRAL),
        )
        assertEquals("まもなく おわり", CapturePrompts.nextCueJa(scenario, TakeScript.Cue.DONE))
    }

    @Test
    fun everyStudyActionRemainsRegistered() {
        val expected = mapOf(
            CaptureScenario.NEUTRAL to "顔の力を抜く",
            CaptureScenario.CHEEK_PUFF_LIGHT to "両方のほっぺを 軽く ふくらませる",
            CaptureScenario.CHEEK_PUFF_MID to "両方のほっぺを 中くらいに ふくらませる",
            CaptureScenario.CHEEK_PUFF to "両方のほっぺを ふくらませる",
            CaptureScenario.CHEEK_PUFF_LEFT to "自分から見て 左 のほっぺだけ ふくらませる",
            CaptureScenario.CHEEK_PUFF_RIGHT to "自分から見て 右 のほっぺだけ ふくらませる",
            CaptureScenario.PUCKER to "くちびるを すぼめる",
            CaptureScenario.BLOW to "ろうそくを 吹き消すように 息を吹く",
            CaptureScenario.CHEEK_PUFF_YAW_RIGHT to "顔を 右 に向けてから ほっぺをふくらませる",
            CaptureScenario.CHEEK_PUFF_YAW_LEFT to "顔を 左 に向けてから ほっぺをふくらませる",
            CaptureScenario.JAW_OPEN to "口を 大きく開ける",
            CaptureScenario.SMILE to "笑顔にして そのまま保つ",
            CaptureScenario.SPEECH to "何でもよいので 声に出して読む",
            CaptureScenario.EYE_WIDEN to "目を 大きく開く",
            CaptureScenario.NOSE_SNEER to "鼻に しわを寄せる",
            CaptureScenario.TONGUE_OUT to "舌を 出す",
            CaptureScenario.JAW_FORWARD to "あごを 前に出す",
        )
        expected.forEach { (scenario, prompt) -> assertEquals(prompt, CapturePrompts.actionJa(scenario)) }
    }

    @Test
    fun blowAttemptsAddTheirRegisteredStyleWithoutChangingTheExistingCue() {
        val held = StudyTakeSlot(CaptureScenario.BLOW, 1, StudyTakeGroup.PROTOCOL)
        val natural = StudyTakeSlot(CaptureScenario.BLOW, 2, StudyTakeGroup.PROTOCOL)

        assertEquals("ろうそくを 吹き消すように 息を吹く", CapturePrompts.actionJa(CaptureScenario.BLOW))
        assertEquals("今回: ろうそくを吹き続けるように 息を吹き続ける", studyTakeSupplementJa(held))
        assertEquals("今回: 「フッ」と短く 2〜3回 息を吹く", studyTakeSupplementJa(natural))
    }
}
