package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.BlendShapeRoute
import io.github.kmpfacelink.model.DerivedFaceSignal
import io.github.kmpfacelink.model.DerivedFaceSignalSample
import io.github.kmpfacelink.model.DerivedFaceSignalValueSemantics
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.EvidenceAcceptance
import io.github.kmpfacelink.model.EvidenceScope
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.MeasurementContext
import io.github.kmpfacelink.model.ParticipantCalibrationArtifact
import io.github.kmpfacelink.model.ParticipantCalibrationClock
import io.github.kmpfacelink.model.ParticipantCalibrationCollection
import io.github.kmpfacelink.model.ParticipantCalibrationDecisionFactory
import io.github.kmpfacelink.model.ParticipantCalibrationDecisionInput
import io.github.kmpfacelink.model.ParticipantCalibrationEvidenceReceipt
import io.github.kmpfacelink.model.ParticipantCalibrationFallbackPolicy
import io.github.kmpfacelink.model.ParticipantCalibrationLifecycle
import io.github.kmpfacelink.model.ParticipantCalibrationProfile
import io.github.kmpfacelink.model.ParticipantCalibrationReducer
import io.github.kmpfacelink.model.ParticipantCalibrationReductionInput
import io.github.kmpfacelink.model.ParticipantCalibrationRuntimeIdentity
import io.github.kmpfacelink.model.ParticipantEnrollmentBinding
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.model.TransformKind
import io.github.kmpfacelink.model.TransformSpec
import io.github.kmpfacelink.model.ValidationClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalFaceLinkApi::class)
class ParticipantCalibrationContractTest {

    @Test
    fun reducerProducesEveryLifecycleWithoutHiddenState() {
        val unavailable = reducer(NOW).reduce(reduction(profile = null))
        assertIs<ParticipantCalibrationLifecycle.Unavailable>(unavailable)

        val collection = ParticipantCalibrationCollection(BINDING, collectedUnits = 3, requiredUnits = 12)
        val collecting = reducer(NOW).reduce(reduction(profile = null, collection = collection))
        assertIs<ParticipantCalibrationLifecycle.Collecting>(collecting)
        assertEquals(3, collecting.collectedUnits)

        assertIs<ParticipantCalibrationLifecycle.Ready>(reducer(NOW).reduce(reduction()))

        val expired = reducer(EXPIRES_AT).reduce(reduction())
        assertIs<ParticipantCalibrationLifecycle.Expired>(expired)

        val mismatched = reducer(NOW).reduce(
            reduction(
                runtime = runtime().copy(
                    enrollmentBinding = ParticipantEnrollmentBinding("other-local-binding"),
                ),
            ),
        )
        assertIs<ParticipantCalibrationLifecycle.Mismatched>(mismatched)
        assertEquals(setOf("enrollmentBinding"), mismatched.differingFields)

        val rejected = reducer(NOW).reduce(
            reduction(acceptance = EvidenceAcceptance.Rejected("product acceptance rejected this scope")),
        )
        assertIs<ParticipantCalibrationLifecycle.Rejected>(rejected)
        assertEquals(RECEIPT_HASH, rejected.evidenceReceipt.receiptSha256)
    }

    @Test
    fun expiryUsesExactExclusiveBoundary() {
        assertIs<ParticipantCalibrationLifecycle.Ready>(reducer(EXPIRES_AT - 1).reduce(reduction()))
        assertIs<ParticipantCalibrationLifecycle.Expired>(reducer(EXPIRES_AT).reduce(reduction()))
        assertIs<ParticipantCalibrationLifecycle.Expired>(reducer(EXPIRES_AT + 1).reduce(reduction()))
    }

    @Test
    fun everyArtifactRuntimeAndReceiptIdentityMutationFailsClosed() {
        val base = runtime()
        val mutations = listOf(
            "profileId" to base.copy(profileId = "other-profile"),
            "profileRevision" to base.copy(profileRevision = base.profileRevision + 1u),
            "enrollmentBinding" to base.copy(enrollmentBinding = ParticipantEnrollmentBinding("other-binding")),
            "candidateId" to base.copy(candidateId = "other-candidate"),
            "candidateImplementationSha256" to base.copy(candidateImplementationSha256 = hash('b')),
            "preprocessingSha256" to base.copy(preprocessingSha256 = hash('c')),
            "candidateModelSha256" to base.copy(candidateModelSha256 = hash('d')),
            "thresholdSha256" to base.copy(thresholdSha256 = hash('e')),
            "candidateArtifactSha256" to base.copy(candidateArtifactSha256 = hash('f')),
            "evidenceReceiptSha256" to base.copy(evidenceReceiptSha256 = hash('a')),
            "sdkSourceSha256" to base.copy(sdkSourceSha256 = hash('b')),
            "binarySha256" to base.copy(binarySha256 = hash('c')),
        )

        mutations.forEach { (expectedField, mutatedRuntime) ->
            assertMismatch(expectedField, reduction(runtime = mutatedRuntime))
        }

        assertMismatch(
            "acceptance.profileId",
            reduction(acceptance = EvidenceAcceptance.Accepted("other-profile", scope())),
        )
        assertMismatch(
            "acceptance.acceptedScope",
            reduction(
                acceptance = EvidenceAcceptance.Accepted(
                    PROFILE_ID,
                    scope().copy(independentSessionCount = 2),
                ),
            ),
        )
    }

