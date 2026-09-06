package io.github.kmpfacelink.capture

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateObservationReceipt
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateSelectionPolicy
import io.github.kmpfacelink.model.FaceLandmark

/**
 * Internal-capture replay adapter for the core candidate-observation seam.
 *
 * Capture remains raw. A replay reader maps one persisted input into [CandidateObservationInput]
 * and this adapter invokes the same core [CandidateObservationSession.observe] function as live
 * Android processing. No candidate route is enabled unless the caller supplies one explicitly.
 */
@ExperimentalFaceLinkApi
public class CandidateObservationReplaySession(
    producerFactories: List<CandidateObservationProducerFactory> = emptyList(),
    private val selectionPolicy: CandidateSelectionPolicy = CandidateSelectionPolicy.DEFAULT_OFF,
) {
    private val observationSession: CandidateObservationSession = CandidateObservationSession(producerFactories)

    public fun observe(input: CandidateObservationInput): CandidateObservationReceipt =
        observationSession.observe(input, selectionPolicy)

    /**
     * Replays one persisted raw result through the core observation seam used by live processing.
     *
     * [blendshapeNames] is the authoritative category-index order stored in the take manifest.
     * The caller supplies [identity] because run/take/frame identity remains outside the core seam.
     * A record without a usable face becomes empty estimator/landmark input; its fixed-stride zero
     * fill is never interpreted as an observed numeric zero.
     */
    public fun observe(
        record: InferenceRecord,
        blendshapeNames: List<String>,
        identity: CandidateInputIdentity,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
    ): CandidateObservationReceipt = observe(
        record = record,
        blendshapeNames = blendshapeNames,
        identity = identity,
        sourceImageWidth = sourceImageWidth,
        sourceImageHeight = sourceImageHeight,
        featurePlanes = emptyMap(),
    )

    /** Replays raw inference identity with additive opaque preprocessed feature planes. */
    @Suppress("LongParameterList") // Raw identity plus one additive opaque feature carrier.
    public fun observe(
        record: InferenceRecord,
        blendshapeNames: List<String>,
        identity: CandidateInputIdentity,
        sourceImageWidth: Int,
        sourceImageHeight: Int,
        featurePlanes: Map<String, List<Float>>,
    ): CandidateObservationReceipt {
        val usable = record.hasFace && !record.inferenceFailed
        val estimator = if (usable) {
            estimatorFrom(record.blendshapes, blendshapeNames)
        } else {
            emptyMap()
        }
        val landmarks = if (usable) landmarksFrom(record.landmarks) else emptyList()
        return observe(
            CandidateObservationInput(
                identity = identity,
                estimator = estimator,
                landmarks = landmarks,
                sourceImageWidth = sourceImageWidth,
                sourceImageHeight = sourceImageHeight,
                featurePlanes = featurePlanes,
            ),
        )
    }

    public fun reset() {
        observationSession.reset()
    }

    private fun estimatorFrom(
        values: FloatArray?,
        names: List<String>,
    ): Map<BlendShape, Float> {
        if (values == null) return emptyMap()
        require(names.size == values.size) {
            "blendshape names (${names.size}) must match persisted values (${values.size})"
        }
        require(names.size == names.toSet().size) { "blendshape names must be unique" }
        return buildMap {
            names.forEachIndexed { index, name ->
                if (name != "_neutral") {
                    BlendShape.fromArKitName(name)?.let { shape -> put(shape, values[index]) }
                }
            }
        }
    }

    private fun landmarksFrom(values: FloatArray?): List<FaceLandmark> {
        if (values == null) return emptyList()
        return List(InferenceRecordFormat.LANDMARK_COUNT) { index ->
            val offset = index * 3
            FaceLandmark(
                x = values[offset],
                y = values[offset + 1],
                relativeDepth = values[offset + 2],
            )
        }
    }
}
