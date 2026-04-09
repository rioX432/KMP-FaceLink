package io.github.kmpfacelink.avatar

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceTrackingData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AvatarParameterMapper] (fun interface contract) and the integration
 * between [UnifiedExpressionsMapper] and [VrcOscParameterBuilder].
 *
 * Unit tests specific to [VrcOscParameterBuilder] are in VrcOscParameterBuilderTest.
 * Unit tests specific to [UnifiedExpressionsMapper] are in UnifiedExpressionsMapperTest.
 */
class AvatarParameterMapperTest {

    // ── AvatarParameterMapper fun interface ───────────────────────────────────

    @Test
    fun funInterface_tracking_returnsNonEmptyMap() {
        val mapper = AvatarParameterMapper { data ->
            if (data.isTracking) mapOf("TestParam" to 1.0f) else emptyMap()
        }
        val result = mapper.map(trackingData())
        assertEquals(mapOf("TestParam" to 1.0f), result)
    }

    @Test
    fun funInterface_notTracking_returnsEmptyMap() {
        val mapper = AvatarParameterMapper { data ->
            if (data.isTracking) mapOf("TestParam" to 1.0f) else emptyMap()
        }
        val result = mapper.map(FaceTrackingData.notTracking())
        assertTrue(result.isEmpty())
    }

    @Test
    fun funInterface_valuesPassedThrough_unchanged() {
        val expectedParams = mapOf("A" to 0.25f, "B" to 0.75f)
        val mapper = AvatarParameterMapper { _ -> expectedParams }
        assertEquals(expectedParams, mapper.map(trackingData()))
    }

    @Test
    fun funInterface_lambdaCanUseBlendShapes() {
        val mapper = AvatarParameterMapper { data ->
            val jaw = data.blendShapes[BlendShape.JAW_OPEN] ?: 0f
            mapOf("JawParam" to jaw)
        }
        val data = trackingData(BlendShape.JAW_OPEN to 0.55f)
        val result = mapper.map(data)
        assertEquals(0.55f, result["JawParam"]!!, DEFAULT_TOLERANCE)
    }

    // ── VrcOscParameterBuilder + UnifiedExpressionsMapper integration ─────────

    @Test
    fun integration_mapperOutputFeedsIntoOscBuilder_addressesAreValid() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(parameterPrefix = UnifiedExpressionsConfig.NO_PREFIX),
        )
        val data = trackingData(BlendShape.JAW_OPEN to 0.5f)
        val params = mapper.map(data)
        val entries = VrcOscParameterBuilder.buildOscEntries(params)

        assertTrue(entries.isNotEmpty())
        for ((address, _) in entries) {
            assertTrue(
                address.startsWith(VrcOscParameterBuilder.AVATAR_PARAMETER_PREFIX),
                "Address '$address' must start with '${VrcOscParameterBuilder.AVATAR_PARAMETER_PREFIX}'",
            )
        }
    }

    @Test
    fun integration_jawOpenOscAddress_containsCorrectValue() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(parameterPrefix = UnifiedExpressionsConfig.NO_PREFIX),
        )
        val data = trackingData(BlendShape.JAW_OPEN to 0.7f)
        val entries = VrcOscParameterBuilder.buildOscEntries(mapper.map(data))

        val jawEntry = entries.find { it.first == "/avatar/parameters/JawOpen" }
        assertNotNull(jawEntry, "JawOpen OSC entry must be present")
        assertEquals(0.7f, jawEntry.second, DEFAULT_TOLERANCE)
    }

    @Test
    fun integration_v2PrefixedMapper_producesDoubleSeparatedOscPath() {
        val mapper = UnifiedExpressionsMapper() // default prefix "v2/"
        val data = trackingData(BlendShape.JAW_OPEN to 0.5f)
        val entries = VrcOscParameterBuilder.buildOscEntries(mapper.map(data))

        val jawEntry = entries.find { it.first == "/avatar/parameters/v2/JawOpen" }
        assertNotNull(jawEntry, "v2-prefixed JawOpen OSC entry must be present")
    }

    @Test
    fun integration_notTracking_producesNoOscEntries() {
        val mapper = UnifiedExpressionsMapper()
        val entries = VrcOscParameterBuilder.buildOscEntries(mapper.map(FaceTrackingData.notTracking()))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun integration_allOscValues_inRange() {
        val mapper = UnifiedExpressionsMapper(
            UnifiedExpressionsConfig(parameterPrefix = UnifiedExpressionsConfig.NO_PREFIX),
        )
        // All blend shapes at max to stress-test clamping through the full pipeline
        val shapes = BlendShape.entries.associateWith { 1f }
        val entries = VrcOscParameterBuilder.buildOscEntries(mapper.map(trackingData(blendShapes = shapes)))

        for ((address, value) in entries) {
            assertTrue(value in 0f..1f, "OSC value for '$address' is out of [0, 1]: $value")
        }
    }

    @Test
    fun integration_customMapperLambda_feedsOscBuilder() {
        val mapper = AvatarParameterMapper { data ->
            if (!data.isTracking) return@AvatarParameterMapper emptyMap()
            mapOf("Custom" to 0.42f)
        }
        val entries = VrcOscParameterBuilder.buildOscEntries(mapper.map(trackingData()))
        assertEquals(1, entries.size)
        assertEquals("/avatar/parameters/Custom", entries[0].first)
        assertEquals(0.42f, entries[0].second, DEFAULT_TOLERANCE)
    }
}
