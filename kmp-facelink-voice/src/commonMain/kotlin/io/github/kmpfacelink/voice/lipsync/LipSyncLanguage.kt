package io.github.kmpfacelink.voice.lipsync

/**
 * Language for lip sync phoneme-to-viseme mapping.
 *
 * Different languages produce different phoneme sets from TTS engines.
 * Each language has optimized phoneme-to-viseme mappings for accurate mouth animation.
 */
public enum class LipSyncLanguage {
    /** English (IPA phonemes). Default language. */
    ENGLISH,

    /** Japanese (VOICEVOX mora / kana phonemes). */
    JAPANESE,

    /** Mandarin Chinese (Pinyin initials/finals). */
    CHINESE,
}
