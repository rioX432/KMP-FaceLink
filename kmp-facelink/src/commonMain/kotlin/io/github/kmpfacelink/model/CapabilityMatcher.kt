package io.github.kmpfacelink.model

/** Result of matching one evidence profile to one emitted face observation. */
public sealed interface TechnicalContextMatch {
    /** Every required technical and output condition matched. */
    public data class Exact(val profile: ObservedResponseProfile) : TechnicalContextMatch

    /** Known fields differ. */
    public data class Incompatible(val differingFields: Set<String>) : TechnicalContextMatch

    /** One or more required fields were not observed. */
    public data class Unknown(val unresolvedFields: Set<String>) : TechnicalContextMatch

    /** The estimator/schema may contain the shape, but this runtime filtered it. */
    public data class RuntimeFiltered(val shape: BlendShape) : TechnicalContextMatch

    /** The frame predates tracking context or otherwise lacks an epoch snapshot. */
    public data object NoFrameContext : TechnicalContextMatch
}

/** Evidence returned after schema and technical matching, before product acceptance. */
public sealed interface CapabilityEvidence {
    /** Evidence matched, with its complete profile and scope retained. */
    public data class TechnicallyMatched(
        val profile: ObservedResponseProfile,
        val observation: ResponseObservation,
    ) : CapabilityEvidence

    /** The requested shape is absent from the selected schema layer. */
    public data class SchemaAbsent(val schema: BlendShapeSchema) : CapabilityEvidence

    /** The requested shape was removed by runtime output filtering. */
    public data class RuntimeFiltered(val shape: BlendShape) : CapabilityEvidence

    /** No evidence can safely be applied. */
    public data class NoApplicableEvidence(val reason: String) : CapabilityEvidence
}

/** Product-owned decision about whether an evidence scope is sufficient. */
public fun interface EvidenceAcceptancePolicy {
    /** Accepts or rejects technically matched evidence without selecting a fallback. */
    public fun accept(
        shape: BlendShape,
        evidence: CapabilityEvidence.TechnicallyMatched,
    ): EvidenceAcceptance
}

/** Explicit result of product evidence-scope acceptance. */
public sealed interface EvidenceAcceptance {
    /** The product deliberately accepts the named profile and scope. */
    public data class Accepted(
        val profileId: String,
        val acceptedScope: EvidenceScope,
    ) : EvidenceAcceptance {
        init {
            require(profileId.isNotBlank()) { "profileId must not be blank" }
        }
    }

    /** The product rejects this evidence for the current use. */
    public data class Rejected(val reason: String) : EvidenceAcceptance
}

/** A product-selected output route; evidence matching never returns this type. */
public sealed interface BlendShapeRoute {
    /** Use the estimator output. */
    public data object Estimator : BlendShapeRoute

    /** Use an application-defined alternative that has its own validation. */
    public data class Alternative(val id: String) : BlendShapeRoute {
        init {
            require(id.isNotBlank()) { "id must not be blank" }
        }
    }

    /** Require an explicit user-controlled input. */
    public data object ExplicitInput : BlendShapeRoute

    /** Disable the affected expression. */
    public data object Disabled : BlendShapeRoute
}

/** Application-owned policy applied only after evidence acceptance. */
public fun interface BlendShapeFallbackPolicy {
    /** Selects an output route for accepted or rejected evidence. */
    public fun decide(
        shape: BlendShape,
        evidence: CapabilityEvidence,
        acceptance: EvidenceAcceptance,
    ): BlendShapeRoute
}

/** Fail-closed matcher for a profile, frame epoch, face provenance, and shape. */
public object CapabilityMatcher {

    /** Matches one profile to one face observation. */
    @Suppress("ReturnCount")
    public fun match(
        profile: ObservedResponseProfile,
        frame: FaceTrackingData,
        face: TrackedFace,
        shape: BlendShape,
    ): TechnicalContextMatch {
        val epoch = frame.contextEpoch ?: return TechnicalContextMatch.NoFrameContext
        val provenance = face.provenance
            ?: return TechnicalContextMatch.Unknown(setOf("face.provenance"))
        if (frame.faces.getOrNull(face.faceIndex) != face) {
            return TechnicalContextMatch.Incompatible(setOf("frame.faces"))
        }
        if (provenance.faceIndex != face.faceIndex) {
            return TechnicalContextMatch.Incompatible(setOf("faceIndex"))
        }
        if (provenance.contextEpochId != epoch.epochId) {
            return TechnicalContextMatch.Incompatible(setOf("contextEpochId"))
        }
        val differences = linkedSetOf<String>()
        val unknown = linkedSetOf<String>()
        compareMeasurementContext(profile.context, epoch, differences, unknown)
        compareOutputCondition(profile.outputCondition, epoch, provenance, differences)
        if (differences.isNotEmpty()) return TechnicalContextMatch.Incompatible(differences)
        if (unknown.isNotEmpty()) return TechnicalContextMatch.Unknown(unknown)
        if (shape !in provenance.emittedBlendShapes) return TechnicalContextMatch.RuntimeFiltered(shape)
        return TechnicalContextMatch.Exact(profile)
    }

