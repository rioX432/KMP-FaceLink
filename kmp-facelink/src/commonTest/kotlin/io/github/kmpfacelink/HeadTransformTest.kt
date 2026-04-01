package io.github.kmpfacelink

import io.github.kmpfacelink.model.HeadTransform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadTransformTest {

    @Test
    fun defaultValuesAreZero() {
        val h = HeadTransform()
        assertEquals(0f, h.pitch)
        assertEquals(0f, h.yaw)
        assertEquals(0f, h.roll)
        assertEquals(0f, h.positionX)
        assertEquals(0f, h.positionY)
        assertEquals(0f, h.positionZ)
        assertNull(h.transformMatrix)
    }

    @Test
    fun constructorAssignsScalarProperties() {
        val h = HeadTransform(
            pitch = 10f,
            yaw = -20f,
            roll = 5f,
            positionX = 0.1f,
            positionY = 0.2f,
            positionZ = -0.3f,
        )
        assertEquals(10f, h.pitch)
        assertEquals(-20f, h.yaw)
        assertEquals(5f, h.roll)
        assertEquals(0.1f, h.positionX)
        assertEquals(0.2f, h.positionY)
        assertEquals(-0.3f, h.positionZ)
    }

    @Test
    fun transformMatrixIsStoredCorrectly() {
        val matrix = FloatArray(16) { it.toFloat() }
        val h = HeadTransform(transformMatrix = matrix)
        val stored = checkNotNull(h.transformMatrix)
        assertEquals(16, stored.size)
        for (i in 0 until 16) {
            assertEquals(i.toFloat(), stored[i])
        }
    }

    @Test
    fun equalsReturnsTrueForSameScalarValues() {
        val a = HeadTransform(pitch = 10f, yaw = 5f, roll = 2f, positionX = 1f)
        val b = HeadTransform(pitch = 10f, yaw = 5f, roll = 2f, positionX = 1f)
        assertEquals(a, b)
    }

    @Test
    fun equalsReturnsTrueForBothNullMatrix() {
        val a = HeadTransform(pitch = 1f, transformMatrix = null)
        val b = HeadTransform(pitch = 1f, transformMatrix = null)
        assertEquals(a, b)
    }

    @Test
    fun equalsReturnsFalseWhenOneMatrixIsNull() {
        val a = HeadTransform(transformMatrix = null)
        val b = HeadTransform(transformMatrix = FloatArray(16) { 0f })
        assertFalse(a == b)
    }

    @Test
    fun equalsReturnsFalseForDifferentPitch() {
        val a = HeadTransform(pitch = 10f)
        val b = HeadTransform(pitch = 11f)
        assertFalse(a == b)
    }

    @Test
    fun equalsReturnsFalseForDifferentYaw() {
        val a = HeadTransform(yaw = 5f)
        val b = HeadTransform(yaw = 6f)
        assertFalse(a == b)
    }

    @Test
    fun hashCodeIsConsistentForScalarEquality() {
        val a = HeadTransform(pitch = 5f, yaw = 3f)
        val b = HeadTransform(pitch = 5f, yaw = 3f)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun hashCodeDiffersForDifferentValues() {
        val a = HeadTransform(pitch = 10f)
        val b = HeadTransform(pitch = 20f)
        assertNotEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun copyModifiesSelectedFields() {
        val original = HeadTransform(pitch = 10f, yaw = 20f, roll = 5f)
        val copy = original.copy(yaw = 99f)
        assertEquals(10f, copy.pitch)
        assertEquals(99f, copy.yaw)
        assertEquals(5f, copy.roll)
    }

    @Test
    fun sameInstanceEqualsItself() {
        val h = HeadTransform(pitch = 1f)
        assertTrue(h == h)
    }
}
