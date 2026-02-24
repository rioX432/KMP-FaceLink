package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonaConfigTest {

    @Test
    fun defaultValues() {
        val config = PersonaConfig()
        assertEquals("", config.systemPrompt)
        assertEquals("Assistant", config.name)
        assertNull(config.language)
    }

    @Test
    fun customValues() {
        val config = PersonaConfig(
            systemPrompt = "You are a VTuber named Miku.",
            name = "Miku",
            language = "ja",
        )
        assertEquals("You are a VTuber named Miku.", config.systemPrompt)
        assertEquals("Miku", config.name)
        assertEquals("ja", config.language)
    }
}
