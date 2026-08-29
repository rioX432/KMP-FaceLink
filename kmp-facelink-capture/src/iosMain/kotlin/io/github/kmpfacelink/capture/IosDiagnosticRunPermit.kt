@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.ExperimentalStdlibApi::class)

package io.github.kmpfacelink.capture

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFNumberSInt64Type
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSFileTypeSymbolicLink
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyVerifySignature
import platform.Security.errSecSuccess
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.open
import platform.posix.read

/** Internal, non-exported authority created only after the native verifier succeeds. */
internal class IosDiagnosticRunPermit(
    internal val evidence: ReviewEvidenceBinding,
)

/**
 * Kotlin/Native verification authority used at the final create-and-start boundary.
 *
 * It intentionally reads the resource and bundle from the process again. Swift's verifier is only
 * a UX preflight; neither Swift booleans nor a caller-constructed ReviewEvidenceBinding can pass
 * this boundary.
 */
internal object IosDiagnosticRunPermitVerifier {
    private const val PLATFORM = "ios-app-bundle"
    private const val SCHEMA = "facelink-run-authorization-v1"
    private const val SIGNING_KEY_ID =
        "e56a022d8e99eb94af5521b207b8dc2e73228cf7ca00eac9d94c911289c14f1c"
    private const val MAX_AUTHORIZATION_BYTES = 16 * 1024
    private val publicKeyX963 = base64Decode(
        "BPtWONREX15XsgS9mphckQyaEhgHIEyOwZYcKior2LxEzf4EcLPSQ2NZgcs4gJ/gGd91ciVSOxt//wkSgGZEE0I=",
    )
    private val authorizationKeys = listOf(
        "schema",
        "platform",
        "binaryPacketId",
        "binaryPacketManifestSha256",
        "binarySha256",
        "sourceAuthorizationSha256",
        "signingKeyId",
        "signatureDerHex",
    )

    fun verify(rawAuthorization: NSData): IosDiagnosticRunPermit {
        val authorizationBytes = rawAuthorization.toByteArray()
        check(authorizationBytes.size in 1..MAX_AUTHORIZATION_BYTES) {
            "run authorization must be non-empty and no larger than $MAX_AUTHORIZATION_BYTES bytes"
        }
        val source = readSourceEvidence()
        val bundleSha256 = canonicalAppBundleSha256()
        val values = parseCanonicalAuthorization(authorizationBytes)

        check(values.getValue("schema") == SCHEMA) { "unsupported run authorization schema" }
        check(values.getValue("platform") == PLATFORM) {
            "run authorization platform does not match this iOS app bundle"
        }
        val binaryPacketId = values.getValue("binaryPacketId")
        check(binaryPacketId.isCanonicalPacketId()) { "binary review packet ID is not canonical" }
        val binaryPacketManifestSha = values.getValue("binaryPacketManifestSha256")
        check(binaryPacketManifestSha.isSha256Hex()) {
            "binary review packet manifest must be a lowercase SHA-256"
        }
        check(values.getValue("binarySha256") == bundleSha256) {
            "run authorization does not name the complete installed app bundle"
        }
        check(values.getValue("sourceAuthorizationSha256") == source.authorizationSha256) {
            "run authorization does not name the embedded source authorization"
        }
        check(values.getValue("signingKeyId") == SIGNING_KEY_ID) {
            "run authorization signing key is not trusted"
        }
        check(sha256(publicKeyX963) == SIGNING_KEY_ID) {
            "compiled signing key does not match its declared identity"
        }
        val signature = hexDecode(values.getValue("signatureDerHex"))
        val payload = canonicalPayload(authorizationBytes)
        check(verifyP256Signature(payload, signature)) { "run authorization signature is invalid" }

        return IosDiagnosticRunPermit(
            ReviewEvidenceBinding(
                sourcePacketId = source.packetId,
                sourcePacketManifestSha256 = source.packetManifestSha256,
                sourceManifestSha256 = source.sourceManifestSha256,
                sourceAuthorizationSha256 = source.authorizationSha256,
                binaryPacketId = binaryPacketId,
                binaryPacketManifestSha256 = binaryPacketManifestSha,
                binarySha256 = bundleSha256,
                runAuthorizationSha256 = sha256(authorizationBytes),
                runAuthorizationSigningKeyId = SIGNING_KEY_ID,
            ),
        )
    }

