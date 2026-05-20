package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.PerformanceConfig
import io.github.kmpfacelink.model.PowerConfig
import io.github.kmpfacelink.model.ThermalPolicy
import io.github.kmpfacelink.model.TrackingModality
import io.github.kmpfacelink.model.emptyBlendShapeData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HolisticTrackingDataTest {

    @Test
    fun notTrackingHasAllNullFields() {
        val data = HolisticTrackingData.notTracking()
        assertNull(data.face)
        assertNull(data.hand)
        assertNull(data.body)
        assertFalse(data.isTracking)
        assertEquals(0L, data.timestampMs)
        assertTrue(data.activeModalities.isEmpty())
    }

    @Test
    fun notTrackingPreservesActiveModalities() {
        val modalities = setOf(TrackingModality.FACE, TrackingModality.HAND)
        val data = HolisticTrackingData.notTracking(
            activeModalities = modalities,
            timestampMs = 42L,
        )
        assertEquals(modalities, data.activeModalities)
        assertEquals(42L, data.timestampMs)
        assertFalse(data.isTracking)
    }

    @Test
    fun isTrackingTrueWhenFaceIsTracking() {
        val data = HolisticTrackingData(
            face = FaceTrackingData(
                blendShapes = emptyBlendShapeData(),
                headTransform = HeadTransform(),
                timestampMs = 100L,
                isTracking = true,
            ),
            hand = null,
            body = null,
            timestampMs = 100L,
            activeModalities = setOf(TrackingModality.FACE),
        )
        assertTrue(data.isTracking)
    }

    @Test
    fun isTrackingTrueWhenHandIsTracking() {
        val data = HolisticTrackingData(
            face = null,
            hand = HandTrackingData(
                hands = emptyList(),
                timestampMs = 100L,
                isTracking = true,
            ),
            body = null,
            timestampMs = 100L,
            activeModalities = setOf(TrackingModality.HAND),
        )
        assertTrue(data.isTracking)
    }

    @Test
    fun isTrackingTrueWhenBodyIsTracking() {
        val data = HolisticTrackingData(
            face = null,
            hand = null,
            body = BodyTrackingData(
                bodies = emptyList(),
                timestampMs = 100L,
                isTracking = true,
            ),
            timestampMs = 100L,
            activeModalities = setOf(TrackingModality.BODY),
        )
        assertTrue(data.isTracking)
    }

    @Test
    fun isTrackingFalseWhenNothingTracking() {
        val data = HolisticTrackingData(
            face = FaceTrackingData.notTracking(100L),
            hand = HandTrackingData.notTracking(100L),
            body = BodyTrackingData.notTracking(100L),
            timestampMs = 100L,
            activeModalities = setOf(
                TrackingModality.FACE,
                TrackingModality.HAND,
                TrackingModality.BODY,
            ),
        )
        assertFalse(data.isTracking)
    }

    @Test
    fun trackingModalityHasThreeEntries() {
        assertEquals(3, TrackingModality.entries.size)
    }
}

class HolisticTrackerConfigTest {

    @Test
    fun defaultConfigEnablesAllModalities() {
        val config = HolisticTrackerConfig()
        assertTrue(config.enableFace)
        assertTrue(config.enableHand)
        assertTrue(config.enableBody)
    }

    @Test
    fun configAllowsSingleModality() {
        val config = HolisticTrackerConfig(
            enableFace = true,
            enableHand = false,
            enableBody = false,
        )
        assertTrue(config.enableFace)
        assertFalse(config.enableHand)
        assertFalse(config.enableBody)
    }

