package io.github.kmpfacelink.capture.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kmpfacelink.api.OfflineFrame
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.openOfflineFaceInference
import io.github.kmpfacelink.capture.AndroidCaptureSession
import io.github.kmpfacelink.capture.CapturePhase
import io.github.kmpfacelink.capture.RawFrame
import io.github.kmpfacelink.capture.RawFrameFormat
import io.github.kmpfacelink.capture.RawFrameReader
import io.github.kmpfacelink.capture.openRawFrameSink
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.InferenceRunningMode
import io.github.kmpfacelink.model.PerformanceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Whether a recorded take can be put back through inference on this device.
 *
 * This is the half of the field check that does not need a face: that the model on this phone is the
 * model the study analysed, and that frames written by the sink are accepted by inference exactly as
 * they come back off the disk. What it cannot show is agreement between a live take and its replay —
 * that needs a face in front of the camera, and the app reports it under "Verify newest take".
 */
@RunWith(AndroidJUnit4::class)
class OfflineReinferenceOnDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun theModelOnThisDeviceIsTheOneTheStudyAnalysed() {
        // The single field that makes any two takes comparable. Read from the asset rather than
        // declared: a declared version is what let an earlier study attribute numbers to a model it
        // was not running.
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(AndroidCaptureSession.DEFAULT_MODEL_ASSET).use { stream ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        assertEquals(STUDIED_MODEL_SHA256, sha)
    }

    @Test
    fun framesWrittenBySinkComeBackAndFeedInference() {
        val dir = File(context.filesDir, "reinference-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val width = 240
        val height = 320
        val sink = openRawFrameSink(dir.absolutePath, width, height)
        repeat(FRAMES) { seq ->
            sink.write(
                RawFrame(
                    width = width,
                    height = height,
                    analysisFrameSeq = seq,
                    captureTimestampNs = 33_000_000L * seq,
                    inferenceTimestampMs = 33L * seq,
                    phase = CapturePhase.ACTED,
                    // Noise, not a face: what is being checked is that inference accepts the bytes,
                    // runs on this hardware, and answers for every frame. A detection would need a
                    // real face and is the operator's step.
                    pixels = Random(seq).nextBytes(width * height * RawFrameFormat.BYTES_PER_PIXEL),
                ),
            )
        }
        sink.close()

        val reader = RawFrameReader(dir.absolutePath)
        assertEquals(FRAMES, reader.frameCount)

        val inference = openOfflineFaceInference(
            // Offline inference never touches the lifecycle owner; PlatformContext just requires
            // one, and an instrumented test has no Activity here.
            PlatformContext(context, DetachedLifecycleOwner()),
            FaceTrackerConfig(performanceConfig = PerformanceConfig(runningMode = InferenceRunningMode.IMAGE)),
        )
        var answered = 0
        try {
            reader.frames().forEach { frame ->
                val result = inference.inferMeasured(
                    OfflineFrame(
                        width = frame.width,
                        height = frame.height,
                        rgba = frame.copyPixels(),
                        timestampMs = frame.inferenceTimestampMs,
                    ),
                )
                assertEquals(
                    result.timing.estimatorCallNs + result.timing.sdkProcessToOutputReadyNs,
                    result.timing.estimatorToSdkOutputReadyNs,
                )
                assertTrue(result.contextEpoch.configurationFingerprint.isLowercaseSha256())
                assertTrue(result.contextEpoch.epochFingerprint.isLowercaseSha256())
                answered++
            }
        } finally {
            inference.release()
        }
        assertEquals(FRAMES, answered)
        dir.deleteRecursively()
    }

    @Test
    fun statefulMeasuredRunnerRejectsMirrorAndDimensionChanges() {
        val inference = openOfflineFaceInference(
            PlatformContext(context, DetachedLifecycleOwner()),
            FaceTrackerConfig(performanceConfig = PerformanceConfig(runningMode = InferenceRunningMode.IMAGE)),
        )
        try {
            inference.inferMeasured(syntheticOfflineFrame(240, 320, mirrored = false))
            val mirrorFailure = runCatching {
                inference.inferMeasured(syntheticOfflineFrame(240, 320, mirrored = true))
            }.exceptionOrNull()
            assertTrue(mirrorFailure is IllegalArgumentException)

            val dimensionFailure = runCatching {
                inference.inferMeasured(syntheticOfflineFrame(320, 240, mirrored = false))
            }.exceptionOrNull()
            assertTrue(dimensionFailure is IllegalArgumentException)
        } finally {
            inference.release()
        }
    }

    private fun syntheticOfflineFrame(width: Int, height: Int, mirrored: Boolean): OfflineFrame =
        OfflineFrame(
            width = width,
            height = height,
            rgba = Random(SYNTHETIC_SEED).nextBytes(width * height * RawFrameFormat.BYTES_PER_PIXEL),
            timestampMs = 0L,
            mirroredBeforeInference = mirrored,
        )

    private fun String.isLowercaseSha256(): Boolean =
        length == 64 && all { it in '0'..'9' || it in 'a'..'f' }

    private companion object {
        const val FRAMES = 5
        const val SYNTHETIC_SEED = 8_290

        /** The face_landmarker model a production Android host app bundles, and the one every published figure used. */
        const val STUDIED_MODEL_SHA256 =
            "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
    }

    /** Created unsafely on purpose: this runs off the main thread and nothing observes it. */
    private class DetachedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
