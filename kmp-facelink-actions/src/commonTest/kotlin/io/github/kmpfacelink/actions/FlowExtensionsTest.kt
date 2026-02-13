package io.github.kmpfacelink.actions

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowExtensionsTest {

    @Test
    fun feedActionsFacePassesThroughData() = runTest {
        val system = ActionSystem()
        val frame1 = faceFrame(TIMESTAMP_100)
        val frame2 = faceFrame(TIMESTAMP_200)

        val result = flowOf(frame1, frame2).feedActions(system).toList()
        assertEquals(2, result.size)
        assertEquals(frame1, result[0])
        assertEquals(frame2, result[1])
    }

    @Test
    fun feedActionsHandPassesThroughData() = runTest {
        val system = ActionSystem()
        val frame1 = handFrame(TIMESTAMP_100)
        val frame2 = handFrame(TIMESTAMP_200)

        val result = flowOf(frame1, frame2).feedActions(system).toList()
        assertEquals(2, result.size)
        assertEquals(frame1, result[0])
        assertEquals(frame2, result[1])
    }

    // --- Helpers ---

    private fun faceFrame(
        timestampMs: Long,
        vararg blendShapes: Pair<BlendShape, Float>,
    ): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(),
            timestampMs = timestampMs,
            isTracking = true,
        )
    }

    private fun handFrame(
        timestampMs: Long,
        gesture: HandGesture = HandGesture.NONE,
    ): HandTrackingData = HandTrackingData(
        hands = if (gesture == HandGesture.NONE) {
            emptyList()
        } else {
            listOf(
                TrackedHand(
                    handedness = Handedness.RIGHT,
                    landmarks = emptyList(),
                    gesture = gesture,
                    gestureConfidence = DEFAULT_CONFIDENCE,
                ),
            )
        },
        timestampMs = timestampMs,
        isTracking = true,
    )

    companion object {
        private const val TIMESTAMP_100 = 100L
        private const val TIMESTAMP_200 = 200L
        private const val DEFAULT_CONFIDENCE = 0.9f
    }
}
