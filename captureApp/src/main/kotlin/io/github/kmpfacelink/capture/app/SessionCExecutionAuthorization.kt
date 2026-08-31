package io.github.kmpfacelink.capture.app

import io.github.kmpfacelink.capture.CaptureStorageProfile
import io.github.kmpfacelink.capture.CaptureStudyProtocol
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.time.Instant
import kotlin.io.encoding.Base64

/** Public-safe, canonical Session-C identity checked before capacity, camera, or storage. */
internal data class SessionCExecutionAuthorization(
    val sourceClosureSha256: String,
    val apkSha256: String,
    val executionContractSha256: String,
    val binaryReceiptSha256: String,
    val cheekModelSha256: String,
    val mouthControlModelSha256: String,
    val runId: String,
    val expiresAt: Instant,
    val fixture: String,
)

internal data class SessionCAuthorizationExpectation(
    val sourceClosureSha256: String,
    val apkSha256: String,
    val executionContractSha256: String,
    val cheekModelSha256: String,
    val mouthControlModelSha256: String,
    val now: Instant,
    val permitSyntheticFixture: Boolean,
    val publicKeyX963: ByteArray = Base64.decode(SessionCExecutionAuthorizationVerifier.PUBLIC_KEY_X963_BASE64),
    val signingKeyId: String = SessionCExecutionAuthorizationVerifier.SIGNING_KEY_ID,
)

/** Parser/validator intentionally has no model payload, signing key, or device side effect. */
internal object SessionCExecutionAuthorizationVerifier {
    private const val SCHEMA = "session-c-execution-authorization-v1"
    private const val PRODUCTION_FIXTURE = "PRODUCTION"
    private const val SYNTHETIC_FIXTURE = "HOST_TEST_ONLY"
    internal const val SIGNING_KEY_ID =
        "e56a022d8e99eb94af5521b207b8dc2e73228cf7ca00eac9d94c911289c14f1c"
    internal const val PUBLIC_KEY_X963_BASE64 =
        "BPtWONREX15XsgS9mphckQyaEhgHIEyOwZYcKior2LxEzf4EcLPSQ2NZgcs4gJ/gGd91ciVSOxt//wkSgGZEE0I="
    private val keys = listOf(
        "schema",
        "protocolId",
        "storageProfile",
        "sourceClosureSha256",
        "apkSha256",
        "executionContractSha256",
        "binaryReceiptSha256",
        "cheekModelSha256",
        "mouthControlModelSha256",
        "runId",
        "expiresAt",
        "fixture",
        "signingKeyId",
        "signatureDerHex",
    )

    @Suppress("MagicNumber") // Numeric indices are the signed canonical line order defined above.
    fun verify(bytes: ByteArray, expected: SessionCAuthorizationExpectation): SessionCExecutionAuthorization {
        val text = bytes.decodeToString()
        require(text.encodeToByteArray().contentEquals(bytes) && '\r' !in text && text.endsWith('\n')) {
            "Session-C authorization must be canonical LF UTF-8"
        }
        val lines = text.split('\n')
        require(lines.size == keys.size + 1 && lines.last().isEmpty()) {
            "Session-C authorization must have exactly ${keys.size} lines"
        }
        val values = lines.dropLast(1).mapIndexed { index, line ->
            val separator = line.indexOf('=')
            require(separator > 0 && separator == line.lastIndexOf('=')) { "invalid line ${index + 1}" }
            require(line.substring(0, separator) == keys[index]) { "Session-C authorization order mismatch" }
            line.substring(separator + 1)
        }
        require(values[0] == SCHEMA) { "unsupported Session-C authorization schema" }
        require(values[1] == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) { "wrong Session-C protocol" }
        require(values[2] == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1.name) {
            "wrong Session-C storage profile"
        }
        listOf(values[3], values[4], values[5], values[6], values[7], values[8]).forEach(::requireSha256)
        require(values[3] == expected.sourceClosureSha256) { "wrong Session-C source closure" }
        require(values[4] == expected.apkSha256) { "wrong installed Session-C APK" }
        require(expected.executionContractSha256.matches(Regex("[0-9a-f]{64}"))) {
            "missing reviewed Session-C execution contract"
        }
        require(values[5] == expected.executionContractSha256) { "wrong Session-C execution contract" }
        require(values[7] == expected.cheekModelSha256) { "wrong Session-C cheek model" }
        require(values[8] == expected.mouthControlModelSha256) { "wrong Session-C mouth control model" }
        require(values[9].matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "invalid Session-C run ID" }
        val expiry = runCatching { Instant.parse(values[10]) }.getOrElse { error("invalid Session-C expiry") }
        require(expiry.isAfter(expected.now)) { "Session-C authorization is expired" }
        require(values[11] == PRODUCTION_FIXTURE || values[11] == SYNTHETIC_FIXTURE) { "invalid fixture" }
        require(values[11] == PRODUCTION_FIXTURE || expected.permitSyntheticFixture) {
            "synthetic Session-C authorization is test-only"
        }
        require(values[12] == expected.signingKeyId) { "untrusted Session-C signing key" }
        require(expected.signingKeyId.matches(Regex("[0-9a-f]{64}"))) { "invalid expected signing key ID" }
        require(sha256(expected.publicKeyX963) == expected.signingKeyId) {
            "Session-C public key identity mismatch"
        }
        val signature = decodeLowercaseHex(values[13])
        val signedPayload = lines.take(SIGNED_LINE_COUNT)
            .joinToString(separator = "\n", postfix = "\n")
            .encodeToByteArray()
        require(runCatching { verifyP256(expected.publicKeyX963, signedPayload, signature) }.getOrDefault(false)) {
            "Session-C authorization signature is invalid"
        }
        return SessionCExecutionAuthorization(
            values[3], values[4], values[5], values[6], values[7], values[8], values[9], expiry, values[11],
        )
    }

    private fun requireSha256(value: String) {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "expected lowercase SHA-256" }
    }

    private fun decodeLowercaseHex(value: String): ByteArray {
        require(
            value.length in 2..MAX_SIGNATURE_DER_HEX_LENGTH &&
                value.length % 2 == 0 &&
                value.all { it in '0'..'9' || it in 'a'..'f' },
        ) {
            "Session-C signature must be lowercase DER hex"
        }
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun verifyP256(publicKeyX963: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
        require(publicKeyX963.size == P256_UNCOMPRESSED_BYTES && publicKeyX963[0] == 0x04.toByte()) {
            "Session-C public key must be uncompressed P-256"
        }
        val parameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(ECParameterSpec::class.java)
        val coordinateSize = (P256_UNCOMPRESSED_BYTES - 1) / 2
        val x = BigInteger(1, publicKeyX963.copyOfRange(1, 1 + coordinateSize))
        val y = BigInteger(1, publicKeyX963.copyOfRange(1 + coordinateSize, publicKeyX963.size))
        val key = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), parameters))
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(payload)
            verify(signature)
        }
    }

    private fun sha256(bytes: ByteArray): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and BYTE_MASK) }

    private const val SIGNED_LINE_COUNT = 13
    private const val P256_UNCOMPRESSED_BYTES = 65
    private const val MAX_SIGNATURE_DER_HEX_LENGTH = 1024
    private const val BYTE_MASK = 0xff
}
