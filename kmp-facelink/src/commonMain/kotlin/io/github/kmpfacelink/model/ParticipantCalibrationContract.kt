package io.github.kmpfacelink.model

import io.github.kmpfacelink.ExperimentalFaceLinkApi

/**
 * Opaque host-selected binding for one local participant enrollment.
 *
 * The value must not contain a name, image, face embedding, account ID, or
 * identity inferred from visual input.
 */
@ExperimentalFaceLinkApi
public data class ParticipantEnrollmentBinding(val opaqueValue: String) {
    init {
        require(opaqueValue.isNotBlank()) { "opaqueValue must not be blank" }
    }
}

/** A derived signal that is deliberately outside the canonical 52-value blend-shape vocabulary. */
@ExperimentalFaceLinkApi
public sealed interface DerivedFaceSignal {
    /** Stable signal identifier for provenance and application policy. */
    public val id: String

    /** Canonical estimator value retained as the baseline for this signal. */
    public val canonicalBlendShape: BlendShape

    /** Participant-specific cheek-puff candidate; this is not a 53rd blend shape. */
    public data object CheekPuffCandidate : DerivedFaceSignal {
        override val id: String = "cheek-puff-candidate-v1"
        override val canonicalBlendShape: BlendShape = BlendShape.CHEEK_PUFF
    }
}

/** Numeric meaning of a derived signal value. */
@ExperimentalFaceLinkApi
public enum class DerivedFaceSignalValueSemantics {
    /** Finite activation value in the inclusive range 0.0–1.0. */
    UNIT_INTERVAL_ACTIVATION,
}

/** One candidate sample bound to the reviewed candidate artifact. */
@ExperimentalFaceLinkApi
public data class DerivedFaceSignalSample(
    val signal: DerivedFaceSignal,
    val value: Float,
    val candidateId: String,
    val artifactSha256: String,
) {
    init {
        require(value.isFinite() && value in 0f..1f) { "value must be finite and within 0.0..1.0" }
        require(candidateId.isNotBlank()) { "candidateId must not be blank" }
        require(artifactSha256.isSha256Hex()) { "artifactSha256 must be lowercase SHA-256" }
    }
}

