package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H2CueContractTest {
    @Test
    fun exactV2CueSchedulesCoverEveryBlockWithoutGaps() {
        H2ConfirmationContract.blocks.forEach { block ->
            val segments = H2CueContract.segmentsFor(block.id)
            assertEquals(0, segments.first().startMillis)
            assertEquals(block.totalDurationSeconds * 1_000L, segments.last().endMillis)
            segments.zipWithNext().forEach { (left, right) ->
                assertEquals(left.endMillis, right.startMillis)
            }
        }
        assertEquals(1, H2CueContract.segmentsFor("B01_STARTUP_NEUTRAL_READINESS").size)
        assertEquals(2, H2CueContract.segmentsFor("B02_OPENING_NEUTRAL_READY").size)
        assertEquals(2, H2CueContract.segmentsFor("B10_NATURAL_SPEECH").size)
        assertEquals(4, H2CueContract.segmentsFor("B03_JAW_OPEN").size)
        assertEquals(6, H2CueContract.segmentsFor("B04_PUKU_BILATERAL_FRONT_LIGHT_1").size)
    }

    @Test
    fun trackerRejectsWrongOrderBoundaryAndClockWithoutFabricatingCompletion() {
        val tracker = H2CueTracker("B03_JAW_OPEN", 1_000_000_000)
        assertFailsWith<IllegalArgumentException> {
            tracker.route(H2CueSegmentName.READY_PREPARE, 3_000, 4_000_000_000)
        }
        assertFailsWith<IllegalArgumentException> {
            tracker.route(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 1, 1_001_000_000)
        }
        assertFailsWith<IllegalArgumentException> {
            tracker.route(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 1_000_000_001)
        }
        assertFalse(tracker.complete)
        H2CueContract.segmentsFor("B03_JAW_OPEN").forEach { segment ->
            tracker.route(
                segment.name,
                segment.startMillis,
                1_000_000_000 + segment.startMillis * 1_000_000L,
            )
            tracker.acknowledge(segment.name, 1_000_000_000 + segment.startMillis * 1_000_000L)
        }
        assertTrue(tracker.complete)
    }

    @Test
    fun composeCueAcknowledgementCannotArriveAfterItsSegmentEnd() {
        val origin = 1_000_000_000L
        val tracker = H2CueTracker("B03_JAW_OPEN", origin)
        tracker.route(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, origin)
        assertFailsWith<IllegalArgumentException> {
            tracker.acknowledge(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, origin + 3_000_000_001L)
        }
        assertFalse(tracker.complete)
    }
}
