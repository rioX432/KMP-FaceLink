package io.github.kmpfacelink.capture

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducer
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateProducerDescriptor
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSemanticIntent
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.research.CheekSignalCandidateFactories
import io.github.kmpfacelink.research.HashBoundStaticAppearanceModelProvider
import io.github.kmpfacelink.research.StaticAppearanceCandidateFactories
import io.github.kmpfacelink.research.StaticAppearanceModelByteLoader
import io.github.kmpfacelink.research.StaticAppearanceModelParser
import io.github.kmpfacelink.research.StaticAppearanceScoreModel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalFaceLinkApi::class)
class CandidateObservationReplaySessionTest {

    @Test
    fun c3bOpaqueFeaturePlaneMatchesDirectAndRawRecordReplay() {
        val bytes = "synthetic-c3b-model".encodeToByteArray()
        val provider = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = SYNTHETIC_MODEL_SHA256,
            byteLoader = StaticAppearanceModelByteLoader { bytes },
            parser = StaticAppearanceModelParser {
                StaticAppearanceScoreModel { features -> features.first() }
            },
        )
        val factories = listOf(StaticAppearanceCandidateFactories.c3bStaticCheekPatch(provider))
        val direct = CandidateObservationSession(factories)
        val replay = CandidateObservationReplaySession(factories)
        val identity = CandidateInputIdentity(9u, 1u, 0, 50L)
        val features = List(384) { index -> if (index == 0) 0.625f else 0f }
        val planes = mapOf(StaticAppearanceCandidateFactories.FEATURE_PLANE_KEY to features)
        val names = listOf("_neutral") + BlendShape.entries
            .filterNot { it == BlendShape.TONGUE_OUT }
            .map { it.arKitName }
        val values = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT)
        val estimator = buildMap {
            names.forEachIndexed { index, name ->
                BlendShape.fromArKitName(name)?.let { shape -> put(shape, values[index]) }
            }
        }
        val record = InferenceRecord(
            analysisFrameSeq = 1,
            captureTimestampNs = 50_000_000L,
            resultElapsedNs = 51_000_000L,
            phase = CapturePhase.ACTED,
            hasFace = true,
            inferenceFailed = false,
            landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS),
            blendshapes = values,
            transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
        )

        val directReceipt = direct.observe(
            CandidateObservationInput(identity, estimator, emptyList(), 320, 240, planes),
        )
        val replayReceipt = replay.observe(record, names, identity, 320, 240, planes)

        assertEquals(0.625f, directReceipt.alternatives.single().value)
        assertEquals(
            directReceipt.alternatives.map { listOf(it.value, it.readiness, it.reason) },
            replayReceipt.alternatives.map { listOf(it.value, it.readiness, it.reason) },
        )
        assertEquals(directReceipt.selected, replayReceipt.selected)
    }

    @Test
    fun frozenC2FactoriesMatchDirectInputAndRawInferenceRecordReplay() {
        val factories = CheekSignalCandidateFactories.c2LipOnly()
        val direct = CandidateObservationSession(factories)
        val replay = CandidateObservationReplaySession(factories)
        val names = listOf("_neutral") + BlendShape.entries
            .filterNot { it == BlendShape.TONGUE_OUT }
            .map { it.arKitName }
        val timestamps = listOf(0L) + List(15) { index -> 250L + index * 50L } + 1300L

        timestamps.forEachIndexed { frameIndex, timestampMs ->
            val values = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT)
            if (timestampMs == 1300L) {
                values[names.indexOf(BlendShape.MOUTH_ROLL_LOWER.arKitName)] = 0.2f
                values[names.indexOf(BlendShape.MOUTH_ROLL_UPPER.arKitName)] = 0.3f
                values[names.indexOf(BlendShape.MOUTH_LEFT.arKitName)] = 0.4f
                values[names.indexOf(BlendShape.MOUTH_FUNNEL.arKitName)] = 0.1f
            }
            val identity = CandidateInputIdentity(8u, 3u, 0, timestampMs)
            val estimator = buildMap {
                names.forEachIndexed { index, name ->
                    BlendShape.fromArKitName(name)?.let { shape -> put(shape, values[index]) }
                }
            }
            val directReceipt = direct.observe(
                CandidateObservationInput(identity, estimator, emptyList(), 320, 240),
            )
            val replayReceipt = replay.observe(
                record = InferenceRecord(
                    analysisFrameSeq = frameIndex,
                    captureTimestampNs = timestampMs * 1_000_000L,
                    resultElapsedNs = timestampMs * 1_000_000L,
                    phase = CapturePhase.ACTED,
                    hasFace = true,
                    inferenceFailed = false,
                    landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS),
                    blendshapes = values,
                    transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
                ),
                blendshapeNames = names,
                identity = identity,
                sourceImageWidth = 320,
                sourceImageHeight = 240,
            )

            assertEquals(
                directReceipt.alternatives.map { listOf(it.producerId, it.value, it.readiness, it.reason) },
                replayReceipt.alternatives.map { listOf(it.producerId, it.value, it.readiness, it.reason) },
            )
            assertEquals(directReceipt.selected, replayReceipt.selected)
        }
    }

    @Test
    fun liveCoreAndReplayUseTheSameCandidateObservationContract() {
        val input = CandidateObservationInput(
            identity = CandidateInputIdentity(4u, 2u, faceIndex = 0, timestampMs = 90L),
            estimator = mapOf(BlendShape.CHEEK_PUFF to 0.3f),
            landmarks = emptyList(),
            sourceImageWidth = 320,
            sourceImageHeight = 240,
        )
        val live = CandidateObservationSession(listOf(StatefulFactory())).observe(input)
        val replay = CandidateObservationReplaySession(listOf(StatefulFactory())).observe(input)

        assertEquals(live.identity.contextEpochId, replay.identity.contextEpochId)
        assertEquals(live.identity.processingStateRevision, replay.identity.processingStateRevision)
        assertEquals(live.identity.faceIndex, replay.identity.faceIndex)
        assertEquals(live.identity.timestampMs, replay.identity.timestampMs)
        assertEquals(live.estimator, replay.estimator)
        assertEquals(live.selected, replay.selected)
        assertEquals(live.alternatives.single().producerId, replay.alternatives.single().producerId)
        assertEquals(live.alternatives.single().implementationRevision, replay.alternatives.single().implementationRevision)
        assertEquals(live.alternatives.single().semanticId, replay.alternatives.single().semanticId)
        assertEquals(live.alternatives.single().value, replay.alternatives.single().value)
        assertEquals(live.alternatives.single().readiness, replay.alternatives.single().readiness)
    }

    @Test
    fun persistedRawRecordReplaysThroughTheSameProducerInput() {
        val names = listOf("_neutral") + BlendShape.entries
            .filterNot { it == BlendShape.TONGUE_OUT }
            .map { it.arKitName }
        val scores = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT)
        scores[names.indexOf(BlendShape.CHEEK_PUFF.arKitName)] = 0.3f
        val rawLandmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS)
        rawLandmarks[0] = 0.25f
        rawLandmarks[1] = 0.5f
        rawLandmarks[2] = -0.1f
        val identity = CandidateInputIdentity(4u, 2u, faceIndex = 0, timestampMs = 90L)
        val directInput = CandidateObservationInput(
            identity = identity,
            estimator = buildMap {
                names.forEachIndexed { index, name ->
                    BlendShape.fromArKitName(name)?.let { put(it, scores[index]) }
                }
            },
            landmarks = List(InferenceRecordFormat.LANDMARK_COUNT) { index ->
                if (index == 0) {
                    FaceLandmark(0.25f, 0.5f, -0.1f)
                } else {
                    FaceLandmark(0f, 0f, 0f)
                }
            },
            sourceImageWidth = 320,
            sourceImageHeight = 240,
        )
        val record = InferenceRecord(
            analysisFrameSeq = 17,
            captureTimestampNs = 90_000_000L,
            resultElapsedNs = 95_000_000L,
            phase = CapturePhase.ACTED,
            hasFace = true,
            inferenceFailed = false,
            landmarks = rawLandmarks,
            blendshapes = scores,
            transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
        )

        val live = CandidateObservationSession(listOf(LandmarkFactory())).observe(directInput)
        val replay = CandidateObservationReplaySession(listOf(LandmarkFactory())).observe(
            record = record,
            blendshapeNames = names,
            identity = identity,
            sourceImageWidth = 320,
            sourceImageHeight = 240,
        )

        assertEquals(live.estimator, replay.estimator)
        assertEquals(live.selected, replay.selected)
        assertEquals(live.alternatives.single().value, replay.alternatives.single().value)
        assertEquals(identity, replay.identity)
    }

    @Test
    fun unusablePersistedRecordDoesNotReplayFixedStrideZerosAsMeasurements() {
        val names = listOf("_neutral") + BlendShape.entries
            .filterNot { it == BlendShape.TONGUE_OUT }
            .map { it.arKitName }
        val receipt = CandidateObservationReplaySession(listOf(LandmarkFactory())).observe(
            record = InferenceRecord(
                analysisFrameSeq = 18,
                captureTimestampNs = 100_000_000L,
                resultElapsedNs = 105_000_000L,
                phase = CapturePhase.ACTED,
                hasFace = false,
                inferenceFailed = false,
                landmarks = FloatArray(InferenceRecordFormat.LANDMARK_FLOATS),
                blendshapes = FloatArray(InferenceRecordFormat.BLENDSHAPE_COUNT),
                transform = FloatArray(InferenceRecordFormat.TRANSFORM_FLOATS),
            ),
            blendshapeNames = names,
            identity = CandidateInputIdentity(4u, 2u, faceIndex = 0, timestampMs = 100L),
            sourceImageWidth = 320,
            sourceImageHeight = 240,
        )

        assertEquals(emptyMap(), receipt.estimator)
        assertEquals(null, receipt.alternatives.single().value)
        assertEquals(CandidateReadiness.MISSING_INPUT, receipt.alternatives.single().readiness)
    }

    private class StatefulFactory : CandidateObservationProducerFactory {
        override val descriptor = CandidateProducerDescriptor(
            producerId = "candidate:parity",
            implementationRevision = "parity-test/v1",
            semanticId = "canonical:cheekPuff",
            semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
            proposedTarget = BlendShape.CHEEK_PUFF,
        )

        override fun create(): CandidateObservationProducer {
            var calls = 0
            return CandidateObservationProducer {
                calls++
                CandidateProducerOutput.ready(calls.toFloat())
            }
        }
    }

    private class LandmarkFactory : CandidateObservationProducerFactory {
        override val descriptor = CandidateProducerDescriptor(
            producerId = "candidate:landmark-parity",
            implementationRevision = "landmark-parity-test/v1",
            semanticId = "canonical:cheekPuff",
            semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
            proposedTarget = BlendShape.CHEEK_PUFF,
        )

        override fun create(): CandidateObservationProducer = CandidateObservationProducer { input ->
            input.landmarks.firstOrNull()?.let { CandidateProducerOutput.ready(it.x) }
                ?: CandidateProducerOutput.absent(
                    CandidateReadiness.MISSING_INPUT,
                    "landmarks_missing",
                )
        }
    }

    private companion object {
        const val SYNTHETIC_MODEL_SHA256 =
            "04151d830c9801c978475455fe1f6c6ca489a86f493509449d2a946548c469f2"
    }
}
