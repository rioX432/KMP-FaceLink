@file:OptIn(ExperimentalForeignApi::class, kotlin.ExperimentalStdlibApi::class)
@file:Suppress(
    "TooGenericExceptionCaught", // Publish-last cleanup must cover every write/sync failure.
    "TooManyFunctions", // One native filesystem boundary keeps all POSIX/Foundation rules together.
)

package io.github.kmpfacelink.capture

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSCondition
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.S_IWUSR
import platform.posix.errno
import platform.posix.fsync
import platform.posix.realpath
import platform.posix.rename
import platform.posix.strerror
import platform.posix.unlink
import platform.posix.close as posixClose
import platform.posix.free as posixFree
import platform.posix.open as posixOpen
import platform.posix.read as posixRead
import platform.posix.write as posixWrite

internal inline fun <T> NSLock.withCaptureLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

internal inline fun <T> NSCondition.withCaptureLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

internal class NativeOutput private constructor(
    internal val path: String,
    private var descriptor: Int,
) {
    fun write(bytes: ByteArray) {
        check(descriptor >= 0) { "$path is closed" }
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                val count = posixWrite(
                    descriptor,
                    pinned.addressOf(offset).reinterpret<ByteVar>(),
                    (bytes.size - offset).convert(),
                )
                check(count > 0) { "could not write $path: ${nativeError()}" }
                offset += count.toInt()
            }
        }
    }

    /** Writes an immutable native payload without copying it through a Kotlin ByteArray. */
    fun write(data: NSData) {
        check(descriptor >= 0) { "$path is closed" }
        val size = data.length.toLong()
        check(size > 0L && size <= Int.MAX_VALUE) { "invalid native payload size $size for $path" }
        // A regular local capture file should accept the immutable snapshot in one write. A short
        // write is deliberately terminal rather than silently writing a partial frame; retrying
        // would require C-pointer arithmetic over NSData and risks making a mutable source appear
        // complete. The bounded sink converts this failure into a discarded take.
        val count = posixWrite(descriptor, data.bytes, size.convert())
        check(count == size) { "could not write complete native payload to $path: ${nativeError()}" }
    }

    fun closeAndSync() {
        if (descriptor < 0) return
        val current = descriptor
        descriptor = -1
        val syncResult = fsync(current)
        val syncError = if (syncResult == 0) null else nativeError()
        val closeResult = posixClose(current)
        check(syncError == null && closeResult == 0) {
            "could not finalize $path: ${syncError ?: nativeError()}"
        }
    }

    fun closeAfterFailure() {
        if (descriptor >= 0) {
            posixClose(descriptor)
            descriptor = -1
        }
    }

    companion object {
        fun create(path: String): NativeOutput {
            val mode = S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH
            val descriptor = posixOpen(path, O_WRONLY or O_CREAT or O_TRUNC, mode)
            check(descriptor >= 0) { "could not open $path: ${nativeError()}" }
            return NativeOutput(path, descriptor)
        }
    }
}