    private fun readSourceEvidence(): SourceEvidence {
        val path = checkNotNull(
            NSBundle.mainBundle.pathForResource("FaceLinkReviewAuthorization", ofType = "properties"),
        ) { "this app bundle has no source-review authorization" }
        val bytes = readRegularFile(path)
        val values = parseCanonicalProperties(bytes, listOf(
            "packetId",
            "packetManifestSha256",
            "sourceManifestSha256",
        ))
        val packetId = values.getValue("packetId")
        val packetManifestSha = values.getValue("packetManifestSha256")
        val sourceManifestSha = values.getValue("sourceManifestSha256")
        check(packetId.isCanonicalPacketId()) { "embedded source packet ID is not canonical" }
        check(packetManifestSha.isSha256Hex() && sourceManifestSha.isSha256Hex()) {
            "embedded source review binding is incomplete or noncanonical"
        }
        return SourceEvidence(packetId, packetManifestSha, sourceManifestSha, sha256(bytes))
    }

    private fun parseCanonicalAuthorization(bytes: ByteArray): Map<String, String> {
        val values = parseCanonicalProperties(bytes, authorizationKeys)
        val signature = values.getValue("signatureDerHex")
        check(signature.length >= 2 && signature.length % 2 == 0 && signature.all { it.isLowercaseHexDigit() }) {
            "run authorization signature must use lowercase DER hex"
        }
        return values
    }

    private fun parseCanonicalProperties(bytes: ByteArray, expectedKeys: List<String>): Map<String, String> {
        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (failure: Throwable) {
            throw IllegalStateException("authorization must be canonical UTF-8", failure)
        }
        check(!text.contains('\r') && text.endsWith('\n')) {
            "authorization must use LF and end with exactly one LF"
        }
        val lines = text.split('\n')
        check(lines.size == expectedKeys.size + 1 && lines.last().isEmpty()) {
            "authorization has an unexpected number of LF-terminated lines"
        }
        return buildMap {
            expectedKeys.forEachIndexed { index, expectedKey ->
                val line = lines[index]
                check(line.count { it == '=' } == 1) {
                    "authorization line ${index + 1} must contain exactly one '='"
                }
                val key = line.substringBefore('=')
                val value = line.substringAfter('=')
                check(key == expectedKey) { "authorization keys/order are not canonical" }
                put(key, value)
            }
        }
    }

    private fun canonicalPayload(authorizationBytes: ByteArray): ByteArray {
        val firstSevenLines = authorizationBytes.decodeToString(throwOnInvalidSequence = true)
            .split('\n')
            .take(7)
            .joinToString("\n", postfix = "\n")
        return firstSevenLines.encodeToByteArray()
    }

