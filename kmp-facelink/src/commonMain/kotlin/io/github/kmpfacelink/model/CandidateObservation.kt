package io.github.kmpfacelink.model

import io.github.kmpfacelink.ExperimentalFaceLinkApi

/** Stable identity shared by estimator, alternative, and selected planes for one face input. */
@ExperimentalFaceLinkApi
public class CandidateInputIdentity(
    public val contextEpochId: ULong,
    public val processingStateRevision: ULong,
    public val faceIndex: Int,
    public val timestampMs: Long,
) {
    init {
        require(faceIndex >= 0) { "faceIndex must be non-negative" }
        require(timestampMs >= 0L) { "timestampMs must be non-negative" }
    }
}

/** Meaning a producer claims for its observation, independently of how it is implemented. */
@ExperimentalFaceLinkApi
public enum class CandidateSemanticIntent {
    CANONICAL_ALTERNATIVE_CANDIDATE,
    INDEPENDENT_OBSERVATION,
    UNRESOLVED,
}

/** Why a producer did or did not emit a numeric observation for an input. */
@ExperimentalFaceLinkApi
public enum class CandidateReadiness {
    READY,
    UNAVAILABLE,
    NOT_READY,
    REJECTED,
    MISSING_INPUT,
    RESET,
    DUPLICATE_INPUT,
    OUT_OF_ORDER_INPUT,
    NON_FINITE,
}

/**
 * Stable implementation and semantic identity for one candidate producer.
 *
 * A canonical alternative names [proposedTarget]. Independent and unresolved observations keep it
 * null; their distinct [semanticIntent] values prevent "not canonical" from being mistaken for one
 * already-qualified independent meaning.
 */
@ExperimentalFaceLinkApi
public class CandidateProducerDescriptor(
    public val producerId: String,
    public val implementationRevision: String,
    public val semanticId: String,
    public val semanticIntent: CandidateSemanticIntent,
    public val proposedTarget: BlendShape?,
) {
    init {
        require(producerId.isNotBlank()) { "producerId must not be blank" }
        require(implementationRevision.isNotBlank()) { "implementationRevision must not be blank" }
        require(semanticId.isNotBlank()) { "semanticId must not be blank" }
        require(
            semanticIntent == CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE || proposedTarget == null,
        ) { "only a canonical alternative candidate may name a proposed target" }
        require(
            semanticIntent != CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE || proposedTarget != null,
        ) { "a canonical alternative candidate must name its proposed target" }
    }
}

/** One producer invocation result before the session adds identity and descriptor fields. */
@ExperimentalFaceLinkApi
public class CandidateProducerOutput(
    public val value: Float?,
    public val readiness: CandidateReadiness,
    public val reason: String?,
) {
    init {
        require(readiness != CandidateReadiness.READY || value != null) {
            "a ready producer output must carry a value"
        }
        require(readiness == CandidateReadiness.READY || value == null) {
            "a non-ready producer output must not carry a numeric value"
        }
        require(readiness == CandidateReadiness.READY || !reason.isNullOrBlank()) {
            "a non-ready producer output must carry a reason"
        }
    }

    public companion object {
        public fun ready(value: Float): CandidateProducerOutput = CandidateProducerOutput(
            value = value,
            readiness = CandidateReadiness.READY,
            reason = null,
        )

        public fun absent(readiness: CandidateReadiness, reason: String): CandidateProducerOutput {
            require(readiness != CandidateReadiness.READY) { "absent output cannot be READY" }
            return CandidateProducerOutput(value = null, readiness = readiness, reason = reason)
        }
    }
}

/** Immutable input snapshot passed to every producer registered for one observation session. */
@ExperimentalFaceLinkApi
@Suppress("LongParameterList") // Private snapshot constructor preserves two public binary signatures.
public class CandidateObservationInput private constructor(
    public val identity: CandidateInputIdentity,
    estimator: BlendShapeData,
    landmarks: List<FaceLandmark>,
    public val sourceImageWidth: Int,
    public val sourceImageHeight: Int,
    featurePlanes: Map<String, List<Float>>,
    @Suppress("UNUSED_PARAMETER") snapshotMarker: Boolean,
) {
    /** Preserves the original constructor and its binary signature. */
    public constructor(
        identity: CandidateInputIdentity,
        estimator: BlendShapeData,
        landmarks: List<FaceLandmark>,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
    ) : this(
        identity,
        estimator,
        landmarks,
        sourceImageWidth,
        sourceImageHeight,
        emptyMap(),
        true,
    )

    /** Additive opaque preprocessed feature planes for default-off research producers. */
    public constructor(
        identity: CandidateInputIdentity,
        estimator: BlendShapeData,
        landmarks: List<FaceLandmark>,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
        featurePlanes: Map<String, List<Float>>,
    ) : this(
        identity,
        estimator,
        landmarks,
        sourceImageWidth,
        sourceImageHeight,
        featurePlanes,
        true,
    )

    public val estimator: BlendShapeData = HashMap(estimator)
    public val landmarks: List<FaceLandmark> = landmarks.toList()
    public val featurePlanes: Map<String, List<Float>> =
        featurePlanes.mapValuesTo(HashMap()) { (_, values) -> values.toList() }

    init {
        require(sourceImageWidth > 0) { "sourceImageWidth must be positive" }
        require(sourceImageHeight > 0) { "sourceImageHeight must be positive" }
    }
}

