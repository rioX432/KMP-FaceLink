package io.github.kmpfacelink.capture.app

import io.github.kmpfacelink.capture.CaptureFailureCategory
import io.github.kmpfacelink.capture.CaptureFailureException
import io.github.kmpfacelink.capture.CaptureStorageProfile
import io.github.kmpfacelink.capture.CaptureStudyProtocol
import io.github.kmpfacelink.capture.ParticipantQualificationEvidence
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureLaunchRouteTest {
    @Test
    fun explicitPreviewCheckRouteCannotFallThroughToTheCaptureScreen() {
        assertEquals(CaptureLaunchRoute.PREVIEW_CHECK, captureLaunchRoute(previewCheckRequested = true))
        assertEquals(CaptureLaunchRoute.CAPTURE, captureLaunchRoute(previewCheckRequested = false))
        assertEquals(
            CaptureStudyProtocol.guided22(),
            studyProtocolForLaunchRoute(CaptureLaunchRoute.PREVIEW_CHECK, LEARNED_PATCH_SESSION_C_INTENT_VALUE),
        )
    }

    @Test
    fun sessionCSelectionBindsTheFrozenProtocolAndCanonicalStorage() {
        val protocol = captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE)

        assertEquals(CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID, protocol.id)
        assertEquals(CaptureStorageProfile.CANONICAL_RGBA_240X320_V1, protocol.storageProfile)
        assertEquals(12, protocol.scenarios.size)
    }

    @Test
    fun participantQualificationSelectionReusesExact12WithoutBecomingTheSignedRoute() {
        val protocol = captureStudyProtocol(PARTICIPANT_QUALIFICATION_INTENT_VALUE)

        assertEquals(CaptureStudyProtocol.learnedPatchSessionC12(), protocol)
        assertEquals(true, isParticipantQualificationRoute(PARTICIPANT_QUALIFICATION_INTENT_VALUE))
        assertEquals(false, isParticipantQualificationRoute(LEARNED_PATCH_SESSION_C_INTENT_VALUE))
        assertEquals(false, isParticipantQualificationRoute(null))
    }

    @Test
    fun participantQualificationGuardRequiresCleanReviewedSourceAndExactCapacity() {
        val protocol = captureStudyProtocol(PARTICIPANT_QUALIFICATION_INTENT_VALUE)
        val receipt = requireParticipantQualificationPrecamera(
            protocol = protocol,
            gitSha = "1".repeat(40),
            dirty = false,
            evidence = qualificationEvidence(),
            availableBytes = SESSION_C_MINIMUM_AVAILABLE_BYTES,
        )

        assertEquals(SESSION_C_MINIMUM_AVAILABLE_BYTES, receipt.availableBytes)
        assertEquals(PARTICIPANT_QUALIFICATION_WORST_CASE_BYTES, receipt.worstCaseBytes)
        assertFailsWith<IllegalArgumentException> {
            requireParticipantQualificationPrecamera(
                protocol,
                "1".repeat(40),
                dirty = true,
                qualificationEvidence(),
                SESSION_C_MINIMUM_AVAILABLE_BYTES,
            )
        }
        val capacity = assertFailsWith<CaptureFailureException> {
            requireParticipantQualificationPrecamera(
                protocol,
                "1".repeat(40),
                dirty = false,
                qualificationEvidence(),
                SESSION_C_MINIMUM_AVAILABLE_BYTES - 1,
            )
        }
        assertEquals(CaptureFailureCategory.CAPACITY, capacity.category)
    }

    @Test
    fun participantQualificationGuardHasNoSessionCAuthorizationInput() {
        requireParticipantQualificationPrecamera(
            captureStudyProtocol(PARTICIPANT_QUALIFICATION_INTENT_VALUE),
            "1".repeat(40),
            dirty = false,
            qualificationEvidence(),
            SESSION_C_MINIMUM_AVAILABLE_BYTES,
        )
        // The old signed route remains independently fail-closed.
        assertFailsWith<IllegalStateException> {
            requireSessionCAuthorizationBytes(null)
        }
    }

    @Test
    fun sessionCCapacityGateRequiresFourGibibytesBeforeCameraPermission() {
        val protocol = captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE)

        requireCaptureProtocolCapacity(protocol, SESSION_C_MINIMUM_AVAILABLE_BYTES)
        val failure = assertFailsWith<CaptureFailureException> {
            requireCaptureProtocolCapacity(protocol, SESSION_C_MINIMUM_AVAILABLE_BYTES - 1)
        }
        assertEquals(CaptureFailureCategory.CAPACITY, failure.category)
    }

    @Test
    fun coefficientOnlyGuidedCaptureDoesNotInheritTheSessionCCapacityGate() {
        requireCaptureProtocolCapacity(CaptureStudyProtocol.guided22(), 0)
    }

    @Test
    fun absentSelectionUsesGuided22AndUnknownSelectionFailsClosed() {
        assertEquals(CaptureStudyProtocol.guided22(), captureStudyProtocol(null))
        assertFailsWith<IllegalStateException> { captureStudyProtocol("typo") }
    }

    @Test
    fun sessionCAuthorizationRejectsMissingWrongOrExpiredBindingBeforeThePreCameraRoute() {
        val expected = expectation()
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(ByteArray(0), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(fixture(FixtureSpec(cheek = "b".repeat(64))), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(fixture(FixtureSpec(mouth = "e".repeat(64))), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(fixture(FixtureSpec(source = "f".repeat(64))), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(fixture(FixtureSpec(apk = "0".repeat(64))), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(fixture(FixtureSpec(contract = "f".repeat(64))), expected)
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(),
                expectation().copy(executionContractSha256 = ""),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(FixtureSpec(expiry = "2020-01-01T00:00:00Z")),
                expected,
            )
        }
    }

    @Test
    fun syntheticSessionCAuthorizationReachesOnlyThePreCameraEligibilityRoute() {
        requireSessionCAuthorizationBeforeCapacity(
            captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE),
            { fixture() },
            ::expectation,
            { SESSION_C_MINIMUM_AVAILABLE_BYTES },
        )
        var expectationCalls = 0
        var capacityCalls = 0
        requireSessionCAuthorizationBeforeCapacity(
            CaptureStudyProtocol.guided22(),
            authorizationBytes = { error("guided-22 must not read Session-C bytes") },
            expectation = {
                expectationCalls += 1
                error("guided-22 must not construct Session-C expectation")
            },
            availableBytes = {
                capacityCalls += 1
                error("guided-22 must not probe Session-C capacity")
            },
        )
        assertEquals(0, expectationCalls)
        assertEquals(0, capacityCalls)
        assertEquals(
            null,
            revalidateSessionCAuthorizationBeforeOpen(
                studyProtocolForLaunchRoute(CaptureLaunchRoute.PREVIEW_CHECK, LEARNED_PATCH_SESSION_C_INTENT_VALUE),
                { error("preview-only must not read Session-C bytes") },
                { error("preview-only must not construct Session-C expectation") },
            ),
        )
    }

    @Test
    fun sessionCAuthorizationRejectsUnsignedMutationAndSyntheticFixtureInAnInstalledRoute() {
        val tampered = fixture().decodeToString()
            .replace("runId=session-c-test-1", "runId=session-c-forged")
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(tampered, expectation())
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(),
                expectation().copy(permitSyntheticFixture = false),
            )
        }
    }

    @Test
    fun nullAuthorizationUsesTheExplicitFailClosedHelper() {
        assertFailsWith<IllegalStateException> { requireSessionCAuthorizationBytes(null) }
        assertFailsWith<IllegalStateException> {
            revalidateSessionCAuthorizationBeforeOpen(
                captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE),
                { null },
                ::expectation,
            )
        }
    }

    @Test
    fun validSignedAlternateRunFlowsToTheActivityStoreIdentity() {
        val authorization = SessionCExecutionAuthorizationVerifier.verify(
            fixture(FixtureSpec(runId = "session-c-alternate-run")),
            expectation(),
        )
        assertEquals(
            "session-c-alternate-run",
            requiredSessionCStudyRunId(captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE), authorization),
        )
    }

    @Test
    fun canonicalNegativeBytesRejectReorderingCrLfWrongProtocolAndWrongProfile() {
        val protocolLine = "protocolId=${CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID}\n"
        val profileLine = "storageProfile=${CaptureStorageProfile.CANONICAL_RGBA_240X320_V1.name}\n"
        val reordered = fixture().decodeToString()
            .replace(protocolLine, "")
            .replace(profileLine, profileLine + protocolLine)
            .encodeToByteArray()
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(reordered, expectation())
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(FixtureSpec(lineEnding = "\r\n")),
                expectation(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(FixtureSpec(protocol = "CHEEK_PUFF_GUIDED_22_V1")),
                expectation(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SessionCExecutionAuthorizationVerifier.verify(
                fixture(FixtureSpec(profile = "COEFFICIENTS_ONLY_V1")),
                expectation(),
            )
        }
    }

    @Test
    fun callbackRevalidationRejectsAnAuthorizationThatExpiredSincePermissionRequest() {
        assertFailsWith<IllegalArgumentException> {
            revalidateSessionCAuthorizationBeforeOpen(
                captureStudyProtocol(LEARNED_PATCH_SESSION_C_INTENT_VALUE),
                { fixture(FixtureSpec(expiry = "2026-08-30T00:00:01Z")) },
                { expectation(now = "2026-08-30T00:00:01Z") },
            )
        }
    }

    private fun expectation(now: String = "2026-08-30T00:00:00Z") = SessionCAuthorizationExpectation(
        sourceClosureSha256 = "a".repeat(64),
        apkSha256 = "b".repeat(64),
        executionContractSha256 = "e".repeat(64),
        cheekModelSha256 = "c".repeat(64),
        mouthControlModelSha256 = "d".repeat(64),
        now = Instant.parse(now),
        permitSyntheticFixture = true,
        publicKeyX963 = (keyPair.public as ECPublicKey).x963(),
        signingKeyId = sha256((keyPair.public as ECPublicKey).x963()),
    )

    private fun qualificationEvidence() = ParticipantQualificationEvidence(
        reviewPacketSha256 = "1".repeat(64),
        sourceManifestSha256 = "2".repeat(64),
        sourceAuthorizationSha256 = "3".repeat(64),
        installedApkSha256 = "4".repeat(64),
        cheekModelSha256 = "5".repeat(64),
        mouthControlModelSha256 = "6".repeat(64),
        mediaPipeTaskSha256 = "7".repeat(64),
        deviceContractSha256 = "8".repeat(64),
    )

    private data class FixtureSpec(
        val cheek: String = "c".repeat(64),
        val mouth: String = "d".repeat(64),
        val source: String = "a".repeat(64),
        val apk: String = "b".repeat(64),
        val contract: String = "e".repeat(64),
        val receipt: String = "f".repeat(64),
        val runId: String = "session-c-test-1",
        val protocol: String = "CHEEK_PUFF_LEARNED_PATCH_CONFIRMATORY_12_V1",
        val profile: String = "CANONICAL_RGBA_240X320_V1",
        val lineEnding: String = "\n",
        val expiry: String = "2026-09-01T00:00:00Z",
    )

    private fun fixture(spec: FixtureSpec = FixtureSpec()): ByteArray {
        val expected = expectation()
        val payload = """
        schema=session-c-execution-authorization-v1
        protocolId=${spec.protocol}
        storageProfile=${spec.profile}
        sourceClosureSha256=${spec.source}
        apkSha256=${spec.apk}
        executionContractSha256=${spec.contract}
        binaryReceiptSha256=${spec.receipt}
        cheekModelSha256=${spec.cheek}
        mouthControlModelSha256=${spec.mouth}
        runId=${spec.runId}
        expiresAt=${spec.expiry}
        fixture=HOST_TEST_ONLY
        signingKeyId=${expected.signingKeyId}
        """.trimIndent().lines().joinToString(spec.lineEnding, postfix = spec.lineEnding)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload.encodeToByteArray())
            sign()
        }.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return (payload + "signatureDerHex=$signature${spec.lineEnding}").encodeToByteArray()
    }

    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
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
