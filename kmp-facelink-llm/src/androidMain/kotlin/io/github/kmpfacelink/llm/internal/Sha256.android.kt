package io.github.kmpfacelink.llm.internal

import java.io.FileInputStream
import java.security.MessageDigest

private const val SHA256_BUFFER_SIZE = 8192

@OptIn(ExperimentalStdlibApi::class)
internal actual suspend fun sha256Hex(filePath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(SHA256_BUFFER_SIZE)
    FileInputStream(filePath).use { fis ->
        var bytesRead = fis.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            bytesRead = fis.read(buffer)
        }
    }
    return digest.digest().toHexString()
}
