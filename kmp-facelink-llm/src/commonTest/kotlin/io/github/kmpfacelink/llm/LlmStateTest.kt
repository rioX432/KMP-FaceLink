package io.github.kmpfacelink.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for [LlmState] sealed class variants.
 */
class LlmStateTest {

    @Test
    fun idleIsSingleton() {
        val a = LlmState.Idle
        val b = LlmState.Idle
        assertEquals(a, b)
    }

    @Test
    fun streamingIsSingleton() {
        val a = LlmState.Streaming
        val b = LlmState.Streaming
        assertEquals(a, b)
    }

    @Test
    fun errorHoldsMessage() {
        val error = LlmState.Error("connection failed")
        assertEquals("connection failed", error.message)
    }

    @Test
    fun errorDataClassEquality() {
        val a = LlmState.Error("timeout")
        val b = LlmState.Error("timeout")
        assertEquals(a, b)
    }

    @Test
    fun allVariantsAreSubtypesOfLlmState() {
        val states: List<LlmState> = listOf(
            LlmState.Idle,
            LlmState.Streaming,
            LlmState.Error("test"),
        )
        assertIs<LlmState.Idle>(states[0])
        assertIs<LlmState.Streaming>(states[1])
        assertIs<LlmState.Error>(states[2])
    }
}
