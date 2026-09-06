package io.github.kmpfacelink.capture

import android.content.Context
import io.github.kmpfacelink.api.PlatformContext
import java.io.File
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlin.io.encoding.Base64

/** Canonical source-review values embedded by the installed candidate build. */
public data class EmbeddedSourceReviewEvidence(
    val packetId: String,
    val packetManifestSha256: String,
    val sourceManifestSha256: String,
    val authorizationSha256: String,
)

/**
 * Values read from the installed app's generated BuildConfig, never supplied by an SDK caller.
 * The exact-build execution gate reads this afresh for each execution attempt.
 */
internal data class InstalledAndroidBuildEvidence(
    val sourceReview: EmbeddedSourceReviewEvidence,
    val provenance: SourceProvenance,
)

/** Reads immutable generated fields from the installed app package rather than caller arguments. */
internal object InstalledAndroidBuildEvidenceReader {
    fun read(context: Context): InstalledAndroidBuildEvidence {
        val buildConfig = Class.forName("${context.packageName}.BuildConfig")
        fun stringField(name: String): String = buildConfig.getField(name).get(null) as? String
            ?: error("installed BuildConfig.$name is not a String")
        fun booleanField(name: String): Boolean = buildConfig.getField(name).getBoolean(null)

        return InstalledAndroidBuildEvidence(
            sourceReview = EmbeddedSourceReviewEvidence(
                packetId = stringField("REVIEW_PACKET_ID"),
                packetManifestSha256 = stringField("REVIEW_PACKET_SHA256"),
                sourceManifestSha256 = stringField("REVIEW_SOURCE_MANIFEST_SHA256"),
                authorizationSha256 = stringField("REVIEW_AUTHORIZATION_SHA256"),
            ),
            provenance = SourceProvenance(
                repo = "KMP-FaceLink",
                branch = stringField("GIT_BRANCH"),
                gitSha = stringField("GIT_SHA"),
                commitTimeIso = stringField("GIT_COMMIT_TIME"),
                dirty = booleanField("GIT_DIRTY"),
            ),
        )
    }
}

/**
 * The only public Android performance entrypoint.
 *
 * It accepts raw signed run-authorization bytes and delegates to the actual replay entrance. That
 * entrance independently rereads the installed BuildConfig and base APK before it touches a take.
 * There is intentionally no public method that accepts [ReviewEvidenceBinding] or replay config.
 */
public class AuthorizedAndroidPerformanceRunner(
    private val platformContext: PlatformContext,
) {
    /** Optional UI preflight; the replay entrance always repeats this verification. */
    public fun validate(authorizationBytes: ByteArray): ReviewEvidenceBinding =
        AndroidRunAuthorizationVerifier.verifyInstalled(platformContext.context, authorizationBytes).evidence

    /** The actual replay entrance verifies in this final call before it can create an artifact. */
    public fun run(takeDir: File, authorizationBytes: ByteArray): PerformanceBenchmarkArtifact {
        return AndroidPerformanceBenchmark(platformContext).run(takeDir, authorizationBytes)
    }
}

/** Exact installed identity used only as input to [AndroidRunAuthorizationVerifier]. */
public data class InstalledAndroidBinaryIdentity(
    val platform: String,
    val sha256: String,
)

/** Verified installed identity available only after raw-byte authorization validation. */
internal data class VerifiedInstalledAndroidAuthorization(
    val evidence: ReviewEvidenceBinding,
    val provenance: SourceProvenance,
)

/** Strict parser and P-256 verifier for post-binary diagnostic authorization. */
@Suppress("MagicNumber") // P-256 wire-format offsets and fixed authorization limits are protocol constants.
public object AndroidRunAuthorizationVerifier {
    public const val PLATFORM_ANDROID_APK: String = "android-apk"
    public const val SCHEMA: String = "facelink-run-authorization-v1"
    public const val SIGNING_KEY_ID: String =
        "e56a022d8e99eb94af5521b207b8dc2e73228cf7ca00eac9d94c911289c14f1c"

