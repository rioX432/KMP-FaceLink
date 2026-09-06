@file:Suppress("MagicNumber", "TooGenericExceptionCaught")
@file:OptIn(io.github.kmpfacelink.ExperimentalFaceLinkApi::class)

package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationProducer
import io.github.kmpfacelink.model.CandidateObservationProducerFactory
import io.github.kmpfacelink.model.CandidateProducerDescriptor
import io.github.kmpfacelink.model.CandidateProducerOutput
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSemanticIntent
import io.github.kmpfacelink.model.sha256Hex
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

/** Loads opaque model bytes without exposing a filesystem path to the observation seam. */
@ExperimentalFaceLinkApi
public fun interface StaticAppearanceModelByteLoader {
    public fun load(): ByteArray?
}

/** Parses already hash-verified bytes into an immutable score model. */
@ExperimentalFaceLinkApi
public fun interface StaticAppearanceModelParser {
    public fun parse(bytes: ByteArray): StaticAppearanceScoreModel
}

/** Scores one exact 384-float static appearance feature plane. */
@ExperimentalFaceLinkApi
public fun interface StaticAppearanceScoreModel {
    public fun score(features: List<Float>): Float
}

/** Closed provider state. No failure state carries a usable model. */
@ExperimentalFaceLinkApi
public enum class StaticAppearanceModelAvailability {
    AVAILABLE,
    ABSENT,
    HASH_MISMATCH,
    LOAD_FAILED,
    PARSE_FAILED,
}

/**
 * Loads once, verifies SHA-256 before parsing, and fails closed without retaining unverified bytes.
 */
@ExperimentalFaceLinkApi
public class HashBoundStaticAppearanceModelProvider(
    expectedSha256: String,
    byteLoader: StaticAppearanceModelByteLoader,
    parser: StaticAppearanceModelParser,
) {
    public val availability: StaticAppearanceModelAvailability
    public val absenceReason: String?
    private val model: StaticAppearanceScoreModel?

    init {
        require(SHA256.matches(expectedSha256)) { "expectedSha256 must be 64 lowercase hex characters" }
        var loadFailed = false
        val loaded = try {
            byteLoader.load()?.copyOf()
        } catch (_: Exception) {
            loadFailed = true
            null
        }
        if (loadFailed) {
            availability = StaticAppearanceModelAvailability.LOAD_FAILED
            absenceReason = "c3b_model_load_failed"
            model = null
        } else if (loaded == null) {
            availability = StaticAppearanceModelAvailability.ABSENT
            absenceReason = "c3b_model_absent"
            model = null
        } else if (sha256Hex(loaded) != expectedSha256) {
            availability = StaticAppearanceModelAvailability.HASH_MISMATCH
            absenceReason = "c3b_model_hash_mismatch"
            model = null
        } else {
            val parsed = try {
                parser.parse(loaded)
            } catch (_: Exception) {
                null
            }
            if (parsed == null) {
                availability = StaticAppearanceModelAvailability.PARSE_FAILED
                absenceReason = "c3b_model_parse_failed"
                model = null
            } else {
                availability = StaticAppearanceModelAvailability.AVAILABLE
                absenceReason = null
                model = parsed
            }
        }
    }

    internal fun score(features: List<Float>): Float? = model?.score(features)

    public companion object {
        /** Uses the exact public `DK_SESSION_B_LEARNED_CHEEK_PATCH_V1` export schema. */
        public fun c3bStaticCheekPatch(
            expectedSha256: String,
            byteLoader: StaticAppearanceModelByteLoader,
        ): HashBoundStaticAppearanceModelProvider = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = expectedSha256,
            byteLoader = byteLoader,
            parser = StaticAppearanceModelParser(::parseC3bExport),
        )
    }
}

/** Exact default-off factory for the frozen static per-frame C3b candidate. */
@ExperimentalFaceLinkApi
public object StaticAppearanceCandidateFactories {
    public const val FEATURE_PLANE_KEY: String = "research:c3b-static-cheek-patch/features384/v1"

    public fun c3bStaticCheekPatch(
        provider: HashBoundStaticAppearanceModelProvider,
    ): CandidateObservationProducerFactory = C3bStaticFactory(provider)
}

private class C3bStaticFactory(
    private val provider: HashBoundStaticAppearanceModelProvider,
) : CandidateObservationProducerFactory {
    override val descriptor: CandidateProducerDescriptor = CandidateProducerDescriptor(
        producerId = "research:c3b-static-cheek-patch/v1",
        implementationRevision = "dk-c3b-static-cheek-patch-runtime/v1",
        semanticId = "research:static-cheek-appearance/v1",
        semanticIntent = CandidateSemanticIntent.UNRESOLVED,
        proposedTarget = null,
    )

    override fun create(): CandidateObservationProducer = CandidateObservationProducer { input ->
        observeStaticAppearance(input, provider)
    }
}

