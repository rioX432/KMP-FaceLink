package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * English IPA phoneme-to-viseme mappings.
 *
 * Covers IPA symbols, common digraphs, and VOICEVOX uppercase vowels.
 */
internal object EnglishVisemeMap {

    val mappings: Map<String, Viseme> = buildMap {
        addSilenceMappings()
        addConsonantMappings()
        addVowelMappings()
        addMiscMappings()
    }
}

/** Silence and pause phonemes. */
private fun MutableMap<String, Viseme>.addSilenceMappings() {
    put("sil", Viseme.SIL)
    put("", Viseme.SIL)
    put("pau", Viseme.SIL)
}

/** Consonant phonemes: bilabial, labiodental, dental, alveolar, velar, palato-alveolar, fricative, nasal, approximant. */
private fun MutableMap<String, Viseme>.addConsonantMappings() {
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

    // RR — approximant (r, ɹ, w, ɾ)
    put("r", Viseme.RR)
    put("\u0279", Viseme.RR) // ɹ
    put("w", Viseme.RR)
    put("\u027E", Viseme.RR) // ɾ
}

/** Vowel phonemes: open, mid, close, and VOICEVOX uppercase variants. */
private fun MutableMap<String, Viseme>.addVowelMappings() {
    // AA — open vowels
    put("a", Viseme.AA)
    put("\u0251", Viseme.AA) // ɑ
    put("\u00E6", Viseme.AA) // æ
    put("\u028C", Viseme.AA) // ʌ
    put("A", Viseme.AA) // VOICEVOX

    // E — mid-front vowels
    put("e", Viseme.E)
    put("\u025B", Viseme.E) // ɛ
    put("E", Viseme.E) // VOICEVOX

    // IH — close-front vowels
    put("i", Viseme.IH)
    put("\u026A", Viseme.IH) // ɪ
    put("I", Viseme.IH) // VOICEVOX

    // OH — mid-back vowels
    put("o", Viseme.OH)
    put("\u0254", Viseme.OH) // ɔ
    put("O", Viseme.OH) // VOICEVOX

    // OU — close-back vowels
    put("u", Viseme.OU)
    put("\u028A", Viseme.OU) // ʊ
    put("U", Viseme.OU) // VOICEVOX
}

/** Common digraphs, reduced vowels, and other phonemes. */
private fun MutableMap<String, Viseme>.addMiscMappings() {
    put("\u0259", Viseme.E) // ə — schwa → E approximation
    put("h", Viseme.SIL) // aspirate → silence
    put("\u0294", Viseme.SIL) // ʔ — glottal stop
    put("y", Viseme.IH) // palatal approximant
}
