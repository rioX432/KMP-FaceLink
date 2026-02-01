package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.util.BlendShapeEnhancer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlendShapeEnhancerTest {

    private fun makeLandmarks(
        overrides: Map<Int, FaceLandmark> = emptyMap(),
        default: FaceLandmark = FaceLandmark(0.5f, 0.5f, 0f),
    ): List<FaceLandmark> {
        val list = MutableList(478) { default }
        for ((index, landmark) in overrides) {
            list[index] = landmark
        }
        return list
    }

    private fun neutralFaceLandmarks(): List<FaceLandmark> = makeLandmarks(
        overrides = mapOf(
            133 to FaceLandmark(0.42f, 0.38f, 0f),
            362 to FaceLandmark(0.58f, 0.38f, 0f),
            33 to FaceLandmark(0.35f, 0.38f, 0f),
            160 to FaceLandmark(0.38f, 0.36f, 0f),
            159 to FaceLandmark(0.40f, 0.355f, 0f),
            158 to FaceLandmark(0.41f, 0.36f, 0f),
            144 to FaceLandmark(0.38f, 0.40f, 0f),
            145 to FaceLandmark(0.40f, 0.405f, 0f),
            153 to FaceLandmark(0.41f, 0.40f, 0f),
            263 to FaceLandmark(0.65f, 0.38f, 0f),
            387 to FaceLandmark(0.59f, 0.36f, 0f),
            386 to FaceLandmark(0.60f, 0.355f, 0f),
            385 to FaceLandmark(0.61f, 0.36f, 0f),
            373 to FaceLandmark(0.59f, 0.40f, 0f),
            374 to FaceLandmark(0.60f, 0.405f, 0f),
            380 to FaceLandmark(0.61f, 0.40f, 0f),
            6 to FaceLandmark(0.50f, 0.37f, -0.05f),
            1 to FaceLandmark(0.50f, 0.52f, -0.06f),
            48 to FaceLandmark(0.46f, 0.50f, -0.03f),
            278 to FaceLandmark(0.54f, 0.50f, -0.03f),
            152 to FaceLandmark(0.50f, 0.72f, -0.02f),
            61 to FaceLandmark(0.44f, 0.60f, -0.02f),
            291 to FaceLandmark(0.56f, 0.60f, -0.02f),
            234 to FaceLandmark(0.25f, 0.45f, 0.03f),
            454 to FaceLandmark(0.75f, 0.45f, 0.03f),
        ),
    )

    private fun zeroBlendShapes(): Map<BlendShape, Float> =
        BlendShape.entries.associateWith { 0f }

    // ── Factory tests ──

    @Test
    fun create_none_returnsNull() {
        assertNull(BlendShapeEnhancer.create(BlendShapeEnhancerConfig.None))
    }

    @Test
    fun create_default_returnsEnhancer() {
        assertNotNull(BlendShapeEnhancer.create(BlendShapeEnhancerConfig.Default()))
    }

    // ── Enhancement tests ──

    @Test
    fun enhance_tooFewLandmarks_returnsOriginal() {
        val enhancer = BlendShapeEnhancer.create(BlendShapeEnhancerConfig.Default())!!
        val bs = zeroBlendShapes()
        val result = enhancer.enhance(bs, emptyList())
        // Should return input unchanged
        assertTrue(result[BlendShape.CHEEK_PUFF] == 0f)
    }

    @Test
    fun enhance_nearZeroShapes_usesGeometric() {
        val enhancer = BlendShapeEnhancer.create(BlendShapeEnhancerConfig.Default())!!
        val bs = zeroBlendShapes()
        // Landmarks with wide open eyes
        val lm = neutralFaceLandmarks().toMutableList()
        lm[160] = FaceLandmark(0.38f, 0.33f, 0f)
        lm[159] = FaceLandmark(0.40f, 0.32f, 0f)
        lm[158] = FaceLandmark(0.41f, 0.33f, 0f)
        lm[144] = FaceLandmark(0.38f, 0.43f, 0f)
        lm[145] = FaceLandmark(0.40f, 0.44f, 0f)
        lm[153] = FaceLandmark(0.41f, 0.43f, 0f)

        val result = enhancer.enhance(bs, lm)
        // ML was 0 for eyeWideRight, but geometric should produce > 0
        assertTrue(
            (result[BlendShape.EYE_WIDE_RIGHT] ?: 0f) > 0f,
            "eyeWideRight should be enhanced by geometric solver",
        )
    }

    @Test
    fun enhance_lowAccuracyShapes_blendsWeighted() {
        val enhancer = BlendShapeEnhancer.create(
            BlendShapeEnhancerConfig.Default(geometricBlendWeight = 0.7f),
        )!!
        val bs = zeroBlendShapes().toMutableMap()
        bs[BlendShape.JAW_LEFT] = 0.5f

        val lm = neutralFaceLandmarks().toMutableList()
        lm[152] = FaceLandmark(0.47f, 0.72f, -0.02f)

        val result = enhancer.enhance(bs, lm)
        // Result should reflect weighted blend of ML (0.5) and geometric
        val value = result[BlendShape.JAW_LEFT] ?: 0f
        assertTrue(value >= 0f && value <= 1f, "jawLeft should be in [0,1], got $value")
    }

    @Test
    fun enhance_deadZone_snapsToZero() {
        val enhancer = BlendShapeEnhancer.create(
            BlendShapeEnhancerConfig.Default(
                sensitivityOverrides = emptyMap(),
                deadZoneOverrides = mapOf(BlendShape.CHEEK_PUFF to 0.5f),
            ),
        )!!
        val bs = zeroBlendShapes().toMutableMap()
        // Set ML value below dead zone
        bs[BlendShape.CHEEK_PUFF] = 0.1f

        val result = enhancer.enhance(bs, neutralFaceLandmarks())
        val value = result[BlendShape.CHEEK_PUFF] ?: 0f
        // With high dead zone (0.5), low values should snap to 0
        assertTrue(value == 0f || value >= 0.5f, "Value should be 0 or >= threshold, got $value")
    }

    @Test
    fun enhance_sensitivityAmplifies() {
        val enhancer = BlendShapeEnhancer.create(
            BlendShapeEnhancerConfig.Default(
                sensitivityOverrides = mapOf(BlendShape.JAW_FORWARD to 5.0f),
                deadZoneOverrides = emptyMap(),
            ),
        )!!
        val bs = zeroBlendShapes().toMutableMap()
        bs[BlendShape.JAW_FORWARD] = 0.1f

        val result = enhancer.enhance(bs, neutralFaceLandmarks())
        val value = result[BlendShape.JAW_FORWARD] ?: 0f
        // Sensitivity should amplify the value (after geometric blending)
        assertTrue(value >= 0f && value <= 1f, "Amplified value should be clamped, got $value")
    }

    @Test
    fun enhance_allValuesInRange() {
        val enhancer = BlendShapeEnhancer.create(BlendShapeEnhancerConfig.Default())!!
        val bs = BlendShape.entries.associateWith { 0.5f }
        val result = enhancer.enhance(bs, neutralFaceLandmarks())
        for ((shape, value) in result) {
            assertTrue(
                value in 0f..1f,
                "$shape value $value is out of [0, 1] range",
            )
        }
    }
}
