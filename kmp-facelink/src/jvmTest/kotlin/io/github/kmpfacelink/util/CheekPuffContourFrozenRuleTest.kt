package io.github.kmpfacelink.util

import io.github.kmpfacelink.model.FaceLandmark
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays the session-A recordings through [CheekPuffContourDetector] and requires the per-take
 * firing rate to equal, bit for bit, the rate the frozen Python implementation produced.
 *
 * The recordings are face landmarks of a named person and are never copied into this repository;
 * the session directory and the frozen-rule file are injected through the environment:
 *
 * ```
 * CHEEKPUFF_TESTDATA=/path/to/plan2-17take \
 * CHEEKPUFF_FROZEN_RULE=/path/to/scripts/plan2/frozen_rule.json \
 *   ./gradlew :kmp-facelink:jvmTest --tests '*CheekPuffContourFrozenRuleTest*'
 * ```
 *
 * Normal CI excludes this class and relies on the non-biometric `CheekPuffContourContractTest`
 * for decision-logic regressions. Run `:kmp-facelink:privateCheekPuffQualityCheck` only in a
 * controlled local environment; the dedicated task executes this class, and missing inputs fail
 * closed instead of being counted as replay coverage.
 */
class CheekPuffContourFrozenRuleTest {

    @Test
    fun sessionARatesMatchTheFrozenPythonImplementation() {
        if (pathFromOptional(RESULT_ENV) != null) return
        val session = pathFrom(TESTDATA_ENV) ?: return
        val ruleFile = pathFrom(RULE_ENV) ?: return

        val ruleSha = MessageDigest.getInstance("SHA-256").digest(ruleFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(FROZEN_RULE_SHA256, ruleSha, "frozen rule file is not the frozen revision")

        val rule = Json.parseToJsonElement(ruleFile.readText()).jsonObject
        assertThresholdsMatchTheRuleFile(rule)

        val expected = rule.getValue("session_a_fire_rates").jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.double }

        val actual = scoreSession(session)
        val compared = expected.keys.sorted()
        assertTrue(actual.keys.containsAll(expected.keys), "missing takes: ${expected.keys - actual.keys}")

        val mismatches = compared.filter { abs(actual.getValue(it).rate - expected.getValue(it)) > 0.0 }
        val table = compared.joinToString("\n") {
            val take = actual.getValue(it)
            "  %-24s kotlin %.16f (%d/%d)  python %.16f".format(
                it, take.rate, take.firedFrames, take.scoredFrames, expected.getValue(it),
            )
        }
        val extras = (actual.keys - expected.keys).sorted().joinToString("\n") {
            val take = actual.getValue(it)
            "  %-24s kotlin %.16f (%d/%d)  python (not judged at freeze time)".format(
                it, take.rate, take.firedFrames, take.scoredFrames,
            )
        }
        println("session-A firing rates\n$table\nnot in the frozen rule\n$extras")
        assertTrue(mismatches.isEmpty(), "firing rates differ for $mismatches\n$table")
    }

