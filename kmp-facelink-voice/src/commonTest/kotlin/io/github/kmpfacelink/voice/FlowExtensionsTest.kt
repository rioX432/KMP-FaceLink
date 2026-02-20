package io.github.kmpfacelink.voice

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.emptyBlendShapeData
import io.github.kmpfacelink.voice.lipsync.LipSyncFrame
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowExtensionsTest {

    private val baseFaceData = FaceTrackingData(
        blendShapes = emptyBlendShapeData().toMutableMap().apply {
            put(BlendShape.EYE_BLINK_LEFT, 0.8f)
            put(BlendShape.JAW_OPEN, 0.1f)
        },
        headTransform = HeadTransform(),
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun withLipSyncOverridesMouthShapes() = runTest {
        val lipSyncFrame = LipSyncFrame(
            blendShapes = mapOf(
                BlendShape.JAW_OPEN to 0.9f,
                BlendShape.MOUTH_FUNNEL to 0.5f,
            ),
            timestampMs = 0L,
        )

        val result = flowOf(baseFaceData)
            .withLipSync(flowOf(lipSyncFrame))
            .first()

        // JAW_OPEN should be overridden by lip sync
        assertEquals(0.9f, result.blendShapes[BlendShape.JAW_OPEN])
        // MOUTH_FUNNEL should be added
        assertEquals(0.5f, result.blendShapes[BlendShape.MOUTH_FUNNEL])
        // EYE_BLINK_LEFT should pass through unchanged
        assertEquals(0.8f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
    }

    @Test
    fun withLipSyncEmptyFramePassesThrough() = runTest {
        val emptyFrame = LipSyncFrame(blendShapes = emptyMap(), timestampMs = 0L)

        val result = flowOf(baseFaceData)
            .withLipSync(flowOf(emptyFrame))
            .first()

        // Original values should be unchanged
        assertEquals(0.1f, result.blendShapes[BlendShape.JAW_OPEN])
        assertEquals(0.8f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
    }

    @Test
    fun withLipSyncPreservesNonMouthShapes() = runTest {
        val lipSyncFrame = LipSyncFrame(
            blendShapes = mapOf(BlendShape.JAW_OPEN to 0.7f),
            timestampMs = 0L,
        )

        val result = flowOf(baseFaceData)
            .withLipSync(flowOf(lipSyncFrame))
            .first()

        // Eye tracking should be completely unaffected
        assertEquals(0.8f, result.blendShapes[BlendShape.EYE_BLINK_LEFT])
        assertTrue(result.isTracking)
    }

    @Test
    fun withLipSyncShapesWorks() = runTest {
        val shapes = mapOf(BlendShape.JAW_OPEN to 0.6f)

        val result = flowOf(baseFaceData)
            .withLipSyncShapes(flowOf(shapes))
            .first()

        assertEquals(0.6f, result.blendShapes[BlendShape.JAW_OPEN])
    }
}