    /** Converts a technical match and schema fact into evidence without selecting a route. */
    public fun evidence(
        profile: ObservedResponseProfile,
        schema: BlendShapeSchema,
        frame: FaceTrackingData,
        face: TrackedFace,
        shape: BlendShape,
    ): CapabilityEvidence {
        val schemaDifferences = schemaCompatibilityDifferences(profile, schema)
        if (schemaDifferences.isNotEmpty()) {
            return CapabilityEvidence.NoApplicableEvidence(
                "Incompatible schema fields: ${schemaDifferences.sorted().joinToString()}",
            )
        }
        if (schema.presence.getValue(shape) == SchemaPresence.ABSENT) {
            return CapabilityEvidence.SchemaAbsent(schema)
        }
        return when (val match = match(profile, frame, face, shape)) {
            is TechnicalContextMatch.Exact -> CapabilityEvidence.TechnicallyMatched(
                profile = match.profile,
                observation = match.profile.observations.getValue(shape),
            )
            is TechnicalContextMatch.RuntimeFiltered -> CapabilityEvidence.RuntimeFiltered(match.shape)
            is TechnicalContextMatch.Incompatible -> CapabilityEvidence.NoApplicableEvidence(
                "Incompatible fields: ${match.differingFields.sorted().joinToString()}",
            )
            is TechnicalContextMatch.Unknown -> CapabilityEvidence.NoApplicableEvidence(
                "Unknown fields: ${match.unresolvedFields.sorted().joinToString()}",
            )
            TechnicalContextMatch.NoFrameContext -> CapabilityEvidence.NoApplicableEvidence("Frame context is absent")
        }
    }

    private fun schemaCompatibilityDifferences(
        profile: ObservedResponseProfile,
        schema: BlendShapeSchema,
    ): Set<String> = buildSet {
        if (schema.id != profile.schemaId) add("schema.id")
        if (schema.layer != profile.outputCondition.schemaLayer) add("schema.layer")
        if (schema.estimator.family != profile.context.estimator.family) add("schema.estimator.family")
        if (schema.estimator.libraryVersion != profile.context.estimator.libraryVersion) {
            add("schema.estimator.libraryVersion")
        }
        if (schema.estimator.modelSha256 != profile.context.estimator.modelSha256) {
            add("schema.estimator.modelSha256")
        }
    }

    private fun compareMeasurementContext(
        measured: MeasurementContext,
        runtime: TrackingContextEpoch,
        differences: MutableSet<String>,
        unknown: MutableSet<String>,
    ) {
        if (measured.estimator.family != runtime.estimator.family) differences += "estimator.family"
        compareDimension(
            "estimator.libraryVersion",
            measured.estimator.libraryVersion,
            runtime.estimator.libraryVersion,
            differences,
            unknown,
        )
        compareDimension(
            "estimator.modelSha256",
            measured.estimator.modelSha256,
            runtime.estimator.modelSha256,
            differences,
            unknown,
        )
        if (measured.platform.platform != runtime.platform.platform) differences += "platform"
        compareDimension(
            "platform.osVersion",
            measured.platform.osVersion,
            runtime.platform.osVersion,
            differences,
            unknown,
        )
        compareDimension(
            "platform.deviceModel",
            measured.platform.deviceModel,
            runtime.platform.deviceModel,
            differences,
            unknown,
        )
        compareDimension(
            "platform.estimatorConfigurationId",
            measured.platform.estimatorConfigurationId,
            runtime.platform.estimatorConfigurationId,
            differences,
            unknown,
        )
        compareDimension("runningMode", measured.runningMode, runtime.runningMode, differences, unknown)
        compareDimension("resolvedDelegate", measured.resolvedDelegate, runtime.resolvedDelegate, differences, unknown)
        compareDimension("inputWidth", measured.inputWidth, runtime.inputWidth, differences, unknown)
        compareDimension("inputHeight", measured.inputHeight, runtime.inputHeight, differences, unknown)
        compareDimension(
            "mirroredBeforeInference",
            measured.mirroredBeforeInference,
            runtime.mirroredBeforeInference,
            differences,
            unknown,
        )
        if (measured.configuredTransforms != runtime.configuredTransforms) differences += "configuredTransforms"
        if (measured.activeBlendShapes != runtime.activeBlendShapes) differences += "activeBlendShapes"
        if (unknown.isEmpty() && measured.configurationFingerprint != runtime.configurationFingerprint) {
            differences += "configurationFingerprint"
        }
    }

