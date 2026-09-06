package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducer
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateProducerDescriptor
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSelectionMode
import io.github.kmpfacelink.model.CandidateSelectionPolicy
import io.github.kmpfacelink.model.CandidateSelectionRoute
import io.github.kmpfacelink.model.CandidateSelectionSource
import io.github.kmpfacelink.model.CandidateSemanticIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalFaceLinkApi::class)
class CandidateObservationSessionTest {

    @Test
    fun estimatorAndAlternativePlanesStayDurableAcrossFrames() {
        val factory = CountingFactory(canonicalDescriptor())
        val session = CandidateObservationSession(listOf(factory))
        val estimator = mutableMapOf(BlendShape.CHEEK_PUFF to 0.25f)

        val first = session.observe(input(timestampMs = 10L, estimator = estimator))
        estimator[BlendShape.CHEEK_PUFF] = 0.9f
        val second = session.observe(input(timestampMs = 20L, estimator = estimator))

        assertEquals(0.25f, first.estimator[BlendShape.CHEEK_PUFF])
        assertEquals(1f, first.alternatives.single().value)
        assertEquals(2f, second.alternatives.single().value)
        assertEquals(1f, first.alternatives.single().value)
    }

    @Test
    fun opaqueFeaturePlanesAreNestedSnapshots() {
        val feature = mutableListOf(0.1f, 0.2f)
        val planes: MutableMap<String, List<Float>> = mutableMapOf("test:plane" to feature)
        val input = CandidateObservationInput(
            identity = CandidateInputIdentity(1u, 0u, 0, 0L),
            estimator = emptyMap(),
            landmarks = emptyList(),
            sourceImageWidth = 1,
            sourceImageHeight = 1,
            featurePlanes = planes,
        )

        feature[0] = 0.9f
        planes["test:plane"] = listOf(1f)

        assertEquals(listOf(0.1f, 0.2f), input.featurePlanes.getValue("test:plane"))
    }

    @Test
    fun allThreePlanesCarryOneInputIdentity() {
        val session = CandidateObservationSession(listOf(CountingFactory(canonicalDescriptor())))
        val receipt = session.observe(input(epoch = 7u, revision = 3u, timestampMs = 42L))

        assertIdentity(receipt.identity, epoch = 7u, revision = 3u, timestampMs = 42L)
        assertIdentity(receipt.alternatives.single().identity, epoch = 7u, revision = 3u, timestampMs = 42L)
        assertEquals(0.2f, receipt.selected[BlendShape.CHEEK_PUFF])
    }

    @Test
    fun absentAndNonFiniteProducerOutputsNeverBecomeZero() {
        val notReady = FixedFactory(
            descriptor = canonicalDescriptor("not-ready"),
            output = CandidateProducerOutput.absent(CandidateReadiness.NOT_READY, "calibration_pending"),
        )
        val nonFinite = FixedFactory(
            descriptor = canonicalDescriptor("non-finite"),
            output = CandidateProducerOutput.ready(Float.NaN),
        )
        val receipt = CandidateObservationSession(listOf(notReady, nonFinite)).observe(input())

        val absent = receipt.alternatives.associateBy { it.producerId }
        assertNull(absent.getValue("not-ready").value)
        assertEquals(CandidateReadiness.NOT_READY, absent.getValue("not-ready").readiness)
        assertNull(absent.getValue("non-finite").value)
        assertEquals(CandidateReadiness.NON_FINITE, absent.getValue("non-finite").readiness)
    }

    @Test
    fun defaultOffAndUnresolvedCandidatesCannotOverwriteCanonicalValues() {
        val canonical = FixedFactory(canonicalDescriptor(), CandidateProducerOutput.ready(0.9f))
        val unresolved = FixedFactory(
            descriptor = CandidateProducerDescriptor(
                producerId = "unresolved",
                implementationRevision = "test/v1",
                semanticId = "unresolved:test-signal",
                semanticIntent = CandidateSemanticIntent.UNRESOLVED,
                proposedTarget = null,
            ),
            output = CandidateProducerOutput.ready(0.8f),
        )
        val routeToUnresolved = CandidateSelectionPolicy(
            mapOf(
                BlendShape.CHEEK_PUFF to CandidateSelectionRoute("unresolved"),
            ),
        )
        val session = CandidateObservationSession(listOf(canonical, unresolved))

        val defaultOff = session.observe(input(timestampMs = 1L))
        val unresolvedSelected = session.observe(input(timestampMs = 2L), routeToUnresolved)

        assertEquals(0.2f, defaultOff.selected[BlendShape.CHEEK_PUFF])
        assertEquals(0.2f, unresolvedSelected.selected[BlendShape.CHEEK_PUFF])
    }