    @Test
    fun everyTechnicalContextMutationFailsClosed() {
        val base = epoch()
        val mutations = listOf(
            "context.estimator" to base.copy(
                estimator = base.estimator.copy(family = FaceTrackingBackend.ARKIT),
            ),
            "context.estimator" to base.copy(
                estimator = base.estimator.copy(libraryVersion = DimensionValue.Known("other")),
            ),
            "context.estimator" to base.copy(
                estimator = base.estimator.copy(modelSha256 = DimensionValue.Known(hash('b'))),
            ),
            "context.platform" to base.copy(platform = base.platform.copy(platform = "ios")),
            "context.platform" to base.copy(
                platform = base.platform.copy(osVersion = DimensionValue.Known("other")),
            ),
            "context.platform" to base.copy(
                platform = base.platform.copy(deviceModel = DimensionValue.Known("other")),
            ),
            "context.platform" to base.copy(
                platform = base.platform.copy(estimatorConfigurationId = DimensionValue.Known("other")),
            ),
            "context.runningMode" to base.copy(runningMode = DimensionValue.Known(InferenceRunningMode.VIDEO)),
            "context.resolvedDelegate" to base.copy(resolvedDelegate = DimensionValue.Known("GPU")),
            "context.inputWidth" to base.copy(inputWidth = DimensionValue.Known(321)),
            "context.inputHeight" to base.copy(inputHeight = DimensionValue.Known(241)),
            "context.mirroredBeforeInference" to base.copy(
                mirroredBeforeInference = DimensionValue.Known(true),
            ),
            "context.configuredTransforms" to base.copy(
                configuredTransforms = listOf(
                    TransformSpec(TransformKind.SMOOTHER, "other-smoother", "other-config"),
                ),
            ),
            "context.activeBlendShapes" to base.copy(activeBlendShapes = setOf(BlendShape.JAW_OPEN)),
        )

        mutations.forEach { (expectedField, mutatedEpoch) ->
            assertMismatch(expectedField, reduction(runtime = runtime().copy(contextEpoch = mutatedEpoch)))
        }
    }

    @Test
    fun routeTableRequiresReadyAcceptedEvidenceAndCandidateValue() {
        val enabled = ParticipantCalibrationDecisionFactory.enabled(
            clock = ParticipantCalibrationClock { NOW },
            fallbackPolicy = ParticipantCalibrationFallbackPolicy { _, _ -> BlendShapeRoute.ExplicitInput },
        )
        val ready = enabled.decide(decisionInput())
        assertIs<BlendShapeRoute.Alternative>(ready.route)
        assertEquals(CANDIDATE_ID, ready.route.id)
        assertEquals(DerivedFaceSignal.CheekPuffCandidate, ready.signal)
        assertEquals(BlendShape.CHEEK_PUFF, ready.canonicalBlendShape)
        assertEquals(DerivedFaceSignalValueSemantics.UNIT_INTERVAL_ACTIVATION, ready.valueSemantics)
        assertEquals(ESTIMATOR_VALUE, ready.canonicalValue)
        assertEquals(CANDIDATE_VALUE, ready.candidateValue)

        val notReadyInputs = listOf(
            decisionInput(profile = null),
            decisionInput(profile = null, collection = ParticipantCalibrationCollection(BINDING, 1, 12)),
            decisionInput(runtime = runtime().copy(profileRevision = 2u)),
            decisionInput(acceptance = EvidenceAcceptance.Rejected("not accepted")),
            decisionInput(candidate = null),
        )
        notReadyInputs.forEach { input ->
            val decision = enabled.decide(input)
            assertEquals(BlendShapeRoute.ExplicitInput, decision.route)
            assertNull(decision.candidateValue)
        }

        val expiredEngine = ParticipantCalibrationDecisionFactory.enabled(
            clock = ParticipantCalibrationClock { EXPIRES_AT },
            fallbackPolicy = ParticipantCalibrationFallbackPolicy { _, _ -> BlendShapeRoute.ExplicitInput },
        )
        assertEquals(BlendShapeRoute.ExplicitInput, expiredEngine.decide(decisionInput()).route)

        val maliciousFallback = ParticipantCalibrationDecisionFactory.enabled(
            clock = ParticipantCalibrationClock { NOW },
            fallbackPolicy = ParticipantCalibrationFallbackPolicy { _, _ -> BlendShapeRoute.Alternative("unsafe") },
        )
        assertEquals(BlendShapeRoute.Disabled, maliciousFallback.decide(decisionInput(profile = null)).route)
    }

