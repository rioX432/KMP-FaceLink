package io.github.kmpfacelink.capture.app

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.capture.AndroidRunAuthorizationVerifier
import io.github.kmpfacelink.capture.AuthorizedAndroidPerformanceRunner
import io.github.kmpfacelink.capture.EmbeddedSourceReviewEvidence
import io.github.kmpfacelink.capture.InstalledAndroidBinaryIdentity
import io.github.kmpfacelink.capture.ReviewEvidenceBinding
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagnosticRunAuthorizationTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val publicKeyX963 = (keyPair.public as ECPublicKey).x963()
    private val keyId = sha256(publicKeyX963)
    private val source = EmbeddedSourceReviewEvidence(
        packetId = "source-packet-v4",
        packetManifestSha256 = "a".repeat(64),
        sourceManifestSha256 = "b".repeat(64),
        authorizationSha256 = "c".repeat(64),
    )
    private val binary = InstalledAndroidBinaryIdentity(
        platform = AndroidRunAuthorizationVerifier.PLATFORM_ANDROID_APK,
        sha256 = "d".repeat(64),
    )

    @Test
    fun signedAuthorizationBindsReviewedPacketApkAndSourceAuthorization() {
        val bytes = signedAuthorization()

        val evidence = AndroidRunAuthorizationVerifier.verify(
            bytes,
            source,
            binary,
            publicKeyX963,
            keyId,
        )

        assertEquals(source.packetId, evidence.sourcePacketId)
        assertEquals("binary-packet-v1", evidence.binaryPacketId)
        assertEquals(binary.sha256, evidence.binarySha256)
        assertEquals(source.authorizationSha256, evidence.sourceAuthorizationSha256)
        assertEquals(sha256(bytes), evidence.runAuthorizationSha256)
        assertEquals(keyId, evidence.runAuthorizationSigningKeyId)
    }

    @Test
    fun sourceOnlyCandidateCannotStartWithoutPostBinaryAuthorization() {
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(
                ByteArray(0),
                source,
                binary,
                publicKeyX963,
                keyId,
            )
        }
    }

    @Test
    fun exactBinaryAndEmbeddedSourceHashesFailClosed() {
        val bytes = signedAuthorization()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(
                bytes,
                source,
                binary.copy(sha256 = "e".repeat(64)),
                publicKeyX963,
                keyId,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(
                bytes,
                source.copy(authorizationSha256 = "f".repeat(64)),
                binary,
                publicKeyX963,
                keyId,
            )
        }
    }

    @Test
    fun tamperingWrongSignatureAndWrongKeyFailClosed() {
        val bytes = signedAuthorization()
        val tampered = bytes.decodeToString()
            .replace("binaryPacketId=binary-packet-v1", "binaryPacketId=binary-packet-v2")
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(tampered, source, binary, publicKeyX963, keyId)
        }

        val wrongSignature = bytes.decodeToString()
            .replaceAfter("signatureDerHex=", "00".repeat(70) + "\n")
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(wrongSignature, source, binary, publicKeyX963, keyId)
        }

        val otherKey = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair().public as ECPublicKey
        }.x963()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(bytes, source, binary, otherKey, keyId)
        }
    }

    @Test
    fun canonicalBytesRequireTerminalLfFixedOrderAndNoExtraKey() {
        val bytes = signedAuthorization()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(
                bytes.dropLast(1).toByteArray(),
                source,
                binary,
                publicKeyX963,
                keyId,
            )
        }
        val extra = bytes.decodeToString()
            .replace("signatureDerHex=", "extra=forbidden\nsignatureDerHex=")
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(extra, source, binary, publicKeyX963, keyId)
        }
        val reordered = bytes.decodeToString()
            .replace(
                "platform=android-apk\nbinaryPacketId=binary-packet-v1",
                "binaryPacketId=binary-packet-v1\nplatform=android-apk",
            )
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            AndroidRunAuthorizationVerifier.verify(reordered, source, binary, publicKeyX963, keyId)
        }
    }

    @Test
    fun everyJvmVisibleAndroidArtifactEntranceNeedsRawAuthorizationBytes() {
        val runMethods = AuthorizedAndroidPerformanceRunner::class.java.methods.filter { it.name == "run" }
        assertEquals(1, runMethods.size)
        assertEquals(
            listOf(File::class.java, ByteArray::class.java),
            runMethods.single().parameterTypes.toList(),
        )
        assertTrue(runMethods.single().parameterTypes.none { it == ReviewEvidenceBinding::class.java })
        val publicConstructors = AuthorizedAndroidPerformanceRunner::class.java.constructors
        assertEquals(1, publicConstructors.size)
        assertEquals(listOf(PlatformContext::class.java), publicConstructors.single().parameterTypes.toList())
        assertTrue(
            publicConstructors.single().parameterTypes.none { it == EmbeddedSourceReviewEvidence::class.java },
        )

        val legacyRunner = Class.forName("io.github.kmpfacelink.capture.AndroidPerformanceBenchmark")
        val replayMethods = legacyRunner.methods.filter { it.name.startsWith("run") }
        assertEquals(1, replayMethods.size)
        assertEquals(listOf(File::class.java, ByteArray::class.java), replayMethods.single().parameterTypes.toList())
        assertTrue(replayMethods.single().isSynthetic)
        assertTrue(replayMethods.single().parameterTypes.none { it == ReviewEvidenceBinding::class.java })

        // Reflection may invoke synthetic members, so invoke the engine itself. Empty raw bytes
        // must be rejected before it can dereference the reflected null PlatformContext, read a
        // take, open inference, or create an aggregate artifact.
        val legacy = allocateWithoutConstructor(legacyRunner)
        val failure = assertFailsWith<InvocationTargetException> {
            replayMethods.single().invoke(legacy, File("unreachable-take"), ByteArray(0))
        }
        assertTrue(failure.cause is IllegalArgumentException)
    }

    /** Avoids an Android Context: the reflected run must reject bytes before touching its fields. */
    private fun allocateWithoutConstructor(type: Class<*>): Any {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocate = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        return requireNotNull(allocate.invoke(unsafe, type))
    }

    private fun signedAuthorization(): ByteArray {
        val payload = """
            schema=facelink-run-authorization-v1
            platform=android-apk
            binaryPacketId=binary-packet-v1
            binaryPacketManifestSha256=${"1".repeat(64)}
            binarySha256=${binary.sha256}
            sourceAuthorizationSha256=${source.authorizationSha256}
            signingKeyId=$keyId
        """.trimIndent() + "\n"
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload.encodeToByteArray())
            sign()
        }
        val signatureHex = signature.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return (payload + "signatureDerHex=$signatureHex\n").encodeToByteArray()
    }

    private fun ECPublicKey.x963(): ByteArray = byteArrayOf(0x04) +
        w.affineX.fixed32() + w.affineY.fixed32()

    private fun BigInteger.fixed32(): ByteArray {
        val unsigned = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        require(unsigned.size <= 32)
        return ByteArray(32 - unsigned.size) + unsigned
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