    /** Complete bundle digest: sorted relative names, lengths, then every regular file's bytes. */
    private fun canonicalAppBundleSha256(): String {
        val root = NSBundle.mainBundle.bundlePath
        val manager = NSFileManager.defaultManager
        val files = ArrayList<BundleFile>()

        fun collect(directory: String, relativeDirectory: String) {
            val entries = (manager.contentsOfDirectoryAtPath(directory, error = null) as? List<*>)
                ?.filterIsInstance<String>()
                ?.sorted()
                ?: error("app bundle directory cannot be enumerated")
            entries.forEach { name ->
                val path = "$directory/$name"
                val relative = if (relativeDirectory.isEmpty()) name else "$relativeDirectory/$name"
                val attributes = manager.attributesOfItemAtPath(path, error = null)
                    ?: error("app bundle item cannot be inspected")
                when (attributes[NSFileType] as? String) {
                    NSFileTypeSymbolicLink -> error("app bundle symlinks are not accepted by the canonical digest")
                    NSFileTypeDirectory -> collect(path, relative)
                    NSFileTypeRegular -> {
                        val size = (attributes[NSFileSize] as? Number)?.toLong()
                            ?: error("app bundle regular file has no size")
                        check(size >= 0) { "app bundle regular file has invalid size" }
                        files += BundleFile(relative, path, size)
                    }
                    else -> Unit
                }
            }
        }

        collect(root, "")
        check(files.isNotEmpty()) { "app bundle contains no regular files" }
        val hash = NativeSha256()
        files.sortedBy { it.relative }.forEach { file ->
            val path = file.relative.encodeToByteArray()
            check(path.size <= UInt.MAX_VALUE.toLong()) { "app bundle relative path is too long" }
            hash.updateUInt32(path.size.toUInt())
            hash.update(path)
            hash.updateUInt64(file.size.toULong())
            hash.updateFile(file.path)
        }
        return hash.finish().toHexString()
    }

    private fun verifyP256Signature(payload: ByteArray, signature: ByteArray): Boolean {
        return memScoped {
            val keySize = allocArray<LongVar>(1).apply { this[0] = 256L }
            val keyData = publicKeyX963.toCFData()
            val payloadData = payload.toCFData()
            val signatureData = signature.toCFData()
            val keySizeNumber = CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt64Type, keySize)
            val keys = allocArray<COpaquePointerVar>(3)
            val values = allocArray<COpaquePointerVar>(3)
            keys[0] = kSecAttrKeyType
            values[0] = kSecAttrKeyTypeECSECPrimeRandom
            keys[1] = kSecAttrKeyClass
            values[1] = kSecAttrKeyClassPublic
            keys[2] = kSecAttrKeySizeInBits
            values[2] = keySizeNumber
            val attributes = CFDictionaryCreate(
                kCFAllocatorDefault,
                keys,
                values,
                3,
                null,
                null,
            )
            try {
                val key = SecKeyCreateWithData(keyData, attributes, null) ?: return@memScoped false
                try {
                    SecKeyVerifySignature(
                        key,
                        kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
                        payloadData,
                        signatureData,
                        null,
                    )
                } finally {
                    CFRelease(key)
                }
            } finally {
                CFRelease(attributes)
                CFRelease(keySizeNumber)
                CFRelease(signatureData)
                CFRelease(payloadData)
                CFRelease(keyData)
            }
        }
    }

    private data class SourceEvidence(
        val packetId: String,
        val packetManifestSha256: String,
        val sourceManifestSha256: String,
        val authorizationSha256: String,
    )

    private data class BundleFile(val relative: String, val path: String, val size: Long)
}

private class NativeSha256 {
    private val context = kotlinx.cinterop.nativeHeap.alloc<CC_SHA256_CTX>()

    init {
        CC_SHA256_Init(context.ptr)
    }

    fun update(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            CC_SHA256_Update(context.ptr, pinned.addressOf(0), bytes.size.convert())
        }
    }

    fun updateUInt32(value: UInt) = update(
        ByteArray(UInt.SIZE_BYTES) { index -> (value shr ((UInt.SIZE_BYTES - 1 - index) * 8)).toByte() },
    )

    fun updateUInt64(value: ULong) = update(
        ByteArray(ULong.SIZE_BYTES) { index -> (value shr ((ULong.SIZE_BYTES - 1 - index) * 8)).toByte() },
    )

    fun updateFile(path: String) {
        val descriptor = open(path, O_RDONLY)
        check(descriptor >= 0) { "could not read app bundle file $path" }
        val buffer = ByteArray(64 * 1024)
        try {
            while (true) {
                val count = buffer.usePinned { pinned ->
                    read(descriptor, pinned.addressOf(0).reinterpret<ByteVar>(), buffer.size.convert())
                }
                check(count >= 0) { "could not read app bundle file $path" }
                if (count == 0L) break
                buffer.usePinned { pinned ->
                    CC_SHA256_Update(context.ptr, pinned.addressOf(0), count.convert())
                }
            }
        } finally {
            close(descriptor)
        }
    }

    fun finish(): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { pinned -> CC_SHA256_Final(pinned.addressOf(0).reinterpret(), context.ptr) }
        kotlinx.cinterop.nativeHeap.free(context)
        return digest
    }
}

