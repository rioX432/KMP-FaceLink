package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * Japanese phoneme-to-viseme mappings.
 *
 * Covers VOICEVOX mora output (uppercase vowels A/I/U/E/O),
 * kana-derived consonants, and IPA transcriptions of Japanese phonemes.
 */
internal object JapaneseVisemeMap {

    val mappings: Map<String, Viseme> = buildMap {
        // Silence / pause
        put("sil", Viseme.SIL)
        put("", Viseme.SIL)
        put("pau", Viseme.SIL)

        // VOICEVOX uppercase vowels
        put("A", Viseme.AA)
        put("I", Viseme.IH)
        put("U", Viseme.OU)
        put("E", Viseme.E)
        put("O", Viseme.OH)

        // Lowercase vowels (IPA style)
        put("a", Viseme.AA)
        put("i", Viseme.IH)
        put("u", Viseme.OU)
        put("e", Viseme.E)
        put("o", Viseme.OH)

        // Japanese nasal (ん / N)
        put("N", Viseme.NN)
        put("n", Viseme.DD)

        // Bilabials (ぱ行, ば行, ま行)
        put("p", Viseme.PP)
        put("b", Viseme.PP)
        put("m", Viseme.PP)

        // Alveolar (た行, だ行)
        put("t", Viseme.DD)
        put("d", Viseme.DD)

        // Velar (か行, が行)
        put("k", Viseme.KK)
        put("g", Viseme.KK)

        // Fricatives (さ行, ざ行)
        put("s", Viseme.SS)
        put("z", Viseme.SS)

        // Palato-alveolar (し, ち, じ)
        put("sh", Viseme.CH)
        put("ch", Viseme.CH)
        put("j", Viseme.CH)
        put("ʃ", Viseme.CH)
        put("tʃ", Viseme.CH)
        put("dʒ", Viseme.CH)

        // Japanese flap (ら行)
        put("r", Viseme.RR)
        put("ɾ", Viseme.RR)

        // Semi-vowels (わ行, や行)
        put("w", Viseme.RR)
        put("y", Viseme.IH)

        // Japanese ふ (bilabial fricative, not labiodental)
        put("ɸ", Viseme.FF)
        put("f", Viseme.FF)

        // Aspirate / glottal
        put("h", Viseme.SIL)
        put("ʔ", Viseme.SIL)

        // Moraic nasal before consonants
        put("ŋ", Viseme.KK)

        // VOICEVOX consonant labels (cl = closure for geminate)
        put("cl", Viseme.SIL)
    }
}
