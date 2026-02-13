package io.github.kmpfacelink.live2d

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowExtensionsTest {

    @Test
    fun driveRendererCallsUpdateParametersPerEmission() = runTest {
        val renderer = FakeRenderer()
        val params1 = mapOf("ParamAngleX" to 10f, "ParamEyeLOpen" to 0.8f)
        val params2 = mapOf("ParamAngleX" to -5f, "ParamEyeLOpen" to 1.0f)

        flowOf(params1, params2).driveRenderer(renderer)

        assertEquals(2, renderer.updateCalls.size)
        assertEquals(params1, renderer.updateCalls[0])
        assertEquals(params2, renderer.updateCalls[1])
    }

    @Test
    fun driveRendererHandlesEmptyFlow() = runTest {
        val renderer = FakeRenderer()

        flowOf<Map<String, Float>>().driveRenderer(renderer)

        assertEquals(0, renderer.updateCalls.size)
    }

    @Test
    fun driveRendererHandlesEmptyParameterMap() = runTest {
        val renderer = FakeRenderer()

        flowOf(emptyMap<String, Float>()).driveRenderer(renderer)

        assertEquals(1, renderer.updateCalls.size)
        assertEquals(emptyMap(), renderer.updateCalls[0])
    }

    private class FakeRenderer : Live2DRenderer {
        private val _state = MutableStateFlow(Live2DRenderState.READY)
        override val state: StateFlow<Live2DRenderState> = _state
        override val modelInfo: Live2DModelInfo? = null

        val updateCalls = mutableListOf<Map<String, Float>>()

        override suspend fun initialize(modelInfo: Live2DModelInfo) {
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
