package io.github.kmpfacelink

import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeData
import io.github.kmpfacelink.model.LiveLinkBlendShapes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Asserts `kmp-facelink-cbridge/conformance/live_link_conformance.json` — the shared
 * cross-repo golden fixture (see `docs/raw-tracking-seam.md`) — against the actual
 * [LiveLinkBlendShapes] lowering, so the fixture and the Kotlin implementation cannot
 * silently drift in either direction.
 *
 * Lives in `jvmTest` because commonTest has no portable file I/O; the fixture path is
 * injected via the `kmpfacelink.conformance.fixture` system property set by the module's
 * `jvmTest` Gradle task. The target-portable literal smoke tests remain in commonTest
 * ([LiveLinkBlendShapesTest]).
 */
class LiveLinkConformanceFixtureTest {

    private val fixture: JsonObject by lazy {
        val path = System.getProperty(FIXTURE_PATH_PROPERTY)
        assertNotNull(path, "System property '$FIXTURE_PATH_PROPERTY' must point to the conformance fixture")
        val file = File(path)
        assertTrue(file.isFile, "Conformance fixture not found at $path")
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    private val cases: List<JsonObject>
        get() = fixture.getValue("cases").jsonArray.map { it.jsonObject }

    @Test
    fun channelOrderMatchesLiveLinkBlendShapesNames() {
        val fixtureOrder = fixture.getValue("blendShapeChannelOrder").jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(LiveLinkBlendShapes.names, fixtureOrder)
    }

    @Test
    fun fixtureContainsExpectedCases() {
        val names = cases.map { it.getValue("name").jsonPrimitive.content }
        assertEquals(names.size, names.toSet().size, "case names must be unique")
        val required = listOf(
            "identity", "jaw_open_full", "mediapipe_no_tongue", "head_yaw_10deg",
            "head_translate_x_0p1m", "full_face", "head_pitch_neg5deg",
            "head_roll_neg5deg", "head_translate_xyz",
        )
        assertTrue(names.containsAll(required), "missing cases: ${required - names.toSet()}")
    }

    @Test
    fun lowerReproducesEveryCase() {
        for (case in cases) {
            val name = case.getValue("name").jsonPrimitive.content
            val expected = case.expected().getValue("blendShapesLiveLink").jsonArray
                .map { it.jsonPrimitive.float }
            assertEquals(LiveLinkBlendShapes.COUNT, expected.size, "case '$name': expected array size")

            val lowered = LiveLinkBlendShapes.lower(case.inputBlendShapes())
            for (i in lowered.indices) {
                assertEquals(
                    expected[i],
                    lowered[i],
                    "case '$name': channel $i (${LiveLinkBlendShapes.names[i]})",
                )
            }
        }
    }

    @Test
    fun fullFaceCaseCoversAll52ChannelsWithDistinctValues() {
        val fullFace = cases.first { it.getValue("name").jsonPrimitive.content == "full_face" }
        val values = fullFace.expected().getValue("blendShapesLiveLink").jsonArray
            .map { it.jsonPrimitive.float }
        assertTrue(values.all { it != 0f }, "full_face must set every channel non-zero")
        assertEquals(values.size, values.toSet().size, "full_face values must be distinct")
    }

    @Test
    fun expectedHeadIsBridgePassthroughOfInputHead() {
        for (case in cases) {
            val name = case.getValue("name").jsonPrimitive.content
            val input = case.getValue("input").jsonObject.getValue("head").jsonObject
            val output = case.expected().getValue("head").jsonObject
            for (field in HEAD_FIELDS) {
                assertEquals(
                    input.getValue(field).jsonPrimitive.double,
                    output.getValue(field).jsonPrimitive.double,
                    "case '$name': head.$field must pass through unchanged (degrees/meters)",
                )
            }
        }
    }

    @Test
    fun expectedHeadRadiansMatchesDegreesTimesFactor() {
        val degToRad = fixture.getValue("degToRadFactor").jsonPrimitive.double
        for (case in cases) {
            val name = case.getValue("name").jsonPrimitive.content
            val degrees = case.getValue("input").jsonObject.getValue("head").jsonObject
            val radians = case.expected().getValue("headRadians").jsonObject
            for (axis in ROTATION_FIELDS) {
                assertEquals(
                    degrees.getValue(axis).jsonPrimitive.double * degToRad,
                    radians.getValue(axis).jsonPrimitive.double,
                    RADIANS_TOLERANCE,
                    "case '$name': headRadians.$axis must equal $axis degrees * degToRadFactor",
                )
            }
        }
    }

    private fun JsonObject.expected(): JsonObject = getValue("expected").jsonObject

    private fun JsonObject.inputBlendShapes(): BlendShapeData =
        getValue("input").jsonObject.getValue("blendShapes").jsonObject
            .map { (arKitName: String, value: JsonElement) ->
                val shape = BlendShape.fromArKitName(arKitName)
                assertNotNull(shape, "fixture uses unknown ARKit blendshape name '$arKitName'")
                shape to value.jsonPrimitive.float
            }
            .toMap()

    private companion object {
        const val FIXTURE_PATH_PROPERTY = "kmpfacelink.conformance.fixture"
        const val RADIANS_TOLERANCE = 1e-6
        val HEAD_FIELDS = listOf("pitch", "yaw", "roll", "positionX", "positionY", "positionZ")
        val ROTATION_FIELDS = listOf("pitch", "yaw", "roll")
    }
}