private fun readRegularFile(path: String): ByteArray {
    val descriptor = open(path, O_RDONLY)
    check(descriptor >= 0) { "could not read $path" }
    val chunks = ArrayList<ByteArray>()
    var total = 0
    try {
        while (true) {
            val buffer = ByteArray(4096)
            val count = buffer.usePinned { pinned -> read(descriptor, pinned.addressOf(0), buffer.size.convert()) }
            check(count >= 0) { "could not read $path" }
            if (count == 0L) break
            chunks += buffer.copyOf(count.toInt())
            total += count.toInt()
        }
    } finally {
        close(descriptor)
    }
    return ByteArray(total).also { all ->
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(all, offset)
            offset += chunk.size
        }
    }
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) {
        output.usePinned { pinned -> platform.posix.memcpy(pinned.addressOf(0), bytes, output.size.convert()) }
    }
}

private fun ByteArray.toCFData() = usePinned { pinned ->
    CFDataCreate(
        kCFAllocatorDefault,
        if (isEmpty()) null else pinned.addressOf(0).reinterpret<UByteVar>(),
        size.convert(),
    ) ?: error("could not create CoreFoundation data")
}

private fun sha256(bytes: ByteArray): String = NativeSha256().also { it.update(bytes) }.finish().toHexString()

private fun ByteArray.toHexString(): String = joinToString("") { byte ->
    HEX_DIGITS[byte.toInt().ushr(4) and 0x0f].toString() + HEX_DIGITS[byte.toInt() and 0x0f]
}

private fun String.isSha256Hex(): Boolean = length == 64 && all { it.isLowercaseHexDigit() }

private fun Char.isLowercaseHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'

private fun String.isCanonicalPacketId(): Boolean =
    isNotEmpty() && length <= 128 && first().isAsciiLowerOrDigit() && all {
        it.isAsciiLowerOrDigit() || it == '.' || it == '_' || it == '-'
    }

private fun Char.isAsciiLowerOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'

private fun hexDecode(text: String): ByteArray {
    check(text.length % 2 == 0 && text.all { it.isLowercaseHexDigit() }) { "invalid lowercase hexadecimal" }
    return ByteArray(text.length / 2) { index ->
        text.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private fun base64Decode(text: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val clean = text.filterNot(Char::isWhitespace)
    check(clean.length % 4 == 0) { "invalid base64" }
    val output = ArrayList<Byte>()
    clean.chunked(4).forEach { quartet ->
        val first = alphabet.indexOf(quartet[0])
        val second = alphabet.indexOf(quartet[1])
        val third = if (quartet[2] == '=') 0 else alphabet.indexOf(quartet[2])
        val fourth = if (quartet[3] == '=') 0 else alphabet.indexOf(quartet[3])
        check(first >= 0 && second >= 0 && third >= 0 && fourth >= 0) { "invalid base64" }
        output += ((first shl 2) or (second shr 4)).toByte()
        if (quartet[2] != '=') output += (((second and 0x0f) shl 4) or (third shr 2)).toByte()
        if (quartet[3] != '=') output += (((third and 0x03) shl 6) or fourth).toByte()
    }
    return output.toByteArray()
}

private const val HEX_DIGITS = "0123456789abcdef"
