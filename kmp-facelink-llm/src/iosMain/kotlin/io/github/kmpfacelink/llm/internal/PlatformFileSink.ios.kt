@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.llm.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSOutputStream
import platform.Foundation.NSURL

internal actual class PlatformFileSink actual constructor(path: String, append: Boolean) {
    private val stream: NSOutputStream

    init {
        if (!append) {
            // Ensure the file is created fresh
            NSFileManager.defaultManager.createFileAtPath(path, contents = null, attributes = null)
        }
        val url = NSURL.fileURLWithPath(path)
        stream = NSOutputStream(uRL = url, append = append)
        stream.open()
    }

    actual fun write(buffer: ByteArray, offset: Int, length: Int) {
        buffer.usePinned { pinned ->
            stream.write(
                pinned.addressOf(offset).reinterpret(),
                length.convert(),
            )
        }
    }

    actual fun close() {
        stream.close()
    }
}