    @Test
    fun candidateAndCanonicalIdentityMutationsFailClosed() {
        val enabled = ParticipantCalibrationDecisionFactory.enabled(
            clock = ParticipantCalibrationClock { NOW },
        )
        val candidateIdMismatch = enabled.decide(
            decisionInput(candidate = sample().copy(candidateId = "other-candidate")),
        )
        assertIs<ParticipantCalibrationLifecycle.Mismatched>(candidateIdMismatch.lifecycle)
        assertEquals(BlendShapeRoute.Estimator, candidateIdMismatch.route)
        assertNull(candidateIdMismatch.candidateValue)

        val artifactMismatch = enabled.decide(
            decisionInput(candidate = sample().copy(artifactSha256 = hash('f'))),
        )
        assertIs<ParticipantCalibrationLifecycle.Mismatched>(artifactMismatch.lifecycle)
        assertEquals(BlendShapeRoute.Estimator, artifactMismatch.route)
        assertNull(artifactMismatch.candidateValue)

        val missingCanonical = enabled.decide(decisionInput(canonicalValue = null))
        assertIs<ParticipantCalibrationLifecycle.Mismatched>(missingCanonical.lifecycle)
        assertEquals(BlendShapeRoute.Disabled, missingCanonical.route)
        assertNull(missingCanonical.canonicalValue)
        assertNull(missingCanonical.candidateValue)
    }

