package io.github.kmpfacelink.voice.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AudioFormatTest {

    @Test
    fun defaultValuesAreCorrect() {
        val format = AudioFormat()
        assertEquals(16000, format.sampleRate)
        assertEquals(1, format.channels)
        assertEquals(16, format.bitsPerSample)
    }

    @Test
    fun customValuesAreStored() {
        val format = AudioFormat(sampleRate = 44100, channels = 2, bitsPerSample = 32)
        assertEquals(44100, format.sampleRate)
        assertEquals(2, format.channels)
        assertEquals(32, format.bitsPerSample)
    }

    @Test
    fun equalityByValue() {
        val a = AudioFormat(sampleRate = 24000, channels = 1, bitsPerSample = 16)
        val b = AudioFormat(sampleRate = 24000, channels = 1, bitsPerSample = 16)
        assertEquals(a, b)
    }

    @Test
    fun inequalityOnDifferentSampleRate() {
        val a = AudioFormat(sampleRate = 16000)
        val b = AudioFormat(sampleRate = 44100)
        assertNotEquals(a, b)
    }

    @Test
    fun inequalityOnDifferentChannels() {
        val a = AudioFormat(channels = 1)
        val b = AudioFormat(channels = 2)
        assertNotEquals(a, b)
    }

    @Test
    fun inequalityOnDifferentBitsPerSample() {
        val a = AudioFormat(bitsPerSample = 16)
        val b = AudioFormat(bitsPerSample = 32)
        assertNotEquals(a, b)
    }

    @Test
    fun copyPreservesValues() {
        val original = AudioFormat(sampleRate = 48000, channels = 2, bitsPerSample = 24)
        val copy = original.copy(channels = 1)
        assertEquals(48000, copy.sampleRate)
        assertEquals(1, copy.channels)
        assertEquals(24, copy.bitsPerSample)
    }

    @Test
    fun hashCodeConsistentWithEquals() {
        val a = AudioFormat(sampleRate = 22050, channels = 1, bitsPerSample = 16)
        val b = AudioFormat(sampleRate = 22050, channels = 1, bitsPerSample = 16)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
