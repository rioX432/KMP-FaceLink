package io.github.kmpfacelink.live2d

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Live2DRendererTest {

    private val modelInfo = Live2DModelInfo(
        modelId = "test-model",
        name = "Test",
        modelPath = "test.model3.json",
    )

    @Test
    fun initialStateIsUninitialized() {
        val renderer = TestableRenderer()
        assertEquals(Live2DRenderState.UNINITIALIZED, renderer.state.value)
        assertNull(renderer.modelInfo)
    }

    @Test
    fun initializeTransitionsToReady() = runTest {
        val renderer = TestableRenderer()
        renderer.initialize(modelInfo)

        assertEquals(Live2DRenderState.READY, renderer.state.value)
        assertEquals(modelInfo, renderer.modelInfo)
    }

    @Test
    fun updateParametersRecordsValues() = runTest {
        val renderer = TestableRenderer()
        renderer.initialize(modelInfo)

        val params = mapOf("ParamAngleX" to 15f, "ParamEyeLOpen" to 0.9f)
        renderer.updateParameters(params)

        assertEquals(1, renderer.updateCalls.size)
        assertEquals(params, renderer.updateCalls[0])
    }

    @Test
    fun updateParametersWithEmptyMap() = runTest {
        val renderer = TestableRenderer()
        renderer.initialize(modelInfo)

        renderer.updateParameters(emptyMap())

        assertEquals(1, renderer.updateCalls.size)
        assertTrue(renderer.updateCalls[0].isEmpty())
    }

    @Test
    fun multipleUpdatesAccumulate() = runTest {
        val renderer = TestableRenderer()
        renderer.initialize(modelInfo)

        repeat(5) { i ->
            renderer.updateParameters(mapOf("ParamAngleX" to i.toFloat()))
        }

        assertEquals(5, renderer.updateCalls.size)
    }

    @Test
    fun releaseTransitionsToReleased() = runTest {
        val renderer = TestableRenderer()
        renderer.initialize(modelInfo)
        renderer.release()

        assertEquals(Live2DRenderState.RELEASED, renderer.state.value)
    }

    @Test
    fun releaseFromUninitializedState() {
        val renderer = TestableRenderer()
        renderer.release()

        assertEquals(Live2DRenderState.RELEASED, renderer.state.value)
    }

    @Test
    fun fullLifecycle() = runTest {
        val renderer = TestableRenderer()

        assertEquals(Live2DRenderState.UNINITIALIZED, renderer.state.value)

        renderer.initialize(modelInfo)
        assertEquals(Live2DRenderState.READY, renderer.state.value)

        renderer.updateParameters(mapOf("ParamAngleX" to 10f))
        assertEquals(1, renderer.updateCalls.size)

        renderer.release()
        assertEquals(Live2DRenderState.RELEASED, renderer.state.value)
    }

    @Test
    fun renderStateEnumValues() {
        val states = Live2DRenderState.entries
        assertEquals(5, states.size)
        assertTrue(states.contains(Live2DRenderState.UNINITIALIZED))
        assertTrue(states.contains(Live2DRenderState.READY))
        assertTrue(states.contains(Live2DRenderState.RENDERING))
        assertTrue(states.contains(Live2DRenderState.ERROR))
        assertTrue(states.contains(Live2DRenderState.RELEASED))
    }

    private class TestableRenderer : Live2DRenderer {
        private val _state = MutableStateFlow(Live2DRenderState.UNINITIALIZED)
        override val state: StateFlow<Live2DRenderState> = _state
        private var _modelInfo: Live2DModelInfo? = null
        override val modelInfo: Live2DModelInfo? get() = _modelInfo

        val updateCalls = mutableListOf<Map<String, Float>>()

        override suspend fun initialize(modelInfo: Live2DModelInfo) {
            _modelInfo = modelInfo
            _state.value = Live2DRenderState.READY
        }

        override fun updateParameters(parameters: Map<String, Float>) {
            updateCalls.add(parameters)
        }

        override fun release() {
            _state.value = Live2DRenderState.RELEASED
        }
    }
}
