package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FrameSkipConfig
import io.github.kmpfacelink.model.FrameSkipLevel
import io.github.kmpfacelink.util.ConfidenceFrameStrategy
import io.github.kmpfacelink.util.FrameDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfidenceFrameStrategyTest {

    private fun stableBlendShapes(value: Float = 0.5f): BlendShapeData =
        BlendShape.entries.associateWith { value }

    private fun slightlyDifferent(base: Float = 0.5f, delta: Float = 0.01f): BlendShapeData =
        BlendShape.entries.associateWith { base + delta }

    @Test
    fun disabledConfigAlwaysProcesses() {
        val strategy = ConfidenceFrameStrategy(FrameSkipConfig(enabled = false))
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun lowConfidenceForcesProcess() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        // Below BALANCED threshold (0.8)
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(0.5f))
    }

    @Test
    fun highConfidenceStableAllowsSkip() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        val data = stableBlendShapes()
        strategy.onFrameProcessed(data, 1.0f)
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.005f), 1.0f)

        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))
    }

    @Test
    fun maxConsecutiveSkipsForceProcess() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.CONSERVATIVE),
        )
        // CONSERVATIVE: maxConsecutiveSkips = 1
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.005f), 1.0f)

        // First skip is allowed
        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))
        // Second must process (max 1 consecutive skip)
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun largeMovementForcesProcess() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        // BALANCED movementThreshold = 0.025
        strategy.onFrameProcessed(stableBlendShapes(0.3f), 1.0f)
        strategy.onFrameProcessed(stableBlendShapes(0.5f), 1.0f) // delta 0.2 > 0.025

        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun aggressiveLevelAllowsMoreSkips() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.AGGRESSIVE),
        )
        // AGGRESSIVE: maxConsecutiveSkips = 3
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.005f), 1.0f)

        assertEquals(FrameDecision.SKIP, strategy.evaluate(0.8f))
        assertEquals(FrameDecision.SKIP, strategy.evaluate(0.8f))
        assertEquals(FrameDecision.SKIP, strategy.evaluate(0.8f))
        // 4th skip exceeds max
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(0.8f))
    }

    @Test
    fun noHistoryAlwaysProcesses() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun singleFrameHistoryProcesses() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        // Only one frame processed, no previous to compare
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun resetClearsState() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.005f), 1.0f)
        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))

        strategy.reset()
        // After reset, should process (no history)
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))
    }

    @Test
    fun processResetsSkipCounter() {
        val strategy = ConfidenceFrameStrategy(
            FrameSkipConfig(enabled = true, level = FrameSkipLevel.BALANCED),
        )
        // BALANCED: maxConsecutiveSkips = 2
        strategy.onFrameProcessed(stableBlendShapes(), 1.0f)
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.005f), 1.0f)

        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))
        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))
        // Hit max — force process
        assertEquals(FrameDecision.PROCESS, strategy.evaluate(1.0f))

        // After onFrameProcessed resets counter, can skip again
        strategy.onFrameProcessed(slightlyDifferent(delta = 0.003f), 1.0f)
        assertEquals(FrameDecision.SKIP, strategy.evaluate(1.0f))
    }
}
