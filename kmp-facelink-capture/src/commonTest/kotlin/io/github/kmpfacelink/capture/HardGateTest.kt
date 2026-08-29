package io.github.kmpfacelink.capture

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue


class HardGateTest {

    // Centered, adequately sized box; luma in range — so only IOD decides the extra gate.
    private val goodBox = HardGate.FaceBox(centerX = 0.5f, centerY = 0.5f, width = 0.4f, height = 0.5f)
    private val goodLuma = 120f

    @Test
    fun `iod inside band passes the distance gate`() {
        val result = HardGate.evaluate(goodBox, goodLuma, iod = 0.33f)
        assertTrue(result.distanced)
        assertTrue(result.passed)
    }

    @Test
    fun `iod below band fails distance and overall gate`() {
        val result = HardGate.evaluate(goodBox, goodLuma, iod = 0.20f)
        assertFalse(result.distanced)
        assertFalse(result.passed)
    }

    @Test
    fun `iod above band fails distance gate`() {
        val result = HardGate.evaluate(goodBox, goodLuma, iod = 0.42f)
        assertFalse(result.distanced)
        assertFalse(result.passed)
    }

    @Test
    fun `no face yields all-false result`() {
        val result = HardGate.evaluate(box = null, luma = goodLuma, iod = 0.33f)
        assertFalse(result.hasFace)
        assertFalse(result.distanced)
        assertFalse(result.passed)
    }

    @Test
    fun `distance hint points the subject the right way`() {
        assertEquals(HardGate.DistanceHint.MOVE_CLOSER, HardGate.distanceHint(0.20f, hasFace = true))
        assertEquals(HardGate.DistanceHint.MOVE_BACK, HardGate.distanceHint(0.42f, hasFace = true))
        assertEquals(HardGate.DistanceHint.NONE, HardGate.distanceHint(0.33f, hasFace = true))
        assertEquals(HardGate.DistanceHint.NONE, HardGate.distanceHint(0.20f, hasFace = false))
    }

    @Test
    fun `iod metric is aspect-corrected outer-eye distance`() {
        val landmarks = FloatArray(478 * 3)
        landmarks[33 * 3] = 0.4f
        landmarks[33 * 3 + 1] = 0.5f
        landmarks[263 * 3] = 0.6f
        landmarks[263 * 3 + 1] = 0.6f
        // dx=0.2, dy=0.1; iod = hypot(0.2, 0.1*1.333)
        assertTrue(abs((FaceMetrics.iod(landmarks, aspect = 1.333f)) - (0.2403f)) <= (1e-3f))
    }
}
