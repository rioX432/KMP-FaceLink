package io.github.kmpfacelink.model

/** Explicit state for a capability comparison dimension. */
public sealed interface DimensionValue<out T> {
    /** A value that was observed and can participate in exact matching. */
    public data class Known<T>(val value: T) : DimensionValue<T>

    /** The dimension does not exist for this estimator or platform. */
    public data object NotApplicable : DimensionValue<Nothing>

    /** The dimension should exist, but its value was not observed. */
    public data object Unknown : DimensionValue<Nothing>
}

/** Opaque, non-biometric identity of one tracker instance. */
public data class SessionInstanceId(val opaqueValue: String) {
    init {
        require(opaqueValue.isNotBlank()) { "opaqueValue must not be blank" }
    }
}

/** Whether an evidence profile is portable or bound to one tracker instance. */
public sealed interface SessionBinding {
    /** Evidence contains no learned per-session state. */
    public data object Portable : SessionBinding

    /** Evidence may be used only by the tracker instance that produced it. */
    public data class SameSession(val id: SessionInstanceId) : SessionBinding
}

/** Revision requirement for a measured output condition. */
public sealed interface RevisionRequirement {
    /** No session-state revision participates in matching. */
    public data object NotApplicable : RevisionRequirement

    /** Both state revisions must match inside the same tracker instance. */
    public data class Exact(
        val calibrationStateRevision: ULong,
        val processingStateRevision: ULong,
    ) : RevisionRequirement
}

/** Estimator artifact that produced the raw output. */
public data class EstimatorArtifact(
    val family: FaceTrackingBackend,
    val libraryVersion: DimensionValue<String>,
    val modelSha256: DimensionValue<String>,
)

/** Platform facts that can change estimator behavior. */
public data class PlatformIdentity(
    val platform: String,
    val osVersion: DimensionValue<String>,
    val deviceModel: DimensionValue<String>,
    val estimatorConfigurationId: DimensionValue<String>,
) {
    init {
        require(platform.isNotBlank()) { "platform must not be blank" }
    }
}

/** One stage in the configured or applied output pipeline. */
public enum class TransformKind {
    ENHANCER,
    CALIBRATOR,
    SMOOTHER,
    ACTIVE_SHAPE_FILTER,
    INTERPOLATOR,
}

/** Whether a transform can be compared by configuration or contains learned session state. */
public enum class TransformStateDependence {
    PORTABLE_CONFIGURATION,
    SESSION_LEARNED_STATE,
}

/** Immutable specification of a configured transform. */
public data class TransformSpec(
    val kind: TransformKind,
    val implementationId: String,
    val configurationFingerprint: String,
    val stateDependence: TransformStateDependence = TransformStateDependence.PORTABLE_CONFIGURATION,
) {
    init {
        require(implementationId.isNotBlank()) { "implementationId must not be blank" }
        require(configurationFingerprint.isNotBlank()) { "configurationFingerprint must not be blank" }
    }
}

/** Immutable runtime context captured when an inference input is submitted. */
public data class TrackingContextEpoch(
    val epochId: ULong,
    val sessionInstanceId: SessionInstanceId,
    val estimator: EstimatorArtifact,
    val platform: PlatformIdentity,
    val runningMode: DimensionValue<InferenceRunningMode>,
    val resolvedDelegate: DimensionValue<String>,
    val inputWidth: DimensionValue<Int>,
    val inputHeight: DimensionValue<Int>,
    val mirroredBeforeInference: DimensionValue<Boolean>,
    val configuredTransforms: List<TransformSpec>,
    val activeBlendShapes: Set<BlendShape>,
) {
    /** Portable SHA-256 of estimator, platform, input, and pipeline configuration. */
    public val configurationFingerprint: String = CanonicalContextFingerprint.computeConfiguration(this)

    /** Runtime SHA-256 that additionally binds [epochId] and [sessionInstanceId]. */
    public val epochFingerprint: String = CanonicalContextFingerprint.computeEpoch(this)

    init {
        require(activeBlendShapes.isNotEmpty()) { "activeBlendShapes must not be empty" }
    }
}

/** Primary and secondary faces use different output pipelines on Android. */
public enum class FacePipelineKind {
    PRIMARY,
    SECONDARY,
}

/** Per-frame phase of the session calibrator. */
public enum class CalibrationPhase {
    DISABLED,
    WARMUP,
    ACTIVE,
}

/** Whether values came from a full estimator run or interpolation. */
public enum class InferenceMaterialization {
    FULL_INFERENCE,
    INTERPOLATED,
}

/** One transform that was actually applied to a face observation. */
public data class AppliedTransform(
    val kind: TransformKind,
    val transformFingerprint: String,
) {
    init {
        require(transformFingerprint.isNotBlank()) { "transformFingerprint must not be blank" }
    }
}

/** Exact per-face conditions that produced one emitted value map. */
public data class FaceObservationProvenance(
    val contextEpochId: ULong,
    val faceIndex: Int,
    val pipelineKind: FacePipelineKind,
    val appliedTransforms: List<AppliedTransform>,
    val calibrationPhase: CalibrationPhase,
    val calibrationStateRevision: ULong,
    val processingStateRevision: ULong,
    val materialization: InferenceMaterialization,
    val emittedBlendShapes: Set<BlendShape>,
) {
    init {
        require(faceIndex >= 0) { "faceIndex must not be negative" }
    }
}

