package io.github.kmpfacelink.model

import io.github.kmpfacelink.ExperimentalFaceLinkApi

/** Pure, stateless lifecycle reducer whose only time dependency is [clock]. */
@ExperimentalFaceLinkApi
public class ParticipantCalibrationReducer(
    private val clock: ParticipantCalibrationClock,
) {
    /** Reduces the supplied immutable identities to one fail-closed lifecycle. */
    public fun reduce(input: ParticipantCalibrationReductionInput): ParticipantCalibrationLifecycle {
        val profile = input.profile ?: return reduceWithoutProfile(input)
        val artifact = profile.artifact
        val receipt = profile.evidenceReceipt
        val acceptance = input.evidenceAcceptance
        val differences = identityDifferences(profile, input.runtime, acceptance)
        if (differences.isNotEmpty()) {
            return ParticipantCalibrationLifecycle.Mismatched(
                profileId = artifact.profileId,
                profileRevision = artifact.profileRevision,
                differingFields = differences,
            )
        }
        if (acceptance is EvidenceAcceptance.Rejected) {
            return ParticipantCalibrationLifecycle.Rejected(
                profileId = artifact.profileId,
                profileRevision = artifact.profileRevision,
                reason = acceptance.reason,
                evidenceReceipt = receipt,
            )
        }
        if (clock.nowEpochMs() >= artifact.expiresAtEpochMs) {
            return ParticipantCalibrationLifecycle.Expired(
                profileId = artifact.profileId,
                profileRevision = artifact.profileRevision,
                expiredAtEpochMs = artifact.expiresAtEpochMs,
            )
        }
        return ParticipantCalibrationLifecycle.Ready(profile)
    }

    private fun reduceWithoutProfile(
        input: ParticipantCalibrationReductionInput,
    ): ParticipantCalibrationLifecycle {
        val collection = input.collection
            ?: return ParticipantCalibrationLifecycle.Unavailable("participant profile is absent")
        if (collection.enrollmentBinding != input.runtime.enrollmentBinding) {
            return ParticipantCalibrationLifecycle.Mismatched(
                profileId = null,
                profileRevision = null,
                differingFields = setOf("enrollmentBinding"),
            )
        }
        return ParticipantCalibrationLifecycle.Collecting(
            enrollmentBinding = collection.enrollmentBinding,
            collectedUnits = collection.collectedUnits,
            requiredUnits = collection.requiredUnits,
        )
    }

    private fun identityDifferences(
        profile: ParticipantCalibrationProfile,
        runtime: ParticipantCalibrationRuntimeIdentity,
        acceptance: EvidenceAcceptance,
    ): Set<String> = buildSet {
        val artifact = profile.artifact
        val receipt = profile.evidenceReceipt
        if (runtime.profileId != artifact.profileId) add("profileId")
        if (runtime.profileRevision != artifact.profileRevision) add("profileRevision")
        if (runtime.enrollmentBinding != artifact.enrollmentBinding) add("enrollmentBinding")
        if (runtime.candidateId != artifact.candidateId) add("candidateId")
        if (runtime.targetSignal != artifact.targetSignal) add("targetSignal")
        compareArtifactHashes(artifact, runtime, this)
        compareReceipt(receipt, runtime, acceptance, this)
        compareContext(artifact.expectedContext, runtime.contextEpoch, this)
    }

    private fun compareArtifactHashes(
        artifact: ParticipantCalibrationArtifact,
        runtime: ParticipantCalibrationRuntimeIdentity,
        differences: MutableSet<String>,
    ) {
        if (runtime.candidateImplementationSha256 != artifact.candidateImplementationSha256) {
            differences += "candidateImplementationSha256"
        }
        if (runtime.preprocessingSha256 != artifact.preprocessingSha256) differences += "preprocessingSha256"
        if (runtime.candidateModelSha256 != artifact.candidateModelSha256) differences += "candidateModelSha256"
        if (runtime.thresholdSha256 != artifact.thresholdSha256) differences += "thresholdSha256"
        if (runtime.candidateArtifactSha256 != artifact.artifactSha256) differences += "candidateArtifactSha256"
    }

    private fun compareReceipt(
        receipt: ParticipantCalibrationEvidenceReceipt,
        runtime: ParticipantCalibrationRuntimeIdentity,
        acceptance: EvidenceAcceptance,
        differences: MutableSet<String>,
    ) {
        if (runtime.evidenceReceiptSha256 != receipt.receiptSha256) differences += "evidenceReceiptSha256"
        if (runtime.sdkSourceSha256 != receipt.sdkSourceSha256) differences += "sdkSourceSha256"
        if (runtime.binarySha256 != receipt.reviewedBinarySha256) differences += "binarySha256"
        if (acceptance is EvidenceAcceptance.Accepted) {
            if (acceptance.profileId != receipt.profileId) differences += "acceptance.profileId"
            if (acceptance.acceptedScope != receipt.evidenceScope) differences += "acceptance.acceptedScope"
        }
    }

    private fun compareContext(
        expected: MeasurementContext,
        runtime: TrackingContextEpoch,
        differences: MutableSet<String>,
    ) {
        if (expected.estimator != runtime.estimator) differences += "context.estimator"
        if (expected.platform != runtime.platform) differences += "context.platform"
        if (expected.runningMode != runtime.runningMode) differences += "context.runningMode"
        if (expected.resolvedDelegate != runtime.resolvedDelegate) differences += "context.resolvedDelegate"
        if (expected.inputWidth != runtime.inputWidth) differences += "context.inputWidth"
        if (expected.inputHeight != runtime.inputHeight) differences += "context.inputHeight"
        if (expected.mirroredBeforeInference != runtime.mirroredBeforeInference) {
            differences += "context.mirroredBeforeInference"
        }
        if (expected.configuredTransforms != runtime.configuredTransforms) differences += "context.configuredTransforms"
        if (expected.activeBlendShapes != runtime.activeBlendShapes) differences += "context.activeBlendShapes"
        if (expected.configurationFingerprint != runtime.configurationFingerprint) {
            differences += "context.configurationFingerprint"
        }
    }
}