/** Immutable participant-specific candidate artifact identity. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationArtifact(
    val profileId: String,
    val enrollmentBinding: ParticipantEnrollmentBinding,
    val candidateId: String,
    val targetSignal: DerivedFaceSignal,
    val candidateImplementationSha256: String,
    val preprocessingSha256: String,
    val candidateModelSha256: String,
    val thresholdSha256: String,
    val artifactSha256: String,
    val expectedContext: MeasurementContext,
    val profileRevision: ULong,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(candidateId.isNotBlank()) { "candidateId must not be blank" }
        require(candidateImplementationSha256.isSha256Hex()) {
            "candidateImplementationSha256 must be lowercase SHA-256"
        }
        require(preprocessingSha256.isSha256Hex()) { "preprocessingSha256 must be lowercase SHA-256" }
        require(candidateModelSha256.isSha256Hex()) { "candidateModelSha256 must be lowercase SHA-256" }
        require(thresholdSha256.isSha256Hex()) { "thresholdSha256 must be lowercase SHA-256" }
        require(artifactSha256.isSha256Hex()) { "artifactSha256 must be lowercase SHA-256" }
        require(issuedAtEpochMs >= 0L) { "issuedAtEpochMs must not be negative" }
        require(expiresAtEpochMs > issuedAtEpochMs) { "expiresAtEpochMs must be after issue time" }
    }
}

/** Reviewed evidence receipt required before a participant-specific candidate can be ready. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationEvidenceReceipt(
    val receiptSha256: String,
    val profileId: String,
    val profileRevision: ULong,
    val artifactSha256: String,
    val enrollmentProtocolId: String,
    val enrollmentRunId: String,
    val qualificationProtocolId: String,
    val qualificationRunId: String,
    val qualificationReceiptSha256: String,
    val sdkSourceSha256: String,
    val reviewedBinarySha256: String,
    val publicReviewPacketSha256: String,
    val evidenceScope: EvidenceScope,
) {
    init {
        require(receiptSha256.isSha256Hex()) { "receiptSha256 must be lowercase SHA-256" }
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(artifactSha256.isSha256Hex()) { "artifactSha256 must be lowercase SHA-256" }
        require(enrollmentProtocolId.isNotBlank()) { "enrollmentProtocolId must not be blank" }
        require(enrollmentRunId.isNotBlank()) { "enrollmentRunId must not be blank" }
        require(qualificationProtocolId.isNotBlank()) { "qualificationProtocolId must not be blank" }
        require(qualificationRunId.isNotBlank()) { "qualificationRunId must not be blank" }
        require(qualificationReceiptSha256.isSha256Hex()) {
            "qualificationReceiptSha256 must be lowercase SHA-256"
        }
        require(sdkSourceSha256.isSha256Hex()) { "sdkSourceSha256 must be lowercase SHA-256" }
        require(reviewedBinarySha256.isSha256Hex()) { "reviewedBinarySha256 must be lowercase SHA-256" }
        require(publicReviewPacketSha256.isSha256Hex()) {
            "publicReviewPacketSha256 must be lowercase SHA-256"
        }
    }
}

/** Artifact and reviewed evidence that form one immutable participant profile. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationProfile(
    val artifact: ParticipantCalibrationArtifact,
    val evidenceReceipt: ParticipantCalibrationEvidenceReceipt,
) {
    init {
        require(evidenceReceipt.profileId == artifact.profileId) { "receipt profileId must match artifact" }
        require(evidenceReceipt.profileRevision == artifact.profileRevision) {
            "receipt profileRevision must match artifact"
        }
        require(evidenceReceipt.artifactSha256 == artifact.artifactSha256) {
            "receipt artifactSha256 must match artifact"
        }
    }
}

/** Enrollment progress before a reviewed profile exists. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationCollection(
    val enrollmentBinding: ParticipantEnrollmentBinding,
    val collectedUnits: Int,
    val requiredUnits: Int,
) {
    init {
        require(requiredUnits > 0) { "requiredUnits must be positive" }
        require(collectedUnits in 0 until requiredUnits) {
            "collectedUnits must be non-negative and less than requiredUnits"
        }
    }
}

/** Exact runtime and build identity presented to the participant-calibration reducer. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationRuntimeIdentity(
    val profileId: String,
    val profileRevision: ULong,
    val enrollmentBinding: ParticipantEnrollmentBinding,
    val contextEpoch: TrackingContextEpoch,
    val candidateId: String,
    val targetSignal: DerivedFaceSignal,
    val candidateImplementationSha256: String,
    val preprocessingSha256: String,
    val candidateModelSha256: String,
    val thresholdSha256: String,
    val candidateArtifactSha256: String,
    val evidenceReceiptSha256: String,
    val sdkSourceSha256: String,
    val binarySha256: String,
) {
    init {
        require(profileId.isNotBlank()) { "profileId must not be blank" }
        require(candidateId.isNotBlank()) { "candidateId must not be blank" }
        require(candidateImplementationSha256.isSha256Hex()) {
            "candidateImplementationSha256 must be lowercase SHA-256"
        }
        require(preprocessingSha256.isSha256Hex()) { "preprocessingSha256 must be lowercase SHA-256" }
        require(candidateModelSha256.isSha256Hex()) { "candidateModelSha256 must be lowercase SHA-256" }
        require(thresholdSha256.isSha256Hex()) { "thresholdSha256 must be lowercase SHA-256" }
        require(candidateArtifactSha256.isSha256Hex()) {
            "candidateArtifactSha256 must be lowercase SHA-256"
        }
        require(evidenceReceiptSha256.isSha256Hex()) {
            "evidenceReceiptSha256 must be lowercase SHA-256"
        }
        require(sdkSourceSha256.isSha256Hex()) { "sdkSourceSha256 must be lowercase SHA-256" }
        require(binarySha256.isSha256Hex()) { "binarySha256 must be lowercase SHA-256" }
    }
}

/** Clock dependency for deterministic lifecycle evaluation. */
@ExperimentalFaceLinkApi
public fun interface ParticipantCalibrationClock {
    /** Current Unix epoch time in milliseconds. */
    public fun nowEpochMs(): Long
}