/** Schema layer being described. */
public enum class SchemaLayer {
    RAW_ESTIMATOR,
    CANONICAL_SDK_OUTPUT,
}

/** Presence in a schema, not an observed response. */
public enum class SchemaPresence {
    PRESENT,
    ABSENT,
}

/** Exact schema of one estimator artifact and layer. */
public data class BlendShapeSchema(
    val id: String,
    val estimator: EstimatorArtifact,
    val layer: SchemaLayer,
    val presence: Map<BlendShape, SchemaPresence>,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(presence.keys == BlendShape.entries.toSet()) { "presence must cover all blend shapes" }
    }
}

/** Observed response class under a dedicated measurement protocol. */
public enum class ObservedResponseClass {
    RESPONDED,
    FLOOR,
    CEILING,
}

/** Measurement state; untested is not a low score or a zero. */
public sealed interface ResponseObservation {
    /** A measured response linked to its profile. */
    public data class Measured(
        val response: ObservedResponseClass,
        val evidenceProfileId: String,
    ) : ResponseObservation

    /** No applicable dedicated stimulus was measured. */
    public data object Untested : ResponseObservation
}

/** Validation scope that must survive lookup and product acceptance. */
public enum class ValidationClass {
    EXPLORATORY_SINGLE_SESSION,
    INDEPENDENT_SESSION,
    MULTI_PARTICIPANT,
    PRODUCT_VALIDATED,
}

/** Scope and limitations of an evidence profile. */
public data class EvidenceScope(
    val validationClass: ValidationClass,
    val participantCount: Int,
    val independentSessionCount: Int,
    val deviceCount: Int,
    val environmentCount: Int,
    val limitations: List<String>,
) {
    init {
        require(participantCount >= 0) { "participantCount must not be negative" }
        require(independentSessionCount >= 0) { "independentSessionCount must not be negative" }
        require(deviceCount >= 0) { "deviceCount must not be negative" }
        require(environmentCount >= 0) { "environmentCount must not be negative" }
    }
}

/** Technical context of the measurements in an evidence profile. */
public data class MeasurementContext(
    val estimator: EstimatorArtifact,
    val platform: PlatformIdentity,
    val runningMode: DimensionValue<InferenceRunningMode>,
    val resolvedDelegate: DimensionValue<String>,
    val inputWidth: DimensionValue<Int>,
    val inputHeight: DimensionValue<Int>,
    val mirroredBeforeInference: DimensionValue<Boolean>,
    val configuredTransforms: List<TransformSpec>,
    val activeBlendShapes: Set<BlendShape>,
    val configurationFingerprint: String,
) {
    init {
        require(configurationFingerprint.isSha256Hex()) { "configurationFingerprint must be lowercase SHA-256" }
        require(activeBlendShapes.isNotEmpty()) { "activeBlendShapes must not be empty" }
    }
}

/** Actual face-level output condition used by a measurement profile. */
public data class MeasuredOutputCondition(
    val schemaLayer: SchemaLayer,
    val pipelineKind: FacePipelineKind,
    val appliedTransforms: List<AppliedTransform>,
    val calibrationPhase: CalibrationPhase,
    val materialization: InferenceMaterialization,
    val requiredEmittedShapes: Set<BlendShape>,
    val sessionBinding: SessionBinding,
    val revisionRequirement: RevisionRequirement,
) {
    init {
        val portable = sessionBinding == SessionBinding.Portable
        require(portable == (revisionRequirement == RevisionRequirement.NotApplicable)) {
            "Portable evidence must use NotApplicable revisions; same-session evidence must use exact revisions"
        }
        if (portable) {
            require(calibrationPhase == CalibrationPhase.DISABLED) {
                "Portable evidence cannot contain learned calibration"
            }
        }
    }
}

/** One immutable, reviewed set of observed responses. */
public data class ObservedResponseProfile(
    val id: String,
    val context: MeasurementContext,
    val outputCondition: MeasuredOutputCondition,
    val schemaId: String,
    val observations: Map<BlendShape, ResponseObservation>,
    val scope: EvidenceScope,
    val protocolId: String,
    val publicReviewPacketSha256: String,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(schemaId.isNotBlank()) { "schemaId must not be blank" }
        require(protocolId.isNotBlank()) { "protocolId must not be blank" }
        require(observations.keys == BlendShape.entries.toSet()) { "observations must cover all blend shapes" }
        require(
            observations.values.filterIsInstance<ResponseObservation.Measured>()
                .all { it.evidenceProfileId == id },
        ) { "Measured observations must reference their enclosing profile ID" }
    }
}

internal fun String.isSha256Hex(): Boolean =
    length == SHA256_HEX_LENGTH && all { it in SHA256_HEX_CHARS }

private const val SHA256_HEX_LENGTH: Int = 64
private const val SHA256_HEX_CHARS: String = "0123456789abcdef"