    @Test
    fun providedSessionRatesMatchTheFrozenPythonImplementation() {
        val resultFile = pathFromOptional(RESULT_ENV) ?: return
        val session = pathFrom(TESTDATA_ENV) ?: return
        val ruleFile = pathFrom(RULE_ENV) ?: return

        val result = Json.parseToJsonElement(resultFile.readText()).jsonObject
        assertEquals(FROZEN_RULE_SHA256, result.getValue("rule_sha256").jsonPrimitive.content)
        val expected = buildMap {
            for (section in listOf("fire_rates", "extra_takes_not_judged")) {
                putAll(result.getValue(section).jsonObject.mapValues { (_, value) -> value.jsonPrimitive.double })
            }
        }
        val actual = scoreSession(session)
        assertEquals(expected.keys, actual.keys, "the aggregate result and private session must name the same takes")

        val mismatches = expected.keys.sorted().filter {
            abs(actual.getValue(it).rate - expected.getValue(it)) > 0.0
        }
        val table = expected.keys.sorted().joinToString("\n") {
            val take = actual.getValue(it)
            "  %-24s kotlin %.16f (%d/%d)  python %.16f".format(
                it, take.rate, take.firedFrames, take.scoredFrames, expected.getValue(it),
            )
        }
        println("provided-session firing rates\n$table")
        assertTrue(mismatches.isEmpty(), "firing rates differ for $mismatches\n$table")

        val ruleSha = MessageDigest.getInstance("SHA-256").digest(ruleFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(FROZEN_RULE_SHA256, ruleSha, "frozen rule file is not the frozen revision")
    }

    private fun assertThresholdsMatchTheRuleFile(rule: JsonObject) {
        val conditions = rule.getValue("conditions").jsonArray.associate {
            val condition = it.jsonObject
            condition.getValue("feature").jsonPrimitive.content to
                Pair(
                    condition.getValue("op").jsonPrimitive.content,
                    condition.getValue("threshold").jsonPrimitive.double,
                )
        }
        assertEquals(Pair("<=", CheekPuffContourDetector.MOUTH_WIDTH_MAX), conditions["mouth_width"])
        assertEquals(Pair(">=", CheekPuffContourDetector.OVAL_JAW_MIN), conditions["oval_jaw"])
        assertEquals(Pair("<=", CheekPuffContourDetector.OVAL_CHIN_MAX), conditions["oval_chin"])
        assertEquals(3, conditions.size, "the frozen rule is exactly three conditions")
    }

    private class TakeResult(val rate: Double, val firedFrames: Int, val scoredFrames: Int)

    private fun scoreSession(session: File): Map<String, TakeResult> {
        val takes = session.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "take.json").isFile }
            .map { Take(it) }
            .sortedBy { it.takeId }
        val counters = mutableMapOf<String, Int>()
        val out = linkedMapOf<String, TakeResult>()
        for (take in takes) {
            val attempt = counters.merge(take.scenario, 1, Int::plus)
            out["${take.scenario}#$attempt"] = take.score()
        }
        return out
    }

    /** One recorded take: the landmark stream plus the script spans its frames are labelled by. */
    private class Take(directory: File) {
        private class Span(val cue: String, val startMs: Int, val endMs: Int, val first: Int, val last: Int)

        val takeId: String
        val scenario: String
        private val width: Int
        private val height: Int
        private val frameCount: Int
        private val seq: IntArray
        private val seconds: DoubleArray
        private val landmarks: FloatArray
        private val spans: List<Span>

        init {
            val meta = Json.parseToJsonElement(File(directory, "take.json").readText()).jsonObject
            takeId = meta.getValue("takeId").jsonPrimitive.content
            scenario = takeId.substringAfter('_').substringBeforeLast('_')
            frameCount = meta.getValue("inferenceResults").jsonPrimitive.int
            val resolution = meta.getValue("condition").jsonObject
                .getValue("nativeResolution").jsonPrimitive.content.split("x")
            width = resolution[0].toInt()
            height = resolution[1].toInt()
            spans = meta.getValue("spans").jsonArray.map {
                val span = it.jsonObject
                Span(
                    span.getValue("cue").jsonPrimitive.content,
                    span.getValue("startMs").jsonPrimitive.int,
                    span.getValue("endMs").jsonPrimitive.int,
                    span.getValue("firstFrameSeq").jsonPrimitive.int,
                    span.getValue("lastFrameSeq").jsonPrimitive.int,
                )
            }

            val index = ByteBuffer.wrap(File(directory, "results.idx").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            seq = IntArray(frameCount) { index.getInt(it * INDEX_RECORD_BYTES) }
            val firstCapture = index.getLong(CAPTURE_NS_OFFSET)
            seconds = DoubleArray(frameCount) {
                val captured = index.getLong(it * INDEX_RECORD_BYTES + CAPTURE_NS_OFFSET)
                (captured - firstCapture).toDouble() / NANOS_PER_SECOND
            }
            val floats = ByteBuffer.wrap(File(directory, "landmarks.f32").readBytes())
                .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            landmarks = FloatArray(floats.remaining())
            floats.get(landmarks)
        }

        fun score(): TakeResult {
            val neutral = spans.first { it.cue == "NEUTRAL" }
            val neutralFrames = neutral.last - neutral.first + 1
            val neutralSeconds = (neutral.endMs - neutral.startMs) / MILLIS
            val burnIn = Math.rint(BURN_IN_SECONDS * neutralFrames / neutralSeconds).toInt()
            val calibrationFrom = neutral.first + burnIn

            val detector = CheekPuffContourDetector(width, height)
            for (i in 0 until frameCount) {
                if (seq[i] in calibrationFrom..neutral.last) detector.addCalibrationFrame(frame(i), seconds[i])
            }
            detector.finishCalibration()

            val fired = BooleanArray(frameCount)
            for (i in 0 until frameCount) {
                if (seq[i] > neutral.last) fired[i] = detector.update(frame(i), seconds[i]).fired
            }

            val scored = scoredMask()
            var scoredFrames = 0
            var firedFrames = 0
            for (i in 0 until frameCount) {
                if (!scored[i]) continue
                scoredFrames++
                if (fired[i]) firedFrames++
            }
            return TakeResult(firedFrames.toDouble() / scoredFrames.toDouble(), firedFrames, scoredFrames)
        }

        /** Both HOLD spans trimmed by half a second at each end; the neutral take is scored whole. */
        private fun scoredMask(): BooleanArray {
            val mask = BooleanArray(frameCount)
            if (scenario == WHOLE_TAKE_SCENARIO) return BooleanArray(frameCount) { true }
            for (span in spans.filter { it.cue == "HOLD" }) {
                val frames = span.last - span.first + 1
                val fps = frames / ((span.endMs - span.startMs) / MILLIS)
                val trim = Math.rint(EDGE_TRIM_SECONDS * fps).toInt()
                val low = span.first + trim
                val high = span.last - trim
                for (i in 0 until frameCount) {
                    if (seq[i] in low..high) mask[i] = true
                }
            }
            return mask
        }

        private fun frame(index: Int): List<FaceLandmark> {
            val base = index * CheekPuffContourDetector.LANDMARK_COUNT * 3
            return List(CheekPuffContourDetector.LANDMARK_COUNT) {
                FaceLandmark(landmarks[base + it * 3], landmarks[base + it * 3 + 1], landmarks[base + it * 3 + 2])
            }
        }
    }

    private fun pathFrom(variable: String): File? {
        val raw = System.getenv(variable) ?: System.getProperty(variable.lowercase().replace('_', '.'))
        if (raw.isNullOrBlank()) {
            error("$variable is required by privateCheekPuffQualityCheck")
        }
        val file = File(raw)
        assertTrue(file.exists(), "$variable points at a missing path: $raw")
        return file
    }

    private fun pathFromOptional(variable: String): File? {
        val raw = System.getenv(variable) ?: System.getProperty(variable.lowercase().replace('_', '.'))
        if (raw.isNullOrBlank()) return null
        val file = File(raw)
        assertTrue(file.exists(), "$variable points at a missing path: $raw")
        return file
    }

    private companion object {
        const val TESTDATA_ENV = "CHEEKPUFF_TESTDATA"
        const val RULE_ENV = "CHEEKPUFF_FROZEN_RULE"
        const val RESULT_ENV = "CHEEKPUFF_EXPECTED_RESULT"
        const val FROZEN_RULE_SHA256 = "32829cea15a5fb3bf2e734ab3249bcf0b8d949e5e17694bfb0ae6f2c181cb231"
        const val INDEX_RECORD_BYTES = 32
        const val CAPTURE_NS_OFFSET = 16
        const val NANOS_PER_SECOND = 1e9
        const val MILLIS = 1000.0
        const val BURN_IN_SECONDS = 1.5
        const val EDGE_TRIM_SECONDS = 0.5
        const val WHOLE_TAKE_SCENARIO = "neutral_10s"
    }
}
