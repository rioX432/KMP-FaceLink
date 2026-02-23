package io.github.kmpfacelink.rive

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RiveRendererTest {

    @Test
    fun initialStateIsReady() {
        val renderer = TestRiveRenderer()
        // TestRiveRenderer initializes to READY for test convenience
        assertEquals(RiveRenderState.READY, renderer.state.value)
    }

    @Test
    fun loadModelTransitionsToReady() = runTest {
        val renderer = TestRiveRenderer()
        renderer.loadModel("test.riv", "StateMachine")
        assertEquals(RiveRenderState.READY, renderer.state.value)
    }

    @Test
    fun releaseTransitionsToReleased() {
        val renderer = TestRiveRenderer()
        renderer.release()
        assertEquals(RiveRenderState.RELEASED, renderer.state.value)
    }

    @Test
    fun riveInputTypesAreCorrect() {
        val number = RiveInput.Number(0.5f)
        val boolean = RiveInput.BooleanInput(true)
        val trigger = RiveInput.Trigger

        assertEquals(0.5f, number.value)
        assertEquals(true, boolean.value)
        assertEquals(RiveInput.Trigger, trigger)
    }

    @Test
    fun riveRenderStateValues() {
        val states = RiveRenderState.entries
        assertEquals(
            listOf("UNINITIALIZED", "LOADING", "READY", "ERROR", "RELEASED"),
            states.map { it.name },
        )
    }

    @Test
    fun updateInputsDispatchesCorrectly() {
        val renderer = TestRiveRenderer()
        val inputs = mapOf(
            "jawOpen" to RiveInput.Number(0.8f),
            "isSmiling" to RiveInput.BooleanInput(true),
            "blink" to RiveInput.Trigger,
        )

        renderer.updateInputs(inputs)

        assertEquals(1, renderer.updateCalls.size)
        assertEquals(inputs, renderer.updateCalls.first())
    }
}
