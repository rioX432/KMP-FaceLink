package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlowExtensionsTest {

    private val mapper = Live2DParameterMapper()

    private fun frame(isTracking: Boolean, jawOpen: Float = 0f) = FaceTrackingData(
        blendShapes = mapOf(BlendShape.JAW_OPEN to jawOpen),
        headTransform = HeadTransform(),
        timestampMs = 0L,
        isTracking = isTracking,
    )

    @Test
    fun trackingFramesAreMapped() = runTest {
        val flow = flowOf(frame(isTracking = true, jawOpen = 0.5f))
        val results = flow.toAvatarParameters(mapper).toList()
        assertEquals(1, results.size)
        assertEquals(0.7f, results[0][Live2DParameterIds.PARAM_MOUTH_OPEN_Y]!!, TOLERANCE)
    }

    @Test
    fun notTrackingFramesAreFiltered() = runTest {
        val flow = flowOf(
            frame(isTracking = false),
            frame(isTracking = true),
            frame(isTracking = false),
        )
        val results = flow.toAvatarParameters(mapper).toList()
        assertEquals(1, results.size)
    }

    @Test
    fun emptyFlowReturnsEmpty() = runTest {
        val flow = flowOf<FaceTrackingData>()
        val results = flow.toAvatarParameters(mapper).toList()
        assertTrue(results.isEmpty())
    }

    companion object {
        private const val TOLERANCE = 0.001f
    }
}
