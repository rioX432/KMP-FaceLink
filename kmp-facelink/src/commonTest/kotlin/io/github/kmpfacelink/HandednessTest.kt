package io.github.kmpfacelink

import io.github.kmpfacelink.model.Handedness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HandednessTest {

    @Test
    fun hasThreeEntries() {
        assertEquals(3, Handedness.entries.size)
    }

    @Test
    fun leftExists() {
        assertNotNull(Handedness.valueOf("LEFT"))
        assertEquals(Handedness.LEFT, Handedness.valueOf("LEFT"))
    }

    @Test
    fun rightExists() {
        assertNotNull(Handedness.valueOf("RIGHT"))
        assertEquals(Handedness.RIGHT, Handedness.valueOf("RIGHT"))
    }

    @Test
    fun unknownExists() {
        assertNotNull(Handedness.valueOf("UNKNOWN"))
        assertEquals(Handedness.UNKNOWN, Handedness.valueOf("UNKNOWN"))
    }

    @Test
    fun allNamesAreUnique() {
        val names = Handedness.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun valueOfRoundTripsAllEntries() {
        for (handedness in Handedness.entries) {
            assertEquals(handedness, Handedness.valueOf(handedness.name))
        }
    }
}
