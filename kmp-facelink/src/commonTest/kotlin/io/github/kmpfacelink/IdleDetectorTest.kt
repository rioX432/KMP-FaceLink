package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.util.IdleDetector
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdleDetectorTest {

    private fun createBlendShapes(jawOpen: Float = 0f): BlendShapeData =
        BlendShape.entries.associateWith { shape ->
            if (shape == BlendShape.JAW_OPEN) jawOpen else 0f
        }

    @Test
    fun firstFrameIsNeverIdle() {
        val detector = IdleDetector(idleDurationMs = 100L)
        assertFalse(detector.update(createBlendShapes(), 0L))
    }

    @Test
    fun movementResetsIdleTimer() {
        val detector = IdleDetector(idleThreshold = 0.01f, idleDurationMs = 100L)
        detector.update(createBlendShapes(jawOpen = 0.0f), 0L)
        detector.update(createBlendShapes(jawOpen = 0.0f), 50L)
        // Large movement
        detector.update(createBlendShapes(jawOpen = 0.5f), 100L)
        // Should not be idle because we just moved
        assertFalse(detector.update(createBlendShapes(jawOpen = 0.5f), 150L))
    }

    @Test
    fun becomesIdleAfterDuration() {
        val detector = IdleDetector(idleThreshold = 0.05f, idleDurationMs = 100L)
        detector.update(createBlendShapes(jawOpen = 0.1f), 0L)
        // Same values — no movement
        assertFalse(detector.update(createBlendShapes(jawOpen = 0.1f), 50L))
        assertFalse(detector.update(createBlendShapes(jawOpen = 0.1f), 99L))
        // After 100ms of no movement
        assertTrue(detector.update(createBlendShapes(jawOpen = 0.1f), 100L))
    }

    @Test
    fun smallMovementBelowThresholdStaysIdle() {
        val detector = IdleDetector(idleThreshold = 0.05f, idleDurationMs = 100L)
        detector.update(createBlendShapes(jawOpen = 0.1f), 0L)
        detector.update(createBlendShapes(jawOpen = 0.1f), 100L)
        assertTrue(detector.isIdle)
        // Small movement within threshold
        assertTrue(detector.update(createBlendShapes(jawOpen = 0.13f), 150L))
    }

    @Test
    fun resetClearsState() {
        val detector = IdleDetector(idleThreshold = 0.05f, idleDurationMs = 100L)
        detector.update(createBlendShapes(jawOpen = 0.1f), 0L)
        detector.update(createBlendShapes(jawOpen = 0.1f), 200L)
        assertTrue(detector.isIdle)

        detector.reset()
        assertFalse(detector.isIdle)
    }

    @Test
    fun computeMaxDeltaFindsLargestDifference() {
        val a = BlendShape.entries.associateWith { 0f }
        val b = BlendShape.entries.associateWith { shape ->
            if (shape == BlendShape.MOUTH_SMILE_LEFT) 0.7f else 0f
        }
        val delta = IdleDetector.computeMaxDelta(a, b)
        assertTrue(delta > 0.69f && delta < 0.71f)
    }
}
