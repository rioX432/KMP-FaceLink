package io.github.kmpfacelink.capture

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Public, non-biometric identities frozen into one local participant-qualification run.
 *
 * The capture app supplies values from its reviewed build and installed APK. This type deliberately
 * has no participant identifier, path, frame, landmark, coefficient, or model payload.
 */
@Serializable
public data class ParticipantQualificationEvidence(
    public val reviewPacketSha256: String,
    public val sourceManifestSha256: String,
    public val sourceAuthorizationSha256: String,
    public val installedApkSha256: String,
    public val cheekModelSha256: String,
    public val mouthControlModelSha256: String,
    public val mediaPipeTaskSha256: String,
    public val deviceContractSha256: String,
) {
    init {
        listOf(
            reviewPacketSha256,
            sourceManifestSha256,
            sourceAuthorizationSha256,
            installedApkSha256,
            cheekModelSha256,
            mouthControlModelSha256,
            mediaPipeTaskSha256,
            deviceContractSha256,
        ).forEach { value ->
            require(value.isLowercaseSha256()) { "qualification evidence requires lowercase SHA-256" }
        }
    }
}

@Serializable
internal enum class ParticipantQualificationPhase { OPEN, TERMINATED, SEALED }

@Serializable
internal data class ParticipantQualificationFailure(
    val slotKey: String,
    val captureAttempt: Int,
    val code: String,
)

@Serializable
internal data class ParticipantQualificationSealedSlot(
    val slotKey: String,
    val takeId: String,
    val takeManifestSha256: String,
    val frameManifestSha256: String,
    val streamSetSha256: String,
)

@Serializable
internal data class ParticipantQualificationReferencePreflight(
    val takeId: String,
    val takeManifestSha256: String,
) {
    init {
        require(takeId.isNotBlank()) { "qualification preflight take ID is missing" }
        require(takeManifestSha256.isLowercaseSha256()) {
            "qualification preflight manifest digest is invalid"
        }
    }
}

@Serializable
internal data class ParticipantQualificationSeal(
    val schemaVersion: Int,
    val runId: String,
    val protocolId: String,
    val storageProfile: CaptureStorageProfile,
    val sealedAtIso: String,
    val evidence: ParticipantQualificationEvidence,
    val captureConditionsSha256: String,
    val orderedInputSha256: String,
    val referencePreflight: ParticipantQualificationReferencePreflight,
    val slots: List<ParticipantQualificationSealedSlot>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "unsupported qualification seal schema" }
        require(protocolId == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
            "qualification seal has the wrong protocol"
        }
        require(storageProfile == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1) {
            "qualification seal has the wrong storage profile"
        }
        require(runId.isNotBlank()) { "qualification seal has no run ID" }
        require(sealedAtIso.isNotBlank()) { "qualification seal has no seal time" }
        require(captureConditionsSha256.isLowercaseSha256()) { "invalid capture-condition digest" }
        require(orderedInputSha256.isLowercaseSha256()) { "invalid ordered-input digest" }
        require(slots.size == QUALIFICATION_SLOT_COUNT) { "qualification seal must contain 12 slots" }
        require(slots.map { it.slotKey }.distinct().size == slots.size) { "duplicate sealed slot" }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