/** Factory for disabled-by-default or explicitly enabled conditional sidecar decisions. */
@ExperimentalFaceLinkApi
public object ParticipantCalibrationDecisionFactory {
    /**
     * Creates the default-off engine. The candidate value is discarded and estimator output is retained.
     */
    public fun disabled(
        signal: DerivedFaceSignal = DerivedFaceSignal.CheekPuffCandidate,
        fallbackRoute: BlendShapeRoute = BlendShapeRoute.Estimator,
    ): ParticipantCalibrationDecisionEngine {
        require(fallbackRoute !is BlendShapeRoute.Alternative) {
            "Disabled engine cannot use an Alternative route"
        }
        return ParticipantCalibrationDecisionEngine { input ->
            val lifecycle = ParticipantCalibrationLifecycle.Unavailable("participant calibration is disabled")
            val canonicalValue = canonicalValue(input, signal)
            val route = if (fallbackRoute is BlendShapeRoute.Estimator && canonicalValue == null) {
                BlendShapeRoute.Disabled
            } else {
                fallbackRoute
            }
            decision(input, signal, canonicalValue, lifecycle, candidateValue = null, route = route)
        }
    }

    /** Creates an explicitly enabled engine with fail-closed fallback routing. */
    public fun enabled(
        signal: DerivedFaceSignal = DerivedFaceSignal.CheekPuffCandidate,
        clock: ParticipantCalibrationClock,
        fallbackPolicy: ParticipantCalibrationFallbackPolicy = ParticipantCalibrationFallbackPolicy { _, _ ->
            BlendShapeRoute.Estimator
        },
    ): ParticipantCalibrationDecisionEngine {
        val reducer = ParticipantCalibrationReducer(clock)
        return ParticipantCalibrationDecisionEngine { input ->
            val reduced = reducer.reduce(input.reductionInput)
            val canonicalValue = canonicalValue(input, signal)
            val differences = decisionDifferences(input, signal, canonicalValue)
            val lifecycle = if (differences.isEmpty()) {
                reduced
            } else {
                val profile = input.reductionInput.profile?.artifact
                ParticipantCalibrationLifecycle.Mismatched(
                    profileId = profile?.profileId,
                    profileRevision = profile?.profileRevision,
                    differingFields = differences,
                )
            }
            val route = enabledRoute(input, signal, canonicalValue, lifecycle, fallbackPolicy)
            val candidateValue = if (route is BlendShapeRoute.Alternative) input.candidate?.value else null
            decision(input, signal, canonicalValue, lifecycle, candidateValue, route)
        }
    }

