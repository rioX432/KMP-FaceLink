package io.github.kmpfacelink.llm

/**
 * Configuration for a character persona (system prompt and personality).
 *
 * @property systemPrompt The system instruction sent at the beginning of conversations
 * @property name Display name of the assistant character
 * @property language Optional language hint (e.g. "ja", "en")
 */
public data class PersonaConfig(
    val systemPrompt: String = "",
    val name: String = "Assistant",
    val language: String? = null,
)
