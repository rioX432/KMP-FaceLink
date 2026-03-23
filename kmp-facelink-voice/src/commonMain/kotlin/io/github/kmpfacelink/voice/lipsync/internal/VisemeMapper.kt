package io.github.kmpfacelink.voice.lipsync.internal

import io.github.kmpfacelink.voice.lipsync.LipSyncLanguage
import io.github.kmpfacelink.voice.lipsync.Viseme

/**
 * Maps phoneme strings (IPA / VOICEVOX / Pinyin / character approximations) to [Viseme].
 *
 * Supports language-specific mappings via [LipSyncLanguage].
 */
internal object VisemeMapper {

    private val languageMaps: Map<LipSyncLanguage, Map<String, Viseme>> = mapOf(
        LipSyncLanguage.ENGLISH to EnglishVisemeMap.mappings,
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
        val langMap = languageMaps[language] ?: EnglishVisemeMap.mappings
        return langMap[trimmed]
            ?: langMap[trimmed.lowercase()]
            ?: Viseme.SIL
    }
}
