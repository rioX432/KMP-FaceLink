package io.github.kmpfacelink.llm.internal

import java.io.BufferedOutputStream
import java.io.FileOutputStream

internal actual class PlatformFileSink actual constructor(path: String, append: Boolean) {
    private val stream = BufferedOutputStream(FileOutputStream(path, append))

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        stream.write(buffer, offset, length)
    }

    actual fun close() {
        stream.flush()
        stream.close()
    }
}
