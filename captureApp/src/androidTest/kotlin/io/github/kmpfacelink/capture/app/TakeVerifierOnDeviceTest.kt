package io.github.kmpfacelink.capture.app

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.capture.AndroidCaptureSession
import io.github.kmpfacelink.capture.CaptureCondition
import io.github.kmpfacelink.capture.CapturePhase
import io.github.kmpfacelink.capture.CapturePixelSemantics
import io.github.kmpfacelink.capture.CaptureStorageProfile
import io.github.kmpfacelink.capture.CaptureStorageReceipt
import io.github.kmpfacelink.capture.InferenceRecord
import io.github.kmpfacelink.capture.InferenceRecordFormat
import io.github.kmpfacelink.capture.MeasurementLayer
import io.github.kmpfacelink.capture.RawFrame
import io.github.kmpfacelink.capture.RawFrameFormat
import io.github.kmpfacelink.capture.SourceProvenance
import io.github.kmpfacelink.capture.StudyRunIdentity
import io.github.kmpfacelink.capture.TakeManifest
import io.github.kmpfacelink.capture.TakeVerifier
import io.github.kmpfacelink.capture.openInferenceRecordSink
import io.github.kmpfacelink.capture.openRawFrameSink
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * That the verifier actually joins a replay to the take it came from.
 *
 * Built rather than recorded: a synthetic take with a known sparse result stream is the only way to
 * assert the join arithmetic, because a real take's dropped frames are up to the engine. The pixels
 * are noise, so neither side finds a face — which is itself the assertion worth making here, since
 * agreeing on "no face" is agreement, and the count of joined frames is what proves the comparison
 * ran at all rather than skipping every frame.
 */
@RunWith(AndroidJUnit4::class)
class TakeVerifierOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aReplayIsJoinedToTheLiveResultsBySequence() {
        val width = 240
        val height = 320
        val takeDir = File(context.filesDir, "verifier-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val framesDir = File(takeDir, AndroidCaptureSession.FRAMES_DIR)

        val admitted = (0 until 6).toList()
        val answered = listOf(0, 2, 3, 5) // LIVE_STREAM declined two of them
        val frames = openRawFrameSink(framesDir.absolutePath, width, height)
        val results = openInferenceRecordSink(takeDir.absolutePath)
        admitted.forEach { seq ->
            frames.write(
                RawFrame(
                    width = width,
                    height = height,
                    analysisFrameSeq = seq,
                    captureTimestampNs = 33_000_000L * seq,
                    inferenceTimestampMs = 33L * seq,
                    phase = CapturePhase.ACTED,
                    pixels = Random(seq).nextBytes(width * height * RawFrameFormat.BYTES_PER_PIXEL),
                ),
            )
            if (seq !in answered) return@forEach
            results.write(
                InferenceRecord(
                    analysisFrameSeq = seq,
                    captureTimestampNs = 33_000_000L * seq,
                    resultElapsedNs = 33_000_000L * seq + 5_000_000L,
                    phase = CapturePhase.ACTED,
                    hasFace = false, // noise: the live pipeline would find nothing here either
                    inferenceFailed = false,
                    landmarks = null,
                    blendshapes = null,
                    transform = null,
                ),
            )
        }
        frames.close()
        results.close()
        writeManifest(takeDir, results.hashes())

        val report = TakeVerifier(PlatformContext(context, DetachedLifecycleOwner()))
            .verify(takeDir, InferenceRunningMode.IMAGE)

        assertEquals(admitted.size, report.storedFrames)
        assertEquals(admitted.size, report.replayedFrames)
        assertEquals(answered.size, report.liveResults)
        // The line the static analyser called unreachable. If the join never ran this would be 0.
        assertEquals(answered.size, report.joined)
        assertTrue("replay and live disagreed about the absence of a face", report.faceAgreement == 1f)
        assertTrue(report.summary().contains("joined=${answered.size}"))
        takeDir.deleteRecursively()
    }

    private fun writeManifest(dir: File, hashes: List<String>) {
        val manifest = TakeManifest(
            schemaVersion = TakeManifest.SCHEMA_VERSION,
            takeId = "verifier-test",
            scenarioId = "cheek_puff",
            attempt = 1,
            startedAtIso = "2026-08-18T00:00:00Z",
            condition = CaptureCondition(
                nativeResolution = "240x320",
                runningMode = InferenceRunningMode.LIVE_STREAM.name,
                delegate = "GPU",
                numFaces = 1,
                minDetectionConfidence = 0.5f,
                minTrackingConfidence = 0.5f,
                minPresenceConfidence = 0.5f,
                mediapipeVersion = "0.10.35",
                modelSha256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                mirrored = true,
                layer = MeasurementLayer.DEVICE_REALTIME,
                provenance = SourceProvenance("KMP-FaceLink", "test", "0".repeat(40), "2026-08-18T00:00:00Z", true),
            ),
            analysisFramesAdmitted = 6,
            inferenceResults = 4,
            noFaceResults = 4,
            inferenceFailedResults = 0,
            inferenceErrors = 0,
            framesStored = 6,
            softValidationPassed = true,
            softValidationReason = "time_only",
            medianIod = 0f,
            outOfDistanceBandFraction = 0f,
            spans = emptyList(),
            blendshapeNames = emptyList(),
            resultIndexSha256 = hashes[0],
            landmarksSha256 = hashes[1],
            blendshapesSha256 = hashes[2],
            transformSha256 = hashes[3],
            storageReceipt = CaptureStorageReceipt(
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                CapturePixelSemantics.EXACT_ANDROID_INFERENCE_INPUT,
                240,
                320,
                240,
                320,
                "TEST",
                "TEST",
                "TEST",
            ),
            studyRun = StudyRunIdentity(
                StudyRunIdentity.SCHEMA_VERSION,
                "run-test",
                StudyRunIdentity.GUIDED_PROTOCOL_ID,
                CaptureStorageProfile.CANONICAL_RGBA_240X320_V1,
                "2026-08-18T00:00:00Z",
            ),
        )
        File(dir, InferenceRecordFormat.TAKE_MANIFEST_FILE)
            .writeText(Json.encodeToString(manifest))
    }

    /** Created unsafely on purpose: this runs off the main thread and nothing observes it. */
    private class DetachedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
