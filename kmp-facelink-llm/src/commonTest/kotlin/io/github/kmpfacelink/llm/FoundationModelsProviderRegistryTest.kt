package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FoundationModelsProviderRegistryTest {

    @AfterTest
    fun tearDown() {
        FoundationModelsProviderRegistry.clear()
    }

    @Test
    fun initiallyEmpty() {
        assertNull(FoundationModelsProviderRegistry.get())
        assertFalse(FoundationModelsProviderRegistry.isAvailable())
    }

    @Test
    fun registerAndGet() {
        val provider = StubProvider(available = true)
        FoundationModelsProviderRegistry.register(provider)
        assertNotNull(FoundationModelsProviderRegistry.get())
        assertTrue(FoundationModelsProviderRegistry.isAvailable())
    }

    @Test
    fun isAvailableReflectsProviderState() {
        val provider = StubProvider(available = false)
        FoundationModelsProviderRegistry.register(provider)
        assertFalse(FoundationModelsProviderRegistry.isAvailable())
    }

    @Test
    fun clearRemovesProvider() {
        FoundationModelsProviderRegistry.register(StubProvider(available = true))
        FoundationModelsProviderRegistry.clear()
        assertNull(FoundationModelsProviderRegistry.get())
    }

    private class StubProvider(private val available: Boolean) : FoundationModelsProvider {
        override fun isAvailable(): Boolean = available

        override suspend fun respond(
            prompt: String,
            instructions: String,
            maxTokens: Int,
            temperature: Double,
        ): String = ""

        override fun streamResponse(
            prompt: String,
            instructions: String,
            maxTokens: Int,
            temperature: Double,
        ): Flow<String> = emptyFlow()
    }
}
