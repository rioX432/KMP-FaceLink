package io.github.kmpfacelink.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract tests for the [AvatarPipeline] interface.
 *
 * Verifies the state machine transitions and behavioral contracts
 * that any conforming implementation must obey.
 */
class AvatarPipelineContractTest {

    private fun createPipeline(): FakeAvatarPipeline = FakeAvatarPipeline()

    @Test
    fun initialStateIsIdle() {
        val pipeline = createPipeline()
        assertIs<AvatarPipelineState.Idle>(pipeline.state.value)
    }

    @Test
    fun startSetsIdleState() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        assertIs<AvatarPipelineState.Idle>(pipeline.state.value)
        assertTrue(pipeline.started)
    }

    @Test
    fun processTextTransitionsToProcessing() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        pipeline.processText("Hello")
        assertIs<AvatarPipelineState.Processing>(pipeline.state.value)
    }

    @Test
    fun listenTransitionsToListening() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        pipeline.listen()
        assertIs<AvatarPipelineState.Listening>(pipeline.state.value)
    }

    @Test
    fun stopReturnsToIdle() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        pipeline.processText("Test")
        pipeline.stop()
        assertIs<AvatarPipelineState.Idle>(pipeline.state.value)
    }

    @Test
    fun releaseCleanUp() = runTest {
        val pipeline = createPipeline()
        pipeline.start()
        pipeline.release()
        assertTrue(pipeline.released)
    }

    @Test
    fun outputFlowEmitsFrames() = runTest {
        val pipeline = createPipeline()
        pipeline.start()

        val frames = mutableListOf<AvatarPipelineOutput>()
        val collectJob = launch { pipeline.output.collect { frames.add(it) } }
        yield() // Ensure collector is subscribed before emitting

        pipeline.emitOutput(
            AvatarPipelineOutput(
                blendShapes = emptyMap(),
                emotion = null,
                text = "test",
                isAudioPlaying = false,
                timestampMs = 100L,
            ),
        )
        yield() // Allow emission to propagate

        collectJob.cancel()
        assertTrue(frames.isNotEmpty(), "Should have received at least one frame")
        assertEquals("test", frames.first().text)
        assertEquals(100L, frames.first().timestampMs)
    }
}

private class FakeAvatarPipeline : AvatarPipeline {
    private val _state = MutableStateFlow<AvatarPipelineState>(AvatarPipelineState.Idle)
    private val _output = MutableSharedFlow<AvatarPipelineOutput>(extraBufferCapacity = 16)

    var started = false
        private set
    var released = false
        private set

    override val state: StateFlow<AvatarPipelineState> = _state.asStateFlow()
    override val output: Flow<AvatarPipelineOutput> = _output

    override suspend fun start() {
        started = true
        _state.value = AvatarPipelineState.Idle
    }

    override suspend fun stop() {
        _state.value = AvatarPipelineState.Idle
    }

    override suspend fun listen() {
        _state.value = AvatarPipelineState.Listening
    }

    override suspend fun processText(text: String) {
        _state.value = AvatarPipelineState.Processing
    }

    override fun release() {
        released = true
    }

    fun emitOutput(output: AvatarPipelineOutput) {
        _output.tryEmit(output)
    }
}
