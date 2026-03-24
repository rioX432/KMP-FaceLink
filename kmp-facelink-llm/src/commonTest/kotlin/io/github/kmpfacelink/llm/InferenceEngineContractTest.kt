package io.github.kmpfacelink.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract tests for the [InferenceEngine] interface.
 *
 * Uses a fake implementation to verify load/generate/unload lifecycle contracts.
 */
class InferenceEngineContractTest {

    private fun createEngine(): FakeInferenceEngine = FakeInferenceEngine()

    @Test
    fun initiallyNotLoaded() {
        val engine = createEngine()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun loadMakesEngineReady() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        assertTrue(engine.isLoaded)
    }

    @Test
    fun loadIsIdempotent() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        engine.load("/path/to/model.gguf")
        assertTrue(engine.isLoaded)
        assertEquals(1, engine.loadCount)
    }

    @Test
    fun generateProducesTokens() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        val tokens = engine.generate("Hello", maxTokens = 10, temperature = 0.7).toList()
        assertTrue(tokens.isNotEmpty())
    }

    @Test
    fun unloadReleasesModel() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        engine.unload()
        assertFalse(engine.isLoaded)
    }

    @Test
    fun loadAfterUnloadRequiresReload() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        engine.unload()
        assertFalse(engine.isLoaded)
        engine.load("/path/to/model.gguf")
        assertTrue(engine.isLoaded)
        assertEquals(2, engine.loadCount)
    }

    @Test
    fun generateRespectsMaxTokens() = runTest {
        val engine = createEngine()
        engine.load("/path/to/model.gguf")
        val tokens = engine.generate("prompt", maxTokens = 3, temperature = 0.5).toList()
        assertEquals(3, tokens.size)
    }
}

private class FakeInferenceEngine : InferenceEngine {
    private var loaded = false

    var loadCount = 0
        private set

    override val isLoaded: Boolean get() = loaded

    override suspend fun load(modelPath: String) {
        if (loaded) return
        loadCount++
        loaded = true
    }

    override fun generate(prompt: String, maxTokens: Int, temperature: Double): Flow<String> = flow {
        for (i in 1..maxTokens) {
            emit("token$i")
        }
    }

    override suspend fun unload() {
        loaded = false
    }
}
