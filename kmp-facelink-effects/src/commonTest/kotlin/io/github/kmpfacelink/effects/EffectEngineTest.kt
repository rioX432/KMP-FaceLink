package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandGesture
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.Handedness
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.TrackedHand
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectEngineTest {

    // --- Registration ---

    @Test
    fun duplicateEffectIdThrows() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        assertFailsWith<IllegalArgumentException> {
            engine.addEffect(catEarsEffect())
        }
    }

    @Test
    fun removeEffectReturnsTrue() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        assertTrue(engine.removeEffect("catEars"))
    }

    @Test
    fun removeEffectReturnsFalseForUnknown() = runTest {
        val engine = EffectEngine()
        assertTrue(!engine.removeEffect("unknown"))
    }

    @Test
    fun clearRemovesAllEffects() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        engine.addEffect(glassesEffect())
        engine.clear()
        val output = engine.processFace(faceData(TIMESTAMP_100))
        assertTrue(output.activeEffects.isEmpty())
        assertTrue(output.anchors.isEmpty())
    }

    // --- Disabled effects ---

    @Test
    fun disabledEffectSkipped() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect().let { (it as Effect.AnchorEffect).copy(enabled = false) })
        val output = engine.processFace(faceDataWithLandmarks(TIMESTAMP_100))
        assertTrue(output.activeEffects.isEmpty())
    }

    // --- Anchor effect ---

    @Test
    fun anchorEffectProducesAnchorData() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        val output = engine.processFace(faceDataWithLandmarks(TIMESTAMP_100))
        val anchor = output.anchors["catEars"]
        assertNotNull(anchor)
        assertEquals(LANDMARK_X, anchor.position.x)
        assertEquals(LANDMARK_Y, anchor.position.y)
        assertEquals(1, output.activeEffects.size)
        assertEquals(EffectType.ANCHOR, output.activeEffects[0].type)
    }

    @Test
    fun anchorEffectEmptyLandmarksProducesNoOutput() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        val output = engine.processFace(faceData(TIMESTAMP_100))
        assertNull(output.anchors["catEars"])
        assertTrue(output.activeEffects.isEmpty())
    }

    // --- Expression effect ---

    @Test
    fun expressionEffectAboveThreshold() = runTest {
        val engine = EffectEngine()
        engine.addEffect(smileHeartsEffect())
        val output = engine.processFace(
            faceData(
                TIMESTAMP_100,
                BlendShape.MOUTH_SMILE_LEFT to HIGH_VALUE,
                BlendShape.MOUTH_SMILE_RIGHT to HIGH_VALUE,
            ),
        )
        val intensity = output.parameters["smileHearts.intensity"]
        assertNotNull(intensity)
        assertTrue(intensity > 0f)
        assertEquals(1, output.activeEffects.size)
        assertEquals(EffectType.EXPRESSION, output.activeEffects[0].type)
    }

    @Test
    fun expressionEffectBelowThreshold() = runTest {
        val engine = EffectEngine()
        engine.addEffect(smileHeartsEffect())
        val output = engine.processFace(
            faceData(
                TIMESTAMP_100,
                BlendShape.MOUTH_SMILE_LEFT to LOW_VALUE,
                BlendShape.MOUTH_SMILE_RIGHT to LOW_VALUE,
            ),
        )
        assertEquals(0f, output.parameters["smileHearts.intensity"])
        assertTrue(output.activeEffects.isEmpty())
    }

    // --- Hand effect ---

    @Test
    fun handEffectMatchingGesture() = runTest {
        val engine = EffectEngine()
        engine.addEffect(openPalmParticlesEffect())
        val output = engine.processHand(handData(TIMESTAMP_100, HandGesture.OPEN_PALM))
        val intensity = output.parameters["openPalmParticles.intensity"]
        assertNotNull(intensity)
        assertTrue(intensity > 0f)
        assertEquals(EffectType.HAND, output.activeEffects[0].type)
    }

    @Test
    fun handEffectNonMatchingGesture() = runTest {
        val engine = EffectEngine()
        engine.addEffect(openPalmParticlesEffect())
        val output = engine.processHand(handData(TIMESTAMP_100, HandGesture.CLOSED_FIST))
        assertEquals(0f, output.parameters["openPalmParticles.intensity"])
        assertTrue(output.activeEffects.isEmpty())
    }

    // --- Transform effect ---

    @Test
    fun transformEffectAppliesFunction() = runTest {
        val engine = EffectEngine()
        engine.addEffect(cartoonEyesEffect())
        val output = engine.processFace(
            faceData(TIMESTAMP_100, BlendShape.EYE_WIDE_LEFT to HIGH_VALUE),
        )
        val value = output.parameters["cartoonEyes"]
        assertNotNull(value)
        assertTrue(value > 1f)
        assertEquals(EffectType.TRANSFORM, output.activeEffects[0].type)
    }

    // --- Ambient effect ---

    @Test
    fun ambientEffectComputesFromData() = runTest {
        val engine = EffectEngine()
        val ambient = Effect.AmbientEffect(
            id = "ambient",
            compute = { face, _ -> if (face?.isTracking == true) 1f else 0f },
        )
        engine.addEffect(ambient)
        val output = engine.processFace(faceData(TIMESTAMP_100))
        assertEquals(1f, output.parameters["ambient.intensity"])
        assertEquals(EffectType.AMBIENT, output.activeEffects[0].type)
    }

    // --- Multiple effects compose ---

    @Test
    fun multipleEffectsComposeIndependently() = runTest {
        val engine = EffectEngine()
        engine.addEffect(catEarsEffect())
        engine.addEffect(smileHeartsEffect())
        val output = engine.processFace(
            faceDataWithLandmarks(
                TIMESTAMP_100,
                BlendShape.MOUTH_SMILE_LEFT to HIGH_VALUE,
                BlendShape.MOUTH_SMILE_RIGHT to HIGH_VALUE,
            ),
        )
        assertNotNull(output.anchors["catEars"])
        assertNotNull(output.parameters["smileHearts.intensity"])
        assertEquals(2, output.activeEffects.size)
    }

    // --- No face data for face effects ---

    @Test
    fun noFaceDataProducesEmptyForFaceEffects() = runTest {
        val engine = EffectEngine()
        engine.addEffect(smileHeartsEffect())
        // Only hand data, no face
        val output = engine.processHand(handData(TIMESTAMP_100, HandGesture.NONE))
        assertTrue(output.parameters.isEmpty())
        assertTrue(output.activeEffects.isEmpty())
    }

    // --- Helpers ---

    private fun faceData(
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

    private fun faceDataWithLandmarks(
        timestampMs: Long,
        vararg blendShapes: Pair<BlendShape, Float>,
    ): FaceTrackingData {
        val data = emptyBlendShapeData().toMutableMap()
        blendShapes.forEach { (shape, value) -> data[shape] = value }
        val landmarks = MutableList(LANDMARK_COUNT) { FaceLandmark(0f, 0f) }
        landmarks[AnchorPoint.FOREHEAD.landmarkIndex] = FaceLandmark(LANDMARK_X, LANDMARK_Y)
        return FaceTrackingData(
            blendShapes = data,
            headTransform = HeadTransform(roll = HEAD_ROLL),
            landmarks = landmarks,
            timestampMs = timestampMs,
            isTracking = true,
        )
    }

    private fun handData(
        timestampMs: Long,
        gesture: HandGesture,
        confidence: Float = DEFAULT_CONFIDENCE,
    ): HandTrackingData = HandTrackingData(
        hands = if (gesture == HandGesture.NONE) {
            emptyList()
        } else {
            listOf(
                TrackedHand(
                    handedness = Handedness.RIGHT,
                    landmarks = emptyList(),
                    gesture = gesture,
                    gestureConfidence = confidence,
                ),
            )
        },
        timestampMs = timestampMs,
        isTracking = true,
    )

    companion object {
        private const val TIMESTAMP_100 = 100L
        private const val HIGH_VALUE = 0.8f
        private const val LOW_VALUE = 0.1f
        private const val LANDMARK_X = 0.5f
        private const val LANDMARK_Y = 0.3f
        private const val HEAD_ROLL = 15f
        private const val DEFAULT_CONFIDENCE = 0.9f
        private const val LANDMARK_COUNT = 478
    }
}
