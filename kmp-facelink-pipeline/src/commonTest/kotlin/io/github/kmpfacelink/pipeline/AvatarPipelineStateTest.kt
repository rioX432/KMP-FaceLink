package io.github.kmpfacelink.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AvatarPipelineStateTest {

    @Test
    fun idleIsSingleton() {
        val a = AvatarPipelineState.Idle
        val b = AvatarPipelineState.Idle
        assertEquals(a, b)
    }

    @Test
    fun listeningIsSingleton() {
        val a = AvatarPipelineState.Listening
        val b = AvatarPipelineState.Listening
        assertEquals(a, b)
    }

    @Test
    fun processingIsSingleton() {
        val a = AvatarPipelineState.Processing
        val b = AvatarPipelineState.Processing
        assertEquals(a, b)
    }

    @Test
    fun speakingIsSingleton() {
        val a = AvatarPipelineState.Speaking
        val b = AvatarPipelineState.Speaking
        assertEquals(a, b)
    }

    @Test
    fun errorHoldsMessage() {
        val error = AvatarPipelineState.Error("something went wrong")
        assertEquals("something went wrong", error.message)
        assertNull(error.cause)
    }

    @Test
    fun errorHoldsMessageAndCause() {
        val cause = RuntimeException("root cause")
        val error = AvatarPipelineState.Error("failed", cause)
        assertEquals("failed", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun allStatesAreSubtypesOfAvatarPipelineState() {
        val states: List<AvatarPipelineState> = listOf(
            AvatarPipelineState.Idle,
            AvatarPipelineState.Listening,
            AvatarPipelineState.Processing,
            AvatarPipelineState.Speaking,
            AvatarPipelineState.Error("test"),
        )
        assertIs<AvatarPipelineState.Idle>(states[0])
        assertIs<AvatarPipelineState.Listening>(states[1])
        assertIs<AvatarPipelineState.Processing>(states[2])
        assertIs<AvatarPipelineState.Speaking>(states[3])
        assertIs<AvatarPipelineState.Error>(states[4])
    }
}
