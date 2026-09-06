package io.github.kmpfacelink.model

/**
 * A value some producer proposed for a frame, before anything decided whether to use it.
 *
 * The enhancer used to compute a landmark-derived value, combine it with the estimator's value on
 * the spot, and return one map. Both inputs were gone by the time anything downstream saw the
 * result, so a derived value arrived wearing the same face as a measurement. This type is the
 * proposal on its own, kept separate from the decision about it.
 *
 * @property producerId which producer proposed this, stable across frames.
 * @property value the proposed value.
 * @property proposedTarget the canonical shape this is a candidate for, or `null` when the producer
 *   reports something the ARKit-compatible vocabulary has no equivalent for. A `null` target is not
 *   a missing field: it is the difference between "another way to get `cheekPuff`" and "a signal
 *   that is not `cheekPuff` at all", and the two lead to different designs.
 */
public class AlternativeSignalObservation(
    public val producerId: String,
    public val value: Float,
    public val proposedTarget: BlendShape?,
)

/**
 * The two planes a frame holds before the SDK settles on one set of values.
 *
 * Keeping them apart is what lets a reader answer "did the estimator report this, or did we work it
 * out?" — a question the merged map cannot answer, and the question the cheek work turns on.
 *
 * A third plane, the values actually emitted, is the ordinary [BlendShapeData] the tracker returns.
 * Which candidate wins is a separate decision; see [BlendShapeRoute].
 *
 * @property estimator exactly what the platform tracker reported, with nothing added or removed.
 * @property alternatives every proposal made for this frame, including ones that were not used.
 */
public class FaceSignalCandidates(
    public val estimator: BlendShapeData,
    public val alternatives: List<AlternativeSignalObservation>,
)