/** Per-session producer state. A factory must return a fresh instance for every reset epoch. */
@ExperimentalFaceLinkApi
public fun interface CandidateObservationProducer {
    public fun observe(input: CandidateObservationInput): CandidateProducerOutput
}

/** Registration plus state factory. The session, not the caller, owns the created state. */
@ExperimentalFaceLinkApi
public interface CandidateObservationProducerFactory {
    public val descriptor: CandidateProducerDescriptor

    public fun create(): CandidateObservationProducer
}

/** Durable alternative-plane observation for one input identity. */
@ExperimentalFaceLinkApi
@Suppress("LongParameterList") // Identity, implementation, semantics, and absence state are one atomic receipt.
public class CandidateObservation(
    public val identity: CandidateInputIdentity,
    public val producerId: String,
    public val implementationRevision: String,
    public val semanticId: String,
    public val semanticIntent: CandidateSemanticIntent,
    public val proposedTarget: BlendShape?,
    public val value: Float?,
    public val readiness: CandidateReadiness,
    public val reason: String?,
)

/** Which plane supplied a selected canonical value. */
@ExperimentalFaceLinkApi
public enum class CandidateSelectionSource {
    ESTIMATOR,
    ALTERNATIVE,
    ABSENT,
}

/** Explicit provenance for one canonical target after selection. */
@ExperimentalFaceLinkApi
public class CandidateSelectionProvenance(
    public val target: BlendShape,
    public val source: CandidateSelectionSource,
    public val producerId: String?,
    public val mode: CandidateSelectionMode?,
    public val reason: String?,
)

/** Explicit opt-in routes. An empty policy leaves the estimator map unchanged. */
@ExperimentalFaceLinkApi
public enum class CandidateSelectionMode {
    REPLACE,
    MAX_WITH_ESTIMATOR,
    WEIGHTED_WITH_ESTIMATOR,
}

/** One explicit producer route and its estimator-combination rule. */
@ExperimentalFaceLinkApi
public class CandidateSelectionRoute(
    public val producerId: String,
    public val mode: CandidateSelectionMode = CandidateSelectionMode.REPLACE,
    public val alternativeWeight: Float = 1f,
) {
    init {
        require(producerId.isNotBlank()) { "producerId must not be blank" }
        require(alternativeWeight in 0f..1f) { "alternativeWeight must be in [0, 1]" }
    }
}

/** Explicit opt-in routes. An empty policy leaves the estimator map unchanged. */
@ExperimentalFaceLinkApi
public class CandidateSelectionPolicy(
    routes: Map<BlendShape, CandidateSelectionRoute> = emptyMap(),
) {
    public val routes: Map<BlendShape, CandidateSelectionRoute> = HashMap(routes)

    public companion object {
        public val DEFAULT_OFF: CandidateSelectionPolicy = CandidateSelectionPolicy()
    }
}

/** Durable estimator, alternatives, and selected receipt for one input identity. */
@ExperimentalFaceLinkApi
public class CandidateObservationReceipt(
    public val identity: CandidateInputIdentity,
    estimator: BlendShapeData,
    alternatives: List<CandidateObservation>,
    selected: BlendShapeData,
    selectionProvenance: List<CandidateSelectionProvenance>,
) {
    public val estimator: BlendShapeData = HashMap(estimator)
    public val alternatives: List<CandidateObservation> = alternatives.toList()
    public val selected: BlendShapeData = HashMap(selected)
    public val selectionProvenance: List<CandidateSelectionProvenance> = selectionProvenance.toList()
}

/**
 * Stateful observation seam shared by live processing and replay.
 *
 * Producer state is created and owned here. A context epoch or processing revision change discards
 * every producer instance before the new input is observed. Selection is explicit and default-off.
 */