@Serializable
internal data class ParticipantQualificationRunState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val studyRun: StudyRunIdentity,
    val evidence: ParticipantQualificationEvidence,
    val phase: ParticipantQualificationPhase = ParticipantQualificationPhase.OPEN,
    val activeSlotKey: String? = null,
    val activeCaptureAttempt: Int? = null,
    val replacementUsedSlots: Set<String> = emptySet(),
    val failures: List<ParticipantQualificationFailure> = emptyList(),
    val referencePreflight: ParticipantQualificationReferencePreflight? = null,
    val terminalCode: String? = null,
    val seal: ParticipantQualificationSeal? = null,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) { "unsupported qualification-run schema" }
        require(studyRun.protocolId == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
            "qualification state has the wrong protocol"
        }
        require(studyRun.storageProfile == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1) {
            "qualification state has the wrong storage profile"
        }
        require((activeSlotKey == null) == (activeCaptureAttempt == null)) {
            "active slot and attempt must change together"
        }
        when (phase) {
            ParticipantQualificationPhase.OPEN -> require(seal == null && terminalCode == null)
            ParticipantQualificationPhase.TERMINATED -> require(
                seal == null && activeSlotKey == null && terminalCode != null,
            )
            ParticipantQualificationPhase.SEALED -> require(
                seal != null && activeSlotKey == null && terminalCode == null,
            )
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

internal enum class ParticipantQualificationFailureDecision { RETRY_ONCE, TERMINATED }

internal interface ParticipantQualificationController {
    val state: StateFlow<ParticipantQualificationRunState>

    suspend fun seal(): ParticipantQualificationSeal
}

internal expect fun participantQualificationController(
    session: CaptureSession,
): ParticipantQualificationController?

internal fun beginParticipantQualificationAttempt(
    state: ParticipantQualificationRunState,
    slotKey: String,
): ParticipantQualificationRunState {
    check(state.phase == ParticipantQualificationPhase.OPEN) { "qualification run is not open" }
    check(state.activeSlotKey == null) { "another qualification attempt is active" }
    require(slotKey in qualificationSlotKeys()) { "unregistered qualification slot" }
    val captureAttempt = if (slotKey in state.replacementUsedSlots) 2 else 1
    return state.copy(activeSlotKey = slotKey, activeCaptureAttempt = captureAttempt)
}

internal fun completeParticipantQualificationAttempt(
    state: ParticipantQualificationRunState,
    slotKey: String,
): ParticipantQualificationRunState {
    check(state.phase == ParticipantQualificationPhase.OPEN) { "qualification run is not open" }
    check(state.activeSlotKey == slotKey) { "qualification completion does not match active slot" }
    return state.copy(activeSlotKey = null, activeCaptureAttempt = null)
}

internal fun failParticipantQualificationAttempt(
    state: ParticipantQualificationRunState,
    slotKey: String,
    code: String,
): Pair<ParticipantQualificationRunState, ParticipantQualificationFailureDecision> {
    check(state.phase == ParticipantQualificationPhase.OPEN) { "qualification run is not open" }
    check(state.activeSlotKey == slotKey) { "qualification failure does not match active slot" }
    val captureAttempt = requireNotNull(state.activeCaptureAttempt)
    val event = ParticipantQualificationFailure(slotKey, captureAttempt, code)
    val eligible = code == CaptureFailureCategory.STREAM_WRITE.code ||
        code == CaptureFailureCategory.INTEGRITY.code
    return if (eligible && slotKey !in state.replacementUsedSlots) {
        state.copy(
            activeSlotKey = null,
            activeCaptureAttempt = null,
            replacementUsedSlots = state.replacementUsedSlots + slotKey,
            failures = state.failures + event,
        ) to ParticipantQualificationFailureDecision.RETRY_ONCE
    } else {
        state.copy(
            phase = ParticipantQualificationPhase.TERMINATED,
            activeSlotKey = null,
            activeCaptureAttempt = null,
            failures = state.failures + event,
            terminalCode = code,
        ) to ParticipantQualificationFailureDecision.TERMINATED
    }
}

internal fun terminateInterruptedParticipantQualification(
    state: ParticipantQualificationRunState,
): ParticipantQualificationRunState = if (
    state.phase == ParticipantQualificationPhase.OPEN && state.activeSlotKey != null
) {
    state.copy(
        phase = ParticipantQualificationPhase.TERMINATED,
        activeSlotKey = null,
        activeCaptureAttempt = null,
        failures = state.failures + ParticipantQualificationFailure(
            requireNotNull(state.activeSlotKey),
            requireNotNull(state.activeCaptureAttempt),
            INTERRUPTION_CODE,
        ),
        terminalCode = INTERRUPTION_CODE,
    )
} else {
    state
}

internal fun qualificationSlotKeys(): List<String> =
    studyTakeSlots(CaptureStudyProtocol.learnedPatchSessionC12().scenarios).map(StudyTakeSlot::key)

internal fun String.isLowercaseSha256(): Boolean = matches(Regex("[0-9a-f]{64}"))

internal const val PARTICIPANT_QUALIFICATION_STATE_FILE: String =
    "participant-qualification-state-v1.json"
internal const val PARTICIPANT_QUALIFICATION_CAPTURE_ROOT: String =
    "participant-qualification-12-v1"
internal const val QUALIFICATION_SLOT_COUNT: Int = 12
internal const val INTERRUPTION_CODE: String = "interrupted_attempt"
