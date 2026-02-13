package io.github.kmpfacelink.effects

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
import kotlin.test.assertTrue

class FlowExtensionsTest {

    @Test
    fun faceFlowProducesEffectOutputs() = runTest {
        val engine = EffectEngine()
        val frame1 = faceFrame(TIMESTAMP_100)
        val frame2 = faceFrame(TIMESTAMP_200)

        val result = flowOf(frame1, frame2).feedEffects(engine).toList()
        assertEquals(2, result.size)
        assertEquals(TIMESTAMP_100, result[0].timestampMs)
        assertEquals(TIMESTAMP_200, result[1].timestampMs)
    }

    @Test
    fun handFlowProducesEffectOutputs() = runTest {
        val engine = EffectEngine()
        val frame1 = handFrame(TIMESTAMP_100)
        val frame2 = handFrame(TIMESTAMP_200)

        val result = flowOf(frame1, frame2).feedEffects(engine).toList()
        assertEquals(2, result.size)
        assertEquals(TIMESTAMP_100, result[0].timestampMs)
        assertEquals(TIMESTAMP_200, result[1].timestampMs)
    }

    @Test
    fun emptyEngineProducesEmptyOutputs() = runTest {
        val engine = EffectEngine()
        val result = flowOf(faceFrame(TIMESTAMP_100)).feedEffects(engine).toList()
        assertEquals(1, result.size)
        assertTrue(result[0].activeEffects.isEmpty())
        assertTrue(result[0].anchors.isEmpty())
        assertTrue(result[0].parameters.isEmpty())
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
