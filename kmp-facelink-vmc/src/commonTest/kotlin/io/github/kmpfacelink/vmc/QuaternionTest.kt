package io.github.kmpfacelink.vmc

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class QuaternionTest {

    @Test
    fun identityQuaternionForZeroAngles() {
        val q = eulerToQuaternion(0f, 0f, 0f)
        assertQuaternionNear(Quaternion(0f, 0f, 0f, 1f), q)
    }

    @Test
    fun quaternionIsNormalized() {
        val testCases = listOf(
            Triple(30f, 0f, 0f),
            Triple(0f, 45f, 0f),
            Triple(0f, 0f, 60f),
            Triple(15f, 25f, 35f),
            Triple(-10f, 20f, -30f),
        )

        for ((pitch, yaw, roll) in testCases) {
            val q = eulerToQuaternion(pitch, yaw, roll)
            val magnitude = sqrt(
                (q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w).toDouble(),
            ).toFloat()
            assertTrue(
                abs(magnitude - 1f) < QUATERNION_EPSILON,
                "Quaternion not normalized for ($pitch, $yaw, $roll): magnitude=$magnitude",
            )
        }
    }

    @Test
    fun pitchOnlyRotation() {
        // 90-degree pitch (nod forward) should produce rotation around X-axis
        val q = eulerToQuaternion(90f, 0f, 0f)
        // For 90-degree X rotation: q = (sin(45°), 0, 0, cos(45°)) ≈ (0.707, 0, 0, 0.707)
        val expected = 0.7071f
        assertTrue(abs(q.x) > 0.5f, "Pitch rotation should have significant X component")
        assertTrue(abs(q.y) < QUATERNION_EPSILON, "Pitch rotation should have zero Y")
        assertTrue(abs(q.z) < QUATERNION_EPSILON, "Pitch rotation should have zero Z")
        assertTrue(abs(abs(q.w) - expected) < 0.01f, "Pitch W component unexpected: ${q.w}")
    }

    private fun assertQuaternionNear(expected: Quaternion, actual: Quaternion) {
        assertTrue(
            abs(expected.x - actual.x) < QUATERNION_EPSILON &&
                abs(expected.y - actual.y) < QUATERNION_EPSILON &&
                abs(expected.z - actual.z) < QUATERNION_EPSILON &&
                abs(expected.w - actual.w) < QUATERNION_EPSILON,
            "Expected $expected but got $actual",
        )
    }

    private companion object {
        const val QUATERNION_EPSILON = 0.001f
    }
}
