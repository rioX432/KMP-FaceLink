package io.github.kmpfacelink.voice.lipsync

import io.github.kmpfacelink.voice.lipsync.internal.VisemeMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageVisemeMapperTest {

    // --- English mappings (default behavior, backward compatible) ---

    @Test
    fun englishDefaultBehavior() {
        assertEquals(Viseme.AA, VisemeMapper.map("a", LipSyncLanguage.ENGLISH))
        assertEquals(Viseme.PP, VisemeMapper.map("p", LipSyncLanguage.ENGLISH))
        assertEquals(Viseme.TH, VisemeMapper.map("th", LipSyncLanguage.ENGLISH))
    }

    @Test
    fun englishDefaultLanguageParameter() {
        // No language parameter should default to English
        assertEquals(Viseme.AA, VisemeMapper.map("a"))
        assertEquals(Viseme.PP, VisemeMapper.map("p"))
    }

    @Test
    fun englishDentalFricatives() {
        assertEquals(Viseme.TH, VisemeMapper.map("\u03B8", LipSyncLanguage.ENGLISH))
        assertEquals(Viseme.TH, VisemeMapper.map("\u00F0", LipSyncLanguage.ENGLISH))
    }

    // --- Japanese mappings ---

    @Test
    fun japaneseVoicevoxVowels() {
        assertEquals(Viseme.AA, VisemeMapper.map("A", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.IH, VisemeMapper.map("I", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.OU, VisemeMapper.map("U", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.E, VisemeMapper.map("E", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.OH, VisemeMapper.map("O", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseLowercaseVowels() {
        assertEquals(Viseme.AA, VisemeMapper.map("a", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.IH, VisemeMapper.map("i", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.OU, VisemeMapper.map("u", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseMoraicNasal() {
        assertEquals(Viseme.NN, VisemeMapper.map("N", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseConsonants() {
        assertEquals(Viseme.PP, VisemeMapper.map("p", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.PP, VisemeMapper.map("b", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.PP, VisemeMapper.map("m", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.KK, VisemeMapper.map("k", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.SS, VisemeMapper.map("s", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.CH, VisemeMapper.map("sh", LipSyncLanguage.JAPANESE))
        assertEquals(Viseme.RR, VisemeMapper.map("r", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseFlap() {
        assertEquals(Viseme.RR, VisemeMapper.map("\u027E", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseBilabialFricative() {
        assertEquals(Viseme.FF, VisemeMapper.map("\u0278", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japaneseGeminateClosure() {
        assertEquals(Viseme.SIL, VisemeMapper.map("cl", LipSyncLanguage.JAPANESE))
    }

    @Test
    fun japanesePause() {
        assertEquals(Viseme.SIL, VisemeMapper.map("pau", LipSyncLanguage.JAPANESE))
    }

    // --- Chinese mappings ---

    @Test
    fun chineseVowels() {
        assertEquals(Viseme.AA, VisemeMapper.map("a", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.OH, VisemeMapper.map("o", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.E, VisemeMapper.map("e", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.IH, VisemeMapper.map("i", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.OU, VisemeMapper.map("u", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chinesePinyinUmlaut() {
        assertEquals(Viseme.OU, VisemeMapper.map("v", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.OU, VisemeMapper.map("\u00FC", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseCompoundFinals() {
        assertEquals(Viseme.AA, VisemeMapper.map("ai", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.E, VisemeMapper.map("ei", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.AA, VisemeMapper.map("ao", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.OH, VisemeMapper.map("ou", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.OH, VisemeMapper.map("ong", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseBilabials() {
        assertEquals(Viseme.PP, VisemeMapper.map("b", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.PP, VisemeMapper.map("p", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.PP, VisemeMapper.map("m", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseRetroflexes() {
        assertEquals(Viseme.CH, VisemeMapper.map("zh", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.CH, VisemeMapper.map("ch", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.CH, VisemeMapper.map("sh", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.RR, VisemeMapper.map("r", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chinesePalatals() {
        assertEquals(Viseme.CH, VisemeMapper.map("j", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.CH, VisemeMapper.map("q", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.CH, VisemeMapper.map("x", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseAlveolarSibilants() {
        assertEquals(Viseme.SS, VisemeMapper.map("z", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.SS, VisemeMapper.map("c", LipSyncLanguage.CHINESE))
        assertEquals(Viseme.SS, VisemeMapper.map("s", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseLabiodental() {
        assertEquals(Viseme.FF, VisemeMapper.map("f", LipSyncLanguage.CHINESE))
    }

    @Test
    fun chineseUnknownFallsToSilence() {
        assertEquals(Viseme.SIL, VisemeMapper.map("xyz", LipSyncLanguage.CHINESE))
    }

    // --- Cross-language behavior ---

    @Test
    fun unknownPhonemeIsSilenceForAllLanguages() {
        for (lang in LipSyncLanguage.entries) {
            assertEquals(
                Viseme.SIL,
                VisemeMapper.map("zzzzz_unknown", lang),
                "Unknown phoneme should be SIL for $lang",
            )
        }
    }

    @Test
    fun silenceIsConsistentAcrossLanguages() {
        for (lang in LipSyncLanguage.entries) {
            assertEquals(Viseme.SIL, VisemeMapper.map("sil", lang), "sil for $lang")
            assertEquals(Viseme.SIL, VisemeMapper.map("", lang), "empty for $lang")
            assertEquals(Viseme.SIL, VisemeMapper.map("pau", lang), "pau for $lang")
        }
    }

    @Test
    fun whitespaceIsTrimmedForAllLanguages() {
        for (lang in LipSyncLanguage.entries) {
            assertEquals(
                VisemeMapper.map("a", lang),
                VisemeMapper.map(" a ", lang),
                "Whitespace should be trimmed for $lang",
            )
        }
    }
}
