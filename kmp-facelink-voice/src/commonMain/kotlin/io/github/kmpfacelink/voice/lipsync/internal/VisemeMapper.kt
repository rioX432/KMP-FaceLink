package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * Maps phoneme strings (IPA / VOICEVOX / character approximations) to [Viseme].
 */
internal object VisemeMapper {

    // IPA → Viseme mapping
    private val ipaMap: Map<String, Viseme> = buildMap {
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
        put("θ", Viseme.TH)
        put("ð", Viseme.TH)
        put("th", Viseme.TH)

        // DD — alveolar (t, d, n, l)
        put("t", Viseme.DD)
        put("d", Viseme.DD)
        put("n", Viseme.DD)
        put("l", Viseme.DD)

        // KK — velar (k, g, ŋ)
        put("k", Viseme.KK)
        put("g", Viseme.KK)
        put("ŋ", Viseme.KK)

        // CH — palato-alveolar (tʃ, dʒ, ʃ, ʒ)
        put("tʃ", Viseme.CH)
        put("dʒ", Viseme.CH)
        put("ʃ", Viseme.CH)
        put("ʒ", Viseme.CH)
        put("ch", Viseme.CH)
        put("sh", Viseme.CH)
        put("j", Viseme.CH)

        // SS — alveolar fricative (s, z)
        put("s", Viseme.SS)
        put("z", Viseme.SS)

        // NN — nasal (n, ŋ) — n is in DD; ŋ in KK; this adds Japanese ん
        put("N", Viseme.NN)

        // RR — approximant (r, ɹ)
        put("r", Viseme.RR)
        put("ɹ", Viseme.RR)
        put("w", Viseme.RR)
        put("ɾ", Viseme.RR)

        // Vowels
        put("a", Viseme.AA)
        put("ɑ", Viseme.AA)
        put("æ", Viseme.AA)
        put("ʌ", Viseme.AA)
        put("A", Viseme.AA) // VOICEVOX

        put("e", Viseme.E)
        put("ɛ", Viseme.E)
        put("E", Viseme.E) // VOICEVOX

        put("i", Viseme.IH)
        put("ɪ", Viseme.IH)
        put("I", Viseme.IH) // VOICEVOX

        put("o", Viseme.OH)
        put("ɔ", Viseme.OH)
        put("O", Viseme.OH) // VOICEVOX

        put("u", Viseme.OU)
        put("ʊ", Viseme.OU)
        put("U", Viseme.OU) // VOICEVOX

        // Common digraphs / reduced
        put("ə", Viseme.E) // schwa → E approximation
        put("h", Viseme.SIL) // aspirate → silence
        put("ʔ", Viseme.SIL) // glottal stop
        put("y", Viseme.IH) // palatal approximant
    }

    /**
     * Maps a phoneme string to a [Viseme].
     *
     * Tries exact match first, then case-insensitive, then falls back to [Viseme.SIL].
     */
    fun map(phoneme: String): Viseme {
        val trimmed = phoneme.trim()
        return ipaMap[trimmed]
            ?: ipaMap[trimmed.lowercase()]
            ?: Viseme.SIL
    }
}
