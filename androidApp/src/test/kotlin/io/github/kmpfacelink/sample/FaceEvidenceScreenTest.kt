package io.github.kmpfacelink.sample

import org.junit.Test
import kotlin.test.assertEquals

class FaceEvidenceScreenTest {

    @Test
    fun formatsFloorValuesWithoutRoundingThemToZeroPercent() {
        assertEquals("0.000010", formatEvidenceValue(0.00001f))
    }

    @Test
    fun formatsResponsiveControlOnTheSameScale() {
        assertEquals("0.978651", formatEvidenceValue(0.978651f))
    }

    @Test
    fun formatsGeometryDeltaInPercentOfInterOcularDistance() {
        assertEquals("1.54", formatGeometryValue(1.536))
    }
}