internal fun ensureCaptureDirectory(path: String): String {
    val manager = NSFileManager.defaultManager
    if (!manager.fileExistsAtPath(path)) {
        check(
            manager.createDirectoryAtPath(
                path,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        ) { "could not create $path to record a take into" }
    }
    val canonical = checkNotNull(realpath(path, null)) {
        "could not resolve $path: ${nativeError()}"
    }
    return try {
        canonical.toKString()
    } finally {
        posixFree(canonical)
    }
}

internal fun removeCapturePath(path: String): Boolean {
    if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return true
    return NSFileManager.defaultManager.removeItemAtPath(path, error = null)
}

internal fun moveCapturePath(from: String, to: String): Boolean =
    NSFileManager.defaultManager.moveItemAtPath(from, toPath = to, error = null)

internal fun joinCapturePath(directory: String, name: String): String =
    if (directory.endsWith('/')) "$directory$name" else "$directory/$name"

internal fun publishCaptureText(directory: String, name: String, text: String) {
    val target = joinCapturePath(directory, name)
    val staging = "$target.partial"
    removeCapturePath(staging)
    val output = NativeOutput.create(staging)
    try {
        output.write(text.encodeToByteArray())
        output.closeAndSync()
        check(rename(staging, target) == 0) {
            "could not publish $target from $staging: ${nativeError()}"
        }
    } catch (t: Throwable) {
        output.closeAfterFailure()
        unlink(staging)
        throw t
    }
}

internal fun readCaptureBytes(path: String): ByteArray {
    val descriptor = posixOpen(path, O_RDONLY)
    check(descriptor >= 0) { "could not open $path: ${nativeError()}" }
    val chunks = ArrayList<ByteArray>()
    var total = 0
    try {
        while (true) {
            val buffer = ByteArray(TEXT_BUFFER_BYTES)
            val count = buffer.usePinned { pinned ->
                posixRead(descriptor, pinned.addressOf(0), buffer.size.convert())
            }
            check(count >= 0) { "could not read $path: ${nativeError()}" }
            if (count == 0L) break
            chunks += buffer.copyOf(count.toInt())
            total += count.toInt()
        }
    } finally {
        posixClose(descriptor)
    }
    val bytes = ByteArray(total)
    var offset = 0
    chunks.forEach { chunk ->
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    return bytes
}

internal fun readCaptureText(path: String): String = readCaptureBytes(path).decodeToString()

internal fun capturePathExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

internal fun captureFileSize(path: String): Long {
    val attributes = checkNotNull(
        NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null),
    ) { "could not read file attributes for $path" }
    return checkNotNull(attributes[NSFileSize] as? NSNumber) {
        "file size is missing for $path"
    }.longLongValue
}

internal fun captureAvailableBytes(path: String): Long {
    val attributes = checkNotNull(
        NSFileManager.defaultManager.attributesOfFileSystemForPath(path, error = null),
    ) { "could not read filesystem attributes for $path" }
    return checkNotNull(attributes[NSFileSystemFreeSize] as? NSNumber) {
        "free-space value is missing for $path"
    }.longLongValue
}

internal fun sha256OfCaptureFile(path: String): String {
    val descriptor = posixOpen(path, O_RDONLY)
    check(descriptor >= 0) { "could not open $path for hashing: ${nativeError()}" }
    val context = kotlinx.cinterop.nativeHeap.alloc<CC_SHA256_CTX>()
    val buffer = ByteArray(HASH_BUFFER_BYTES)
    try {
        CC_SHA256_Init(context.ptr)
        while (true) {
            val count = buffer.usePinned { pinned ->
                posixRead(descriptor, pinned.addressOf(0), buffer.size.convert())
            }
            check(count >= 0) { "could not hash $path: ${nativeError()}" }
            if (count == 0L) break
            buffer.usePinned { pinned ->
                CC_SHA256_Update(context.ptr, pinned.addressOf(0), count.convert())
            }
        }
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { pinned ->
            CC_SHA256_Final(pinned.addressOf(0).reinterpret(), context.ptr)
        }
        return digest.toHexString()
    } finally {
        posixClose(descriptor)
        kotlinx.cinterop.nativeHeap.free(context)
    }
}

internal fun ByteArray.putIntLittleEndian(offset: Int, value: Int) {
    repeat(Int.SIZE_BYTES) { index ->
        this[offset + index] = (value ushr (index * Byte.SIZE_BITS)).toByte()
    }
}

internal fun ByteArray.putLongLittleEndian(offset: Int, value: Long) {
    repeat(Long.SIZE_BYTES) { index ->
        this[offset + index] = (value ushr (index * Byte.SIZE_BITS)).toByte()
    }
}

internal fun ByteArray.putFloatLittleEndian(offset: Int, value: Float) =
    putIntLittleEndian(offset, value.toRawBits())

private fun nativeError(): String = strerror(errno)?.toKString() ?: "errno $errno"

private const val HASH_BUFFER_BYTES = 1 shl 16
private const val TEXT_BUFFER_BYTES = 4_096
