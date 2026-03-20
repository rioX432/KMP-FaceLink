package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * Mandarin Chinese phoneme-to-viseme mappings.
 *
 * Covers Pinyin initials, finals, and IPA representations
 * commonly produced by Chinese TTS engines.
 */
internal object ChineseVisemeMap {

    val mappings: Map<String, Viseme> = buildMap {
        // Silence / pause
        put("sil", Viseme.SIL)
        put("", Viseme.SIL)
        put("pau", Viseme.SIL)

        // --- Pinyin Finals (vowels) ---
        put("a", Viseme.AA)
        put("o", Viseme.OH)
        put("e", Viseme.E)
        put("i", Viseme.IH)
        put("u", Viseme.OU)
        // ü (Pinyin)
        put("v", Viseme.OU)
        put("ü", Viseme.OU)

        // Compound finals
        put("ai", Viseme.AA)
        put("ei", Viseme.E)
        put("ao", Viseme.AA)
        put("ou", Viseme.OH)
        put("an", Viseme.AA)
        put("en", Viseme.E)
        put("ang", Viseme.AA)
        put("eng", Viseme.E)
        put("er", Viseme.E)
        put("ia", Viseme.AA)
        put("ie", Viseme.E)
        put("iu", Viseme.OU)
        put("in", Viseme.IH)
        put("ing", Viseme.IH)
        put("ian", Viseme.AA)
        put("iang", Viseme.AA)
        put("iong", Viseme.OH)
        put("ua", Viseme.AA)
        put("uo", Viseme.OH)
        put("ui", Viseme.OU)
        put("un", Viseme.OU)
        put("uan", Viseme.AA)
        put("uang", Viseme.AA)
        put("ong", Viseme.OH)

        // --- Pinyin Initials (consonants) ---
        // Bilabials
        put("b", Viseme.PP)
        put("p", Viseme.PP)
        put("m", Viseme.PP)

        // Labiodental
        put("f", Viseme.FF)

        // Alveolar
        put("d", Viseme.DD)
        put("t", Viseme.DD)
        put("n", Viseme.DD)
        put("l", Viseme.DD)

        // Velar
        put("g", Viseme.KK)
        put("k", Viseme.KK)

        // Aspirate
        put("h", Viseme.SIL)

        // Palatal (j, q, x)
        put("j", Viseme.CH)
        put("q", Viseme.CH)
        put("x", Viseme.CH)

        // Retroflex (zh, ch, sh, r)
        put("zh", Viseme.CH)
        put("ch", Viseme.CH)
        put("sh", Viseme.CH)
        put("r", Viseme.RR)

        // Alveolar sibilants (z, c, s)
        put("z", Viseme.SS)
        put("c", Viseme.SS)
        put("s", Viseme.SS)

        // Semi-vowel
        put("w", Viseme.RR)
        put("y", Viseme.IH)

        // IPA representations
        put("ɕ", Viseme.CH)
        put("tɕ", Viseme.CH)
        put("tɕʰ", Viseme.CH)
        put("ʂ", Viseme.CH)
        put("tʂ", Viseme.CH)
        put("tʂʰ", Viseme.CH)
        put("ɻ", Viseme.RR)
        put("ts", Viseme.SS)
        put("tsʰ", Viseme.SS)
        put("ŋ", Viseme.KK)

        // Glottal
        put("ʔ", Viseme.SIL)
    }
}
