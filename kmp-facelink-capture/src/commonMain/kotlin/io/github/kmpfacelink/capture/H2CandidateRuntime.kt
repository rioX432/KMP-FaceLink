@file:Suppress("MagicNumber", "TooGenericExceptionCaught")
@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.FaceLandmark
import io.github.kmpfacelink.research.FrozenC2C1Input
import io.github.kmpfacelink.research.FrozenC2C1ResearchAdapter
import io.github.kmpfacelink.research.FrozenResearchReadiness
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.exp
import kotlin.math.sqrt

internal enum class H2RuntimeReadiness { READY, NOT_READY, UNAVAILABLE, REJECTED }

internal data class H2CandidateValue(
    val role: H2CandidateRole,
    val producerId: String,
    val timestampMillis: Long,
    val value: Float?,
    val readiness: H2RuntimeReadiness,
    val reason: String?,
) {
    init {
        require((readiness == H2RuntimeReadiness.READY) == (value != null))
        require(value == null || value.isFinite() && value in 0f..1f)
        require(readiness == H2RuntimeReadiness.READY || !reason.isNullOrBlank())
    }
}

internal data class H2CandidateFrame(
    val timestampMillis: Long,
    val estimator: BlendShapeData,
    val landmarks: List<FaceLandmark>,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val c3bFeature384: List<Float>?,
) {
    init {
        require(timestampMillis >= 0)
        require(sourceWidth > 0 && sourceHeight > 0)
    }
}

internal fun interface H2ModelByteLoader {
    fun load(): ByteArray?
}

internal fun interface H2Sha256 {
    fun digest(bytes: ByteArray): String
}

internal enum class H2ModelAvailability { AVAILABLE, ABSENT, HASH_MISMATCH, LOAD_FAILED, PARSE_FAILED }

/** One retained buffer is hashed first and then strictly parsed; failed states retain no model. */
internal class H2StrictC3bModelProvider(
    expectedSha256: String,
    loader: H2ModelByteLoader,
    sha256: H2Sha256,
) {
    val availability: H2ModelAvailability
    val reason: String?
    private val model: H2C3bModel?

    init {
        require(expectedSha256.isLowercaseSha256())
        var loadFailed = false
        val retained = try {
            loader.load()?.copyOf()
        } catch (_: Exception) {
            loadFailed = true
            null
        }
        when {
            loadFailed -> {
                availability = H2ModelAvailability.LOAD_FAILED
                reason = "c3b_model_load_failed"
                model = null
            }
            retained == null -> {
                availability = H2ModelAvailability.ABSENT
                reason = "c3b_model_absent"
                model = null
            }
            sha256.digest(retained) != expectedSha256 -> {
                availability = H2ModelAvailability.HASH_MISMATCH
                reason = "c3b_model_hash_mismatch"
                model = null
            }
            else -> {
                val parsed = try {
                    parseC3bModel(retained)
                } catch (_: Exception) {
                    null
                }
                availability = if (parsed == null) H2ModelAvailability.PARSE_FAILED else H2ModelAvailability.AVAILABLE
                reason = if (parsed == null) "c3b_model_parse_failed" else null
                model = parsed
            }
        }
    }

    fun score(features: List<Float>): Float? = model?.score(features)
}

internal class H2C3bCandidate(private val provider: H2StrictC3bModelProvider) {
    fun observe(frame: H2CandidateFrame): H2CandidateValue {
        if (provider.availability != H2ModelAvailability.AVAILABLE) {
            return absent(frame, H2RuntimeReadiness.UNAVAILABLE, checkNotNull(provider.reason))
        }
        val features = frame.c3bFeature384
            ?: return absent(frame, H2RuntimeReadiness.NOT_READY, "c3b_feature_plane_not_ready")
        if (features.size != FEATURE_COUNT || features.any { !it.isFinite() }) {
            return absent(frame, H2RuntimeReadiness.REJECTED, "c3b_feature_plane_invalid")
        }
        val value = try {
            provider.score(features)
        } catch (_: Exception) {
            null
        }
        if (value == null || !value.isFinite() || value !in 0f..1f) {
            return absent(frame, H2RuntimeReadiness.REJECTED, "c3b_model_output_invalid")
        }
        return H2CandidateValue(
            H2CandidateRole.PRIMARY,
            H2ConfirmationContract.primary.producerId,
            frame.timestampMillis,
            value,
            H2RuntimeReadiness.READY,
            null,
        )
    }