@ExperimentalFaceLinkApi
public class CandidateObservationSession(
    producerFactories: List<CandidateObservationProducerFactory> = emptyList(),
) {
    private val factories: List<CandidateObservationProducerFactory> = producerFactories.toList()
    private var producers: List<CandidateObservationProducer> = createProducers()
    private var epochKey: Pair<ULong, ULong>? = null
    private val lastTimestampByFace: MutableMap<Int, Long> = mutableMapOf()

    init {
        val ids = factories.map { it.descriptor.producerId }
        require(ids.size == ids.toSet().size) { "candidate producer IDs must be unique" }
    }

    /** Observes one input, then applies only the explicitly supplied selection routes. */
    public fun observe(
        input: CandidateObservationInput,
        selectionPolicy: CandidateSelectionPolicy = CandidateSelectionPolicy.DEFAULT_OFF,
    ): CandidateObservationReceipt {
        rotateStateIfNeeded(input.identity)
        val orderingFailure = orderingFailure(input.identity)
        val alternatives = factories.mapIndexed { index, factory ->
            val output = orderingFailure ?: producers[index].observe(input)
            observation(input.identity, factory.descriptor, output)
        }
        return select(input, alternatives, selectionPolicy)
    }

    /** Explicitly discards all temporal producer state and ordering history. */
    public fun reset() {
        replaceProducers()
        epochKey = null
        lastTimestampByFace.clear()
    }

    private fun rotateStateIfNeeded(identity: CandidateInputIdentity) {
        val incoming = identity.contextEpochId to identity.processingStateRevision
        if (epochKey == incoming) return
        if (epochKey != null) replaceProducers()
        epochKey = incoming
        lastTimestampByFace.clear()
    }

    private fun orderingFailure(identity: CandidateInputIdentity): CandidateProducerOutput? {
        val previous = lastTimestampByFace[identity.faceIndex]
        if (previous == identity.timestampMs) {
            return CandidateProducerOutput.absent(CandidateReadiness.DUPLICATE_INPUT, "duplicate_timestamp")
        }
        if (previous != null && identity.timestampMs < previous) {
            return CandidateProducerOutput.absent(CandidateReadiness.OUT_OF_ORDER_INPUT, "timestamp_regressed")
        }
        lastTimestampByFace[identity.faceIndex] = identity.timestampMs
        return null
    }

    private fun replaceProducers() {
        val previous = producers
        val replacements = createProducers()
        previous.indices.forEach { index ->
            require(previous[index] !== replacements[index]) {
                "producer factory ${factories[index].descriptor.producerId} reused state across reset"
            }
        }
        producers = replacements
    }

    private fun createProducers(): List<CandidateObservationProducer> = factories.map { it.create() }

    private fun observation(
        identity: CandidateInputIdentity,
        descriptor: CandidateProducerDescriptor,
        output: CandidateProducerOutput,
    ): CandidateObservation {
        val finite = output.value?.isFinite() != false
        val readiness = if (finite) output.readiness else CandidateReadiness.NON_FINITE
        val reason = if (finite) output.reason else "producer_returned_non_finite"
        return CandidateObservation(
            identity = identity,
            producerId = descriptor.producerId,
            implementationRevision = descriptor.implementationRevision,
            semanticId = descriptor.semanticId,
            semanticIntent = descriptor.semanticIntent,
            proposedTarget = descriptor.proposedTarget,
            value = output.value?.takeIf { finite },
            readiness = readiness,
            reason = reason,
        )
    }

    private fun select(
        input: CandidateObservationInput,
        alternatives: List<CandidateObservation>,
        policy: CandidateSelectionPolicy,
    ): CandidateObservationReceipt {
        val selected = HashMap(input.estimator)
        val provenance = input.estimator.keys.associateWith { target ->
            CandidateSelectionProvenance(target, CandidateSelectionSource.ESTIMATOR, null, null, null)
        }.toMutableMap()
        for ((target, route) in policy.routes) {
            applyRoute(target, route, alternatives, selected, provenance)
        }
        return CandidateObservationReceipt(
            identity = input.identity,
            estimator = input.estimator,
            alternatives = alternatives,
            selected = selected,
            selectionProvenance = provenance.values.toList(),
        )
    }

    private fun applyRoute(
        target: BlendShape,
        route: CandidateSelectionRoute,
        alternatives: List<CandidateObservation>,
        selected: MutableMap<BlendShape, Float>,
        provenance: MutableMap<BlendShape, CandidateSelectionProvenance>,
    ) {
        val candidate = alternatives.firstOrNull { it.producerId == route.producerId }
        val selectable = candidate?.semanticIntent == CandidateSemanticIntent.CANONICAL_ALTERNATIVE_CANDIDATE &&
            candidate.proposedTarget == target && candidate.readiness == CandidateReadiness.READY &&
            candidate.value != null
        if (selectable) {
            val candidateValue = checkNotNull(candidate.value)
            val estimated = selected[target]
            selected[target] = combine(candidateValue, estimated, route).coerceIn(0f, 1f)
            provenance[target] = CandidateSelectionProvenance(
                target,
                CandidateSelectionSource.ALTERNATIVE,
                route.producerId,
                route.mode,
                null,
            )
            return
        }
        val source = if (target in selected) CandidateSelectionSource.ESTIMATOR else CandidateSelectionSource.ABSENT
        provenance[target] = CandidateSelectionProvenance(
            target = target,
            source = source,
            producerId = null,
            mode = route.mode,
            reason = candidate?.reason ?: "candidate_not_selectable",
        )
    }

    private fun combine(
        candidate: Float,
        estimated: Float?,
        route: CandidateSelectionRoute,
    ): Float {
        if (estimated == null) return candidate
        return when (route.mode) {
            CandidateSelectionMode.REPLACE -> candidate
            CandidateSelectionMode.MAX_WITH_ESTIMATOR -> maxOf(candidate, estimated)
            CandidateSelectionMode.WEIGHTED_WITH_ESTIMATOR ->
                route.alternativeWeight * candidate + (1f - route.alternativeWeight) * estimated
        }
    }
}
