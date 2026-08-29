package io.github.kmpfacelink.capture

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class HeadPoseTest {

    private fun identity(): FloatArray = FloatArray(16).also {
        it[0] = 1f
        it[5] = 1f
        it[10] = 1f
        it[15] = 1f
    }

    @Test
    fun `identity transform is frontal`() {
        assertTrue(HeadPose.isFrontal(identity()))
    }

    @Test
    fun `large yaw is not frontal`() {
        val yawed = identity().also { it[2] = 0.8f } // |r20| = sin(yaw) well past threshold
        assertFalse(HeadPose.isFrontal(yawed))
    }

    @Test
    fun `null or degenerate transform is not frontal`() {
        assertFalse(HeadPose.isFrontal(null))
        assertFalse(HeadPose.isFrontal(FloatArray(16)))
    }
}