/** Fail-closed participant-calibration lifecycle. */
@ExperimentalFaceLinkApi
public sealed interface ParticipantCalibrationLifecycle {
    /** No participant profile or collection is available. */
    public data class Unavailable(val reason: String) : ParticipantCalibrationLifecycle {
        init {
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }

    /** Enrollment observations are still being collected. */
    public data class Collecting(
        val enrollmentBinding: ParticipantEnrollmentBinding,
        val collectedUnits: Int,
        val requiredUnits: Int,
    ) : ParticipantCalibrationLifecycle {
        init {
            require(requiredUnits > 0) { "requiredUnits must be positive" }
            require(collectedUnits in 0 until requiredUnits) {
                "collectedUnits must be non-negative and less than requiredUnits"
            }
        }
    }

    /** Every identity and the accepted reviewed evidence match. */
    public data class Ready(val profile: ParticipantCalibrationProfile) : ParticipantCalibrationLifecycle

    /** The matched profile reached its exclusive expiry boundary. */
    public data class Expired(
        val profileId: String,
        val profileRevision: ULong,
        val expiredAtEpochMs: Long,
    ) : ParticipantCalibrationLifecycle {
        init {
            require(profileId.isNotBlank()) { "profileId must not be blank" }
            require(expiredAtEpochMs >= 0L) { "expiredAtEpochMs must not be negative" }
        }
    }

    /** At least one required identity or technical field differs. */
    public data class Mismatched(
        val profileId: String?,
        val profileRevision: ULong?,
        val differingFields: Set<String>,
    ) : ParticipantCalibrationLifecycle {
        init {
            require((profileId == null) == (profileRevision == null)) {
                "profileId and profileRevision must both be present or absent"
            }
            require(differingFields.isNotEmpty()) { "differingFields must not be empty" }
        }
    }

