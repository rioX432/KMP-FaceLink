package io.github.kmpfacelink

import io.github.kmpfacelink.model.AppliedTransform
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeFallbackPolicy
import io.github.kmpfacelink.model.BlendShapeRoute
import io.github.kmpfacelink.model.BlendShapeSchema
import io.github.kmpfacelink.model.CalibrationPhase
import io.github.kmpfacelink.model.CanonicalContextFingerprint
import io.github.kmpfacelink.model.CapabilityEvidence
import io.github.kmpfacelink.model.CapabilityMatcher
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.DistributableEvidenceValidator
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.EvidenceAcceptance
import io.github.kmpfacelink.model.EvidenceAcceptancePolicy
import io.github.kmpfacelink.model.EvidenceScope
import io.github.kmpfacelink.model.FaceObservationProvenance
import io.github.kmpfacelink.model.FacePipelineKind
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.FaceTrackingData
import io.github.kmpfacelink.model.HeadTransform
import io.github.kmpfacelink.model.InferenceMaterialization
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.MeasuredOutputCondition
import io.github.kmpfacelink.model.MeasurementContext
import io.github.kmpfacelink.model.ObservedResponseClass
import io.github.kmpfacelink.model.ObservedResponseProfile
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.ResponseObservation
import io.github.kmpfacelink.model.RevisionRequirement
import io.github.kmpfacelink.model.SchemaLayer
import io.github.kmpfacelink.model.SchemaPresence
import io.github.kmpfacelink.model.SessionBinding
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.TechnicalContextMatch
import io.github.kmpfacelink.model.TrackedFace
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.model.TransformKind
import io.github.kmpfacelink.model.TransformSpec
import io.github.kmpfacelink.model.TransformStateDependence
import io.github.kmpfacelink.model.ValidationClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CapabilityContractTest {

    @Test
    fun schemaAbsenceDoesNotBecomeMeasuredFloor() {
        val schema = schema(tonguePresent = false)
        val evidence = CapabilityMatcher.evidence(
            profile(),
            schema,
            frame().first,
            frame().second,
            BlendShape.TONGUE_OUT,
        )
        assertIs<CapabilityEvidence.SchemaAbsent>(evidence)
    }

    @Test
    fun incompatibleSchemaCannotAssertPresenceOrAbsence() {
        val (frame, face) = frame()
        val wrongId = schema(tonguePresent = false).copy(id = "other-schema")
        val idResult = CapabilityMatcher.evidence(
            profile(),
            wrongId,
            frame,
            face,
            BlendShape.TONGUE_OUT,
        )
        val idFailure = assertIs<CapabilityEvidence.NoApplicableEvidence>(idResult)
        assertTrue("schema.id" in idFailure.reason)

        val wrongLayer = schema().copy(layer = SchemaLayer.CANONICAL_SDK_OUTPUT)
        val layerResult = CapabilityMatcher.evidence(
            profile(),
            wrongLayer,
            frame,
            face,
            BlendShape.CHEEK_PUFF,
        )
        val layerFailure = assertIs<CapabilityEvidence.NoApplicableEvidence>(layerResult)
        assertTrue("schema.layer" in layerFailure.reason)

        val wrongEstimator = schema().copy(
            estimator = estimator().copy(libraryVersion = DimensionValue.Known("different")),
        )
        val estimatorResult = CapabilityMatcher.evidence(
            profile(),
            wrongEstimator,
            frame,
            face,
            BlendShape.CHEEK_PUFF,
        )
        val estimatorFailure = assertIs<CapabilityEvidence.NoApplicableEvidence>(estimatorResult)
        assertTrue("schema.estimator.libraryVersion" in estimatorFailure.reason)
    }

    @Test
    fun unknownAndNotApplicableAreDifferent() {
        val arkit = profile(
            runningMode = DimensionValue.NotApplicable,
            resolvedDelegate = DimensionValue.NotApplicable,
        )
        val exact = frame(
            runningMode = DimensionValue.NotApplicable,
            resolvedDelegate = DimensionValue.NotApplicable,
        )
        assertIs<TechnicalContextMatch.Exact>(
            CapabilityMatcher.match(arkit, exact.first, exact.second, BlendShape.CHEEK_PUFF),
        )

        val notApplicableVsUnknown = frame(
            runningMode = DimensionValue.NotApplicable,
            resolvedDelegate = DimensionValue.Unknown,
        )
        val incompatible = CapabilityMatcher.match(
            arkit,
            notApplicableVsUnknown.first,
            notApplicableVsUnknown.second,
            BlendShape.CHEEK_PUFF,
        )
        assertIs<TechnicalContextMatch.Incompatible>(incompatible)
        assertTrue("resolvedDelegate" in incompatible.differingFields)

        val knownProfile = profile(resolvedDelegate = DimensionValue.Known("CPU"))
        val knownVsUnknown = frame(resolvedDelegate = DimensionValue.Unknown)
        val unknown = CapabilityMatcher.match(
            knownProfile,
            knownVsUnknown.first,
            knownVsUnknown.second,
            BlendShape.CHEEK_PUFF,
        )
        assertIs<TechnicalContextMatch.Unknown>(unknown)
        assertTrue("resolvedDelegate" in unknown.unresolvedFields)
    }

    @Test
    fun runningModeAndTransformMutationsFailClosed() {
        val modeMismatch = frame(runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM))
        val modeResult = CapabilityMatcher.match(
            profile(),
            modeMismatch.first,
            modeMismatch.second,
            BlendShape.CHEEK_PUFF,
        )
        assertIs<TechnicalContextMatch.Incompatible>(modeResult)
        assertTrue("runningMode" in modeResult.differingFields)

        val mutatedTransform = listOf(AppliedTransform(TransformKind.SMOOTHER, "other"))
        val transformMismatch = frame(appliedTransforms = mutatedTransform)
        val transformResult = CapabilityMatcher.match(
            profile(),
            transformMismatch.first,
            transformMismatch.second,
            BlendShape.CHEEK_PUFF,
        )
        assertIs<TechnicalContextMatch.Incompatible>(transformResult)
        assertTrue("output.appliedTransforms" in transformResult.differingFields)
    }

    @Test
    fun everyMeasurementContextDimensionFailsClosedWhenMutated() {
        val base = epoch(
            SESSION_A,
            DimensionValue.Known(InferenceRunningMode.IMAGE),
            DimensionValue.Known("CPU"),
        )
        val mutations = listOf(
            "estimator.family" to base.copy(
                estimator = base.estimator.copy(family = FaceTrackingBackend.ARKIT),
            ),
            "estimator.libraryVersion" to base.copy(
                estimator = base.estimator.copy(libraryVersion = DimensionValue.Known("other")),
            ),
            "estimator.modelSha256" to base.copy(
                estimator = base.estimator.copy(modelSha256 = DimensionValue.Known("c".repeat(SHA_LENGTH))),
            ),
            "platform" to base.copy(platform = base.platform.copy(platform = "ios")),
            "platform.osVersion" to base.copy(
                platform = base.platform.copy(osVersion = DimensionValue.Known("other")),
            ),
            "platform.deviceModel" to base.copy(
                platform = base.platform.copy(deviceModel = DimensionValue.Known("other")),
            ),
            "platform.estimatorConfigurationId" to base.copy(
                platform = base.platform.copy(estimatorConfigurationId = DimensionValue.Known("other")),
            ),
            "runningMode" to base.copy(runningMode = DimensionValue.Known(InferenceRunningMode.VIDEO)),
            "resolvedDelegate" to base.copy(resolvedDelegate = DimensionValue.Known("GPU")),
            "inputWidth" to base.copy(inputWidth = DimensionValue.Known(320)),
            "inputHeight" to base.copy(inputHeight = DimensionValue.Known(240)),
            "mirroredBeforeInference" to base.copy(mirroredBeforeInference = DimensionValue.Known(false)),
            "configuredTransforms" to base.copy(
                configuredTransforms = listOf(transformSpec(TransformStateDependence.PORTABLE_CONFIGURATION)),
            ),
            "activeBlendShapes" to base.copy(activeBlendShapes = setOf(BlendShape.CHEEK_PUFF)),
        )

        mutations.forEach { (expectedField, mutatedEpoch) ->
            val (frame, face) = frame(epochOverride = mutatedEpoch)
            val result = CapabilityMatcher.match(profile(), frame, face, BlendShape.CHEEK_PUFF)
            val incompatible = assertIs<TechnicalContextMatch.Incompatible>(result, expectedField)
            assertTrue(expectedField in incompatible.differingFields, incompatible.differingFields.toString())
        }
    }

    @Test
    fun outputConditionIsMatchedPerFace() {
        val secondary = frame(pipelineKind = FacePipelineKind.SECONDARY)
        assertIs<TechnicalContextMatch.Incompatible>(
            CapabilityMatcher.match(profile(), secondary.first, secondary.second, BlendShape.CHEEK_PUFF),
        )

        val warmup = frame(calibrationPhase = CalibrationPhase.WARMUP)
        assertIs<TechnicalContextMatch.Incompatible>(
            CapabilityMatcher.match(profile(), warmup.first, warmup.second, BlendShape.CHEEK_PUFF),
        )

        val interpolated = frame(materialization = InferenceMaterialization.INTERPOLATED)
        assertIs<TechnicalContextMatch.Incompatible>(
            CapabilityMatcher.match(profile(), interpolated.first, interpolated.second, BlendShape.CHEEK_PUFF),
        )
    }

    @Test
    fun faceProvenanceCannotBePairedWithAnotherFrameOrIndex() {
        val (frame, face) = frame()
        val absentFromFrame = frame.copy(faces = emptyList())
        val frameResult = CapabilityMatcher.match(profile(), absentFromFrame, face, BlendShape.CHEEK_PUFF)
        assertIs<TechnicalContextMatch.Incompatible>(frameResult)
        assertTrue("frame.faces" in frameResult.differingFields)

        val wrongIndexFace = face.copy(
            provenance = face.provenance?.copy(faceIndex = face.faceIndex + 1),
        )
        val wrongIndexFrame = frame.copy(faces = listOf(wrongIndexFace))
        val indexResult = CapabilityMatcher.match(profile(), wrongIndexFrame, wrongIndexFace, BlendShape.CHEEK_PUFF)
        assertIs<TechnicalContextMatch.Incompatible>(indexResult)
        assertTrue("faceIndex" in indexResult.differingFields)
    }

    @Test
    fun runtimeFilteringIsNotSchemaAbsence() {
        val filtered = frame(emittedShapes = setOf(BlendShape.JAW_OPEN))
        val match = CapabilityMatcher.match(profile(), filtered.first, filtered.second, BlendShape.CHEEK_PUFF)
        assertIs<TechnicalContextMatch.Incompatible>(match)
        assertTrue("output.requiredEmittedShapes" in match.differingFields)

        val profileWithoutRequirement = profile(requiredShapes = emptySet())
        val filteredResult = CapabilityMatcher.match(
            profileWithoutRequirement,
            filtered.first,
            filtered.second,
            BlendShape.CHEEK_PUFF,
        )
        assertIs<TechnicalContextMatch.RuntimeFiltered>(filteredResult)
    }

    @Test
    fun evidenceScopeSurvivesTechnicalMatch() {
        val profile = profile()
        val (frame, face) = frame()
        val result = CapabilityMatcher.evidence(profile, schema(), frame, face, BlendShape.CHEEK_PUFF)
        val matched = assertIs<CapabilityEvidence.TechnicallyMatched>(result)
        assertEquals(ValidationClass.EXPLORATORY_SINGLE_SESSION, matched.profile.scope.validationClass)
        assertEquals(1, matched.profile.scope.participantCount)
    }

    @Test
    fun evidenceCannotSelectAProductRouteWithoutTwoInjectedProductDecisions() {
        val (frame, face) = frame()
        val evidence = assertIs<CapabilityEvidence.TechnicallyMatched>(
            CapabilityMatcher.evidence(profile(), schema(), frame, face, BlendShape.CHEEK_PUFF),
        )
        var fallbackCalls = 0
        val acceptancePolicy = EvidenceAcceptancePolicy { _, matched ->
            EvidenceAcceptance.Rejected(
                "${matched.profile.scope.validationClass} is insufficient for product routing",
            )
        }
        val fallbackPolicy = BlendShapeFallbackPolicy { _, _, acceptance ->
            fallbackCalls++
            when (acceptance) {
                is EvidenceAcceptance.Accepted -> BlendShapeRoute.Estimator
                is EvidenceAcceptance.Rejected -> BlendShapeRoute.Disabled
            }
        }

        val acceptance = acceptancePolicy.accept(BlendShape.CHEEK_PUFF, evidence)
        assertIs<EvidenceAcceptance.Rejected>(acceptance)
        assertEquals(0, fallbackCalls)
        val route = fallbackPolicy.decide(BlendShape.CHEEK_PUFF, evidence, acceptance)
        assertEquals(BlendShapeRoute.Disabled, route)
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun learnedStateCannotBeDistributed() {
        val learned = transformSpec(stateDependence = TransformStateDependence.SESSION_LEARNED_STATE)
        val profile = profile(
            contextTransforms = listOf(learned),
            calibrationPhase = CalibrationPhase.ACTIVE,
            sessionBinding = SessionBinding.SameSession(SESSION_A),
            revisionRequirement = RevisionRequirement.Exact(1u, 1u),
        )
        val reasons = DistributableEvidenceValidator.rejectionReasons(profile)
        assertFalse(DistributableEvidenceValidator.isDistributable(profile))
        assertTrue("same-session binding" in reasons)
        assertTrue("session-learned transform" in reasons)
        assertTrue("calibration state" in reasons)
    }

    @Test
    fun equalRevisionsCannotCrossTrackerSessions() {
        val profile = profile(
            sessionBinding = SessionBinding.SameSession(SESSION_A),
            revisionRequirement = RevisionRequirement.Exact(1u, 1u),
        )
        val same = frame(session = SESSION_A, calibrationRevision = 1u, processingRevision = 1u)
        assertIs<TechnicalContextMatch.Exact>(
            CapabilityMatcher.match(profile, same.first, same.second, BlendShape.CHEEK_PUFF),
        )

        val other = frame(session = SESSION_B, calibrationRevision = 1u, processingRevision = 1u)
        val result = CapabilityMatcher.match(profile, other.first, other.second, BlendShape.CHEEK_PUFF)
        assertIs<TechnicalContextMatch.Incompatible>(result)
        assertTrue("sessionInstanceId" in result.differingFields)
    }

    @Test
    fun portableEvidenceMatchesConfigurationAcrossSessions() {
        val first = epoch(SESSION_A, DimensionValue.Known(InferenceRunningMode.IMAGE), DimensionValue.Known("CPU"))
        val second = epoch(SESSION_B, DimensionValue.Known(InferenceRunningMode.IMAGE), DimensionValue.Known("CPU"))
        assertEquals(first.configurationFingerprint, second.configurationFingerprint)
        assertFalse(first.epochFingerprint == second.epochFingerprint)

        val otherSessionFrame = frame(session = SESSION_B)
        assertIs<TechnicalContextMatch.Exact>(
            CapabilityMatcher.match(
                profile(),
                otherSessionFrame.first,
                otherSessionFrame.second,
                BlendShape.CHEEK_PUFF,
            ),
        )
    }

    @Test
    fun fingerprintChangesWhenConfigurationOrEpochChanges() {
        val base = epoch(SESSION_A, DimensionValue.Known(InferenceRunningMode.IMAGE), DimensionValue.Known("CPU"))
        val modeMutation = epoch(
            SESSION_A,
            DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
            DimensionValue.Known("CPU"),
        )
        val epochMutation = base.copy(epochId = base.epochId + 1u)
        assertFalse(base.configurationFingerprint == modeMutation.configurationFingerprint)
        assertFalse(base.epochFingerprint == epochMutation.epochFingerprint)
        assertEquals(SHA_LENGTH, base.configurationFingerprint.length)
        assertEquals(SHA_LENGTH, base.epochFingerprint.length)
    }

    @Test
    fun canonicalFingerprintGoldenEncodingAndShaAreFrozen() {
        val golden = TrackingContextEpoch(
            epochId = 0x0102030405060708u,
            sessionInstanceId = SessionInstanceId("golden-session"),
            estimator = EstimatorArtifact(
                family = FaceTrackingBackend.MEDIAPIPE,
                libraryVersion = DimensionValue.Known("0.10.35"),
                modelSha256 = DimensionValue.Unknown,
            ),
            platform = PlatformIdentity(
                platform = "android",
                osVersion = DimensionValue.Known("15"),
                deviceModel = DimensionValue.NotApplicable,
                estimatorConfigurationId = DimensionValue.Known("config-A"),
            ),
            runningMode = DimensionValue.Known(InferenceRunningMode.LIVE_STREAM),
            resolvedDelegate = DimensionValue.Known("CPU"),
            inputWidth = DimensionValue.Known(240),
            inputHeight = DimensionValue.Known(320),
            mirroredBeforeInference = DimensionValue.Known(true),
            configuredTransforms = listOf(
                TransformSpec(
                    kind = TransformKind.SMOOTHER,
                    implementationId = "one-euro-v1",
                    configurationFingerprint = "smooth-config",
                ),
            ),
            activeBlendShapes = setOf(BlendShape.CHEEK_PUFF),
        )

        assertEquals(CONFIGURATION_GOLDEN_HEX, CanonicalContextFingerprint.encodeConfiguration(golden).toHex())
        assertEquals(EPOCH_GOLDEN_HEX, CanonicalContextFingerprint.encodeEpoch(golden).toHex())
        assertEquals(CONFIGURATION_GOLDEN_SHA256, golden.configurationFingerprint)
        assertEquals(EPOCH_GOLDEN_SHA256, golden.epochFingerprint)
    }

    @Test
    fun invalidMeasurementFingerprintIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            measurementContext(
                DimensionValue.Known(InferenceRunningMode.IMAGE),
                DimensionValue.Known("CPU"),
                emptyList(),
            ).copy(configurationFingerprint = "not-a-sha")
        }
    }

    @Test
    fun measuredObservationMustReferenceItsEnclosingProfile() {
        val valid = profile()
        assertFailsWith<IllegalArgumentException> {
            val wrongObservation = ResponseObservation.Measured(
                ObservedResponseClass.FLOOR,
                "another-profile",
            )
            valid.copy(
                observations = valid.observations + (BlendShape.CHEEK_PUFF to wrongObservation),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun profile(
        runningMode: DimensionValue<InferenceRunningMode> = DimensionValue.Known(InferenceRunningMode.IMAGE),
        resolvedDelegate: DimensionValue<String> = DimensionValue.Known("CPU"),
        contextTransforms: List<TransformSpec> = emptyList(),
        appliedTransforms: List<AppliedTransform> = emptyList(),
        calibrationPhase: CalibrationPhase = CalibrationPhase.DISABLED,
        requiredShapes: Set<BlendShape> = setOf(BlendShape.CHEEK_PUFF),
        sessionBinding: SessionBinding = SessionBinding.Portable,
        revisionRequirement: RevisionRequirement = RevisionRequirement.NotApplicable,
    ): ObservedResponseProfile = ObservedResponseProfile(
        id = "profile",
        context = measurementContext(runningMode, resolvedDelegate, contextTransforms),
        outputCondition = MeasuredOutputCondition(
            schemaLayer = SchemaLayer.RAW_ESTIMATOR,
            pipelineKind = FacePipelineKind.PRIMARY,
            appliedTransforms = appliedTransforms,
            calibrationPhase = calibrationPhase,
            materialization = InferenceMaterialization.FULL_INFERENCE,
            requiredEmittedShapes = requiredShapes,
            sessionBinding = sessionBinding,
            revisionRequirement = revisionRequirement,
        ),
        schemaId = "schema",
        observations = BlendShape.entries.associateWith { shape ->
            if (shape == BlendShape.CHEEK_PUFF) {
                ResponseObservation.Measured(ObservedResponseClass.FLOOR, "profile")
            } else {
                ResponseObservation.Untested
            }
        },
        scope = EvidenceScope(
            validationClass = ValidationClass.EXPLORATORY_SINGLE_SESSION,
            participantCount = 1,
            independentSessionCount = 1,
            deviceCount = 1,
            environmentCount = 1,
            limitations = listOf("test fixture"),
        ),
        protocolId = "protocol",
        publicReviewPacketSha256 = "a".repeat(SHA_LENGTH),
    )

    @Suppress("LongParameterList")
    private fun frame(
        session: SessionInstanceId = SESSION_A,
        runningMode: DimensionValue<InferenceRunningMode> = DimensionValue.Known(InferenceRunningMode.IMAGE),
        resolvedDelegate: DimensionValue<String> = DimensionValue.Known("CPU"),
        pipelineKind: FacePipelineKind = FacePipelineKind.PRIMARY,
        appliedTransforms: List<AppliedTransform> = emptyList(),
        calibrationPhase: CalibrationPhase = CalibrationPhase.DISABLED,
        calibrationRevision: ULong = 0u,
        processingRevision: ULong = 0u,
        materialization: InferenceMaterialization = InferenceMaterialization.FULL_INFERENCE,
        emittedShapes: Set<BlendShape> = BlendShape.entries.toSet(),
        epochOverride: TrackingContextEpoch? = null,
    ): Pair<FaceTrackingData, TrackedFace> {
        val epoch = epochOverride ?: epoch(session, runningMode, resolvedDelegate)
        val provenance = FaceObservationProvenance(
            contextEpochId = epoch.epochId,
            faceIndex = 0,
            pipelineKind = pipelineKind,
            appliedTransforms = appliedTransforms,
            calibrationPhase = calibrationPhase,
            calibrationStateRevision = calibrationRevision,
            processingStateRevision = processingRevision,
            materialization = materialization,
            emittedBlendShapes = emittedShapes,
        )
        val face = TrackedFace(
            faceIndex = 0,
            blendShapes = mapOf(BlendShape.CHEEK_PUFF to 0f),
            headTransform = HeadTransform(),
            provenance = provenance,
        )
        val frame = FaceTrackingData(
            blendShapes = face.blendShapes,
            headTransform = face.headTransform,
            timestampMs = 1L,
            isTracking = true,
            faces = listOf(face),
            contextEpoch = epoch,
        )
        return frame to face
    }

    private fun measurementContext(
        runningMode: DimensionValue<InferenceRunningMode>,
        resolvedDelegate: DimensionValue<String>,
        transforms: List<TransformSpec>,
    ): MeasurementContext {
        val matchingEpoch = epoch(SESSION_A, runningMode, resolvedDelegate, transforms)
        return MeasurementContext(
            estimator = estimator(),
            platform = platform(),
            runningMode = runningMode,
            resolvedDelegate = resolvedDelegate,
            inputWidth = DimensionValue.Known(240),
            inputHeight = DimensionValue.Known(320),
            mirroredBeforeInference = DimensionValue.Known(true),
            configuredTransforms = transforms,
            activeBlendShapes = BlendShape.entries.toSet(),
            configurationFingerprint = matchingEpoch.configurationFingerprint,
        )
    }

    private fun epoch(
        session: SessionInstanceId,
        runningMode: DimensionValue<InferenceRunningMode>,
        resolvedDelegate: DimensionValue<String>,
        transforms: List<TransformSpec> = emptyList(),
    ): TrackingContextEpoch = TrackingContextEpoch(
        epochId = 1u,
        sessionInstanceId = session,
        estimator = estimator(),
        platform = platform(),
        runningMode = runningMode,
        resolvedDelegate = resolvedDelegate,
        inputWidth = DimensionValue.Known(240),
        inputHeight = DimensionValue.Known(320),
        mirroredBeforeInference = DimensionValue.Known(true),
        configuredTransforms = transforms,
        activeBlendShapes = BlendShape.entries.toSet(),
    )

    private fun estimator(): EstimatorArtifact = EstimatorArtifact(
        family = FaceTrackingBackend.MEDIAPIPE,
        libraryVersion = DimensionValue.Known("0.10.30"),
        modelSha256 = DimensionValue.Known("b".repeat(SHA_LENGTH)),
    )

    private fun platform(): PlatformIdentity = PlatformIdentity(
        platform = "android",
        osVersion = DimensionValue.Known("test"),
        deviceModel = DimensionValue.Known("test"),
        estimatorConfigurationId = DimensionValue.NotApplicable,
    )

    private fun schema(tonguePresent: Boolean = true): BlendShapeSchema = BlendShapeSchema(
        id = "schema",
        estimator = estimator(),
        layer = SchemaLayer.RAW_ESTIMATOR,
        presence = BlendShape.entries.associateWith { shape ->
            if (shape == BlendShape.TONGUE_OUT && !tonguePresent) SchemaPresence.ABSENT else SchemaPresence.PRESENT
        },
    )

    private fun transformSpec(
        stateDependence: TransformStateDependence,
    ): TransformSpec = TransformSpec(
        kind = TransformKind.CALIBRATOR,
        implementationId = "calibrator",
        configurationFingerprint = "config",
        stateDependence = stateDependence,
    )

    private companion object {
        const val SHA_LENGTH: Int = 64

        @Suppress("MaximumLineLength")
        const val CONFIGURATION_GOLDEN_HEX: String =
            "000000164b4d50464c5f434f4e46494755524154494f4e5f5631000000094d45444941504950450100000007302e31302e3335" +
                "0300000007616e64726f696401000000023135020100000008636f6e6669672d41010000000b4c4956455f53545245414d010000" +
                "000343505501000000f0010000014001010000000100000008534d4f4f544845520000000b6f6e652d6575726f2d7631000000" +
                "0d736d6f6f74682d636f6e66696700000016504f525441424c455f434f4e46494755524154494f4e0000000100000009636865" +
                "656b50756666"

        @Suppress("MaximumLineLength")
        const val EPOCH_GOLDEN_HEX: String =
            "0000000e4b4d50464c5f45504f43485f563101020304050607080000000e676f6c64656e2d73657373696f6e000000d30000" +
                "00164b4d50464c5f434f4e46494755524154494f4e5f5631000000094d45444941504950450100000007302e31302e33350300" +
                "000007616e64726f696401000000023135020100000008636f6e6669672d41010000000b4c4956455f53545245414d01000000" +
                "0343505501000000f0010000014001010000000100000008534d4f4f544845520000000b6f6e652d6575726f2d76310000000d" +
                "736d6f6f74682d636f6e66696700000016504f525441424c455f434f4e46494755524154494f4e000000010000000963686565" +
                "6b50756666"
        const val CONFIGURATION_GOLDEN_SHA256: String =
            "8aaf375c5d08ec9ed311e0e023974a25f83a5b591c52160951c5caf6d674fec6"
        const val EPOCH_GOLDEN_SHA256: String =
            "a4960e7c74e7c967a154d50332354fd24ae68e69be67076818273425916e84e8"
        val SESSION_A: SessionInstanceId = SessionInstanceId("session-a")
        val SESSION_B: SessionInstanceId = SessionInstanceId("session-b")
    }
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(radix = 16).padStart(length = 2, padChar = '0')
}