    private const val PUBLIC_KEY_X963_BASE64 =
        "BPtWONREX15XsgS9mphckQyaEhgHIEyOwZYcKior2LxEzf4EcLPSQ2NZgcs4gJ/gGd91ciVSOxt//wkSgGZEE0I="

    private val expectedKeys = listOf(
        "schema",
        "platform",
        "binaryPacketId",
        "binaryPacketManifestSha256",
        "binarySha256",
        "sourceAuthorizationSha256",
        "signingKeyId",
        "signatureDerHex",
    )

    /**
     * The only verifier used by an artifact-producing Android path. It deliberately has no source,
     * key, APK, or configuration parameter a caller could replace.
     */
    internal fun verifyInstalled(
        context: Context,
        bytes: ByteArray,
    ): VerifiedInstalledAndroidAuthorization {
        requireAuthorizationBytes(bytes)
        val installed = InstalledAndroidBuildEvidenceReader.read(context)
        return VerifiedInstalledAndroidAuthorization(
            evidence = verify(bytes, installed.sourceReview, installedApk(context)),
            provenance = installed.provenance,
        )
    }

    @Suppress("LongMethod") // Canonical fields are deliberately verified together without permissive parsing.
    public fun verify(
        bytes: ByteArray,
        source: EmbeddedSourceReviewEvidence,
        installedBinary: InstalledAndroidBinaryIdentity,
        publicKeyX963: ByteArray = Base64.decode(PUBLIC_KEY_X963_BASE64),
        expectedSigningKeyId: String = SIGNING_KEY_ID,
    ): ReviewEvidenceBinding {
        requireAuthorizationBytes(bytes)
        requireSourceReviewEvidence(source)
        require(installedBinary.platform == PLATFORM_ANDROID_APK) {
            "installed binary is not an Android APK"
        }
        require(installedBinary.sha256.isLowercaseSha256()) {
            "installed APK digest must be a lowercase SHA-256"
        }

        val content = bytes.decodeToString()
        require(content.encodeToByteArray().contentEquals(bytes)) {
            "run authorization must be canonical UTF-8"
        }
        require('\r' !in content && content.endsWith('\n')) {
            "run authorization must use LF and end with one LF"
        }
        val lines = content.split('\n')
        require(lines.size == expectedKeys.size + 1 && lines.last().isEmpty()) {
            "run authorization must contain exactly ${expectedKeys.size} LF-terminated lines"
        }
        val entries = lines.dropLast(1).mapIndexed { index, line ->
            val separator = line.indexOf('=')
            require(separator > 0 && separator == line.lastIndexOf('=')) {
                "run authorization line ${index + 1} must contain exactly one '='"
            }
            val key = line.substring(0, separator)
            require(key == expectedKeys[index]) {
                "run authorization keys/order must match the canonical schema"
            }
            key to line.substring(separator + 1)
        }.toMap()

        require(entries.getValue("schema") == SCHEMA) { "unsupported run authorization schema" }
        require(entries.getValue("platform") == installedBinary.platform) {
            "run authorization platform does not match this binary"
        }
        val binaryPacketId = entries.getValue("binaryPacketId")
        require(binaryPacketId.isCanonicalPacketId()) { "binary review packet ID is not canonical" }
        val binaryPacketManifestSha = entries.getValue("binaryPacketManifestSha256")
        require(binaryPacketManifestSha.isLowercaseSha256()) {
            "binary review packet manifest must be a lowercase SHA-256"
        }
        val authorizedBinarySha = entries.getValue("binarySha256")
        require(authorizedBinarySha == installedBinary.sha256) {
            "run authorization does not name the installed APK"
        }
        val authorizedSourceSha = entries.getValue("sourceAuthorizationSha256")
        require(authorizedSourceSha == source.authorizationSha256) {
            "run authorization does not name the embedded source authorization"
        }
        require(entries.getValue("signingKeyId") == expectedSigningKeyId) {
            "run authorization signing key is not trusted"
        }
        require(expectedSigningKeyId.isLowercaseSha256() && sha256(publicKeyX963) == expectedSigningKeyId) {
            "embedded run-authorization public key has the wrong identity"
        }

        val signatureText = entries.getValue("signatureDerHex")
        require(signatureText.length % 2 == 0 && signatureText.all { it in '0'..'9' || it in 'a'..'f' }) {
            "run authorization signature must use lowercase DER hex"
        }
        val signatureBytes = signatureText.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val signedPayload = lines.take(SIGNED_LINE_COUNT)
            .joinToString(separator = "\n", postfix = "\n")
            .encodeToByteArray()
        require(runCatching { verifyP256(publicKeyX963, signedPayload, signatureBytes) }.getOrDefault(false)) {
            "run authorization signature is invalid"
        }

        return ReviewEvidenceBinding(
            sourcePacketId = source.packetId,
            sourcePacketManifestSha256 = source.packetManifestSha256,
            sourceManifestSha256 = source.sourceManifestSha256,
            sourceAuthorizationSha256 = source.authorizationSha256,
            binaryPacketId = binaryPacketId,
            binaryPacketManifestSha256 = binaryPacketManifestSha,
            binarySha256 = authorizedBinarySha,
            runAuthorizationSha256 = sha256(bytes),
            runAuthorizationSigningKeyId = expectedSigningKeyId,
        )
    }

