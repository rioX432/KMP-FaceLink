package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.LipSyncLanguage
import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * Maps phoneme strings (IPA / VOICEVOX / Pinyin / character approximations) to [Viseme].
 *
 * Supports language-specific mappings via [LipSyncLanguage].
 */
internal object VisemeMapper {

    // English IPA → Viseme mapping (default / legacy)
    private val englishMap: Map<String, Viseme> = buildEnglishMap()

    private val languageMaps: Map<LipSyncLanguage, Map<String, Viseme>> = mapOf(
        LipSyncLanguage.ENGLISH to englishMap,
        LipSyncLanguage.JAPANESE to JapaneseVisemeMap.mappings,
        LipSyncLanguage.CHINESE to ChineseVisemeMap.mappings,
    )

    /**
     * Maps a phoneme string to a [Viseme] using the specified language's mapping.
     *
     * Tries exact match first, then case-insensitive, then falls back to [Viseme.SIL].
     */
    fun map(phoneme: String, language: LipSyncLanguage = LipSyncLanguage.ENGLISH): Viseme {
        val trimmed = phoneme.trim()
        val langMap = languageMaps[language] ?: englishMap
        return langMap[trimmed]
            ?: langMap[trimmed.lowercase()]
            ?: Viseme.SIL
    }
}

private fun buildEnglishMap(): Map<String, Viseme> = buildMap {
    // Silence
    put("sil", Viseme.SIL)
    put("", Viseme.SIL)
    put("pau", Viseme.SIL)

    // PP — bilabial (p, b, m)
    put("p", Viseme.PP)
    put("b", Viseme.PP)
    put("m", Viseme.PP)

    // FF — labiodental (f, v)
    put("f", Viseme.FF)
    put("v", Viseme.FF)

    // TH — dental fricative
    put("\u03B8", Viseme.TH) // θ
    put("\u00F0", Viseme.TH) // ð
    put("th", Viseme.TH)

    // DD — alveolar (t, d, n, l)
    put("t", Viseme.DD)
    put("d", Viseme.DD)
    put("n", Viseme.DD)
    put("l", Viseme.DD)

    // KK — velar (k, g, ŋ)
    put("k", Viseme.KK)
    put("g", Viseme.KK)
    put("\u014B", Viseme.KK) // ŋ

    // CH — palato-alveolar (tʃ, dʒ, ʃ, ʒ)
    put("t\u0283", Viseme.CH) // tʃ
    put("d\u0292", Viseme.CH) // dʒ
    put("\u0283", Viseme.CH) // ʃ
    put("\u0292", Viseme.CH) // ʒ
    put("ch", Viseme.CH)
    put("sh", Viseme.CH)
    put("j", Viseme.CH)

    // SS — alveolar fricative (s, z)
    put("s", Viseme.SS)
    put("z", Viseme.SS)

    // NN — nasal (Japanese ん)
    put("N", Viseme.NN)

    // RR — approximant (r, ɹ)
    put("r", Viseme.RR)
    put("\u0279", Viseme.RR) // ɹ
    put("w", Viseme.RR)
    put("\u027E", Viseme.RR) // ɾ

    // Vowels
    put("a", Viseme.AA)
    put("\u0251", Viseme.AA) // ɑ
    put("\u00E6", Viseme.AA) // æ
    put("\u028C", Viseme.AA) // ʌ
    put("A", Viseme.AA) // VOICEVOX

    put("e", Viseme.E)
    put("\u025B", Viseme.E) // ɛ
    put("E", Viseme.E) // VOICEVOX

    put("i", Viseme.IH)
    put("\u026A", Viseme.IH) // ɪ
    put("I", Viseme.IH) // VOICEVOX

    put("o", Viseme.OH)
    put("\u0254", Viseme.OH) // ɔ
    put("O", Viseme.OH) // VOICEVOX

    put("u", Viseme.OU)
    put("\u028A", Viseme.OU) // ʊ
    put("U", Viseme.OU) // VOICEVOX

    // Common digraphs / reduced
    put("\u0259", Viseme.E) // ə — schwa → E approximation
    put("h", Viseme.SIL) // aspirate → silence
    put("\u0294", Viseme.SIL) // ʔ — glottal stop
    put("y", Viseme.IH) // palatal approximant
}
