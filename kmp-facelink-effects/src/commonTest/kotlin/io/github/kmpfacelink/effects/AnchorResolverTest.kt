package io.github.kmpfacelink.effects

import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnchorResolverTest {

    @Test
    fun validIndexReturnsPosition() {
        val landmarks = listOf(
            FaceLandmark(LANDMARK_0_X, LANDMARK_0_Y),
            FaceLandmark(LANDMARK_1_X, LANDMARK_1_Y),
        )
        val result = AnchorResolver.resolve(
            landmarks,
            landmarkIndex = 1,
            rotationSource = RotationSource.NONE,
            headTransform = HeadTransform(),
        )
        assertNotNull(result)
        assertEquals(LANDMARK_1_X, result.position.x)
        assertEquals(LANDMARK_1_Y, result.position.y)
        assertEquals(0f, result.rotationDegrees)
    }

    @Test
    fun outOfBoundsReturnsNull() {
        val landmarks = listOf(FaceLandmark(LANDMARK_0_X, LANDMARK_0_Y))
        val result = AnchorResolver.resolve(
            landmarks,
            landmarkIndex = 5,
            rotationSource = RotationSource.NONE,
            headTransform = HeadTransform(),
        )
        assertNull(result)
    }

    @Test
    fun negativeIndexReturnsNull() {
        val landmarks = listOf(FaceLandmark(LANDMARK_0_X, LANDMARK_0_Y))
        val result = AnchorResolver.resolve(
            landmarks,
            landmarkIndex = -1,
            rotationSource = RotationSource.NONE,
            headTransform = HeadTransform(),
        )
        assertNull(result)
    }

    @Test
    fun emptyLandmarksReturnsNull() {
        val result = AnchorResolver.resolve(
            landmarks = emptyList(),
            landmarkIndex = 0,
            rotationSource = RotationSource.NONE,
            headTransform = HeadTransform(),
        )
        assertNull(result)
    }

    @Test
    fun headTransformUsesRoll() {
        val landmarks = listOf(FaceLandmark(LANDMARK_0_X, LANDMARK_0_Y))
        val result = AnchorResolver.resolve(
            landmarks,
            landmarkIndex = 0,
            rotationSource = RotationSource.HEAD_TRANSFORM,
            headTransform = HeadTransform(roll = HEAD_ROLL),
        )
        assertNotNull(result)
        assertEquals(HEAD_ROLL, result.rotationDegrees)
    }

    @Test
    fun noneRotationReturnsZero() {
        val landmarks = listOf(FaceLandmark(LANDMARK_0_X, LANDMARK_0_Y))
        val result = AnchorResolver.resolve(
            landmarks,
            landmarkIndex = 0,
            rotationSource = RotationSource.NONE,
            headTransform = HeadTransform(roll = HEAD_ROLL),
        )
        assertNotNull(result)
        assertEquals(0f, result.rotationDegrees)
    }

    companion object {
        private const val LANDMARK_0_X = 0.1f
        private const val LANDMARK_0_Y = 0.2f
        private const val LANDMARK_1_X = 0.5f
        private const val LANDMARK_1_Y = 0.6f
        private const val HEAD_ROLL = 15f
    }
}