    /** Rejects split installs because a base-APK digest would not identify all installed code. */
    public fun installedApk(context: Context): InstalledAndroidBinaryIdentity {
        val applicationInfo = context.applicationInfo
        require(applicationInfo.splitSourceDirs.isNullOrEmpty()) {
            "split APK installations are not accepted by the exact-binary diagnostic gate"
        }
        val source = File(applicationInfo.sourceDir)
        require(source.isFile) { "installed base APK is unavailable" }
        val digest = MessageDigest.getInstance(SHA_256)
        source.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return InstalledAndroidBinaryIdentity(PLATFORM_ANDROID_APK, digest.digest().hex())
    }

    private fun requireSourceReviewEvidence(source: EmbeddedSourceReviewEvidence) {
        require(source.packetId.isCanonicalPacketId()) { "source review packet ID is not canonical" }
        require(source.packetManifestSha256.isLowercaseSha256()) {
            "source review packet manifest must be a lowercase SHA-256"
        }
        require(source.sourceManifestSha256.isLowercaseSha256()) {
            "source manifest must be a lowercase SHA-256"
        }
        require(source.authorizationSha256.isLowercaseSha256()) {
            "source authorization must be a lowercase SHA-256"
        }
    }

    internal fun requireAuthorizationBytes(bytes: ByteArray) {
        require(bytes.size in 1..MAX_AUTHORIZATION_BYTES) {
            "run authorization must be non-empty and no larger than $MAX_AUTHORIZATION_BYTES bytes"
        }
    }

    private fun verifyP256(publicKeyX963: ByteArray, payload: ByteArray, signature: ByteArray): Boolean {
        require(publicKeyX963.size == P256_UNCOMPRESSED_BYTES && publicKeyX963[0] == 0x04.toByte()) {
            "run-authorization public key must be uncompressed P-256"
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

    private fun String.isLowercaseSha256(): Boolean =
        length == 64 && all { it in '0'..'9' || it in 'a'..'f' }

    private fun String.isCanonicalPacketId(): Boolean =
        length in 1..128 && (first() in 'a'..'z' || first() in '0'..'9') &&
            all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance(SHA_256).digest(bytes).hex()

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private const val SIGNED_LINE_COUNT = 7
    private const val P256_UNCOMPRESSED_BYTES = 65
    private const val MAX_AUTHORIZATION_BYTES = 16 * 1024
    private const val SHA_256 = "SHA-256"
}
