package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateReadiness

/** Frozen threshold grid and causal dwell evaluator for the C2 V1 comparison. */
@ExperimentalFaceLinkApi
public object CheekSignalThresholdPolicy {
    public val onThresholds: List<Float> = listOf(0.05f, 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.40f)

    public fun create(onThreshold: Float): CheekSignalThresholdSession {
        require(onThreshold in onThresholds) { "onThreshold must belong to the frozen C2 V1 grid" }
        return CheekSignalThresholdSession(onThreshold)
    }
}

/** One immutable threshold-state receipt. Availability is separate from the retained active state. */
@ExperimentalFaceLinkApi
public class CheekSignalThresholdObservation(
    public val identity: CandidateInputIdentity,
    public val onThreshold: Float,
    public val offThreshold: Float,
    public val active: Boolean,
    public val available: Boolean,
    public val readiness: CandidateReadiness,
)

/** Stateful causal evaluator. Create one instance per producer and on-threshold. */
@ExperimentalFaceLinkApi
public class CheekSignalThresholdSession internal constructor(
    public val onThreshold: Float,
) {
    public val offThreshold: Float = OFF_RATIO * onThreshold
    private var epochKey: Pair<ULong, ULong>? = null
    private var lastTimestampMs: Long? = null
    private var activationStartMs: Long? = null
    private var releaseStartMs: Long? = null
    private var active: Boolean = false

    public fun observe(
        identity: CandidateInputIdentity,
        value: Float?,
        readiness: CandidateReadiness,
    ): CheekSignalThresholdObservation {
        rotateIfNeeded(identity)
        if (timestampBreaksDwell(identity.timestampMs)) clearDwell()
        lastTimestampMs = identity.timestampMs

        val available = readiness == CandidateReadiness.READY && value?.isFinite() == true
        if (!available) {
            clearDwell()
            return receipt(identity, false, readiness)
        }
        update(checkNotNull(value), identity.timestampMs)
        return receipt(identity, true, CandidateReadiness.READY)
    }

    public fun reset() {
        epochKey = null
        lastTimestampMs = null
        active = false
        clearDwell()
    }

    private fun rotateIfNeeded(identity: CandidateInputIdentity) {
        val incoming = identity.contextEpochId to identity.processingStateRevision
        if (incoming == epochKey) return
        epochKey = incoming
        lastTimestampMs = null
        active = false
        clearDwell()
    }

    private fun timestampBreaksDwell(timestampMs: Long): Boolean {
        val previous = lastTimestampMs ?: return false
        return timestampMs <= previous || timestampMs - previous > MAX_GAP_MS
    }

    private fun update(value: Float, timestampMs: Long) {
        if (!active) {
            releaseStartMs = null
            if (value >= onThreshold) {
                val start = activationStartMs ?: timestampMs.also { activationStartMs = it }
                if (timestampMs - start >= ACTIVATION_DWELL_MS) {
                    active = true
                    clearDwell()
                }
            } else {
                activationStartMs = null
            }
            return
        }

        activationStartMs = null
        if (value < offThreshold) {
            val start = releaseStartMs ?: timestampMs.also { releaseStartMs = it }
            if (timestampMs - start >= RELEASE_DWELL_MS) {
                active = false
                clearDwell()
            }
        } else {
            releaseStartMs = null
        }
    }

    private fun clearDwell() {
        activationStartMs = null
        releaseStartMs = null
    }

    private fun receipt(
        identity: CandidateInputIdentity,
        available: Boolean,
        readiness: CandidateReadiness,
    ): CheekSignalThresholdObservation = CheekSignalThresholdObservation(
        identity = identity,
        onThreshold = onThreshold,
        offThreshold = offThreshold,
        active = active,
        available = available,
        readiness = readiness,
    )

    private companion object {
        const val OFF_RATIO: Float = 0.8f
        const val ACTIVATION_DWELL_MS: Long = 350L
        const val RELEASE_DWELL_MS: Long = 200L
        const val MAX_GAP_MS: Long = 250L
    }
}