    @Test
    fun candidateSampleRejectsNonFiniteAndOutOfRangeValues() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.01f, 1.01f).forEach { value ->
            assertFailsWith<IllegalArgumentException> { sample(value = value) }
        }
    }

    @Test
    fun canonicalVocabularyRemainsExactly52Values() {
        assertEquals(52, BlendShape.entries.size)
    }

    @Test
    fun defaultOffDiscardsCandidateAndDoesNotMutateCanonicalMap() {
        val canonical = mutableMapOf(BlendShape.CHEEK_PUFF to ESTIMATOR_VALUE)
        val before = canonical.toMap()
        val decision = ParticipantCalibrationDecisionFactory.disabled().decide(
            decisionInput(canonicalBlendShapes = canonical),
        )

        assertEquals(BlendShapeRoute.Estimator, decision.route)
        assertIs<ParticipantCalibrationLifecycle.Unavailable>(decision.lifecycle)
        assertEquals(ESTIMATOR_VALUE, decision.canonicalValue)
        assertNull(decision.candidateValue)
        assertEquals(before, canonical)
    }

    @Test
    fun deterministicDecisionReplayPreservesCanonicalInputAndFailsClosed() {
        val canonical = mutableMapOf(
            BlendShape.CHEEK_PUFF to ESTIMATOR_VALUE,
            BlendShape.JAW_OPEN to 0.42f,
        )
        val before = canonical.toMap()
        val enabled = ParticipantCalibrationDecisionFactory.enabled(
            clock = ParticipantCalibrationClock { NOW },
            fallbackPolicy = ParticipantCalibrationFallbackPolicy { _, _ -> BlendShapeRoute.Estimator },
        )
        val replay = listOf(
            decisionInput(canonicalBlendShapes = canonical),
            decisionInput(
                runtime = runtime().copy(profileRevision = 2u),
                canonicalBlendShapes = canonical,
            ),
            decisionInput(candidate = null, canonicalBlendShapes = canonical),
            decisionInput(
                candidate = sample().copy(artifactSha256 = hash('f')),
                canonicalBlendShapes = canonical,
            ),
            decisionInput(canonicalValue = null),
        )

        val decisions = replay.map(enabled::decide)

        assertIs<BlendShapeRoute.Alternative>(decisions.first().route)
        assertEquals(CANDIDATE_VALUE, decisions.first().candidateValue)
        decisions.drop(1).forEach { assertNull(it.candidateValue) }
        assertEquals(
            listOf(
                BlendShapeRoute.Alternative(CANDIDATE_ID),
                BlendShapeRoute.Estimator,
                BlendShapeRoute.Estimator,
                BlendShapeRoute.Estimator,
                BlendShapeRoute.Disabled,
            ),
            decisions.map { it.route },
        )
        assertEquals(before, canonical)
    }

    @Test
    fun genericEnhancerConfigurationCannotEnableParticipantSidecar() {
        val genericConfig = FaceTrackerConfig(enhancerConfig = BlendShapeEnhancerConfig.Default())
        assertIs<BlendShapeEnhancerConfig.Default>(genericConfig.enhancerConfig)

        val decision = ParticipantCalibrationDecisionFactory.disabled().decide(decisionInput())
        assertIs<ParticipantCalibrationLifecycle.Unavailable>(decision.lifecycle)
        assertEquals(BlendShapeRoute.Estimator, decision.route)
        assertNull(decision.candidateValue)
    }

    private fun assertMismatch(expectedField: String, input: ParticipantCalibrationReductionInput) {
        val result = reducer(NOW).reduce(input)
        val mismatch = assertIs<ParticipantCalibrationLifecycle.Mismatched>(result, expectedField)
        assertTrue(expectedField in mismatch.differingFields, mismatch.differingFields.toString())
    }

    private fun reducer(now: Long): ParticipantCalibrationReducer =
        ParticipantCalibrationReducer(ParticipantCalibrationClock { now })

    private fun reduction(
        profile: ParticipantCalibrationProfile? = profile(),
        collection: ParticipantCalibrationCollection? = null,
        runtime: ParticipantCalibrationRuntimeIdentity = runtime(),
        acceptance: EvidenceAcceptance = EvidenceAcceptance.Accepted(PROFILE_ID, scope()),
    ): ParticipantCalibrationReductionInput = ParticipantCalibrationReductionInput(
        profile = profile,
        collection = collection,
        runtime = runtime,
        evidenceAcceptance = acceptance,
    )

    private fun decisionInput(
        profile: ParticipantCalibrationProfile? = profile(),
        collection: ParticipantCalibrationCollection? = null,
        runtime: ParticipantCalibrationRuntimeIdentity = runtime(),
        acceptance: EvidenceAcceptance = EvidenceAcceptance.Accepted(PROFILE_ID, scope()),
        canonicalValue: Float? = ESTIMATOR_VALUE,
        candidate: DerivedFaceSignalSample? = sample(),
        canonicalBlendShapes: Map<BlendShape, Float> = if (canonicalValue == null) {
            emptyMap()
        } else {
            mapOf(BlendShape.CHEEK_PUFF to canonicalValue)
        },
    ): ParticipantCalibrationDecisionInput = ParticipantCalibrationDecisionInput(
        reductionInput = reduction(profile, collection, runtime, acceptance),
        canonicalBlendShapes = canonicalBlendShapes,
        candidate = candidate,
    )

    private fun sample(value: Float = CANDIDATE_VALUE): DerivedFaceSignalSample = DerivedFaceSignalSample(
        signal = DerivedFaceSignal.CheekPuffCandidate,
        value = value,
        candidateId = CANDIDATE_ID,
        artifactSha256 = ARTIFACT_HASH,
    )

    private fun profile(): ParticipantCalibrationProfile {
        val artifact = ParticipantCalibrationArtifact(
            profileId = PROFILE_ID,
            enrollmentBinding = BINDING,
            candidateId = CANDIDATE_ID,
            targetSignal = DerivedFaceSignal.CheekPuffCandidate,
            candidateImplementationSha256 = IMPLEMENTATION_HASH,
            preprocessingSha256 = PREPROCESSING_HASH,
            candidateModelSha256 = MODEL_HASH,
            thresholdSha256 = THRESHOLD_HASH,
            artifactSha256 = ARTIFACT_HASH,
            expectedContext = measurementContext(),
            profileRevision = 1u,
            issuedAtEpochMs = ISSUED_AT,
            expiresAtEpochMs = EXPIRES_AT,
        )
        return ParticipantCalibrationProfile(
            artifact = artifact,
            evidenceReceipt = receipt(),
        )
    }

    private fun receipt(): ParticipantCalibrationEvidenceReceipt = ParticipantCalibrationEvidenceReceipt(
        receiptSha256 = RECEIPT_HASH,
        profileId = PROFILE_ID,
        profileRevision = 1u,
        artifactSha256 = ARTIFACT_HASH,
        enrollmentProtocolId = "enrollment-protocol-v1",
        enrollmentRunId = "opaque-enrollment-run",
        qualificationProtocolId = "qualification-protocol-v1",
        qualificationRunId = "opaque-qualification-run",
        qualificationReceiptSha256 = QUALIFICATION_HASH,
        sdkSourceSha256 = SOURCE_HASH,
        reviewedBinarySha256 = BINARY_HASH,
        publicReviewPacketSha256 = REVIEW_HASH,
        evidenceScope = scope(),
    )

    private fun runtime(): ParticipantCalibrationRuntimeIdentity = ParticipantCalibrationRuntimeIdentity(
        profileId = PROFILE_ID,
        profileRevision = 1u,
        enrollmentBinding = BINDING,
        contextEpoch = epoch(),
        candidateId = CANDIDATE_ID,
        targetSignal = DerivedFaceSignal.CheekPuffCandidate,
        candidateImplementationSha256 = IMPLEMENTATION_HASH,
        preprocessingSha256 = PREPROCESSING_HASH,
        candidateModelSha256 = MODEL_HASH,
        thresholdSha256 = THRESHOLD_HASH,
        candidateArtifactSha256 = ARTIFACT_HASH,
        evidenceReceiptSha256 = RECEIPT_HASH,
        sdkSourceSha256 = SOURCE_HASH,
        binarySha256 = BINARY_HASH,
    )

    private fun epoch(): TrackingContextEpoch = TrackingContextEpoch(
        epochId = 5u,
        sessionInstanceId = SessionInstanceId("runtime-session"),
        estimator = EstimatorArtifact(
            family = FaceTrackingBackend.MEDIAPIPE,
            libraryVersion = DimensionValue.Known("0.10.26"),
            modelSha256 = DimensionValue.Known(hash('a')),
        ),
        platform = PlatformIdentity(
            platform = "android",
            osVersion = DimensionValue.Known("36"),
            deviceModel = DimensionValue.Known("synthetic-device"),
            estimatorConfigurationId = DimensionValue.Known("synthetic-config"),
        ),
        runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
        resolvedDelegate = DimensionValue.Known("CPU"),
        inputWidth = DimensionValue.Known(240),
        inputHeight = DimensionValue.Known(320),
        mirroredBeforeInference = DimensionValue.Known(false),
        configuredTransforms = emptyList(),
        activeBlendShapes = setOf(BlendShape.CHEEK_PUFF, BlendShape.JAW_OPEN),
    )

    private fun measurementContext(): MeasurementContext {
        val epoch = epoch()
        return MeasurementContext(
            estimator = epoch.estimator,
            platform = epoch.platform,
            runningMode = epoch.runningMode,
            resolvedDelegate = epoch.resolvedDelegate,
            inputWidth = epoch.inputWidth,
            inputHeight = epoch.inputHeight,
            mirroredBeforeInference = epoch.mirroredBeforeInference,
            configuredTransforms = epoch.configuredTransforms,
            activeBlendShapes = epoch.activeBlendShapes,
            configurationFingerprint = epoch.configurationFingerprint,
        )
    }

    private fun scope(): EvidenceScope = EvidenceScope(
        validationClass = ValidationClass.INDEPENDENT_SESSION,
        participantCount = 1,
        independentSessionCount = 1,
        deviceCount = 1,
        environmentCount = 1,
        limitations = listOf("synthetic contract fixture only"),
    )

    private companion object {
        const val ISSUED_AT: Long = 1_000L
        const val NOW: Long = 1_500L
        const val EXPIRES_AT: Long = 2_000L
        const val PROFILE_ID: String = "participant-profile-v1"
        const val CANDIDATE_ID: String = "participant-cheek-candidate-v1"
        const val ESTIMATOR_VALUE: Float = 0.02f
        const val CANDIDATE_VALUE: Float = 0.81f
        val BINDING: ParticipantEnrollmentBinding = ParticipantEnrollmentBinding("local-binding-opaque-1")
        val IMPLEMENTATION_HASH: String = hash('1')
        val PREPROCESSING_HASH: String = hash('2')
        val MODEL_HASH: String = hash('3')
        val THRESHOLD_HASH: String = hash('4')
        val ARTIFACT_HASH: String = hash('5')
        val RECEIPT_HASH: String = hash('6')
        val QUALIFICATION_HASH: String = hash('7')
        val SOURCE_HASH: String = hash('8')
        val BINARY_HASH: String = hash('9')
        val REVIEW_HASH: String = hash('a')

        fun hash(character: Char): String = character.toString().repeat(64)
    }
}
