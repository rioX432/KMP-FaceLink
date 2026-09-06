@file:Suppress("LongParameterList")

package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/**
 * Immutable, score-free contract for the fresh H2 confirmation route.
 *
 * This contract binds identities, ordering, readiness, visible output, and publication. It does
 * not contain candidate scores and it grants no authority to select either candidate for the
 * ordinary product route.
 */
public object H2ConfirmationContract {
    public const val PROTOCOL_ID: String = "DK_CHEEK_S02V17_H2_SCOREFREE_PROTOCOL_PREREG_V2"
    public const val PROTOCOL_PREREG_SHA256: String =
        "67cc2445c04201abe5abb65d5b111eda9204e86e75b31d8d6362584fcf684bd4"
    public const val PROTOCOL_V2_CORRECTION_SHA256: String =
        "7f88bae333f16e229400b986e704771b22cbf0b1a5df3dac6db9490859ffc53e"
    public const val V1_SCAFFOLD_RECEIPT_SHA256: String =
        "a67dfecbff906aba9aa0c528c5b0a596976be535be4e905329ffc493ad02a23a"
    public const val CONTRACT_IDENTITY_DESIGN_SHA256: String =
        "7a3d51a45391fb6cb07b1dfb07825134d5f2058026f1c6474b54855852250bbd"
    public const val IMPLEMENTATION_AUTHORITY_SHA256: String =
        "1803f5cb51e42852739dd483ddb401f973617981aac22e33991be6a14414c7b8"
    public const val H1_DECISION_SHA256: String =
        "2318b3d226ca1c3a19f98bc9e4a17ae6d8629573a42d48a443be8fcf2d173cfd"
    public const val H2_READINESS_SHA256: String =
        "e23e481b8d1e91c3fe551a7a120e303493c9301d9151eab6ef0adcb6b4a14dae"
    public const val STARTUP_TOTAL_MILLIS: Long = 15_000L
    public const val STARTUP_TOTAL_SECONDS: Int = 15
    public const val READY_BASELINE_SECONDS: Int = 3
    public const val ONSET_OBSERVATION_SECONDS: Int = 2
    public const val HOLD_SECONDS: Int = 4
    public const val RELEASE_OBSERVATION_SECONDS: Int = 4
    public const val POST_RELEASE_STABILITY_SECONDS: Int = 2
    public const val POSITIVE_CYCLES_PER_TAKE: Int = 2
    public const val CONTROL_CYCLES_PER_TAKE: Int = 1
    public const val EXPECTED_BLOCK_COUNT: Int = 22
    public const val EXPECTED_POSITIVE_ATTEMPTS: Int = 20
    public const val PER_TAKE_CALIBRATION_SECONDS: Int = 3
    public const val FIXED_RECORDED_DURATION_SECONDS: Int = 578
    public const val MAX_CONTINUOUS_GAP_MILLIS: Int = 250

    public val primary: H2CandidateContract = H2CandidateContract(
        role = H2CandidateRole.PRIMARY,
        producerId = "research:c3b-static-cheek-patch/v1",
        packetSha256 = "aee9367959ce3b43deb8ba4f189a870c40577d09c57525c5133a780c3d25216a",
        policy = H2FrozenPolicy(
            policyId = "research:c3b-static-cheek-patch/v1:on=0.5",
            onThreshold = 0.5,
            offThreshold = 0.5,
            activationDwellMillis = 0,
            releaseDwellMillis = 0,
            maxContinuousGapMillis = 250,
            canonicalJsonUtf8Lf = "{\"activation_dwell_ms\":0,\"max_continuous_gap_ms\":250," +
                "\"off_threshold\":0.5,\"on_threshold\":0.5," +
                "\"policy_id\":\"research:c3b-static-cheek-patch/v1:on=0.5\"," +
                "\"release_dwell_ms\":0}\n",
            sha256 = "05e0d942c146d595982c8f428471dbe54eccebe690ec193c9c1825fd64928034",
        ),
        semanticState = "UNRESOLVED",
    )

    public val comparator: H2CandidateContract = H2CandidateContract(
        role = H2CandidateRole.COMPARATOR,
        producerId = "research:c2-lip-gated/v1+c1-frozen-contour/v1",
        packetSha256 = "19a669297ab1c143725b4b3931a77805b94635278bc833b5bb344d0a0f9414ce",
        policy = H2FrozenPolicy(
            policyId = "research:c2-lip-gated/v1+c1-frozen-contour/v1:on=0.05",
            onThreshold = 0.05,
            offThreshold = 0.04000000000000001,
            activationDwellMillis = 350,
            releaseDwellMillis = 200,
            maxContinuousGapMillis = 250,
            canonicalJsonUtf8Lf = "{\"activation_dwell_ms\":350,\"max_continuous_gap_ms\":250," +
                "\"off_threshold\":0.04000000000000001,\"on_threshold\":0.05," +
                "\"policy_id\":\"research:c2-lip-gated/v1+c1-frozen-contour/v1:on=0.05\"," +
                "\"release_dwell_ms\":200}\n",
            sha256 = "474710061642887d9afcb928a34c2130b4485985049dfffeffe97187389fd6f7",
        ),
        semanticState = "CANONICAL_ALTERNATIVE_CANDIDATE_NOT_QUALIFIED",
    )

