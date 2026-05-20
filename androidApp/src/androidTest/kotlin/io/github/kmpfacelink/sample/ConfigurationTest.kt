package io.github.kmpfacelink.sample

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.detectDeviceCapabilities
import io.github.kmpfacelink.model.BodyTrackerConfig
import io.github.kmpfacelink.model.CameraFacing
import io.github.kmpfacelink.model.DetectionConfig
import io.github.kmpfacelink.model.DeviceCapabilityProfile
import io.github.kmpfacelink.model.DeviceTier
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.HandTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.InferenceDelegate
import io.github.kmpfacelink.model.PerformanceConfig
import io.github.kmpfacelink.model.QualityPreset
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TrackingModality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for configuration data classes, default values, and QualityPreset.
 */
@RunWith(AndroidJUnit4::class)
class ConfigurationTest {

    private lateinit var platformContext: PlatformContext

    @Before
    fun setUp() {
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        platformContext = PlatformContext(appContext, lifecycleOwner)
    }

    // -- FaceTrackerConfig defaults --

    @Test
    fun faceTrackerConfigDefaultSmoothing() {
        val config = FaceTrackerConfig()
        assertTrue(
            "Default smoothing should be EMA",
            config.smoothingConfig is SmoothingConfig.Ema,
        )
    }

    @Test
    fun faceTrackerConfigDefaultCameraFacing() {
        val config = FaceTrackerConfig()
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
    }

    @Test
    fun faceTrackerConfigDefaultAnalysisDimensions() {
        val config = FaceTrackerConfig()
        assertEquals(FaceTrackerConfig.DEFAULT_ANALYSIS_WIDTH, config.analysisWidth)
        assertEquals(FaceTrackerConfig.DEFAULT_ANALYSIS_HEIGHT, config.analysisHeight)
    }

    @Test
    fun faceTrackerConfigDefaultDetectionConfidence() {
        val config = FaceTrackerConfig()
        assertEquals(DetectionConfig.DEFAULT_CONFIDENCE, config.minDetectionConfidence, 0f)
        assertEquals(DetectionConfig.DEFAULT_CONFIDENCE, config.minTrackingConfidence, 0f)
        assertEquals(DetectionConfig.DEFAULT_CONFIDENCE, config.minPresenceConfidence, 0f)
    }

    @Test
    fun faceTrackerConfigDefaultMaxFaces() {
        val config = FaceTrackerConfig()
        assertEquals(1, config.maxFaces)
    }

    // -- FaceTrackerConfig custom values --

    @Test
    fun faceTrackerConfigCustomValuesArePreserved() {
        val config = FaceTrackerConfig(
            smoothingConfig = SmoothingConfig.None,
            cameraFacing = CameraFacing.BACK,
            performanceConfig = PerformanceConfig(
                analysisWidth = 320,
                analysisHeight = 240,
                inferenceDelegate = InferenceDelegate.CPU,
            ),
        )
        assertTrue(config.smoothingConfig is SmoothingConfig.None)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
        assertEquals(320, config.analysisWidth)
        assertEquals(240, config.analysisHeight)
        assertEquals(InferenceDelegate.CPU, config.inferenceDelegate)
    }

    @Test
    fun faceTrackerConfigCopyModifiesOnlyTargetField() {
        val original = FaceTrackerConfig()
        val modified = original.copy(cameraFacing = CameraFacing.BACK)
        assertEquals(CameraFacing.BACK, modified.cameraFacing)
        // Other fields should remain unchanged
        assertEquals(original.smoothingConfig, modified.smoothingConfig)
        assertEquals(original.analysisWidth, modified.analysisWidth)
    }

    // -- HandTrackerConfig --

    @Test
    fun handTrackerConfigDefaults() {
        val config = HandTrackerConfig()
        assertEquals(2, config.maxHands)
        assertTrue(config.enableGestureRecognition)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(HandTrackerConfig.DEFAULT_CONFIDENCE, config.minDetectionConfidence, 0f)
    }

