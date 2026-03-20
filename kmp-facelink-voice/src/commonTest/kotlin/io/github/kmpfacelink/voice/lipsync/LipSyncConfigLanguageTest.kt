package io.github.kmpfacelink.voice.lipsync

import kotlin.test.Test
import kotlin.test.assertEquals

class LipSyncConfigLanguageTest {

    @Test
    fun defaultLanguageIsEnglish() {
        val config = LipSyncConfig()
        assertEquals(LipSyncLanguage.ENGLISH, config.language)
    }

    @Test
    fun languageCanBeSetToJapanese() {
        val config = LipSyncConfig(language = LipSyncLanguage.JAPANESE)
        assertEquals(LipSyncLanguage.JAPANESE, config.language)
    }

    @Test
    fun languageCanBeSetToChinese() {
        val config = LipSyncConfig(language = LipSyncLanguage.CHINESE)
        assertEquals(LipSyncLanguage.CHINESE, config.language)
    }

    @Test
    fun languageDoesNotAffectOtherDefaults() {
        val defaultConfig = LipSyncConfig()
        val japaneseConfig = LipSyncConfig(language = LipSyncLanguage.JAPANESE)
        assertEquals(defaultConfig.smoothing, japaneseConfig.smoothing)
        assertEquals(defaultConfig.amplitudeScale, japaneseConfig.amplitudeScale)
        assertEquals(defaultConfig.targetFps, japaneseConfig.targetFps)
    }

    @Test
    fun copyWithDifferentLanguage() {
        val config = LipSyncConfig(smoothing = 0.5f, language = LipSyncLanguage.ENGLISH)
        val chinese = config.copy(language = LipSyncLanguage.CHINESE)
        assertEquals(LipSyncLanguage.CHINESE, chinese.language)
        assertEquals(config.smoothing, chinese.smoothing)
    }
}
