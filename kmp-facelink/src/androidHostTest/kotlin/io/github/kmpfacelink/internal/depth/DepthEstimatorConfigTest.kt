package io.github.kmpfacelink.internal.depth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DepthEstimatorConfigTest {

    @Test
    fun defaultsTargetDepthAnythingV2Small() {
        val config = DepthEstimatorConfig()
        assertEquals("models/depth_anything_v2_small.tflite", config.modelAssetPath)
        assertEquals(518, config.inputSize)
        assertEquals(DepthDelegate.GPU, config.delegate)
        assertEquals(DepthNormalization.IMAGENET, config.normalization)
    }

    @Test
    fun rejectsNonPositiveInputSize() {
        assertFailsWith<IllegalArgumentException> { DepthEstimatorConfig(inputSize = 0) }
    }

    @Test
    fun rejectsNonPositiveCpuThreads() {
        assertFailsWith<IllegalArgumentException> { DepthEstimatorConfig(cpuThreads = 0) }
    }
}
