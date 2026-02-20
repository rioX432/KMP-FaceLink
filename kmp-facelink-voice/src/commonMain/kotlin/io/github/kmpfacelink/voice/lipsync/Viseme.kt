package io.github.kmpfacelink.voice.lipsync

/**
 * Oculus-standard viseme set (15 visemes).
 *
 * Used as intermediate representation between phonemes and blend shapes.
 * Each viseme maps to a specific mouth shape configuration.
 *
 * @see <a href="https://developer.oculus.com/documentation/unity/audio-ovrlipsync-viseme-reference/">Oculus Viseme Reference</a>
 */
public enum class Viseme {
    /** Silence — neutral mouth position. */
    SIL,

    /** Bilabial plosive (p, b, m). */
    PP,

    /** Labiodental fricative (f, v). */
    FF,

    /** Dental fricative (th). */
    TH,

    /** Alveolar plosive/nasal (t, d, n, l). */
    DD,

    /** Velar plosive (k, g). */
    KK,

    /** Palato-alveolar affricate (ch, j, sh). */
    CH,

    /** Alveolar fricative (s, z). */
    SS,

    /** Alveolar nasal (n, ng). */
    NN,

    /** Alveolar approximant (r). */
    RR,

    /** Open vowel (a, ah). */
    AA,

    /** Mid front vowel (e, eh). */
    E,

    /** Close front vowel (i, ih). */
    IH,

    /** Mid back vowel (o, oh). */
    OH,

    /** Close back vowel (u, oo). */
    OU,
}