    @Test
    fun semanticIntentAndProposedTargetEnforceThreeContractClasses() {
        val canonical = canonicalDescriptor()
        val independent = CandidateProducerDescriptor(
            producerId = "independent",
            implementationRevision = "test/v1",
            semanticId = "independent:outer-cheek-expansion",
            semanticIntent = CandidateSemanticIntent.INDEPENDENT_OBSERVATION,
            proposedTarget = null,
        )
        val unresolved = CandidateProducerDescriptor(
            producerId = "unresolved",
            implementationRevision = "test/v1",
            semanticId = "unresolved:appearance-score",
            semanticIntent = CandidateSemanticIntent.UNRESOLVED,
            proposedTarget = null,
        )

        assertEquals(BlendShape.CHEEK_PUFF, canonical.proposedTarget)
        assertEquals(CandidateSemanticIntent.INDEPENDENT_OBSERVATION, independent.semanticIntent)
        assertNull(independent.proposedTarget)
        assertEquals(CandidateSemanticIntent.UNRESOLVED, unresolved.semanticIntent)
        assertNull(unresolved.proposedTarget)

        assertFailsWith<IllegalArgumentException> {
            CandidateProducerDescriptor(
                "bad-independent",
                "test/v1",
                "independent:bad",
                CandidateSemanticIntent.INDEPENDENT_OBSERVATION,
                BlendShape.CHEEK_PUFF,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateProducerDescriptor(
                "bad-unresolved",
                "test/v1",
                "unresolved:bad",
                CandidateSemanticIntent.UNRESOLVED,
                BlendShape.CHEEK_PUFF,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateProducerDescriptor(
                "bad-canonical",
                "test/v1",
                "canonical:bad",
                CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
                null,
            )
        }
    }

    @Test
    fun explicitQualifiedRouteRecordsAlternativeSelection() {
        val session = CandidateObservationSession(
            listOf(FixedFactory(canonicalDescriptor(), CandidateProducerOutput.ready(0.8f))),
        )
        val policy = CandidateSelectionPolicy(
            mapOf(
                BlendShape.CHEEK_PUFF to CandidateSelectionRoute(
                    producerId = PRODUCER_ID,
                    mode = CandidateSelectionMode.MAX_WITH_ESTIMATOR,
                ),
            ),
        )

        val receipt = session.observe(input(), policy)

        assertEquals(0.8f, receipt.selected[BlendShape.CHEEK_PUFF])
        val provenance = receipt.selectionProvenance.single { it.target == BlendShape.CHEEK_PUFF }
        assertEquals(CandidateSelectionSource.ALTERNATIVE, provenance.source)
        assertEquals(PRODUCER_ID, provenance.producerId)
    }

    @Test
    fun epochOrRevisionChangeDiscardsProducerState() {
        val factory = CountingFactory(canonicalDescriptor())
        val session = CandidateObservationSession(listOf(factory))

        val first = session.observe(input(epoch = 1u, timestampMs = 1L))
        val second = session.observe(input(epoch = 1u, timestampMs = 2L))
        val newEpoch = session.observe(input(epoch = 2u, timestampMs = 1L))
        val newRevision = session.observe(input(epoch = 2u, revision = 1u, timestampMs = 1L))

        assertEquals(1f, first.alternatives.single().value)
        assertEquals(2f, second.alternatives.single().value)
        assertEquals(1f, newEpoch.alternatives.single().value)
        assertEquals(1f, newRevision.alternatives.single().value)
        assertEquals(3, factory.createCount)
    }

    @Test
    fun duplicateAndOutOfOrderInputsAreAbsenceWithReasons() {
        val session = CandidateObservationSession(listOf(CountingFactory(canonicalDescriptor())))
        session.observe(input(timestampMs = 10L))

        val duplicate = session.observe(input(timestampMs = 10L)).alternatives.single()
        val outOfOrder = session.observe(input(timestampMs = 9L)).alternatives.single()

        assertNull(duplicate.value)
        assertEquals(CandidateReadiness.DUPLICATE_INPUT, duplicate.readiness)
        assertNull(outOfOrder.value)
        assertEquals(CandidateReadiness.OUT_OF_ORDER_INPUT, outOfOrder.readiness)
    }

    @Test
    fun resetRejectsAFactoryThatReusesProducerState() {
        val singleton = CandidateObservationProducer { CandidateProducerOutput.ready(0.5f) }
        val factory = object : CandidateObservationProducerFactory {
            override val descriptor = canonicalDescriptor()
            override fun create(): CandidateObservationProducer = singleton
        }
        val session = CandidateObservationSession(listOf(factory))

        assertFailsWith<IllegalArgumentException> { session.reset() }
    }

    private fun input(
        epoch: ULong = 1u,
        revision: ULong = 0u,
        timestampMs: Long = 0L,
        estimator: Map<BlendShape, Float> = mapOf(BlendShape.CHEEK_PUFF to 0.2f),
    ): CandidateObservationInput = CandidateObservationInput(
        identity = CandidateInputIdentity(epoch, revision, faceIndex = 0, timestampMs = timestampMs),
        estimator = estimator,
        landmarks = emptyList(),
        sourceImageWidth = 1,
        sourceImageHeight = 1,
    )

    private fun canonicalDescriptor(id: String = PRODUCER_ID): CandidateProducerDescriptor =
        CandidateProducerDescriptor(
            producerId = id,
            implementationRevision = "test/v1",
            semanticId = "canonical:cheekPuff",
            semanticIntent = CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE,
            proposedTarget = BlendShape.CHEEK_PUFF,
        )

    private fun assertIdentity(
        identity: CandidateInputIdentity,
        epoch: ULong,
        revision: ULong,
        timestampMs: Long,
    ) {
        assertEquals(epoch, identity.contextEpochId)
        assertEquals(revision, identity.processingStateRevision)
        assertEquals(0, identity.faceIndex)
        assertEquals(timestampMs, identity.timestampMs)
    }

    private class FixedFactory(
        override val descriptor: CandidateProducerDescriptor,
        private val output: CandidateProducerOutput,
    ) : CandidateObservationProducerFactory {
        override fun create(): CandidateObservationProducer = CandidateObservationProducer { output }
    }

    private class CountingFactory(
        override val descriptor: CandidateProducerDescriptor,
    ) : CandidateObservationProducerFactory {
        var createCount: Int = 0

        override fun create(): CandidateObservationProducer {
            createCount++
            var value = 0f
            return CandidateObservationProducer {
                value += 1f
                CandidateProducerOutput.ready(value)
            }
        }
    }

    private companion object {
        const val PRODUCER_ID = "candidate:test"
    }
}
