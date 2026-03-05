package io.github.kmpfacelink

import io.github.kmpfacelink.model.HandGesture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HandGestureTest {

    @Test
    fun has16Entries() {
        assertEquals(16, HandGesture.entries.size)
    }

    @Test
    fun allExpectedValuesExist() {
        assertNotNull(HandGesture.valueOf("NONE"))
        assertNotNull(HandGesture.valueOf("CLOSED_FIST"))
        assertNotNull(HandGesture.valueOf("OPEN_PALM"))
        assertNotNull(HandGesture.valueOf("POINTING_UP"))
        assertNotNull(HandGesture.valueOf("THUMB_UP"))
        assertNotNull(HandGesture.valueOf("THUMB_DOWN"))
        assertNotNull(HandGesture.valueOf("VICTORY"))
        assertNotNull(HandGesture.valueOf("I_LOVE_YOU"))
        assertNotNull(HandGesture.valueOf("PINCH"))
        assertNotNull(HandGesture.valueOf("OK_SIGN"))
        assertNotNull(HandGesture.valueOf("ROCK"))
        assertNotNull(HandGesture.valueOf("FINGER_COUNT_ONE"))
        assertNotNull(HandGesture.valueOf("FINGER_COUNT_TWO"))
        assertNotNull(HandGesture.valueOf("FINGER_COUNT_THREE"))
        assertNotNull(HandGesture.valueOf("FINGER_COUNT_FOUR"))
        assertNotNull(HandGesture.valueOf("FINGER_COUNT_FIVE"))
    }

    @Test
    fun noneIsFirstEntry() {
        assertEquals(HandGesture.NONE, HandGesture.entries[0])
    }

    @Test
    fun allNamesAreUnique() {
        val names = HandGesture.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun valueOfRoundTripsAllEntries() {
        for (gesture in HandGesture.entries) {
            assertEquals(gesture, HandGesture.valueOf(gesture.name))
        }
    }
}