    private fun enabledRoute(
        input: ParticipantCalibrationDecisionInput,
        signal: DerivedFaceSignal,
        canonicalValue: Float?,
        lifecycle: ParticipantCalibrationLifecycle,
        fallbackPolicy: ParticipantCalibrationFallbackPolicy,
    ): BlendShapeRoute {
        if (lifecycle is ParticipantCalibrationLifecycle.Ready &&
            canonicalValue != null &&
            input.candidate != null
        ) {
            return BlendShapeRoute.Alternative(lifecycle.profile.artifact.candidateId)
        }
        val fallback = fallbackPolicy.decide(signal, lifecycle)
        return when {
            fallback is BlendShapeRoute.Alternative -> BlendShapeRoute.Disabled
            fallback is BlendShapeRoute.Estimator && canonicalValue == null -> BlendShapeRoute.Disabled
            else -> fallback
        }
    }

    private fun decision(
        input: ParticipantCalibrationDecisionInput,
        signal: DerivedFaceSignal,
        canonicalValue: Float?,
        lifecycle: ParticipantCalibrationLifecycle,
        candidateValue: Float?,
        route: BlendShapeRoute,
    ): DerivedFaceSignalDecision {
        val profile = input.reductionInput.profile
        val epoch = input.reductionInput.runtime.contextEpoch
        return DerivedFaceSignalDecision.create(
            signal = signal,
            canonicalValue = canonicalValue,
            candidateValue = candidateValue,
            lifecycle = lifecycle,
            provenance = ParticipantCalibrationDecisionProvenance(
                targetSignal = signal,
                canonicalBlendShape = signal.canonicalBlendShape,
                runtimeConfigurationFingerprint = epoch.configurationFingerprint,
                runtimeEpochFingerprint = epoch.epochFingerprint,
                profileId = profile?.artifact?.profileId,
                profileRevision = profile?.artifact?.profileRevision,
                candidateId = profile?.artifact?.candidateId,
                evidenceReceiptSha256 = profile?.evidenceReceipt?.receiptSha256,
            ),
            route = route,
        )
    }

    private fun canonicalValue(
        input: ParticipantCalibrationDecisionInput,
        signal: DerivedFaceSignal,
    ): Float? = input.canonicalBlendShapes[signal.canonicalBlendShape]
        ?.takeIf { it.isFinite() && it in 0f..1f }

    private fun decisionDifferences(
        input: ParticipantCalibrationDecisionInput,
        signal: DerivedFaceSignal,
        canonicalValue: Float?,
    ): Set<String> = buildSet {
        val artifact = input.reductionInput.profile?.artifact
        val runtime = input.reductionInput.runtime
        val candidate = input.candidate
        if (runtime.targetSignal != signal) add("runtime.targetSignal")
        if (artifact != null && artifact.targetSignal != signal) add("artifact.targetSignal")
        if (canonicalValue == null) add("canonicalBlendShapes.${signal.canonicalBlendShape.name}")
        if (candidate != null) {
            if (candidate.signal != signal) add("candidate.signal")
            if (artifact == null || candidate.candidateId != artifact.candidateId) add("candidate.candidateId")
            if (artifact == null || candidate.artifactSha256 != artifact.artifactSha256) {
                add("candidate.artifactSha256")
            }
        }
    }
}