    /** Exact order. Repetitions are distinct blocks and must never be de-duplicated. */
    public val blocks: List<H2ConfirmationBlock> = listOf(
        H2ConfirmationBlock("B01_STARTUP_NEUTRAL_READINESS", H2BlockKind.STARTUP_READINESS, 15, 15, 0),
        H2ConfirmationBlock("B02_OPENING_NEUTRAL_READY", H2BlockKind.ORDINARY_USE, 63, 60, 0),
        H2ConfirmationBlock("B03_JAW_OPEN", H2BlockKind.DIAGNOSTIC_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B04_PUKU_BILATERAL_FRONT_LIGHT_1", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B05_PUKU_BILATERAL_FRONT_MID_1", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B06_PUKU_BILATERAL_FRONT_STRONG_1", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B07_BLOW_HELD", H2BlockKind.PRODUCT_JUDGMENT_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B08_PUCKER_1", H2BlockKind.PRODUCT_JUDGMENT_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B09_SMILE_HOLD", H2BlockKind.PRODUCT_JUDGMENT_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B10_NATURAL_SPEECH", H2BlockKind.ORDINARY_USE, 123, 120, 0),
        H2ConfirmationBlock("B11_TONGUE_OUT", H2BlockKind.DIAGNOSTIC_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B12_NOSE_SNEER", H2BlockKind.DIAGNOSTIC_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B13_PUKU_UNILATERAL_LEFT_FRONT", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B14_PUKU_UNILATERAL_RIGHT_FRONT", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B15_PUKU_BILATERAL_YAW_LEFT", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B16_PUKU_BILATERAL_YAW_RIGHT", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B17_PUKU_BILATERAL_FRONT_LIGHT_2", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B18_PUKU_BILATERAL_FRONT_MID_2", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B19_PUKU_BILATERAL_FRONT_STRONG_2", H2BlockKind.POSITIVE, 21, 18, 2),
        H2ConfirmationBlock("B20_BLOW_NATURAL", H2BlockKind.PRODUCT_JUDGMENT_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B21_PUCKER_2", H2BlockKind.PRODUCT_JUDGMENT_CONTROL, 13, 10, 1),
        H2ConfirmationBlock("B22_CLOSING_NEUTRAL_READY", H2BlockKind.ORDINARY_USE, 63, 60, 0),
    )

    init {
        check(blocks.size == EXPECTED_BLOCK_COUNT)
        check(blocks.map(H2ConfirmationBlock::id).distinct().size == EXPECTED_BLOCK_COUNT)
        check(blocks.filter { it.kind == H2BlockKind.POSITIVE }.sumOf { it.cycleCount } == EXPECTED_POSITIVE_ATTEMPTS)
        check(blocks.sumOf(H2ConfirmationBlock::totalDurationSeconds) == FIXED_RECORDED_DURATION_SECONDS)
        check(blocks.first().calibrationPreludeSeconds == 0)
        check(blocks.drop(1).all { it.calibrationPreludeSeconds == PER_TAKE_CALIBRATION_SECONDS })
    }
}

@Serializable
public enum class H2CandidateRole { PRIMARY, COMPARATOR }

@Serializable
public data class H2CandidateContract(
    public val role: H2CandidateRole,
    public val producerId: String,
    public val packetSha256: String,
    public val policy: H2FrozenPolicy,
    public val semanticState: String,
    public val ordinaryProductRouteEnabled: Boolean = false,
    public val canonical52OverwriteAllowed: Boolean = false,
) {
    init {
        require(producerId.isNotBlank())
        require(packetSha256.isLowercaseSha256())
        require(semanticState.isNotBlank())
        require(!ordinaryProductRouteEnabled) { "H2 candidate must remain default-off" }
        require(!canonical52OverwriteAllowed) { "H2 candidate cannot overwrite canonical 52" }
    }
}

@Serializable
public data class H2FrozenPolicy(
    public val policyId: String,
    public val onThreshold: Double,
    public val offThreshold: Double,
    public val activationDwellMillis: Int,
    public val releaseDwellMillis: Int,
    public val maxContinuousGapMillis: Int,
    public val canonicalJsonUtf8Lf: String,
    public val sha256: String,
) {
    init {
        require(policyId.isNotBlank())
        require(onThreshold.isFinite() && offThreshold.isFinite())
        require(onThreshold in 0.0..1.0 && offThreshold in 0.0..onThreshold)
        require(activationDwellMillis >= 0 && releaseDwellMillis >= 0)
        require(maxContinuousGapMillis == H2ConfirmationContract.MAX_CONTINUOUS_GAP_MILLIS)
        require(canonicalJsonUtf8Lf.endsWith('\n') && '\r' !in canonicalJsonUtf8Lf)
        require(sha256.isLowercaseSha256())
    }
}

@Serializable
public enum class H2BlockKind {
    STARTUP_READINESS,
    ORDINARY_USE,
    POSITIVE,
    PRODUCT_JUDGMENT_CONTROL,
    DIAGNOSTIC_CONTROL,
}

@Serializable
public data class H2ConfirmationBlock(
    public val id: String,
    public val kind: H2BlockKind,
    public val totalDurationSeconds: Int,
    public val minimumObservableSeconds: Int,
    public val cycleCount: Int,
) {
    init {
        require(id.matches(Regex("B[0-9]{2}_[A-Z0-9_]+")))
        require(totalDurationSeconds > 0)
        require(minimumObservableSeconds >= 0 && minimumObservableSeconds <= totalDurationSeconds)
        require(cycleCount >= 0)
        val calibrationSeconds = totalDurationSeconds - minimumObservableSeconds
        when (kind) {
            H2BlockKind.STARTUP_READINESS -> require(
                totalDurationSeconds == H2ConfirmationContract.STARTUP_TOTAL_SECONDS &&
                    minimumObservableSeconds == totalDurationSeconds &&
                    cycleCount == 0,
            )
            H2BlockKind.ORDINARY_USE -> require(
                calibrationSeconds == H2ConfirmationContract.PER_TAKE_CALIBRATION_SECONDS &&
                    cycleCount == 0,
            )
            H2BlockKind.POSITIVE -> require(
                calibrationSeconds == H2ConfirmationContract.PER_TAKE_CALIBRATION_SECONDS &&
                    cycleCount == H2ConfirmationContract.POSITIVE_CYCLES_PER_TAKE,
            )
            H2BlockKind.PRODUCT_JUDGMENT_CONTROL,
            H2BlockKind.DIAGNOSTIC_CONTROL,
            -> require(
                calibrationSeconds == H2ConfirmationContract.PER_TAKE_CALIBRATION_SECONDS &&
                    cycleCount == H2ConfirmationContract.CONTROL_CYCLES_PER_TAKE,
            )
        }
    }

    public val calibrationPreludeSeconds: Int
        get() = totalDurationSeconds - minimumObservableSeconds
}

/** B01 immutable provider/model/policy/route/render availability, never feature readiness. */
@Serializable
public enum class H2ProviderRouteAvailability { NOT_READY, AVAILABLE, UNAVAILABLE }

@Serializable
public enum class H2ReadinessPhase { WAITING, READY, TIMED_OUT }

@Serializable
public data class H2ReadinessState(
    public val launchMonotonicNanos: Long,
    public val lastMonotonicNanos: Long,
    public val primary: H2ProviderRouteAvailability = H2ProviderRouteAvailability.NOT_READY,
    public val comparator: H2ProviderRouteAvailability = H2ProviderRouteAvailability.NOT_READY,
    public val launchInstantObservedAvailable: Boolean = false,
    public val fullIntervalAvailable: Boolean = true,
    public val readyMonotonicNanos: Long? = null,
    public val phase: H2ReadinessPhase = H2ReadinessPhase.WAITING,
) {
    init {
        require(launchMonotonicNanos >= 0)
        require(lastMonotonicNanos >= launchMonotonicNanos)
        require((phase == H2ReadinessPhase.READY) == (readyMonotonicNanos != null))
        if (phase == H2ReadinessPhase.READY) {
            require(
                launchInstantObservedAvailable && fullIntervalAvailable &&
                    primary == H2ProviderRouteAvailability.AVAILABLE &&
                    comparator == H2ProviderRouteAvailability.AVAILABLE,
            )
        }
    }
}

/**
 * Advances B01 availability without producing feature readiness or a scientific endpoint.
 * A complete interval requires observations no more than the frozen continuous-gap limit apart.
 */
public fun observeH2Readiness(
    state: H2ReadinessState,
    monotonicNanos: Long,
    primary: H2ProviderRouteAvailability,
    comparator: H2ProviderRouteAvailability,
): H2ReadinessState {
    check(state.phase == H2ReadinessPhase.WAITING) { "H2 readiness is already terminal" }
    require(monotonicNanos >= state.lastMonotonicNanos) { "H2 readiness clock moved backwards" }
    val bothReady = primary == H2ProviderRouteAvailability.AVAILABLE &&
        comparator == H2ProviderRouteAvailability.AVAILABLE
    val elapsedNanos = monotonicNanos - state.launchMonotonicNanos
    val exactEndpointNanos = H2ConfirmationContract.STARTUP_TOTAL_MILLIS * 1_000_000L
    val gapNanos = monotonicNanos - state.lastMonotonicNanos
    val maxContinuousGapNanos = H2ConfirmationContract.MAX_CONTINUOUS_GAP_MILLIS * 1_000_000L
    val gapIsCovered = gapNanos <= maxContinuousGapNanos
    val observesLaunch = monotonicNanos == state.launchMonotonicNanos && bothReady
    val launchObservedAvailable = state.launchInstantObservedAvailable || observesLaunch
    val firstObservationWasLate = !state.launchInstantObservedAvailable &&
        state.lastMonotonicNanos == state.launchMonotonicNanos &&
        monotonicNanos > state.launchMonotonicNanos
    val fullIntervalAvailable =
        state.fullIntervalAvailable && bothReady && !firstObservationWasLate && gapIsCovered
    return when {
        elapsedNanos == exactEndpointNanos &&
            launchObservedAvailable && fullIntervalAvailable -> state.copy(
            lastMonotonicNanos = monotonicNanos,
            primary = primary,
            comparator = comparator,
            launchInstantObservedAvailable = launchObservedAvailable,
            fullIntervalAvailable = fullIntervalAvailable,
            readyMonotonicNanos = monotonicNanos,
            phase = H2ReadinessPhase.READY,
        )
        elapsedNanos >= exactEndpointNanos -> state.copy(
            lastMonotonicNanos = monotonicNanos,
            primary = primary,
            comparator = comparator,
            launchInstantObservedAvailable = launchObservedAvailable,
            fullIntervalAvailable = fullIntervalAvailable,
            phase = H2ReadinessPhase.TIMED_OUT,
        )
        else -> state.copy(
            lastMonotonicNanos = monotonicNanos,
            primary = primary,
            comparator = comparator,
            launchInstantObservedAvailable = launchObservedAvailable,
            fullIntervalAvailable = fullIntervalAvailable,
        )
    }
}

@Serializable
public data class H2ClockMapping(
    public val clockId: String,
    public val cameraOriginNanos: Long,
    public val cueOriginNanos: Long,
    public val actionObserverOriginNanos: Long,
    public val visibleOutputOriginNanos: Long,
    public val maxMappingUncertaintyNanos: Long = 0,
) {
    init {
        require(clockId == MONOTONIC_CLOCK_ID) { "H2 requires one monotonic clock domain" }
        require(cameraOriginNanos >= 0 && cueOriginNanos >= 0)
        require(actionObserverOriginNanos >= 0 && visibleOutputOriginNanos >= 0)
        require(maxMappingUncertaintyNanos >= 0)
    }

    public companion object {
        public const val MONOTONIC_CLOCK_ID: String = "android_elapsed_realtime_nanos/v1"
    }
}

@Serializable
public enum class H2VisibleOutputState { VISIBLE_OFF, VISIBLE_ON, OUTPUT_NOT_VISIBLE }

@Serializable
public enum class H2VisibleSurface {
    H2_PRIMARY_C3B_AVATAR_RENDER,
    H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER,
}

@Serializable
public data class H2VisibleOutputEvent(
    public val sequence: Long,
    public val blockId: String,
    public val candidateRole: H2CandidateRole,
    public val surface: H2VisibleSurface,
    public val monotonicNanos: Long,
    public val state: H2VisibleOutputState,
) {
    init {
        require(sequence >= 0)
        require(blockId in H2ConfirmationContract.blocks.map(H2ConfirmationBlock::id))
        require(monotonicNanos >= 0)
        require(h2SurfaceMatchesRole(candidateRole, surface)) {
            "H2 candidate and visible surface identities diverged"
        }
    }
}

@Serializable
public data class H2AvatarRenderCommand(
    public val frameIndex: Long,
    public val monotonicNanos: Long,
    public val candidateRole: H2CandidateRole,
    public val surface: H2VisibleSurface,
    public val bilateralPukuVisible: Boolean,
) {
    init {
        require(frameIndex >= 0 && monotonicNanos >= 0)
        require(h2SurfaceMatchesRole(candidateRole, surface))
    }
}

/** Creates two isolated bilateral avatar commands from one frame without touching canonical 52. */
public fun h2AvatarRenderCommands(
    frameIndex: Long,
    monotonicNanos: Long,
    primaryActive: Boolean,
    comparatorActive: Boolean,
): List<H2AvatarRenderCommand> = listOf(
    H2AvatarRenderCommand(
        frameIndex,
        monotonicNanos,
        H2CandidateRole.PRIMARY,
        H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER,
        primaryActive,
    ),
    H2AvatarRenderCommand(
        frameIndex,
        monotonicNanos,
        H2CandidateRole.COMPARATOR,
        H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER,
        comparatorActive,
    ),
)

private fun h2SurfaceMatchesRole(role: H2CandidateRole, surface: H2VisibleSurface): Boolean = when (role) {
    H2CandidateRole.PRIMARY -> surface == H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER
    H2CandidateRole.COMPARATOR -> surface == H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER
}

/** In-memory ordering guard used before events are written to the private atomic ledger. */
public class H2VisibleOutputLedger {
    private val retained: MutableList<H2VisibleOutputEvent> = mutableListOf()

    public val events: List<H2VisibleOutputEvent>
        get() = retained.toList()

    public fun append(event: H2VisibleOutputEvent) {
        val previous = retained.lastOrNull()
        require(event.sequence == retained.size.toLong()) { "H2 output event sequence is not contiguous" }
        require(previous == null || event.monotonicNanos >= previous.monotonicNanos) {
            "H2 output event clock moved backwards"
        }
        retained += event
    }
}

@Serializable
public data class H2PrivacyStoragePolicy(
    public val consentReceiptSha256: String,
    public val physicalPilotAuthoritySha256: String,
    public val retentionDecisionId: String,
    public val deletionOwnerId: String,
    public val outputCaptureEnabled: Boolean,
    public val audioCaptureEnabled: Boolean,
    public val cloudTransferEnabled: Boolean,
    public val requiredAvailableBytes: Long,
    public val worstCaseBytes: Long,
) {
    init {
        require(consentReceiptSha256.isLowercaseSha256()) { "H2 consent decision is unresolved" }
        require(physicalPilotAuthoritySha256.isLowercaseSha256()) { "H2 physical pilot is not authorized" }
        require(retentionDecisionId.isNotBlank()) { "H2 retention decision is unresolved" }
        require(deletionOwnerId.isNotBlank()) { "H2 deletion owner is unresolved" }
        require(outputCaptureEnabled) { "H2 actual visible output must be retained" }
        require(!audioCaptureEnabled) { "H2 route must not capture audio" }
        require(!cloudTransferEnabled) { "H2 route is local-only" }
        require(worstCaseBytes > 0)
        require(requiredAvailableBytes >= worstCaseBytes)
    }
}

/** Seven RIO-only decisions. Null remains an intentional pre-physical fail-closed state. */
@Serializable
public data class H2HumanChoiceReceipts(
    public val primaryUsefulResponseLineSha256: String? = null,
    public val ordinaryUseAndTemporalRiskLinesSha256: String? = null,
    public val actionSpecificProductJudgmentSha256: String? = null,
    public val displayVisibilityAndRouteSemanticsSha256: String? = null,
    public val participantSessionDeviceEnvironmentScopeSha256: String? = null,
    public val privacyConsentRetentionDeletionViewersSha256: String? = null,
    public val physicalPilotAndCaptureAuthoritiesSha256: String? = null,
) {
    public fun requireAllClosed(): H2HumanChoiceReceipts {
        val values = listOf(
            primaryUsefulResponseLineSha256,
            ordinaryUseAndTemporalRiskLinesSha256,
            actionSpecificProductJudgmentSha256,
            displayVisibilityAndRouteSemanticsSha256,
            participantSessionDeviceEnvironmentScopeSha256,
            privacyConsentRetentionDeletionViewersSha256,
            physicalPilotAndCaptureAuthoritiesSha256,
        )
        require(values.all { it?.isLowercaseSha256() == true }) {
            "all seven H2 human-choice receipts must close before physical execution"
        }
        return this
    }
}

@Serializable
public data class H2SourceBinaryIdentity(
    public val sourceManifestSha256: String,
    public val sourcePatchSha256: String,
    public val apkSha256: String,
    public val primaryModelSha256: String,
    public val mediaPipeTaskSha256: String,
    public val baseCommitSha: String,
    public val buildInputMatchesManifest: Boolean,
) {
    init {
        require(sourceManifestSha256.isLowercaseSha256())
        require(sourcePatchSha256.isLowercaseSha256())
        require(apkSha256.isLowercaseSha256())
        require(primaryModelSha256 == SESSION_C_PRIMARY_MODEL_SHA256)
        require(mediaPipeTaskSha256.isLowercaseSha256())
        require(baseCommitSha.matches(Regex("[0-9a-f]{40}")))
        require(buildInputMatchesManifest) { "H2 isolated build input diverges from its manifest" }
    }

    public companion object {
        public const val SESSION_C_PRIMARY_MODEL_SHA256: String =
            "a304d132e36cad9f6dcc9a8c1c78f34201cbee90e640f39625a2fbe05fd12dd3"
    }
}

@Serializable
public enum class H2RunPhase { PREPARED, OPEN, TERMINATED, SEALED }

@Serializable
public enum class H2FailureCode(public val retryEligibleBeforePublication: Boolean) {
    UNPUBLISHED_CAMERA_STREAM_WRITE_FAILURE(true),
    UNPUBLISHED_OUTPUT_STREAM_WRITE_FAILURE(true),
    UNPUBLISHED_EVENT_LOG_WRITE_FAILURE(true),
    UNPUBLISHED_HASH_OR_LENGTH_MISMATCH(true),
    UNPUBLISHED_ATOMIC_PUBLICATION_PRECONDITION_FAILURE(true),
    BEGIN_ATTEMPT_PRECONDITION_FAILURE(false),
    PARTICIPANT_STOP(false),
    READINESS_TIMEOUT(false),
    READINESS_LOSS(false),
    RUNTIME_OR_DELEGATE_FALLBACK(false),
    IDENTITY_DRIFT(false),
    PRIVACY_OR_AUTHORITY_BREACH(false),
    CLOCK_DISCONTINUITY(false),
    CAMERA_LOSS(false),
    RENDER_LOSS(false),
    INSTRUCTION_DEVIATION(false),
    OBSERVATION_INCOMPLETE(false),
    RESTART_DURING_ACTIVE_ATTEMPT(false),
    STALE_OPEN_OR_PREPARED_RUN(false),
    POST_PUBLICATION_MUTATION(false),
}

@Serializable
public data class H2FailureReceipt(
    public val blockId: String,
    public val captureAttempt: Int,
    public val code: H2FailureCode,
) {
    init {
        require(blockId in H2ConfirmationContract.blocks.map(H2ConfirmationBlock::id))
        require(captureAttempt in 1..2)
    }
}

@Serializable
public data class H2BlockReceipt(
    public val blockId: String,
    public val captureAttempt: Int,
    public val observableSeconds: Int,
    public val primaryVisibleEventCount: Int,
    public val comparatorVisibleEventCount: Int,
    public val retainedStreamManifestSha256: String,
    public val publishedAtomically: Boolean,
) {
    init {
        val block = H2ConfirmationContract.blocks.singleOrNull { it.id == blockId }
        require(block != null)
        require(captureAttempt in 1..2)
        require(observableSeconds >= 0)
        if (block.kind == H2BlockKind.STARTUP_READINESS) {
            require(primaryVisibleEventCount == 0 && comparatorVisibleEventCount == 0) {
                "B01 availability evidence must not be represented as scientific output"
            }
        } else {
            require(primaryVisibleEventCount > 0) { "primary actual-output evidence is absent" }
            require(comparatorVisibleEventCount > 0) { "comparator actual-output evidence is absent" }
        }
        require(retainedStreamManifestSha256.isLowercaseSha256())
        require(publishedAtomically) { "H2 block was not published atomically" }
    }
}

@Serializable
public data class H2RunState(
    public val runIdentitySha256: String,
    public val phase: H2RunPhase = H2RunPhase.PREPARED,
    public val nextBlockIndex: Int = 0,
    public val activeBlockId: String? = null,
    public val activeAttempt: Int? = null,
    public val replacementUsedBlockId: String? = null,
    public val failures: List<H2FailureReceipt> = emptyList(),
    public val receipts: List<H2BlockReceipt> = emptyList(),
    public val terminalCode: H2FailureCode? = null,
) {
    init {
        require(runIdentitySha256.isLowercaseSha256())
        require(nextBlockIndex in 0..H2ConfirmationContract.EXPECTED_BLOCK_COUNT)
        require(receipts.size == nextBlockIndex) { "H2 receipt count does not match progress" }
        require(
            receipts.map(H2BlockReceipt::blockId) ==
                H2ConfirmationContract.blocks.take(nextBlockIndex).map(H2ConfirmationBlock::id),
        ) { "H2 receipts are not in exact block order" }
        require((activeBlockId == null) == (activeAttempt == null))
        when (phase) {
            H2RunPhase.PREPARED -> require(
                nextBlockIndex == 0 && activeBlockId == null && receipts.isEmpty() && terminalCode == null,
            )
            H2RunPhase.OPEN -> require(terminalCode == null)
            H2RunPhase.TERMINATED -> require(activeBlockId == null && terminalCode != null)
            H2RunPhase.SEALED -> require(
                activeBlockId == null && terminalCode == null &&
                    nextBlockIndex == H2ConfirmationContract.EXPECTED_BLOCK_COUNT,
            )
        }
    }
}

/** Opens only a fresh exclusive run; neither identity nor progress can arrive from an Intent. */
public fun openPreparedH2Run(
    state: H2RunState,
    exclusiveLeaseAcquired: Boolean,
    noPriorOpenOrTerminalRun: Boolean,
): H2RunState {
    check(state.phase == H2RunPhase.PREPARED)
    require(exclusiveLeaseAcquired) { "H2 exclusive run lease is unavailable" }
    require(noPriorOpenOrTerminalRun) { "H2 run identity is not fresh" }
    return state.copy(phase = H2RunPhase.OPEN)
}

@Serializable
public enum class H2FailureDecision { RETRY_ONCE, TERMINATED }

public fun beginH2Block(
    state: H2RunState,
    blockId: String,
    providerPolicyRouteRenderAvailable: Boolean,
): H2RunState {
    check(state.phase == H2RunPhase.OPEN) { "H2 run is not open" }
    check(state.activeBlockId == null) { "another H2 block is active" }
    val block = H2ConfirmationContract.blocks.getOrNull(state.nextBlockIndex)
        ?: error("H2 inventory is already complete")
    require(block.id == blockId) { "H2 block is out of order" }
    require(block.kind == H2BlockKind.STARTUP_READINESS || providerPolicyRouteRenderAvailable) {
        "H2 block cannot begin without immutable provider/policy/route/render availability"
    }
    val attempt = if (blockId == state.replacementUsedBlockId) 2 else 1
    return state.copy(activeBlockId = blockId, activeAttempt = attempt)
}

public fun completeH2Block(state: H2RunState, receipt: H2BlockReceipt): H2RunState {
    check(state.phase == H2RunPhase.OPEN)
    require(state.activeBlockId == receipt.blockId) { "H2 completion does not match active block" }
    require(state.activeAttempt == receipt.captureAttempt) { "H2 completion attempt does not match" }
    val block = H2ConfirmationContract.blocks[state.nextBlockIndex]
    require(receipt.observableSeconds >= block.minimumObservableSeconds) {
        "H2 block does not have its required observable denominator"
    }
    return state.copy(
        nextBlockIndex = state.nextBlockIndex + 1,
        activeBlockId = null,
        activeAttempt = null,
        receipts = state.receipts + receipt,
    )
}

public fun failH2Block(
    state: H2RunState,
    blockId: String,
    machineFailureCode: H2FailureCode,
): Pair<H2RunState, H2FailureDecision> {
    check(state.phase == H2RunPhase.OPEN)
    require(state.activeBlockId == blockId) { "H2 failure does not match active block" }
    val failureReceipt = H2FailureReceipt(blockId, requireNotNull(state.activeAttempt), machineFailureCode)
    val retryEligible = machineFailureCode.retryEligibleBeforePublication &&
        state.replacementUsedBlockId == null
    return if (retryEligible) {
        state.copy(
            activeBlockId = null,
            activeAttempt = null,
            replacementUsedBlockId = blockId,
            failures = state.failures + failureReceipt,
        ) to H2FailureDecision.RETRY_ONCE
    } else {
        state.copy(
            phase = H2RunPhase.TERMINATED,
            activeBlockId = null,
            activeAttempt = null,
            failures = state.failures + failureReceipt,
            terminalCode = machineFailureCode,
        ) to H2FailureDecision.TERMINATED
    }
}

/** Terminates a stale/restarted run without manufacturing a block attempt that never existed. */
public fun terminateH2Run(state: H2RunState, code: H2FailureCode): H2RunState {
    check(state.phase == H2RunPhase.PREPARED || state.phase == H2RunPhase.OPEN)
    val activeFailure = state.activeBlockId?.let { blockId ->
        H2FailureReceipt(blockId, requireNotNull(state.activeAttempt), code)
    }
    return state.copy(
        phase = H2RunPhase.TERMINATED,
        activeBlockId = null,
        activeAttempt = null,
        failures = if (activeFailure == null) state.failures else state.failures + activeFailure,
        terminalCode = code,
    )
}

internal fun interface H2SealSha256 {
    public fun digest(bytes: ByteArray): String
}

@Serializable
internal class H2SealAuthorityChain private constructor(
    public val sourceManifestSha256: String,
    public val sourcePatchSha256: String,
    public val apkSha256: String,
    public val buildConfigSha256: String,
    public val baseCommitSha: String,
    public val binaryReceiptSha256: String,
    public val devicePreflightSha256: String,
    public val privacyStorageParticipantAuthoritySha256: String,
    public val rioCaptureAuthoritySha256: String,
) {
    init {
        require(sourceManifestSha256.isLowercaseSha256())
        require(sourcePatchSha256.isLowercaseSha256())
        require(apkSha256.isLowercaseSha256())
        require(buildConfigSha256.isLowercaseSha256())
        require(baseCommitSha.matches(Regex("[0-9a-f]{40}")))
        require(binaryReceiptSha256.isLowercaseSha256())
        require(devicePreflightSha256.isLowercaseSha256())
        require(privacyStorageParticipantAuthoritySha256.isLowercaseSha256())
        require(rioCaptureAuthoritySha256.isLowercaseSha256())
    }

    companion object {
        internal fun fromRetainedBytes(
            sourceManifest: ByteArray,
            sourcePatch: ByteArray,
            apk: ByteArray,
            buildConfig: ByteArray,
            baseCommitSha: String,
            binaryReceipt: ByteArray,
            devicePreflight: ByteArray,
            privacyStorageParticipantAuthority: ByteArray,
            rioCaptureAuthority: ByteArray,
            sha256: H2SealSha256,
        ): H2SealAuthorityChain {
            require(
                listOf(
                    sourceManifest,
                    sourcePatch,
                    apk,
                    buildConfig,
                    binaryReceipt,
                    devicePreflight,
                    privacyStorageParticipantAuthority,
                    rioCaptureAuthority,
                ).all(ByteArray::isNotEmpty),
            ) { "H2 seal authority bytes are incomplete" }
            return H2SealAuthorityChain(
                sourceManifestSha256 = sha256.digest(sourceManifest),
                sourcePatchSha256 = sha256.digest(sourcePatch),
                apkSha256 = sha256.digest(apk),
                buildConfigSha256 = sha256.digest(buildConfig),
                baseCommitSha = baseCommitSha,
                binaryReceiptSha256 = sha256.digest(binaryReceipt),
                devicePreflightSha256 = sha256.digest(devicePreflight),
                privacyStorageParticipantAuthoritySha256 = sha256.digest(
                    privacyStorageParticipantAuthority,
                ),
                rioCaptureAuthoritySha256 = sha256.digest(rioCaptureAuthority),
            )
        }
    }
}

/** Digests that can only be created by hashing the supplied retained evidence bytes. */
internal class H2VerifiedSealDigestBundle internal constructor(
    internal val runIdentitySha256: String,
    internal val orderedBlockReceiptsCanonicalUtf8Lf: String,
    public val orderedBlockReceiptsSha256: String,
    public val controllerEventStreamsSha256: String,
    public val clockMappingReceiptSha256: String,
    public val retainedStreamManifestsSha256: String,
)

@Serializable
public class H2PublicSafeSeal internal constructor(
    public val protocolId: String,
    public val protocolSha256: String,
    public val runIdentitySha256: String,
    public val sourceManifestSha256: String,
    public val sourcePatchSha256: String,
    public val apkSha256: String,
    public val buildConfigSha256: String,
    public val baseCommitSha: String,
    public val binaryReceiptSha256: String,
    public val devicePreflightSha256: String,
    public val privacyStorageParticipantAuthoritySha256: String,
    public val rioCaptureAuthoritySha256: String,
    public val orderedBlockReceiptsSha256: String,
    public val controllerEventStreamsSha256: String,
    public val clockMappingReceiptSha256: String,
    public val retainedStreamManifestsSha256: String,
    public val failureLedger: List<H2FailureReceipt>,
    public val missingExtraDuplicateMutationChecksPassed: Boolean,
    public val scoreOpened: Boolean,
    public val terminalState: H2RunPhase,
    public val disposition: String,
    public val sealedAtIso: String,
    public val blockCount: Int,
    internal val orderedBlockReceiptsCanonicalUtf8Lf: String,
) {
    init {
        require(protocolId == H2ConfirmationContract.PROTOCOL_ID)
        require(protocolSha256 == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
        require(runIdentitySha256.isLowercaseSha256())
        require(sourceManifestSha256.isLowercaseSha256())
        require(sourcePatchSha256.isLowercaseSha256())
        require(apkSha256.isLowercaseSha256())
        require(buildConfigSha256.isLowercaseSha256())
        require(baseCommitSha.matches(Regex("[0-9a-f]{40}")))
        require(binaryReceiptSha256.isLowercaseSha256())
        require(devicePreflightSha256.isLowercaseSha256())
        require(privacyStorageParticipantAuthoritySha256.isLowercaseSha256())
        require(rioCaptureAuthoritySha256.isLowercaseSha256())
        require(orderedBlockReceiptsSha256.isLowercaseSha256())
        require(controllerEventStreamsSha256.isLowercaseSha256())
        require(clockMappingReceiptSha256.isLowercaseSha256())
        require(retainedStreamManifestsSha256.isLowercaseSha256())
        require(missingExtraDuplicateMutationChecksPassed)
        require(!scoreOpened) { "H2 capture seal cannot open a score" }
        require(terminalState == H2RunPhase.SEALED)
        require(disposition == SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION)
        require(sealedAtIso.matches(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z"))) {
            "H2 capture seal time must be a canonical UTC instant"
        }
        require(blockCount == H2ConfirmationContract.EXPECTED_BLOCK_COUNT)
    }

    public companion object {
        public const val SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION: String =
            "SCORE_FREE_RETAINED_EVIDENCE_SEALED_NOT_SCORED"
    }
}

/** Builds the only permitted terminal label and protocol identity from a verified digest bundle. */
internal fun buildH2PublicSafeSeal(
    state: H2RunState,
    authority: H2SealAuthorityChain,
    digests: H2VerifiedSealDigestBundle,
    missingExtraDuplicateMutationChecksPassed: Boolean,
    sealedAtIso: String,
): H2PublicSafeSeal {
    require(digests.runIdentitySha256 == state.runIdentitySha256)
    require(digests.orderedBlockReceiptsCanonicalUtf8Lf == state.orderedBlockReceiptsCanonicalUtf8Lf())
    return H2PublicSafeSeal(
        protocolId = H2ConfirmationContract.PROTOCOL_ID,
        protocolSha256 = H2ConfirmationContract.PROTOCOL_PREREG_SHA256,
        runIdentitySha256 = state.runIdentitySha256,
        sourceManifestSha256 = authority.sourceManifestSha256,
        sourcePatchSha256 = authority.sourcePatchSha256,
        apkSha256 = authority.apkSha256,
        buildConfigSha256 = authority.buildConfigSha256,
        baseCommitSha = authority.baseCommitSha,
        binaryReceiptSha256 = authority.binaryReceiptSha256,
        devicePreflightSha256 = authority.devicePreflightSha256,
        privacyStorageParticipantAuthoritySha256 = authority.privacyStorageParticipantAuthoritySha256,
        rioCaptureAuthoritySha256 = authority.rioCaptureAuthoritySha256,
        orderedBlockReceiptsSha256 = digests.orderedBlockReceiptsSha256,
        controllerEventStreamsSha256 = digests.controllerEventStreamsSha256,
        clockMappingReceiptSha256 = digests.clockMappingReceiptSha256,
        retainedStreamManifestsSha256 = digests.retainedStreamManifestsSha256,
        failureLedger = state.failures,
        missingExtraDuplicateMutationChecksPassed = missingExtraDuplicateMutationChecksPassed,
        scoreOpened = false,
        terminalState = H2RunPhase.SEALED,
        disposition = H2PublicSafeSeal.SCORE_FREE_RETAINED_EVIDENCE_DISPOSITION,
        sealedAtIso = sealedAtIso,
        blockCount = H2ConfirmationContract.EXPECTED_BLOCK_COUNT,
        orderedBlockReceiptsCanonicalUtf8Lf = digests.orderedBlockReceiptsCanonicalUtf8Lf,
    )
}

/** Returns a terminal immutable state only after all exact blocks were sealed atomically. */
internal fun sealH2Run(state: H2RunState, seal: H2PublicSafeSeal): H2RunState {
    check(state.phase == H2RunPhase.OPEN)
    check(state.activeBlockId == null)
    require(state.nextBlockIndex == H2ConfirmationContract.EXPECTED_BLOCK_COUNT)
    require(state.receipts.size == H2ConfirmationContract.EXPECTED_BLOCK_COUNT)
    require(seal.runIdentitySha256 == state.runIdentitySha256)
    require(seal.protocolSha256 == H2ConfirmationContract.PROTOCOL_PREREG_SHA256)
    require(seal.orderedBlockReceiptsCanonicalUtf8Lf == state.orderedBlockReceiptsCanonicalUtf8Lf())
    require(seal.failureLedger == state.failures)
    return state.copy(phase = H2RunPhase.SEALED)
}

internal fun H2RunState.orderedBlockReceiptsCanonicalUtf8Lf(): String = receipts.joinToString(
    separator = "\n",
    postfix = "\n",
) { receipt ->
    "${receipt.blockId}\t${receipt.captureAttempt}\t${receipt.observableSeconds}\t" +
        "${receipt.primaryVisibleEventCount}\t${receipt.comparatorVisibleEventCount}\t" +
        "${receipt.retainedStreamManifestSha256}\t${receipt.publishedAtomically}"
}

/** A later score-blind human annotation binds to an immutable capture seal; it is not part of it. */
@Serializable
public class H2ScoreBlindAnnotationReceipt internal constructor(
    public val captureSealSha256: String,
    public val annotationPayloadSha256: String,
    public val reviewerAuthoritySha256: String,
    public val scoreOpened: Boolean,
) {
    init {
        require(captureSealSha256.isLowercaseSha256())
        require(annotationPayloadSha256.isLowercaseSha256())
        require(reviewerAuthoritySha256.isLowercaseSha256())
        require(!scoreOpened) { "H2 physical/action annotation must remain score blind" }
    }
}
