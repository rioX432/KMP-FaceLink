package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShapeData

internal data class H2TakeEpoch(val sequence: Long, val blockId: String)

internal data class H2TakeRouteObservation(
    val epoch: H2TakeEpoch,
    val elapsedMillis: Long,
    val primary: H2PolicyRouteState,
    val comparator: H2PolicyRouteState,
) {
    val endpointAvailable: Boolean
        get() = elapsedMillis > H2C3bPreprocessor.CALIBRATION_END_MILLIS &&
            primary.available && comparator.available
}

/**
 * V2 take-local orchestration. B01 never enters this type. Every B02-B22 begin resets C3b, the
 * comparator calibration, and both temporal policies. No endpoint exists through 3000 ms.
 */
internal class H2V2TakeRuntime(
    private val primaryCandidate: H2C3bCandidate,
    private val comparatorCandidate: H2C2C1Candidate,
    private val c3bPreprocessor: H2C3bPreprocessor = H2C3bPreprocessor(),
) {
    private val primaryPolicy = H2FrozenPolicySession(H2ConfirmationContract.primary.policy)
    private val comparatorPolicy = H2FrozenPolicySession(H2ConfirmationContract.comparator.policy)
    private var nextEpoch = 0L
    private var activeEpoch: H2TakeEpoch? = null
    private var lastElapsedMillis: Long? = null
    private var failed = false

    fun begin(blockId: String): H2TakeEpoch {
        val block = H2ConfirmationContract.blocks.single { it.id == blockId }
        require(block.kind != H2BlockKind.STARTUP_READINESS) { "B01 is availability-only" }
        check(activeEpoch == null) { "an H2 take epoch is already open" }
        c3bPreprocessor.reset()
        comparatorCandidate.reset()
        primaryPolicy.reset()
        comparatorPolicy.reset()
        lastElapsedMillis = null
        failed = false
        return H2TakeEpoch(nextEpoch++, blockId).also { activeEpoch = it }
    }

    fun observe(
        epoch: H2TakeEpoch,
        elapsedMillis: Long,
        image: H2RgbaLandmarkFrame,
        estimator: BlendShapeData,
    ): H2TakeRouteObservation? {
        require(activeEpoch == epoch) { "stale or wrong H2 take epoch" }
        require(elapsedMillis >= 0)
        check(!failed) { "H2 take epoch is failed" }
        val previousElapsed = lastElapsedMillis
        if (previousElapsed != null && elapsedMillis <= previousElapsed) {
            failed = true
            throw IllegalArgumentException("H2 take time must be strictly increasing")
        }
        lastElapsedMillis = elapsedMillis

        val comparatorFrame = H2CandidateFrame(
            timestampMillis = elapsedMillis,
            estimator = estimator,
            landmarks = image.landmarks,
            sourceWidth = image.width,
            sourceHeight = image.height,
            c3bFeature384 = null,
        )
        val comparatorValue = comparatorCandidate.observe(comparatorFrame)

        if (elapsedMillis <= H2C3bPreprocessor.CALIBRATION_BURN_IN_MILLIS) return null
        if (elapsedMillis <= H2C3bPreprocessor.CALIBRATION_END_MILLIS) {
            c3bPreprocessor.observeCalibration(image, elapsedMillis)
            return null
        }

        val feature = c3bPreprocessor.observeEvaluation(image, elapsedMillis)
        val primaryValue = when (feature.readiness) {
            H2FeatureReadiness.READY -> primaryCandidate.observe(comparatorFrame.copy(c3bFeature384 = feature.values))
            H2FeatureReadiness.CALIBRATING -> absentPrimary(
                elapsedMillis,
                H2RuntimeReadiness.NOT_READY,
                "c3b_not_ready",
            )
            H2FeatureReadiness.REJECTED -> absentPrimary(
                elapsedMillis,
                H2RuntimeReadiness.REJECTED,
                feature.reason ?: "c3b_preprocessor_rejected",
            )
        }

        val bothReady = primaryValue.readiness == H2RuntimeReadiness.READY &&
            comparatorValue.readiness == H2RuntimeReadiness.READY
        val primaryForPolicy = if (bothReady) {
            primaryValue
        } else {
            primaryValue.asUnavailable("paired_route_unavailable")
        }
        val comparatorForPolicy = if (bothReady) {
            comparatorValue
        } else {
            comparatorValue.asUnavailable("paired_route_unavailable")
        }
        return H2TakeRouteObservation(
            epoch,
            elapsedMillis,
            primaryPolicy.observe(primaryForPolicy),
            comparatorPolicy.observe(comparatorForPolicy),
        )
    }

    fun close(epoch: H2TakeEpoch) {
        require(activeEpoch == epoch) { "stale or wrong H2 take epoch" }
        activeEpoch = null
        lastElapsedMillis = null
    }

    private fun absentPrimary(timestamp: Long, readiness: H2RuntimeReadiness, reason: String) = H2CandidateValue(
        H2CandidateRole.PRIMARY,
        H2ConfirmationContract.primary.producerId,
        timestamp,
        null,
        readiness,
        reason,
    )

    private fun H2CandidateValue.asUnavailable(reason: String): H2CandidateValue = if (value == null) {
        this
    } else {
        copy(value = null, readiness = H2RuntimeReadiness.NOT_READY, reason = reason)
    }
}
