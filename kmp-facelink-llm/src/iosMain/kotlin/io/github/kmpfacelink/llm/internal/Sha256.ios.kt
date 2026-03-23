@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.llm.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.Foundation.NSData
import platform.Foundation.NSInputStream
import platform.Foundation.dataWithContentsOfFile

private const val SHA256_BUFFER_SIZE = 8192

@OptIn(ExperimentalStdlibApi::class)
internal actual suspend fun sha256Hex(filePath: String): String {
    val data = NSData.dataWithContentsOfFile(filePath)
        ?: error("Cannot read file: $filePath")

    val context = nativeHeap.alloc<CC_SHA256_CTX>()
    CC_SHA256_Init(context.ptr)

    val inputStream = NSInputStream(data = data)
    inputStream.open()

    try {
        readAndHashChunks(inputStream, context)
        return computeDigestHex(context)
    } finally {
        inputStream.close()
        nativeHeap.free(context)
    }
}

private fun readAndHashChunks(inputStream: NSInputStream, context: CC_SHA256_CTX) {
    val buffer = ByteArray(SHA256_BUFFER_SIZE)
    while (inputStream.hasBytesAvailable) {
        val bytesRead = buffer.usePinned { pinned ->
            inputStream.read(
                pinned.addressOf(0).reinterpret(),
                SHA256_BUFFER_SIZE.convert(),
            )
        }
        if (bytesRead <= 0) break
        buffer.usePinned { pinned ->
            CC_SHA256_Update(
                context.ptr,
                pinned.addressOf(0),
                bytesRead.convert(),
            )
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun computeDigestHex(context: CC_SHA256_CTX): String {
    val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
    digest.usePinned { pinned ->
        CC_SHA256_Final(pinned.addressOf(0).reinterpret(), context.ptr)
    }
    return digest.toHexString()
}