    private fun compareOutputCondition(
        measured: MeasuredOutputCondition,
        epoch: TrackingContextEpoch,
        actual: FaceObservationProvenance,
        differences: MutableSet<String>,
    ) {
        if (measured.pipelineKind != actual.pipelineKind) differences += "output.pipelineKind"
        if (measured.appliedTransforms != actual.appliedTransforms) differences += "output.appliedTransforms"
        if (measured.calibrationPhase != actual.calibrationPhase) differences += "output.calibrationPhase"
        if (measured.materialization != actual.materialization) differences += "output.materialization"
        if (!actual.emittedBlendShapes.containsAll(
                measured.requiredEmittedShapes,
            )
        ) {
            differences += "output.requiredEmittedShapes"
        }
        when (val binding = measured.sessionBinding) {
            SessionBinding.Portable -> {
                if (actual.calibrationPhase != CalibrationPhase.DISABLED) differences += "output.portableCalibration"
                if (epoch.configuredTransforms.any {
                        it.stateDependence == TransformStateDependence.SESSION_LEARNED_STATE
                    }
                ) {
                    differences += "output.portableLearnedState"
                }
            }
            is SessionBinding.SameSession -> {
                if (binding.id != epoch.sessionInstanceId) differences += "sessionInstanceId"
                val revisions = measured.revisionRequirement as? RevisionRequirement.Exact
                if (revisions == null) {
                    differences += "output.revisionRequirement"
                } else {
                    if (revisions.calibrationStateRevision != actual.calibrationStateRevision) {
                        differences += "output.calibrationStateRevision"
                    }
                    if (revisions.processingStateRevision != actual.processingStateRevision) {
                        differences += "output.processingStateRevision"
                    }
                }
            }
        }
    }

    private fun <T> compareDimension(
        name: String,
        measured: DimensionValue<T>,
        runtime: DimensionValue<T>,
        differences: MutableSet<String>,
        unknown: MutableSet<String>,
    ) {
        when {
            measured is DimensionValue.NotApplicable && runtime is DimensionValue.NotApplicable -> Unit
            measured is DimensionValue.NotApplicable ||
                runtime is DimensionValue.NotApplicable -> differences += name
            measured is DimensionValue.Unknown || runtime is DimensionValue.Unknown -> unknown += name
            measured is DimensionValue.Known &&
                runtime is DimensionValue.Known &&
                measured.value == runtime.value -> Unit
            else -> differences += name
        }
    }
}

/** Validation that prevents session-learned evidence from entering a built-in registry. */
public object DistributableEvidenceValidator {
    /** Returns every reason a profile is not safe to distribute. */
    public fun rejectionReasons(profile: ObservedResponseProfile): Set<String> {
        val reasons = linkedSetOf<String>()
        if (profile.outputCondition.sessionBinding is SessionBinding.SameSession) reasons += "same-session binding"
        if (profile.outputCondition.revisionRequirement is RevisionRequirement.Exact) reasons += "session revisions"
        if (profile.outputCondition.calibrationPhase != CalibrationPhase.DISABLED) reasons += "calibration state"
        if (profile.context.configuredTransforms.any {
                it.stateDependence == TransformStateDependence.SESSION_LEARNED_STATE
            }
        ) {
            reasons += "session-learned transform"
        }
        if (!profile.context.configurationFingerprint.isSha256Hex()) {
            reasons += "configuration fingerprint SHA-256"
        }
        if (!profile.publicReviewPacketSha256.isSha256Hex()) reasons += "public review packet SHA-256"
        return reasons
    }

    /** True only for profiles that can enter a distributable registry. */
    public fun isDistributable(profile: ObservedResponseProfile): Boolean =
        rejectionReasons(profile).isEmpty()
}
