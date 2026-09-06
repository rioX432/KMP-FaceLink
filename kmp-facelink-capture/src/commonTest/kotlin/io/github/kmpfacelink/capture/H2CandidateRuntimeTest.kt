@file:Suppress("MagicNumber")

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.FaceLandmark
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2CandidateRuntimeTest {
    @Test
    fun strictProviderHashesAndParsesOneRetainedBuffer() {
        val source = syntheticModel().encodeToByteArray()
        val provider = H2StrictC3bModelProvider(EXPECTED, H2ModelByteLoader { source }, H2Sha256 { retained ->
            assertTrue(retained !== source)
            retained[0] = '!'.code.toByte()
            EXPECTED
        })
        assertEquals(H2ModelAvailability.PARSE_FAILED, provider.availability)
        assertEquals('{'.code.toByte(), source[0])
    }

    @Test
    fun exactSyntheticModelProducesStaticProbabilityAndRejectsMissingFeature() {
        val provider = H2StrictC3bModelProvider(
            EXPECTED,
            H2ModelByteLoader { syntheticModel().encodeToByteArray() },
            H2Sha256 { EXPECTED },
        )
        assertEquals(H2ModelAvailability.AVAILABLE, provider.availability)
        val candidate = H2C3bCandidate(provider)
        val ready = candidate.observe(frame(0, c3b = List(384) { 0f }))
        assertEquals(H2RuntimeReadiness.READY, ready.readiness)
        assertEquals(0.5f, ready.value)
        val missing = candidate.observe(frame(1, c3b = null))
        assertEquals(H2RuntimeReadiness.NOT_READY, missing.readiness)
        assertNull(missing.value)
    }

    @Test
    fun comparatorStaysSeparateAndCannotRescueUnavailablePrimary() {
        val provider = H2StrictC3bModelProvider(EXPECTED, H2ModelByteLoader { null }, H2Sha256 { EXPECTED })
        val comparator = H2C2C1Candidate()
        val runtime = H2PairedCandidateRuntime(H2C3bCandidate(provider), comparator)
        runtime.observe(frame(0, landmarks = face()))
        repeat(15) { index -> runtime.observe(frame(250L + index * 50L, landmarks = face())) }
        val warming = runtime.observe(
            frame(
                1_300,
                estimator = neutralValues() + (BlendShape.MOUTH_ROLL_LOWER to 0.3f),
                landmarks = face(jawOutset = 4f),
            ),
        )
        assertEquals(H2RuntimeReadiness.NOT_READY, warming.comparator.readiness)
        assertNull(warming.comparator.value)
        val observed = runtime.observe(
            frame(
                3_001,
                estimator = neutralValues() + (BlendShape.MOUTH_ROLL_LOWER to 0.3f),
                landmarks = face(jawOutset = 4f),
            ),
        )
        assertEquals(H2RuntimeReadiness.UNAVAILABLE, observed.primary.readiness)
        assertNull(observed.primary.value)
        assertEquals(H2RuntimeReadiness.READY, observed.comparator.readiness)
        assertTrue(checkNotNull(observed.comparator.value) > 0f)
        assertEquals(H2CandidateRole.PRIMARY, observed.primary.role)
        assertEquals(H2CandidateRole.COMPARATOR, observed.comparator.role)
    }

    @Test
    fun exactPoliciesPreserveIndependentStateAndAbsenceBreaksDwell() {
        val primary = H2FrozenPolicySession(H2ConfirmationContract.primary.policy)
        val comparator = H2FrozenPolicySession(H2ConfirmationContract.comparator.policy)
        assertTrue(primary.observe(value(H2CandidateRole.PRIMARY, 0, 0.5f)).active)
        assertTrue(!comparator.observe(value(H2CandidateRole.COMPARATOR, 0, 0.05f)).active)
        comparator.observe(value(H2CandidateRole.COMPARATOR, 200, 0.05f))
        val unavailable = comparator.observe(absent(H2CandidateRole.COMPARATOR, 300))
        assertTrue(!unavailable.available)
        assertTrue(!comparator.observe(value(H2CandidateRole.COMPARATOR, 550, 0.05f)).active)
        assertTrue(!comparator.observe(value(H2CandidateRole.COMPARATOR, 800, 0.05f)).active)
        assertTrue(comparator.observe(value(H2CandidateRole.COMPARATOR, 900, 0.05f)).active)

        val retained = primary.observe(absent(H2CandidateRole.PRIMARY, 1_000))
        assertTrue(retained.active)
        assertTrue(!retained.available)
        primary.reset()
        assertTrue(!primary.observe(value(H2CandidateRole.PRIMARY, 1_100, 0f)).active)
    }

    private fun value(role: H2CandidateRole, timestamp: Long, value: Float) = H2CandidateValue(
        role,
        if (role == H2CandidateRole.PRIMARY) H2ConfirmationContract.primary.producerId else
            H2ConfirmationContract.comparator.producerId,
        timestamp,
        value,
        H2RuntimeReadiness.READY,
        null,
    )

    private fun absent(role: H2CandidateRole, timestamp: Long) = H2CandidateValue(
        role,
        H2ConfirmationContract.comparator.producerId,
        timestamp,
        null,
        H2RuntimeReadiness.NOT_READY,
        "synthetic_absence",
    )

    private fun frame(
        timestamp: Long,
        estimator: Map<BlendShape, Float> = neutralValues(),
        landmarks: List<FaceLandmark> = face(),
        c3b: List<Float>? = null,
    ) = H2CandidateFrame(timestamp, estimator, landmarks, IMAGE_SIZE, IMAGE_SIZE, c3b)

    private fun neutralValues(): Map<BlendShape, Float> = mapOf(
        BlendShape.MOUTH_ROLL_LOWER to 0f,
        BlendShape.MOUTH_ROLL_UPPER to 0f,
        BlendShape.MOUTH_LEFT to 0f,
        BlendShape.MOUTH_RIGHT to 0f,
        BlendShape.MOUTH_FUNNEL to 0f,
        BlendShape.JAW_OPEN to 0f,
        BlendShape.MOUTH_SMILE_LEFT to 0f,
        BlendShape.MOUTH_SMILE_RIGHT to 0f,
    )

    private fun face(jawOutset: Float = 0f, mouthHalfWidth: Float = 20f): List<FaceLandmark> {
        val points = MutableList(478) { index -> landmark(200f + index % 17 * 0.01f, 100f + index % 19 * 0.01f, 0f) }
        fun set(index: Int, x: Float, y: Float, z: Float = 0f) { points[index] = landmark(x, y, z) }
        set(10, 200f, 50f); set(168, 200f, 80f, 5f); set(6, 200f, 105f, -4f)
        set(197, 196f, 115f, 2f); set(195, 200f, 125f); set(33, 155f, 100f, -2f)
        set(133, 185f, 105f, 1f); set(362, 215f, 105f, 1f); set(263, 245f, 100f, -2f)
        set(152, 200f, 250f); set(468, 150f, 105f); set(473, 250f, 105f)
        set(61, 200f - mouthHalfWidth, 170f); set(291, 200f + mouthHalfWidth, 170f)
        set(13, 200f, 168f); set(14, 200f, 172f)
        FACE_OVAL.forEachIndexed { position, index ->
            if (index == 10 || index == 152) return@forEachIndexed
            val fraction = position.toFloat() / (FACE_OVAL.size - 1)
            val side = if (position < FACE_OVAL.size / 2) 1f else -1f
            val outset = 60f + 20f * sin(fraction * kotlin.math.PI).toFloat()
            val delta = if (fraction >= 0.70f && fraction < 0.85f) jawOutset else 0f
            set(index, 200f + side * (outset + delta), 50f + 200f * fraction)
        }
        return points
    }

    private fun landmark(x: Float, y: Float, z: Float) = FaceLandmark(x / IMAGE_SIZE, y / IMAGE_SIZE, z / IMAGE_SIZE)

    private fun syntheticModel(): String {
        fun vector(size: Int, value: String) = List(size) { value }.joinToString(prefix = "[", postfix = "]")
        val components = List(16) { vector(384, "0") }.joinToString(prefix = "[", postfix = "]")
        return "{" +
            "\"schema_version\":1,\"recipe\":\"DK_SESSION_B_LEARNED_CHEEK_PATCH_V1\",\"C\":1," +
            "\"scaler_mean\":${vector(384, "0")},\"scaler_scale\":${vector(384, "1")}," +
            "\"pca_mean\":${vector(384, "0")},\"pca_components\":$components," +
            "\"pca_explained_variance\":${vector(16, "1")}," +
            "\"classifier_intercept\":[0],\"classifier_coef\":[${vector(16, "0")}] }"
    }

    companion object {
        private const val EXPECTED = "0000000000000000000000000000000000000000000000000000000000000000"
        private const val IMAGE_SIZE = 400
        private val FACE_OVAL = intArrayOf(
            10, 338, 297, 332, 284, 251, 389, 356, 454, 323, 361, 288, 397, 365,
            379, 378, 400, 377, 152, 148, 176, 149, 150, 136, 172, 58, 132, 93,
            234, 127, 162, 21, 54, 103, 67, 109,
        )
    }
}
