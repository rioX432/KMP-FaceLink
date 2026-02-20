package io.github.kmpfacelink

import io.github.kmpfacelink.model.BodyTrackingData
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HandTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.HolisticTrackerConfig
import io.github.kmpfacelink.model.HolisticTrackingData
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
        assertTrue(exception.message!!.contains("At least one"))
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
}