    @Test
    fun handTrackerConfigCustomValues() {
        val config = HandTrackerConfig(
            maxHands = 1,
            enableGestureRecognition = false,
            cameraFacing = CameraFacing.BACK,
        )
        assertEquals(1, config.maxHands)
        assertEquals(false, config.enableGestureRecognition)
        assertEquals(CameraFacing.BACK, config.cameraFacing)
    }

    // -- BodyTrackerConfig --

    @Test
    fun bodyTrackerConfigDefaults() {
        val config = BodyTrackerConfig()
        assertEquals(1, config.maxBodies)
        assertEquals(CameraFacing.FRONT, config.cameraFacing)
        assertEquals(BodyTrackerConfig.DEFAULT_CONFIDENCE, config.minDetectionConfidence, 0f)
        assertEquals(false, config.enable3D)
    }

    @Test
    fun bodyTrackerConfigCustomMaxBodies() {
        val config = BodyTrackerConfig(maxBodies = 3)
        assertEquals(3, config.maxBodies)
    }

    // -- HolisticTrackerConfig --

    @Test
    fun holisticTrackerConfigDefaultsAllEnabled() {
        val config = HolisticTrackerConfig()
        assertTrue(config.enableFace)
        assertTrue(config.enableHand)
        assertTrue(config.enableBody)
    }

    @Test
    fun holisticTrackerConfigSelectiveModalities() {
        val config = HolisticTrackerConfig(
            enableFace = true,
            enableHand = false,
            enableBody = false,
        )
        assertTrue(config.enableFace)
        assertEquals(false, config.enableHand)
        assertEquals(false, config.enableBody)
    }

    // -- QualityPreset --

    @Test
    fun qualityPresetAutoSelectHighForNpuDevice() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = true,
            tier = DeviceTier.HIGH,
        )
        val preset = QualityPreset.autoSelect(profile)
        assertTrue("NPU device should select High", preset is QualityPreset.High)
    }

    @Test
    fun qualityPresetAutoSelectMediumForGpuOnlyDevice() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = true,
            hasNpu = false,
            tier = DeviceTier.MID,
        )
        val preset = QualityPreset.autoSelect(profile)
        assertTrue("GPU-only device should select Medium", preset is QualityPreset.Medium)
    }

    @Test
    fun qualityPresetAutoSelectLowForCpuOnlyDevice() {
        val profile = DeviceCapabilityProfile(
            hasGpuAcceleration = false,
            hasNpu = false,
            tier = DeviceTier.LOW,
        )
        val preset = QualityPreset.autoSelect(profile)
        assertTrue("CPU-only device should select Low", preset is QualityPreset.Low)
    }

    @Test
    fun qualityPresetApplyToProducesCorrectConfig() {
        val preset = QualityPreset.Medium
        val config = preset.applyTo()
        assertEquals(preset.analysisWidth, config.analysisWidth)
        assertEquals(preset.analysisHeight, config.analysisHeight)
        assertEquals(preset.powerMode, config.powerMode)
    }

    // -- DeviceCapabilityDetector --

    @Test
    fun detectDeviceCapabilitiesReturnsValidProfile() {
        val profile = detectDeviceCapabilities(platformContext)
        assertNotNull("Profile should not be null", profile)
        assertNotNull("Tier should not be null", profile.tier)
        // tier should be one of the valid values
        assertTrue(
            "Tier should be LOW, MID, or HIGH",
            profile.tier in DeviceTier.entries,
        )
    }

    @Test
    fun detectDeviceCapabilitiesIsCached() {
        val profile1 = detectDeviceCapabilities(platformContext)
        val profile2 = detectDeviceCapabilities(platformContext)
        // Cached results should be identical references
        assertTrue(
            "Subsequent calls should return the same cached profile",
            profile1 === profile2,
        )
    }

    // -- HolisticTracker enabledModalities --

    @Test
    fun holisticTrackerEnabledModalitiesMatchConfig() {
        val tracker = io.github.kmpfacelink.api.createHolisticTracker(
            platformContext,
            HolisticTrackerConfig(
                enableFace = true,
                enableHand = false,
                enableBody = true,
            ),
        )
        try {
            val expected = setOf(TrackingModality.FACE, TrackingModality.BODY)
            assertEquals(expected, tracker.enabledModalities)
        } finally {
            tracker.release()
        }
    }
}
