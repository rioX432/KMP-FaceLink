@file:Suppress("MagicNumber") // Compatibility schema IDs are the contract under test.

package io.github.kmpfacelink.capture

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TakeManifestSchemaGoldenTest {
    @Test
    fun schema3RemainsAnUnscopedLegacyDiagnostic() {
        val manifest = assertFrozenGolden(SCHEMA_3)

        assertEquals(CaptureStorageReceipt.legacyDiagnosticOnly(), manifest.storageReceipt)
        assertEquals(StudyRunIdentity.legacyUnscoped(), manifest.studyRun)
    }

    @Test
    fun schema4IsASupportedTransitionalFormWithExplicitStorage() {
        val manifest = assertFrozenGolden(SCHEMA_4)

        assertEquals(CaptureStorageProfile.COEFFICIENTS_ONLY_V1, manifest.storageReceipt.profile)
        assertEquals(StudyRunIdentity.legacyUnscoped(), manifest.studyRun)
    }

    @Test
    fun schema5RequiresAnExactStudyRunAndStorageProfile() {
        val source = fixture(SCHEMA_5.name)
        val manifest = assertFrozenGolden(SCHEMA_5, source)

        assertEquals(StudyRunIdentity.SCHEMA_VERSION, manifest.studyRun.schemaVersion)
        assertEquals(manifest.storageReceipt.profile, manifest.studyRun.storageProfile)

        assertFails { rawFrameJson.decodeFromString<TakeManifest>(source.withoutTopLevel("studyRun")) }
        assertFails {
            rawFrameJson.decodeFromString<TakeManifest>(
                source.replace(
                    "\"storageProfile\": \"COEFFICIENTS_ONLY_V1\"",
                    "\"storageProfile\": \"CANONICAL_RGBA_240X320_V1\"",
                ),
            )
        }
        assertFails { rawFrameJson.decodeFromString<TakeManifest>(source.withoutTopLevel("takeId")) }
    }

    @Test
    fun everyFrozenSchemaRejectsUnknownFields() {
        GOLDENS.forEach { golden ->
            val source = fixture(golden.name)
            val json = rawFrameJson.parseToJsonElement(source).jsonObject.toMutableMap()
            json["unknownContractField"] = JsonPrimitive("must fail closed")
            assertFails(golden.name) {
                rawFrameJson.decodeFromString<TakeManifest>(JsonObject(json).toString())
            }
        }
    }

    @Test
    fun validCanonicalTakeIdMutationsFailTheIndependentOracle() {
        GOLDENS.forEach { golden ->
            val original = assertFrozenGolden(golden)
            val mutated = original.copy(takeId = "${original.takeId}-mutated")
            val canonicalMutation = rawFrameJson.encodeToString(mutated) + "\n"

            assertEquals(mutated, rawFrameJson.decodeFromString(canonicalMutation))
            assertEquals(canonicalMutation, rawFrameJson.encodeToString(mutated) + "\n")
            assertNotEquals(golden.sha256, sha256(canonicalMutation), golden.name)
            assertNotEquals(golden.snapshot, mutated.semanticSnapshot(), golden.name)
            assertFailsWith<AssertionError>(golden.name) {
                assertFrozenGolden(golden, canonicalMutation)
            }
        }
    }

    @Test
    fun aFutureSchemaIsRejectedEvenWhenEveryKnownFieldIsValid() {
        val future = fixture(SCHEMA_5.name).replace(
            "\"schemaVersion\": 5",
            "\"schemaVersion\": 6",
        )

        assertFails { rawFrameJson.decodeFromString<TakeManifest>(future) }
    }

    private fun assertFrozenGolden(
        golden: Golden,
        source: String = fixture(golden.name),
    ): TakeManifest {
        assertEquals(golden.sha256, sha256(source), "${golden.name} fixture bytes changed")
        val manifest = rawFrameJson.decodeFromString<TakeManifest>(source)
        assertEquals(golden.snapshot, manifest.semanticSnapshot(), "${golden.name} semantics changed")
        assertEquals(source, rawFrameJson.encodeToString(manifest) + "\n")
        assertFixtureSafety(manifest, source)
        return manifest
    }

    private fun assertFixtureSafety(manifest: TakeManifest, source: String) {
        assertTrue(manifest.takeId.startsWith("synthetic-schema-"))
        assertTrue(manifest.spans.isEmpty())
        assertTrue(manifest.blendshapeNames.isEmpty())
        assertTrue(
            listOf(
                manifest.resultIndexSha256,
                manifest.landmarksSha256,
                manifest.blendshapesSha256,
                manifest.transformSha256,
            ).all(String::isEmpty),
        )
        assertEquals(0, manifest.framesStored)
        assertEquals(0, manifest.storageReceipt.storedWidth)
        assertEquals(0, manifest.storageReceipt.storedHeight)
        assertEquals("synthetic-model-contract", manifest.condition.modelSha256)
        assertEquals("synthetic-sdk-contract", manifest.condition.provenance.repo)
        assertEquals("fixture", manifest.condition.provenance.branch)
        assertEquals("0".repeat(GIT_SHA_LENGTH), manifest.condition.provenance.gitSha)
        assertFalse(manifest.condition.provenance.dirty)
        if (manifest.schemaVersion == TakeManifest.SCHEMA_VERSION) {
            assertTrue(manifest.studyRun.runId.startsWith("synthetic-schema-"))
            assertEquals("SYNTHETIC_CONTRACT_V1", manifest.studyRun.protocolId)
        }

        rawFrameJson.parseToJsonElement(source).stringValues().forEach { value ->
            assertFalse(value.startsWith("/"), "absolute path in fixture: $value")
            assertFalse('\\' in value, "backslash path in fixture: $value")
            FORBIDDEN_VALUE_TOKENS.forEach { token ->
                assertFalse(value.contains(token, ignoreCase = true), "$token in fixture value")
            }
        }
    }

    private fun TakeManifest.semanticSnapshot(): SemanticSnapshot = SemanticSnapshot(
        schemaVersion = schemaVersion,
        takeId = takeId,
        workflow = captureWorkflow,
        participantPreview = condition.participantPreview,
        layer = condition.layer,
        framesStored = framesStored,
        storageProfile = storageReceipt.profile,
        pixelSemantics = storageReceipt.pixelSemantics,
        studyRunSchemaVersion = studyRun.schemaVersion,
        studyRunId = studyRun.runId,
        protocolId = studyRun.protocolId,
        runStorageProfile = studyRun.storageProfile,
    )

    private fun fixture(name: String): String {
        val bytes = requireNotNull(javaClass.classLoader?.getResource("contracts/$name")) {
            "missing contract fixture $name"
        }.openStream().use { it.readBytes() }
        val source = bytes.toString(Charsets.UTF_8)
        assertContentEquals(bytes, source.toByteArray(Charsets.UTF_8), "$name is not canonical UTF-8")
        return source
    }

    private fun String.withoutTopLevel(field: String): String {
        val json = rawFrameJson.parseToJsonElement(this).jsonObject.toMutableMap()
        check(json.remove(field) != null) { "fixture has no $field" }
        return JsonObject(json).toString()
    }

    private fun JsonElement.stringValues(): List<String> = when (this) {
        is JsonArray -> flatMap { element -> element.stringValues() }
        is JsonObject -> values.flatMap { element -> element.stringValues() }
        is JsonPrimitive -> if (isString) listOf(content) else emptyList()
    }

    private fun sha256(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class Golden(
        val name: String,
        val sha256: String,
        val snapshot: SemanticSnapshot,
    )

    private data class SemanticSnapshot(
        val schemaVersion: Int,
        val takeId: String,
        val workflow: CaptureWorkflow,
        val participantPreview: ParticipantPreview,
        val layer: MeasurementLayer,
        val framesStored: Int,
        val storageProfile: CaptureStorageProfile,
        val pixelSemantics: CapturePixelSemantics,
        val studyRunSchemaVersion: Int,
        val studyRunId: String,
        val protocolId: String,
        val runStorageProfile: CaptureStorageProfile,
    )

    private companion object {
        const val GIT_SHA_LENGTH = 40
        val FORBIDDEN_VALUE_TOKENS = listOf(
            "/private/",
            "/users/",
            "participant-id",
            "person-id",
            "device-id",
            "serial-number",
        )
        val SCHEMA_3 = Golden(
            name = "take-manifest-schema-3.json",
            sha256 = "86b51a983b06e8db6f9b74b2f39322853249f55b2792a1e0a3bcc2e8b67f3890",
            snapshot = SemanticSnapshot(
                3,
                "synthetic-schema-3",
                CaptureWorkflow.LEGACY_MANUAL_V1,
                ParticipantPreview.NONE,
                MeasurementLayer.HOST_OFFLINE,
                0,
                CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
                CapturePixelSemantics.LEGACY_DIAGNOSTIC_ONLY,
                0,
                StudyRunIdentity.LEGACY_RUN_ID,
                StudyRunIdentity.LEGACY_PROTOCOL_ID,
                CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
            ),
        )
        val SCHEMA_4 = Golden(
            name = "take-manifest-schema-4.json",
            sha256 = "5d50ee10eec2e8076912c8304149045eeca3c73eeb82aa277a9a132e33c5e5d4",
            snapshot = SemanticSnapshot(
                4,
                "synthetic-schema-4",
                CaptureWorkflow.LEGACY_MANUAL_V1,
                ParticipantPreview.NONE,
                MeasurementLayer.HOST_OFFLINE,
                0,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                CapturePixelSemantics.NONE,
                0,
                StudyRunIdentity.LEGACY_RUN_ID,
                StudyRunIdentity.LEGACY_PROTOCOL_ID,
                CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
            ),
        )
        val SCHEMA_5 = Golden(
            name = "take-manifest-schema-5.json",
            sha256 = "7132b0ef17d8aac891b5850c63a3978ea5dfc9e356586922932b594f9388818a",
            snapshot = SemanticSnapshot(
                5,
                "synthetic-schema-5",
                CaptureWorkflow.GUIDED_GATE_AUTOSTART_V1,
                ParticipantPreview.NONE,
                MeasurementLayer.HOST_OFFLINE,
                0,
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                CapturePixelSemantics.NONE,
                StudyRunIdentity.SCHEMA_VERSION,
                "synthetic-schema-5-run",
                "SYNTHETIC_CONTRACT_V1",
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
            ),
        )
        val GOLDENS = listOf(SCHEMA_3, SCHEMA_4, SCHEMA_5)
    }
}