    /** Product evidence acceptance explicitly rejected the profile. */
    public data class Rejected(
        val profileId: String,
        val profileRevision: ULong,
        val reason: String,
        val evidenceReceipt: ParticipantCalibrationEvidenceReceipt,
    ) : ParticipantCalibrationLifecycle {
        init {
            require(profileId.isNotBlank()) { "profileId must not be blank" }
            require(reason.isNotBlank()) { "reason must not be blank" }
        }
    }
}

/** Complete immutable input to one lifecycle reduction. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationReductionInput(
    val profile: ParticipantCalibrationProfile?,
    val collection: ParticipantCalibrationCollection?,
    val runtime: ParticipantCalibrationRuntimeIdentity,
    val evidenceAcceptance: EvidenceAcceptance,
) {
    init {
        require(profile == null || collection == null) { "profile and collection cannot both be present" }
    }
}

/** Provenance attached to one separate conditional decision. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationDecisionProvenance(
    val targetSignal: DerivedFaceSignal,
    val canonicalBlendShape: BlendShape,
    val runtimeConfigurationFingerprint: String,
    val runtimeEpochFingerprint: String,
    val profileId: String?,
    val profileRevision: ULong?,
    val candidateId: String?,
    val evidenceReceiptSha256: String?,
) {
    init {
        require(runtimeConfigurationFingerprint.isSha256Hex()) {
            "runtimeConfigurationFingerprint must be lowercase SHA-256"
        }
        require(runtimeEpochFingerprint.isSha256Hex()) {
            "runtimeEpochFingerprint must be lowercase SHA-256"
        }
        require(profileId == null || profileId.isNotBlank()) { "profileId must not be blank when present" }
        require(candidateId == null || candidateId.isNotBlank()) { "candidateId must not be blank when present" }
        require(evidenceReceiptSha256 == null || evidenceReceiptSha256.isSha256Hex()) {
            "evidenceReceiptSha256 must be lowercase SHA-256 when present"
        }
    }
}

/**
 * Factory-created sidecar outcome that never mutates the canonical 52-value blend-shape map.
 */
@ExperimentalFaceLinkApi
public class DerivedFaceSignalDecision private constructor(
    public val signal: DerivedFaceSignal,
    public val canonicalBlendShape: BlendShape,
    public val canonicalValue: Float?,
    public val candidateValue: Float?,
    public val valueSemantics: DerivedFaceSignalValueSemantics,
    public val lifecycle: ParticipantCalibrationLifecycle,
    public val provenance: ParticipantCalibrationDecisionProvenance,
    public val route: BlendShapeRoute,
) {
    init {
        require(canonicalBlendShape == signal.canonicalBlendShape) {
            "canonicalBlendShape must match signal"
        }
        require(canonicalValue == null || canonicalValue.isFinite() && canonicalValue in 0f..1f) {
            "canonicalValue must be finite and within 0.0..1.0 when present"
        }
        require(candidateValue == null || candidateValue.isFinite() && candidateValue in 0f..1f) {
            "candidateValue must be finite and within 0.0..1.0 when present"
        }
        require(route !is BlendShapeRoute.Alternative || lifecycle is ParticipantCalibrationLifecycle.Ready) {
            "Alternative route requires Ready lifecycle"
        }
        require(route !is BlendShapeRoute.Alternative || candidateValue != null) {
            "Alternative route requires candidateValue"
        }
        require(route is BlendShapeRoute.Alternative || candidateValue == null) {
            "Non-alternative route cannot expose candidateValue"
        }
        require(route !is BlendShapeRoute.Estimator || canonicalValue != null) {
            "Estimator route requires canonicalValue"
        }
    }

    internal companion object {
        internal fun create(
            signal: DerivedFaceSignal,
            canonicalValue: Float?,
            candidateValue: Float?,
            lifecycle: ParticipantCalibrationLifecycle,
            provenance: ParticipantCalibrationDecisionProvenance,
            route: BlendShapeRoute,
        ): DerivedFaceSignalDecision = DerivedFaceSignalDecision(
            signal = signal,
            canonicalBlendShape = signal.canonicalBlendShape,
            canonicalValue = canonicalValue,
            candidateValue = candidateValue,
            valueSemantics = DerivedFaceSignalValueSemantics.UNIT_INTERVAL_ACTIVATION,
            lifecycle = lifecycle,
            provenance = provenance,
            route = route,
        )
    }
}

/** Inputs to one separate derived-signal decision. */
@ExperimentalFaceLinkApi
public data class ParticipantCalibrationDecisionInput(
    val reductionInput: ParticipantCalibrationReductionInput,
    val canonicalBlendShapes: BlendShapeData,
    val candidate: DerivedFaceSignalSample?,
)

/** Application-owned route for any state where the candidate cannot be selected. */
@ExperimentalFaceLinkApi
public fun interface ParticipantCalibrationFallbackPolicy {
    /** Returns Estimator, ExplicitInput, or Disabled for the supplied lifecycle. */
    public fun decide(
        signal: DerivedFaceSignal,
        lifecycle: ParticipantCalibrationLifecycle,
    ): BlendShapeRoute
}

/** Default-off conditional-decision boundary. */
@ExperimentalFaceLinkApi
public fun interface ParticipantCalibrationDecisionEngine {
    /** Produces a sidecar decision without changing tracker output. */
    public fun decide(input: ParticipantCalibrationDecisionInput): DerivedFaceSignalDecision
}