@Suppress("ReturnCount") // Each invalid boundary maps immediately to one explicit absence receipt.
private fun observeStaticAppearance(
    input: CandidateObservationInput,
    provider: HashBoundStaticAppearanceModelProvider,
): CandidateProducerOutput {
    if (provider.availability != StaticAppearanceModelAvailability.AVAILABLE) {
        return CandidateProducerOutput.absent(
            CandidateReadiness.UNAVAILABLE,
            checkNotNull(provider.absenceReason),
        )
    }
    val features = input.featurePlanes[StaticAppearanceCandidateFactories.FEATURE_PLANE_KEY]
        ?: return CandidateProducerOutput.absent(CandidateReadiness.MISSING_INPUT, "c3b_feature_plane_missing")
    if (features.size != FEATURE_COUNT) {
        return CandidateProducerOutput.absent(CandidateReadiness.REJECTED, "c3b_invalid_feature_length")
    }
    if (features.any { !it.isFinite() }) {
        return CandidateProducerOutput.absent(CandidateReadiness.NON_FINITE, "c3b_non_finite_feature")
    }
    val value = try {
        provider.score(features)
    } catch (_: Exception) {
        null
    }
    if (value == null || !value.isFinite() || value !in 0f..1f) {
        return CandidateProducerOutput.absent(CandidateReadiness.NON_FINITE, "c3b_invalid_model_output")
    }
    return CandidateProducerOutput.ready(value)
}

private fun parseC3bExport(bytes: ByteArray): StaticAppearanceScoreModel {
    val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    require(root.keys == MODEL_KEYS) { "C3b model keys do not match the frozen schema" }
    require(root.required("schema_version").jsonPrimitive.int == 1) { "unsupported schema_version" }
    require(root.required("recipe").jsonPrimitive.content == RECIPE) { "unexpected recipe" }
    require(root.requiredFinite("C") > 0.0) { "C must be positive" }
    val scalerMean = root.requiredVector("scaler_mean", FEATURE_COUNT)
    val scalerScale = root.requiredVector("scaler_scale", FEATURE_COUNT, positive = true)
    val pcaMean = root.requiredVector("pca_mean", FEATURE_COUNT)
    val pcaComponents = root.requiredMatrix("pca_components", PCA_COUNT, FEATURE_COUNT)
    val pcaVariance = root.requiredVector("pca_explained_variance", PCA_COUNT, positive = true)
    val intercept = root.requiredVector("classifier_intercept", 1).single()
    val coefficients = root.requiredMatrix("classifier_coef", 1, PCA_COUNT).single()
    return ExportedC3bModel(
        scalerMean,
        scalerScale,
        pcaMean,
        pcaComponents,
        pcaVariance,
        intercept,
        coefficients,
    )
}

@Suppress("LongParameterList") // Exact frozen export tensors form one immutable inference model.
private class ExportedC3bModel(
    private val scalerMean: DoubleArray,
    private val scalerScale: DoubleArray,
    private val pcaMean: DoubleArray,
    private val pcaComponents: List<DoubleArray>,
    private val pcaVariance: DoubleArray,
    private val intercept: Double,
    private val coefficients: DoubleArray,
) : StaticAppearanceScoreModel {
    override fun score(features: List<Float>): Float {
        require(features.size == FEATURE_COUNT) { "C3b feature count mismatch" }
        val centered = DoubleArray(FEATURE_COUNT) { index ->
            (features[index].toDouble() - scalerMean[index]) / scalerScale[index] - pcaMean[index]
        }
        val transformed = DoubleArray(PCA_COUNT) { component ->
            var dot = 0.0
            for (index in 0 until FEATURE_COUNT) dot += centered[index] * pcaComponents[component][index]
            dot / sqrt(pcaVariance[component])
        }
        var logit = intercept
        for (index in 0 until PCA_COUNT) logit += transformed[index] * coefficients[index]
        val probability = if (logit >= 0.0) {
            1.0 / (1.0 + exp(-logit))
        } else {
            val exponential = exp(logit)
            exponential / (1.0 + exponential)
        }
        return probability.toFloat()
    }
}

private fun JsonObject.required(name: String): JsonElement =
    requireNotNull(this[name]) { "missing $name" }

private fun JsonObject.requiredFinite(name: String): Double =
    required(name).jsonPrimitive.double.also { require(it.isFinite()) { "$name must be finite" } }

private fun JsonObject.requiredVector(
    name: String,
    size: Int,
    positive: Boolean = false,
): DoubleArray {
    val array = required(name).jsonArray
    require(array.size == size) { "$name must contain $size values" }
    return array.toFiniteDoubles(name, positive)
}

private fun JsonObject.requiredMatrix(name: String, rows: Int, columns: Int): List<DoubleArray> {
    val array = required(name).jsonArray
    require(array.size == rows) { "$name must contain $rows rows" }
    return array.mapIndexed { index, element ->
        val row = element.jsonArray
        require(row.size == columns) { "$name[$index] must contain $columns values" }
        row.toFiniteDoubles("$name[$index]", positive = false)
    }
}

private fun JsonArray.toFiniteDoubles(name: String, positive: Boolean): DoubleArray =
    DoubleArray(size) { index ->
        this[index].jsonPrimitive.double.also { value ->
            require(value.isFinite()) { "$name[$index] must be finite" }
            if (positive) require(value > 0.0) { "$name[$index] must be positive" }
        }
    }

private val MODEL_KEYS: Set<String> = setOf(
    "schema_version",
    "recipe",
    "C",
    "scaler_mean",
    "scaler_scale",
    "pca_mean",
    "pca_components",
    "pca_explained_variance",
    "classifier_intercept",
    "classifier_coef",
)
private val SHA256: Regex = Regex("[0-9a-f]{64}")
private const val RECIPE: String = "DK_SESSION_B_LEARNED_CHEEK_PATCH_V1"
private const val FEATURE_COUNT: Int = 384
private const val PCA_COUNT: Int = 16
