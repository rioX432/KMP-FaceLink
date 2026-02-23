package io.github.kmpfacelink.rive

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiveFlowExtensionsTest {

    private val trackingFrame = FaceTrackingData(
        blendShapes = mapOf(BlendShape.JAW_OPEN to 0.8f),
        headTransform = HeadTransform(),
        timestampMs = 100L,
        isTracking = true,
    )

    private val notTrackingFrame = FaceTrackingData.notTracking(timestampMs = 200L)

    private val simpleMapper = RiveParameterMapper { data ->
        if (!data.isTracking) {
            emptyMap()
        } else {
            mapOf("jawOpen" to RiveInput.Number(0.8f))
        }
    }

    @Test
    fun toRiveInputsFiltersNonTrackingFrames() = runTest {
        val flow = flowOf(trackingFrame, notTrackingFrame, trackingFrame)
        val results = flow.toRiveInputs(simpleMapper).toList()

        assertEquals(2, results.size)
        results.forEach { inputs ->
            assertTrue(inputs.containsKey("jawOpen"))
        }
    }

    @Test
    fun toRiveInputsEmptyWhenAllFramesNotTracking() = runTest {
        val flow = flowOf(notTrackingFrame, notTrackingFrame)
        val results = flow.toRiveInputs(simpleMapper).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun driveRiveRendererForwardsInputs() = runTest {
        val renderer = TestRiveRenderer()
        val inputs = mapOf(
            "jawOpen" to RiveInput.Number(0.8f),
            "eyeBlinkLeft" to RiveInput.Number(0.5f),
        )

        flowOf(inputs).driveRiveRenderer(renderer)

        assertEquals(1, renderer.updateCalls.size)
        assertEquals(inputs, renderer.updateCalls.first())
    }

    @Test
    fun driveRiveRendererHandlesMultipleFrames() = runTest {
        val renderer = TestRiveRenderer()
        val frame1 = mapOf("jawOpen" to RiveInput.Number(0.3f))
        val frame2 = mapOf("jawOpen" to RiveInput.Number(0.7f))

        flowOf(frame1, frame2).driveRiveRenderer(renderer)

        assertEquals(2, renderer.updateCalls.size)
    }
}

/**
 * Test double for [RiveRenderer] that records all calls.
 */
internal class TestRiveRenderer : RiveRenderer {

    private val _state = MutableStateFlow(RiveRenderState.READY)
    override val state: StateFlow<RiveRenderState> = _state

    val updateCalls = mutableListOf<Map<String, RiveInput>>()
    val numberInputs = mutableListOf<Pair<String, Float>>()
    val booleanInputs = mutableListOf<Pair<String, Boolean>>()
    val triggers = mutableListOf<String>()

    override suspend fun loadModel(assetPath: String, stateMachineName: String) {
        _state.value = RiveRenderState.READY
    }

    override fun setNumberInput(name: String, value: Float) {
        numberInputs.add(name to value)
    }

    override fun setBooleanInput(name: String, value: Boolean) {
        booleanInputs.add(name to value)
    }

    override fun fireTrigger(name: String) {
        triggers.add(name)
    }

    override fun updateInputs(inputs: Map<String, RiveInput>) {
        updateCalls.add(inputs)
    }

    override fun release() {
        _state.value = RiveRenderState.RELEASED
    }
}