    private fun absent(frame: H2CandidateFrame, readiness: H2RuntimeReadiness, reason: String): H2CandidateValue =
        H2CandidateValue(
            H2CandidateRole.PRIMARY,
            H2ConfirmationContract.primary.producerId,
            frame.timestampMillis,
            null,
            readiness,
            reason,
        )
}

/**
 * Exact frozen C2 lip-gated value multiplied by the frozen C1 contour decision.
 * The adapter finalizes its baseline after 1250 ms and continues feeding C1 as a take-local
 * warmup, but the H2-facing comparator cannot become READY until after the shared 3000 ms gate.
 */
internal class H2C2C1Candidate(private val adapter: FrozenC2C1ResearchAdapter = FrozenC2C1ResearchAdapter()) {
    fun observe(frame: H2CandidateFrame): H2CandidateValue {
        val output = adapter.observe(
            FrozenC2C1Input(
                timestampMillis = frame.timestampMillis,
                estimator = frame.estimator,
                landmarks = frame.landmarks,
                sourceWidth = frame.sourceWidth,
                sourceHeight = frame.sourceHeight,
            ),
        )
        val readiness = when {
            output.readiness == FrozenResearchReadiness.READY &&
                frame.timestampMillis <= H2C3bPreprocessor.CALIBRATION_END_MILLIS -> H2RuntimeReadiness.NOT_READY
            output.readiness == FrozenResearchReadiness.READY -> H2RuntimeReadiness.READY
            output.readiness == FrozenResearchReadiness.NOT_READY -> H2RuntimeReadiness.NOT_READY
            else -> H2RuntimeReadiness.REJECTED
        }
        val routeValue = output.value.takeIf { readiness == H2RuntimeReadiness.READY }
        val reason = if (output.readiness == FrozenResearchReadiness.READY &&
            readiness == H2RuntimeReadiness.NOT_READY
        ) {
            "c2_c1_route_warmup_until_3000ms_exclusive"
        } else {
            output.reason
        }
        return H2CandidateValue(
            H2CandidateRole.COMPARATOR,
            FrozenC2C1ResearchAdapter.PRODUCER_ID,
            output.timestampMillis,
            routeValue,
            readiness,
            reason,
        )
    }

    fun reset() = adapter.reset()
}

/** Primary and comparator remain separate by type; neither can rescue, fuse, or select the other. */
internal class H2PairedCandidateRuntime(
    private val primary: H2C3bCandidate,
    private val comparator: H2C2C1Candidate,
) {
    fun observe(frame: H2CandidateFrame): H2PairedCandidateObservation {
        val primaryValue = primary.observe(frame)
        val comparatorValue = comparator.observe(frame)
        check(primaryValue.timestampMillis == comparatorValue.timestampMillis)
        return H2PairedCandidateObservation(frame.timestampMillis, primaryValue, comparatorValue)
    }
}

internal data class H2PairedCandidateObservation(
    val timestampMillis: Long,
    val primary: H2CandidateValue,
    val comparator: H2CandidateValue,
) {
    init {
        require(primary.role == H2CandidateRole.PRIMARY)
        require(comparator.role == H2CandidateRole.COMPARATOR)
        require(primary.timestampMillis == timestampMillis && comparator.timestampMillis == timestampMillis)
    }
}

internal class H2FrozenPolicySession(private val contract: H2FrozenPolicy) {
    private var lastTimestamp: Long? = null
    private var onSince: Long? = null
    private var offSince: Long? = null
    private var active = false