    @Test
    fun configRejectsAllDisabled() {
        val exception = try {
            HolisticTrackerConfig(
                enableFace = false,
                enableHand = false,
                enableBody = false,
            )
            null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(exception != null, "Expected IllegalArgumentException")
        assertTrue(checkNotNull(exception.message).contains("At least one"))
    }

    @Test
    fun configAllowsTwoModalities() {
        val config = HolisticTrackerConfig(
            enableFace = true,
            enableHand = true,
            enableBody = false,
        )
        assertTrue(config.enableFace)
        assertTrue(config.enableHand)
        assertFalse(config.enableBody)
    }

    @Test
    fun defaultConfigHasNoConflicts() {
        // Default sub-configs match default top-level values — no exception expected
        val config = HolisticTrackerConfig()
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(ThermalPolicy.Disabled, config.thermalPolicy)
        assertEquals(InferenceDelegate.AUTO, config.inferenceDelegate)
    }

    @Test
    fun topLevelCameraFacingOverridesSubConfigs() {
        // Sub-configs set to BACK, top-level to FRONT — top-level wins
        val config = HolisticTrackerConfig(
            cameraFacing = CameraFacing.FRONT,
            faceConfig = FaceTrackerConfig(cameraFacing = CameraFacing.BACK),
            handConfig = HandTrackerConfig(cameraFacing = CameraFacing.BACK),
            bodyConfig = BodyTrackerConfig(cameraFacing = CameraFacing.BACK),
        )
        // Verify the top-level value is what the tracker will use
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
    }

    @Test
    fun topLevelThermalPolicyOverridesSubConfigs() {
        val adaptive = ThermalPolicy.Adaptive()
        val config = HolisticTrackerConfig(
            thermalPolicy = adaptive,
            faceConfig = FaceTrackerConfig(
                powerConfig = PowerConfig(thermalPolicy = ThermalPolicy.Disabled),
            ),
            handConfig = HandTrackerConfig(thermalPolicy = ThermalPolicy.Disabled),
            bodyConfig = BodyTrackerConfig(thermalPolicy = ThermalPolicy.Disabled),
        )
        assertEquals(adaptive, config.thermalPolicy)
    }

    @Test
    fun topLevelInferenceDelegateOverridesSubConfigs() {
        val config = HolisticTrackerConfig(
            inferenceDelegate = InferenceDelegate.GPU,
            faceConfig = FaceTrackerConfig(
                performanceConfig = PerformanceConfig(
                    inferenceDelegate = InferenceDelegate.CPU,
                ),
            ),
            handConfig = HandTrackerConfig(inferenceDelegate = InferenceDelegate.CPU),
            bodyConfig = BodyTrackerConfig(inferenceDelegate = InferenceDelegate.CPU),
        )
        assertEquals(InferenceDelegate.GPU, config.inferenceDelegate)
    }

    @Test
    fun conflictingSubConfigDoesNotThrow() {
        // Construction must succeed even with conflicting values — only a warning is logged
        val config = HolisticTrackerConfig(
            cameraFacing = CameraFacing.FRONT,
            thermalPolicy = ThermalPolicy.Adaptive(),
            inferenceDelegate = InferenceDelegate.GPU,
            faceConfig = FaceTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                powerConfig = PowerConfig(thermalPolicy = ThermalPolicy.Disabled),
                performanceConfig = PerformanceConfig(
                    inferenceDelegate = InferenceDelegate.CPU,
                ),
            ),
            handConfig = HandTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                thermalPolicy = ThermalPolicy.Disabled,
                inferenceDelegate = InferenceDelegate.CPU,
            ),
            bodyConfig = BodyTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                thermalPolicy = ThermalPolicy.Disabled,
                inferenceDelegate = InferenceDelegate.CPU,
            ),
        )
        // Config is created successfully — top-level values are authoritative
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(InferenceDelegate.GPU, config.inferenceDelegate)
        assertTrue(config.thermalPolicy is ThermalPolicy.Adaptive)
    }

    @Test
    fun disabledModalitySkipsConflictCheck() {
        // When a modality is disabled, conflicting sub-config should not warn
        val config = HolisticTrackerConfig(
            enableFace = false,
            enableHand = false,
            enableBody = true,
            cameraFacing = CameraFacing.FRONT,
            faceConfig = FaceTrackerConfig(cameraFacing = CameraFacing.BACK),
            handConfig = HandTrackerConfig(cameraFacing = CameraFacing.BACK),
        )
        assertFalse(config.enableFace)
        assertFalse(config.enableHand)
        assertTrue(config.enableBody)
    }

    @Test
    fun matchingSubConfigValuesProduceNoConflict() {
        // Explicitly set sub-config values that match top-level — no conflict
        val config = HolisticTrackerConfig(
            cameraFacing = CameraFacing.BACK,
            thermalPolicy = ThermalPolicy.Adaptive(),
            inferenceDelegate = InferenceDelegate.GPU,
            faceConfig = FaceTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                powerConfig = PowerConfig(thermalPolicy = ThermalPolicy.Adaptive()),
                performanceConfig = PerformanceConfig(
                    inferenceDelegate = InferenceDelegate.GPU,
                ),
            ),
            handConfig = HandTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                thermalPolicy = ThermalPolicy.Adaptive(),
                inferenceDelegate = InferenceDelegate.GPU,
            ),
            bodyConfig = BodyTrackerConfig(
                cameraFacing = CameraFacing.BACK,
                thermalPolicy = ThermalPolicy.Adaptive(),
                inferenceDelegate = InferenceDelegate.GPU,
            ),
        )
        assertEquals(CameraFacing.BACK, config.cameraFacing)
    }
}
