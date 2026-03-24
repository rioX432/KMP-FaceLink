package io.github.kmpfacelink.vmc.osc

/**
 * A simple growable byte buffer for OSC message construction.
 *
 * Not thread-safe; intended for single-threaded encoding use.
 */
internal class OscByteBuffer {
    private var data = ByteArray(INITIAL_CAPACITY)
    private var position = 0

    fun writeByte(value: Int) {
        ensureCapacity(1)
        data[position++] = value.toByte()
    }

    fun write(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        bytes.copyInto(data, position)
        position += bytes.size
    }

    fun writeZeros(count: Int) {
        ensureCapacity(count)
        // ByteArray is zero-initialized, but we may be reusing grown buffer space
        for (i in 0 until count) {
            data[position + i] = 0
        }
        position += count
    }

    fun toByteArray(): ByteArray = data.copyOf(position)

    private fun ensureCapacity(additional: Int) {
        val required = position + additional
        if (required <= data.size) return
        var newSize = data.size
        while (newSize < required) {
            newSize *= 2
        }
        data = data.copyOf(newSize)
    }

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}