    fun observe(value: H2CandidateValue): H2PolicyRouteState {
        val previous = lastTimestamp
        val discontinuous = previous != null &&
            (
                value.timestampMillis <= previous ||
                    value.timestampMillis - previous > contract.maxContinuousGapMillis
                )
        if (discontinuous) {
            onSince = null
            offSince = null
        }
        lastTimestamp = value.timestampMillis
        if (value.readiness != H2RuntimeReadiness.READY) {
            onSince = null
            offSince = null
            return H2PolicyRouteState(active = active, available = false, readiness = value.readiness)
        }
        val numeric = checkNotNull(value.value).toDouble()
        if (!active) {
            offSince = null
            if (numeric >= contract.onThreshold) {
                val start = onSince ?: value.timestampMillis.also { onSince = it }
                if (value.timestampMillis - start >= contract.activationDwellMillis) {
                    active = true
                    onSince = null
                }
            } else {
                onSince = null
            }
        } else {
            onSince = null
            if (numeric < contract.offThreshold) {
                val start = offSince ?: value.timestampMillis.also { offSince = it }
                if (value.timestampMillis - start >= contract.releaseDwellMillis) {
                    active = false
                    offSince = null
                }
            } else {
                offSince = null
            }
        }
        return H2PolicyRouteState(active = active, available = true, readiness = value.readiness)
    }

    fun reset() {
        lastTimestamp = null
        onSince = null
        offSince = null
        active = false
    }
}

/** Frozen temporal state is retained separately from whether an output is currently observable. */
internal data class H2PolicyRouteState(
    val active: Boolean,
    val available: Boolean,
    val readiness: H2RuntimeReadiness,
) {
    init {
        require(available == (readiness == H2RuntimeReadiness.READY))
    }
}

private interface H2C3bModel {
    fun score(features: List<Float>): Float
}

private fun parseC3bModel(bytes: ByteArray): H2C3bModel {
    val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    require(root.keys == MODEL_KEYS)
    require(root.required("schema_version").jsonPrimitive.int == 1)
    require(root.required("recipe").jsonPrimitive.content == MODEL_RECIPE)
    require(root.requiredFinite("C") > 0.0)
    val scalerMean = root.vector("scaler_mean", FEATURE_COUNT)
    val scalerScale = root.vector("scaler_scale", FEATURE_COUNT, positive = true)
    val pcaMean = root.vector("pca_mean", FEATURE_COUNT)
    val components = root.matrix("pca_components", PCA_COUNT, FEATURE_COUNT)
    val variance = root.vector("pca_explained_variance", PCA_COUNT, positive = true)
    val intercept = root.vector("classifier_intercept", 1).single()
    val coefficients = root.matrix("classifier_coef", 1, PCA_COUNT).single()
    return object : H2C3bModel {
        override fun score(features: List<Float>): Float {
            val centered = DoubleArray(FEATURE_COUNT) { index ->
                (features[index].toDouble() - scalerMean[index]) / scalerScale[index] - pcaMean[index]
            }
            val transformed = DoubleArray(PCA_COUNT) { component ->
                var dot = 0.0
                for (index in 0 until FEATURE_COUNT) dot += centered[index] * components[component][index]
                dot / sqrt(variance[component])
            }
            var logit = intercept
            for (index in 0 until PCA_COUNT) logit += transformed[index] * coefficients[index]
            val probability = if (logit >= 0) 1.0 / (1.0 + exp(-logit)) else exp(logit) / (1.0 + exp(logit))
            return probability.toFloat()
        }
    }
}

private fun JsonObject.required(name: String): JsonElement = requireNotNull(this[name])
private fun JsonObject.requiredFinite(name: String): Double =
    required(name).jsonPrimitive.double.also { value ->
        require(value.isFinite())
    }
private fun JsonObject.vector(name: String, count: Int, positive: Boolean = false): DoubleArray {
    val values = required(name).jsonArray
    require(values.size == count)
    return values.finiteDoubles(positive)
}
private fun JsonObject.matrix(name: String, rows: Int, columns: Int): List<DoubleArray> {
    val values = required(name).jsonArray
    require(values.size == rows)
    return values.map { row -> row.jsonArray.also { require(it.size == columns) }.finiteDoubles(false) }
}
private fun JsonArray.finiteDoubles(positive: Boolean): DoubleArray = DoubleArray(size) { index ->
    this[index].jsonPrimitive.double.also { value -> require(value.isFinite() && (!positive || value > 0)) }
}

private const val FEATURE_COUNT = 384
private const val PCA_COUNT = 16
private const val MODEL_RECIPE = "DK_SESSION_B_LEARNED_CHEEK_PATCH_V1"
private val MODEL_KEYS = setOf(
    "schema_version", "recipe", "C", "scaler_mean", "scaler_scale", "pca_mean",
    "pca_components", "pca_explained_variance", "classifier_intercept", "classifier_coef",
)
