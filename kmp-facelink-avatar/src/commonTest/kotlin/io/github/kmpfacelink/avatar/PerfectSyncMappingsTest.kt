package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PerfectSyncMappingsTest {

    private val mapper = Live2DParameterMapper(PerfectSyncMappings.config())

    private fun trackingData(
        blendShapes: Map<BlendShape, Float> = emptyMap(),
    ) = FaceTrackingData(
        blendShapes = blendShapes,
        headTransform = HeadTransform(),
        timestampMs = 0L,
        isTracking = true,
    )

    @Test
    fun configProduces71Parameters() {
        val data = trackingData()
        val result = mapper.map(data)
        // 19 standard + 52 Perfect Sync = 71
        assertEquals(TOTAL_PARAM_COUNT, result.size)
    }

    @Test
    fun perfectSyncContainsAllBlendShapes() {
        val data = trackingData()
        val result = mapper.map(data)
        for (shape in BlendShape.entries) {
            assertTrue(
                shape.arKitName in result,
                "Missing Perfect Sync param: ${shape.arKitName}",
            )
        }
    }

    @Test
    fun perfectSyncDirectPassThrough() {
        val data = trackingData(
            blendShapes = mapOf(
                BlendShape.TONGUE_OUT to 0.75f,
                BlendShape.MOUTH_PUCKER to 0.4f,
                BlendShape.EYE_WIDE_LEFT to 0.9f,
            ),
        )
        val result = mapper.map(data)
        assertEquals(0.75f, result["tongueOut"]!!, TOLERANCE)
        assertEquals(0.4f, result["mouthPucker"]!!, TOLERANCE)
        assertEquals(0.9f, result["eyeWideLeft"]!!, TOLERANCE)
    }

    @Test
    fun standardMappingsPreserved() {
        val data = trackingData(
            blendShapes = mapOf(BlendShape.EYE_BLINK_LEFT to 0.5f),
        )
        val result = mapper.map(data)
        // Standard mapping still present
        assertTrue(Live2DParameterIds.PARAM_EYE_L_OPEN in result)
        // Perfect Sync also has it
        assertTrue("eyeBlinkLeft" in result)
    }

    @Test
    fun perfectSyncNoNameCollisionWithStandard() {
        val config = PerfectSyncMappings.config()
        val standardKeys = Live2DDefaultMappings.buildMappings().keys
        val perfectSyncKeys = config.customMappings.keys
        val overlap = standardKeys.intersect(perfectSyncKeys)
        assertTrue(
            overlap.isEmpty(),
            "Unexpected overlap between standard and Perfect Sync: $overlap",
        )
    }

    companion object {
        private const val TOLERANCE = 0.001f
        private const val STANDARD_PARAM_COUNT = 19
        private const val PERFECT_SYNC_PARAM_COUNT = 52
        private const val TOTAL_PARAM_COUNT = STANDARD_PARAM_COUNT + PERFECT_SYNC_PARAM_COUNT
    }
}
