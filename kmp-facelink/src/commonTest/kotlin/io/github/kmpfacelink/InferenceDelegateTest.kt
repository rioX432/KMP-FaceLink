package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.PerformanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val EXPECTED_ENTRY_COUNT = 5

class InferenceDelegateTest {

    @Test
    fun hasFiveEntries() {
        assertEquals(EXPECTED_ENTRY_COUNT, InferenceDelegate.entries.size)
    }

    @Test
    fun allValuesAccessible() {
        assertEquals(InferenceDelegate.AUTO, InferenceDelegate.valueOf("AUTO"))
        assertEquals(InferenceDelegate.CPU, InferenceDelegate.valueOf("CPU"))
        assertEquals(InferenceDelegate.GPU, InferenceDelegate.valueOf("GPU"))
        assertEquals(InferenceDelegate.NPU, InferenceDelegate.valueOf("NPU"))
        assertEquals(InferenceDelegate.NNAPI, InferenceDelegate.valueOf("NNAPI"))
    }

    @Test
    fun faceTrackerConfigDefaultsToAuto() {
        val config = FaceTrackerConfig()
        assertEquals(InferenceDelegate.AUTO, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigAcceptsGpu() {
        val config = FaceTrackerConfig(
            performanceConfig = PerformanceConfig(inferenceDelegate = InferenceDelegate.GPU),
        )
        assertEquals(InferenceDelegate.GPU, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigAcceptsCpu() {
        val config = FaceTrackerConfig(
            performanceConfig = PerformanceConfig(inferenceDelegate = InferenceDelegate.CPU),
        )
        assertEquals(InferenceDelegate.CPU, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigAcceptsNpu() {
        val config = FaceTrackerConfig(
            performanceConfig = PerformanceConfig(inferenceDelegate = InferenceDelegate.NPU),
        )
        assertEquals(InferenceDelegate.NPU, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigAcceptsNnapi() {
        @Suppress("DEPRECATION")
        val config = FaceTrackerConfig(
            performanceConfig = PerformanceConfig(inferenceDelegate = InferenceDelegate.NNAPI),
        )
        @Suppress("DEPRECATION")
        assertEquals(InferenceDelegate.NNAPI, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigPreservedOnCopy() {
        val original = FaceTrackerConfig(
            performanceConfig = PerformanceConfig(inferenceDelegate = InferenceDelegate.GPU),
        )
        val copy = original.copy(
            detectionConfig = original.detectionConfig.copy(maxFaces = 2),
        )
        assertEquals(InferenceDelegate.GPU, copy.inferenceDelegate)
        assertEquals(2, copy.maxFaces)
    }

    @Test
    fun handTrackerConfigDefaultsToAuto() {
        val config = HandTrackerConfig()
        assertEquals(InferenceDelegate.AUTO, config.inferenceDelegate)
    }

    @Test
    fun handTrackerConfigAcceptsAllDelegates() {
        for (delegate in InferenceDelegate.entries) {
            val config = HandTrackerConfig(inferenceDelegate = delegate)
            assertEquals(delegate, config.inferenceDelegate)
        }
    }

    @Test
    fun handTrackerConfigPreservedOnCopy() {
        val original = HandTrackerConfig(inferenceDelegate = InferenceDelegate.NPU)
        val copy = original.copy(maxHands = 1)
        assertEquals(InferenceDelegate.NPU, copy.inferenceDelegate)
    }

    @Test
    fun bodyTrackerConfigDefaultsToAuto() {
        val config = BodyTrackerConfig()
        assertEquals(InferenceDelegate.AUTO, config.inferenceDelegate)
    }

    @Test
    fun bodyTrackerConfigAcceptsAllDelegates() {
        for (delegate in InferenceDelegate.entries) {
            val config = BodyTrackerConfig(inferenceDelegate = delegate)
            assertEquals(delegate, config.inferenceDelegate)
        }
    }

    @Test
    fun bodyTrackerConfigPreservedOnCopy() {
        val original = BodyTrackerConfig(inferenceDelegate = InferenceDelegate.CPU)
        val copy = original.copy(maxBodies = 2)
        assertEquals(InferenceDelegate.CPU, copy.inferenceDelegate)
    }

    @Test
    fun holisticTrackerConfigDefaultsToAuto() {
        val config = HolisticTrackerConfig()
        assertEquals(InferenceDelegate.AUTO, config.inferenceDelegate)
    }

    @Test
    fun holisticTrackerConfigAcceptsAllDelegates() {
        for (delegate in InferenceDelegate.entries) {
            val config = HolisticTrackerConfig(inferenceDelegate = delegate)
            assertEquals(delegate, config.inferenceDelegate)
        }
    }

    @Test
    fun holisticTrackerConfigPreservedOnCopy() {
        val original = HolisticTrackerConfig(inferenceDelegate = InferenceDelegate.GPU)
        val copy = original.copy(enableBody = false)
        assertEquals(InferenceDelegate.GPU, copy.inferenceDelegate)
        assertTrue(!copy.enableBody)
    }
}
