package io.github.kmpfacelink.capture.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.kmpfacelink.capture.CaptureFailureCategory
import io.github.kmpfacelink.capture.CaptureStudyProtocol
import io.github.kmpfacelink.capture.ParticipantQualificationEvidence
import io.github.kmpfacelink.capture.captureFailure
import java.io.File
import java.security.MessageDigest

/** Keeps sign extension out of the hex digest when a byte is widened to Int. */
private const val BYTE_MASK: Int = 0xff

// Launch-route and study-protocol policy for the capture shell, kept apart from the Activity so
// that deciding *what a run is allowed to be* can be read and tested without a lifecycle.
/** Explicit developer-only shell route. The preview screen itself has no record or benchmark UI. */
internal enum class CaptureLaunchRoute { CAPTURE, PREVIEW_CHECK }

internal fun captureLaunchRoute(previewCheckRequested: Boolean): CaptureLaunchRoute =
    if (previewCheckRequested) CaptureLaunchRoute.PREVIEW_CHECK else CaptureLaunchRoute.CAPTURE

/** Preview-only is never a developer Session-C launch, even when its Intent has unrelated extras. */
internal fun studyProtocolForLaunchRoute(
    route: CaptureLaunchRoute,
    requested: String?,
): CaptureStudyProtocol = if (route == CaptureLaunchRoute.PREVIEW_CHECK) {
    CaptureStudyProtocol.guided22()
} else {
    captureStudyProtocol(requested)
}

internal const val PREVIEW_CHECK_INTENT_EXTRA: String = "preview_check"
internal const val STUDY_PROTOCOL_INTENT_EXTRA: String = "study_protocol"
internal const val LEARNED_PATCH_SESSION_C_INTENT_VALUE: String = "learned_patch_session_c_v1"
internal const val PARTICIPANT_QUALIFICATION_INTENT_VALUE: String =
    "participant_qualification_12_v1"
internal const val SESSION_C_AUTHORIZATION_INTENT_EXTRA: String = "session_c_authorization"
internal const val SESSION_C_CHEEK_MODEL_SHA256 = "a304d132e36cad9f6dcc9a8c1c78f34201cbee90e640f39625a2fbe05fd12dd3"
internal const val SESSION_C_MOUTH_MODEL_SHA256 = "70a340a7ee3ef25a526da8292fe06aecc2353e4d85c68f02c671cd189d8821b1"

/** Developer-only selection. Unknown values fail closed before camera or storage is opened. */
internal fun captureStudyProtocol(requested: String?): CaptureStudyProtocol = when (requested) {
    null -> CaptureStudyProtocol.guided22()
    LEARNED_PATCH_SESSION_C_INTENT_VALUE -> CaptureStudyProtocol.learnedPatchSessionC12()
    PARTICIPANT_QUALIFICATION_INTENT_VALUE -> CaptureStudyProtocol.learnedPatchSessionC12()
    else -> throw captureFailure(CaptureFailureCategory.CONFIGURATION)
}

internal fun isParticipantQualificationRoute(requested: String?): Boolean =
    requested == PARTICIPANT_QUALIFICATION_INTENT_VALUE

internal data class ParticipantQualificationPrecameraReceipt(
    val evidence: ParticipantQualificationEvidence,
    val availableBytes: Long,
    val requiredBytes: Long = SESSION_C_MINIMUM_AVAILABLE_BYTES,
    val worstCaseBytes: Long = PARTICIPANT_QUALIFICATION_WORST_CASE_BYTES,
)

/** Pure pre-camera guard for the simple local route; it never accepts an external run identity. */
internal fun requireParticipantQualificationPrecamera(
    protocol: CaptureStudyProtocol,
    gitSha: String,
    dirty: Boolean,
    evidence: ParticipantQualificationEvidence,
    availableBytes: Long,
): ParticipantQualificationPrecameraReceipt {
    require(protocol == CaptureStudyProtocol.learnedPatchSessionC12()) {
        "participant qualification requires the exact frozen 12-slot protocol"
    }
    require(gitSha.matches(Regex("[0-9a-f]{40}"))) { "reviewed source commit is missing" }
    require(!dirty) { "participant qualification requires a clean reviewed source build" }
    requireCaptureProtocolCapacity(protocol, availableBytes)
    return ParticipantQualificationPrecameraReceipt(
        evidence = evidence,
        availableBytes = availableBytes,
    )
}

/** Session C can retain up to 12 bounded canonical takes; fail before camera permission. */
internal fun requireCaptureProtocolCapacity(
    protocol: CaptureStudyProtocol,
    availableBytes: Long,
) {
    if (protocol.id == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
        if (availableBytes < SESSION_C_MINIMUM_AVAILABLE_BYTES) {
            throw captureFailure(CaptureFailureCategory.CAPACITY)
        }
    }
}

/** Pure launch precondition: validates Session C before the Activity can request camera permission. */
internal fun requireSessionCAuthorizationBeforeCapacity(
    protocol: CaptureStudyProtocol,
    authorizationBytes: () -> ByteArray?,
    expectation: () -> SessionCAuthorizationExpectation,
    availableBytes: () -> Long,
): SessionCExecutionAuthorization? {
    if (protocol.id != CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) return null
    val authorization = SessionCExecutionAuthorizationVerifier.verify(
        requireSessionCAuthorizationBytes(authorizationBytes()),
        expectation(),
    )
    requireCaptureProtocolCapacity(protocol, availableBytes())
    return authorization
}

/** Pure callback-time guard. It intentionally rebuilds time-sensitive expectation values. */
internal fun revalidateSessionCAuthorizationBeforeOpen(
    protocol: CaptureStudyProtocol,
    authorizationBytes: () -> ByteArray?,
    expectation: () -> SessionCAuthorizationExpectation,
): SessionCExecutionAuthorization? {
    if (protocol.id != CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) return null
    return SessionCExecutionAuthorizationVerifier.verify(
        requireSessionCAuthorizationBytes(authorizationBytes()),
        expectation(),
    )
}

/** The only null-to-failure conversion for a Session-C raw authorization supplier. */
internal fun requireSessionCAuthorizationBytes(bytes: ByteArray?): ByteArray =
    bytes ?: error("Session-C authorization is required before camera setup")

/** Keeps the signed ID as the only Session-C store identity selected by this Activity route. */
internal fun requiredSessionCStudyRunId(
    protocol: CaptureStudyProtocol,
    authorization: SessionCExecutionAuthorization?,
): String? = if (protocol.id == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
    requireNotNull(authorization) { "Session-C authorization result is missing" }.runId
} else {
    null
}

internal const val SESSION_C_MINIMUM_AVAILABLE_BYTES: Long = 4L shl 30
internal const val PARTICIPANT_QUALIFICATION_WORST_CASE_BYTES: Long = 3_367_001_472L

internal fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { byte -> "%02x".format(byte) }

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }
