package io.github.kmpfacelink

import io.github.kmpfacelink.util.Quaternion
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuaternionTest {

    // ── Euler ↔ quaternion round-trip ──

    @Test
    fun identityEulerProducesIdentityQuaternion() {
        val q = Quaternion.fromEulerDegrees(0f, 0f, 0f)
        assertNear(1f, q.w, "w")
        assertNear(0f, q.x, "x")
        assertNear(0f, q.y, "y")
        assertNear(0f, q.z, "z")
    }

    @Test
    fun eulerToQuaternionToEulerIsIdentityForPitchOnly() {
        assertRoundTrip(pitch = 30f, yaw = 0f, roll = 0f)
        assertRoundTrip(pitch = -45f, yaw = 0f, roll = 0f)
    }

    @Test
    fun eulerToQuaternionToEulerIsIdentityForYawOnly() {
        assertRoundTrip(pitch = 0f, yaw = 60f, roll = 0f)
        assertRoundTrip(pitch = 0f, yaw = -75f, roll = 0f)
    }

    @Test
    fun eulerToQuaternionToEulerIsIdentityForRollOnly() {
        assertRoundTrip(pitch = 0f, yaw = 0f, roll = 25f)
        assertRoundTrip(pitch = 0f, yaw = 0f, roll = -80f)
    }

    @Test
    fun eulerToQuaternionToEulerIsIdentityForCompositeRotation() {
        assertRoundTrip(pitch = 15f, yaw = 25f, roll = -10f)
        assertRoundTrip(pitch = -33f, yaw = 45f, roll = 12f)
    }

    // ── Normalisation ──

    @Test
    fun normalizeBringsMagnitudeToOne() {
        val q = Quaternion(2f, 0f, 0f, 0f).normalize()
        assertNear(1f, magnitude(q), "magnitude")
    }

    @Test
    fun normalizeOfZeroQuaternionReturnsIdentity() {
        val q = Quaternion(0f, 0f, 0f, 0f).normalize()
        assertEquals(Quaternion.IDENTITY, q)
    }

    // ── Slerp endpoints ──

    @Test
    fun slerpAtZeroReturnsStartQuaternion() {
        val a = Quaternion.fromEulerDegrees(10f, 20f, 30f)
        val b = Quaternion.fromEulerDegrees(40f, 50f, 60f)
        val result = Quaternion.slerp(a, b, 0f)
        assertQuaternionNear(a, result)
    }

    @Test
    fun slerpAtOneReturnsEndQuaternion() {
        val a = Quaternion.fromEulerDegrees(10f, 20f, 30f)
        val b = Quaternion.fromEulerDegrees(40f, 50f, 60f)
        val result = Quaternion.slerp(a, b, 1f)
        assertQuaternionNear(b, result)
    }

    @Test
    fun slerpAtHalfIsEquidistantFromBothEndpoints() {
        val a = Quaternion.fromEulerDegrees(0f, 0f, 0f)
        val b = Quaternion.fromEulerDegrees(0f, 90f, 0f)
        val mid = Quaternion.slerp(a, b, 0.5f)
        val distToA = angularDistance(mid, a)
        val distToB = angularDistance(mid, b)
        assertNear(distToA, distToB, "slerp midpoint should be equidistant from both endpoints")
    }

    // ── Slerp extrapolation (t > 1) ──

    @Test
    fun slerpBeyondOneExtrapolatesAlongTheSameArc() {
        // prev at 0°, cur at 10°, extrapolation at t=1 beyond cur (i.e. slerp(prev, cur, 1+1)=2)
        // should end up near 20° yaw, continuing the motion.
        val prev = Quaternion.fromEulerDegrees(0f, 0f, 0f)
        val cur = Quaternion.fromEulerDegrees(0f, 10f, 0f)
        val extrapolated = Quaternion.slerp(prev, cur, 2f)
        val euler = extrapolated.toEulerDegrees()
        assertNear(20f, euler.yaw, "extrapolated yaw")
    }

    // ── Antipodal handling ──

    @Test
    fun slerpHandlesAntipodalQuaternionsByTakingTheShorterArc() {
        // q and -q represent the same rotation but have opposite sign.
        // slerp must detect this and flip one so it does not take the long way around.
        val a = Quaternion(1f, 0f, 0f, 0f)
        val b = Quaternion(-a.w, -a.x, -a.y, -a.z)
        val mid = Quaternion.slerp(a, b, 0.5f)
        // Since a and b represent the same rotation, every slerp value must represent
        // that rotation too — the magnitude should be 1 and it should not produce NaN.
        assertNear(1f, magnitude(mid), "magnitude after antipodal slerp")
    }

    // ── Helpers ──

    private fun assertRoundTrip(pitch: Float, yaw: Float, roll: Float) {
        val q = Quaternion.fromEulerDegrees(pitch, yaw, roll)
        val euler = q.toEulerDegrees()
        assertNear(pitch, euler.pitch, "pitch round-trip (input=$pitch)")
        assertNear(yaw, euler.yaw, "yaw round-trip (input=$yaw)")
        assertNear(roll, euler.roll, "roll round-trip (input=$roll)")
    }

    private fun magnitude(q: Quaternion): Float =
        sqrt(q.w * q.w + q.x * q.x + q.y * q.y + q.z * q.z)

    private fun angularDistance(a: Quaternion, b: Quaternion): Float {
        val dot = abs(a.dot(b)).coerceIn(-1f, 1f)
        return kotlin.math.acos(dot)
    }

    private fun assertQuaternionNear(expected: Quaternion, actual: Quaternion) {
        // Account for the antipodal equivalence by comparing via the dot product magnitude.
        val dot = abs(expected.dot(actual))
        assertTrue(
            dot > ANTIPODAL_DOT_THRESHOLD,
            "Expected quaternion $expected, got $actual (|dot|=$dot)",
        )
    }

    private fun assertNear(expected: Float, actual: Float, label: String = "") {
        assertTrue(
            abs(expected - actual) < EPSILON,
            "$label: expected $expected, got $actual (delta=${abs(expected - actual)})",
        )
    }

    companion object {
        private const val EPSILON = 1e-3f
        private const val ANTIPODAL_DOT_THRESHOLD = 0.999f
    }
}
