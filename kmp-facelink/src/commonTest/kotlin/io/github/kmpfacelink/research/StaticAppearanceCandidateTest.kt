package io.github.kmpfacelink.research

import io.github.kmpfacelink.ExperimentalFaceLinkApi
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.CandidateInputIdentity
import io.github.kmpfacelink.model.CandidateObservationInput
import io.github.kmpfacelink.model.CandidateObservationSession
import io.github.kmpfacelink.model.CandidateReadiness
import io.github.kmpfacelink.model.CandidateSelectionPolicy
import io.github.kmpfacelink.model.CandidateSelectionRoute
import io.github.kmpfacelink.model.CandidateSemanticIntent
import io.github.kmpfacelink.model.sha256Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalFaceLinkApi::class)
class StaticAppearanceCandidateTest {
    @Test
    fun hashMismatchNeverInvokesParserAndFailsClosed() {
        var parserInvoked = false
        val provider = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = "0".repeat(64),
            byteLoader = StaticAppearanceModelByteLoader { MODEL_BYTES },
            parser = StaticAppearanceModelParser {
                parserInvoked = true
                StaticAppearanceScoreModel { 0.5f }
            },
        )

        assertFalse(parserInvoked)
        assertEquals(StaticAppearanceModelAvailability.HASH_MISMATCH, provider.availability)
        val observation = observe(provider, validFeatures())
        assertNull(observation.value)
        assertEquals(CandidateReadiness.UNAVAILABLE, observation.readiness)
        assertEquals("c3b_model_hash_mismatch", observation.reason)
    }

    @Test
    fun exactPublicSchemaParsesAndProducesUnresolvedDefaultOffObservation() {
        val bytes = validExportJson().encodeToByteArray()
        val provider = HashBoundStaticAppearanceModelProvider.c3bStaticCheekPatch(
            expectedSha256 = sha256Hex(bytes),
            byteLoader = StaticAppearanceModelByteLoader { bytes },
        )
        val factory = StaticAppearanceCandidateFactories.c3bStaticCheekPatch(provider)
        val session = CandidateObservationSession(listOf(factory))
        val receipt = session.observe(input(validFeatures()))
        val observation = receipt.alternatives.single()

        assertEquals(StaticAppearanceModelAvailability.AVAILABLE, provider.availability)
        assertEquals(CandidateSemanticIntent.UNRESOLVED, observation.semanticIntent)
        assertNull(observation.proposedTarget)
        assertEquals(0.5f, observation.value)
        assertEquals(0.2f, receipt.selected[BlendShape.CHEEK_PUFF])

        val attemptedRoute = session.observe(
            input(validFeatures(), timestampMs = 1L),
            CandidateSelectionPolicy(
                mapOf(
                    BlendShape.CHEEK_PUFF to CandidateSelectionRoute(observation.producerId),
                ),
            ),
        )
        assertEquals(0.2f, attemptedRoute.selected[BlendShape.CHEEK_PUFF])
    }

    @Test
    fun absentParserFailureAndInvalidFeaturesRemainExplicitAbsence() {
        val absent = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = "0".repeat(64),
            byteLoader = StaticAppearanceModelByteLoader { null },
            parser = StaticAppearanceModelParser { StaticAppearanceScoreModel { 0.5f } },
        )
        assertEquals(CandidateReadiness.UNAVAILABLE, observe(absent, validFeatures()).readiness)

        val parseFailure = HashBoundStaticAppearanceModelProvider(
            expectedSha256 = sha256Hex(MODEL_BYTES),
            byteLoader = StaticAppearanceModelByteLoader { MODEL_BYTES },
            parser = StaticAppearanceModelParser { error("synthetic parse failure") },
        )
        assertEquals(StaticAppearanceModelAvailability.PARSE_FAILED, parseFailure.availability)
        assertEquals(CandidateReadiness.UNAVAILABLE, observe(parseFailure, validFeatures()).readiness)

        val provider = syntheticProvider { 0.5f }
        assertEquals(CandidateReadiness.MISSING_INPUT, observe(provider, null).readiness)
        assertEquals(CandidateReadiness.REJECTED, observe(provider, listOf(0f)).readiness)
        assertEquals(
            CandidateReadiness.NON_FINITE,
            observe(provider, validFeatures().toMutableList().apply { this[10] = Float.NaN }).readiness,
        )
    }

    @Test
    fun invalidModelOutputIsNotConvertedToZeroAndStaticFramesCarryNoHistory() {
        val invalid = syntheticProvider { Float.NaN }
        val invalidObservation = observe(invalid, validFeatures())
        assertNull(invalidObservation.value)
        assertEquals(CandidateReadiness.NON_FINITE, invalidObservation.readiness)

        val stable = syntheticProvider { features -> features.first() }
        val session = CandidateObservationSession(
            listOf(StaticAppearanceCandidateFactories.c3bStaticCheekPatch(stable)),
        )
        val first = session.observe(input(validFeatures(0.25f), timestampMs = 0L)).alternatives.single()
        val second = session.observe(input(validFeatures(0.25f), timestampMs = 1L)).alternatives.single()
        session.reset()
        val reset = session.observe(input(validFeatures(0.25f), timestampMs = 0L)).alternatives.single()

        assertEquals(0.25f, first.value)
        assertEquals(first.value, second.value)
        assertEquals(first.value, reset.value)
        assertTrue(first.reason == null && second.reason == null && reset.reason == null)
    }

    private fun observe(
        provider: HashBoundStaticAppearanceModelProvider,
        features: List<Float>?,
    ) = CandidateObservationSession(
        listOf(StaticAppearanceCandidateFactories.c3bStaticCheekPatch(provider)),
    ).observe(input(features)).alternatives.single()

    private fun input(features: List<Float>?, timestampMs: Long = 0L): CandidateObservationInput =
        CandidateObservationInput(
            identity = CandidateInputIdentity(1u, 0u, 0, timestampMs),
            estimator = mapOf(BlendShape.CHEEK_PUFF to 0.2f),
            landmarks = emptyList(),
            sourceImageWidth = 1,
            sourceImageHeight = 1,
            featurePlanes = features?.let {
                mapOf(StaticAppearanceCandidateFactories.FEATURE_PLANE_KEY to it)
            } ?: emptyMap(),
        )

    private fun syntheticProvider(
        score: (List<Float>) -> Float,
    ): HashBoundStaticAppearanceModelProvider = HashBoundStaticAppearanceModelProvider(
        expectedSha256 = sha256Hex(MODEL_BYTES),
        byteLoader = StaticAppearanceModelByteLoader { MODEL_BYTES },
        parser = StaticAppearanceModelParser { StaticAppearanceScoreModel(score) },
    )

    private fun validFeatures(first: Float = 0f): List<Float> =
        List(FEATURE_COUNT) { index -> if (index == 0) first else 0f }

    private fun validExportJson(): String {
        val zeros384 = vector(FEATURE_COUNT, 0.0)
        val ones384 = vector(FEATURE_COUNT, 1.0)
        val components = List(PCA_COUNT) { zeros384 }.joinToString(prefix = "[", postfix = "]")
        val pcaVariance = vector(PCA_COUNT, 1.0)
        val classifierCoefficients = vector(PCA_COUNT, 0.0)
        return buildString {
            append("{\"schema_version\":1,")
            append("\"recipe\":\"DK_SESSION_B_LEARNED_CHEEK_PATCH_V1\",\"C\":1.0,")
            append("\"scaler_mean\":$zeros384,\"scaler_scale\":$ones384,")
            append("\"pca_mean\":$zeros384,\"pca_components\":$components,")
            append("\"pca_explained_variance\":$pcaVariance,")
            append("\"classifier_intercept\":[0.0],")
            append("\"classifier_coef\":[$classifierCoefficients]}")
        }
    }

    private fun vector(size: Int, value: Double): String =
        List(size) { value }.joinToString(prefix = "[", postfix = "]")

    private companion object {
        val MODEL_BYTES: ByteArray = "synthetic-c3b-model".encodeToByteArray()
        const val FEATURE_COUNT: Int = 384
        const val PCA_COUNT: Int = 16
    }
}
