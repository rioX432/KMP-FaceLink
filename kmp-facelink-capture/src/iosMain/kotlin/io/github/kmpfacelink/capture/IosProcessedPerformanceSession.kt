@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("TooGenericExceptionCaught") // Session acquisition/release must roll back every failure.

package io.github.kmpfacelink.capture

import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.api.createFaceTracker
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.PerformanceInstrumentationConfig
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.serialization.encodeToString
import platform.Foundation.NSData
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

/** Aggregate-only manifest file and its SHA-256 sidecar. */
public data class IosProcessedPerformanceArtifact(
    val manifest: IosProcessedPerformanceManifest,
    val manifestPath: String,
    val manifestSha256: String,
)

/**
 * Swift-visible execution boundary for the diagnostic session.
 *
 * A [ReviewEvidenceBinding] is deliberately not accepted here: it is a serializable artifact
 * description and callers can construct one. This factory accepts only the canonical signed raw
 * authorization and creates an internal permit after independently checking the bundled source
 * authorization, complete app-bundle digest, and P-256 signature. It also starts the session
 * before returning it, so a caller cannot create an unverified session and choose when to start.
 */
public object IosProcessedPerformanceSessionFactory {
    @Throws(Exception::class)
    public suspend fun createAndStartAuthorized(
        rootDirectory: String,
        provenance: SourceProvenance,
        runAuthorization: NSData,
    ): IosProcessedPerformanceSession {
        val permit = IosDiagnosticRunPermitVerifier.verify(runAuthorization)
        val session = IosProcessedPerformanceSession(
            rootDirectory = rootDirectory,
            provenance = provenance,
            permit = permit,
        )
        try {
            session.startFromVerifiedFactory()
            return session
        } catch (failure: Throwable) {
            session.release()
            throw failure
        }
    }
}

/**
 * Standalone iOS diagnostics session for the actual KMP-FaceLink ARKit tracker.
 *
 * It must not run beside [IosCaptureSession], because two ARSessions would compete for the front
 * camera. A host app stops/releases capture first, creates this session, starts collection, then
 * calls [stopAndWrite]. No frame, coefficient, transform, or per-callback row is written.
 */
public class IosProcessedPerformanceSession internal constructor(
    rootDirectory: String,
    private val provenance: SourceProvenance,
    private val permit: IosDiagnosticRunPermit,
    trackerConfig: FaceTrackerConfig = FaceTrackerConfig(),
) {
    private val outputDirectory = ensureCaptureDirectory(
        joinCapturePath(joinCapturePath(rootDirectory, CAPTURE_ROOT), PERFORMANCE_DIR),
    )
    private val tracker = createFaceTracker(
        PlatformContext(),
        trackerConfig.copy(
            performanceInstrumentation = PerformanceInstrumentationConfig(
                enabled = true,
                eventBufferCapacity = EVENT_CAPACITY,
            ),
        ),
    )
    private var startedAtMs: Long? = null
    private var finished = false
    private var arSessionLease: IosArSessionLease? = null

    init {
        require(!trackerConfig.calibrationConfig.enabled) {
            "iOS controlled performance session requires learned calibration disabled"
        }
    }

    @Throws(Exception::class)
    internal suspend fun startFromVerifiedFactory() {
        check(startedAtMs == null) { "performance session already started" }
        check(!finished) { "performance session already finished" }
        val lease = IosArSessionLeaseRegistry.acquire("processed performance diagnostic")
        try {
            startedAtMs = captureCurrentTimeMillis()
            tracker.start()
            arSessionLease = lease
        } catch (failure: Throwable) {
            startedAtMs = null
            finished = true
            tracker.release()
            lease.release()
            throw failure
        }
    }

    @Throws(Exception::class)
    public suspend fun stopAndWrite(): IosProcessedPerformanceArtifact {
        val started = checkNotNull(startedAtMs) { "performance session is not started" }
        check(!finished) { "performance session already finished" }
        try {
            tracker.stop()
            val events = tracker.performanceEventsSnapshot()
            val manifest = IosProcessedPerformanceAggregator.aggregate(
                runId = "ios-processed-$started",
                startedAtIso = captureIsoTimestamp(started),
                evidence = permit.evidence,
                source = provenance,
                device = IosPerformanceDeviceContext(
                    model = hardwareModelIdentifier(),
                    systemName = UIDevice.currentDevice.systemName,
                    systemVersion = UIDevice.currentDevice.systemVersion,
                ),
                events = events,
            )
            val name = "${manifest.runId}.json"
            publishCaptureText(outputDirectory, name, rawFrameJson.encodeToString(manifest))
            val path = joinCapturePath(outputDirectory, name)
            val sha = sha256OfCaptureFile(path)
            publishCaptureText(outputDirectory, "${manifest.runId}.sha256", "$sha  $name\n")
            return IosProcessedPerformanceArtifact(manifest, path, sha)
        } finally {
            finished = true
            tracker.release()
            arSessionLease?.release()
            arSessionLease = null
        }
    }

    public fun release() {
        if (!finished) tracker.release()
        arSessionLease?.release()
        arSessionLease = null
        finished = true
    }

    private companion object {
        private const val CAPTURE_ROOT = "capture"
        private const val PERFORMANCE_DIR = "performance"
        private const val EVENT_CAPACITY = 4_096
    }
}

private fun hardwareModelIdentifier(): String = memScoped {
    val value = alloc<utsname>()
    if (uname(value.ptr) == 0) value.machine.toKString() else UIDevice.currentDevice.model
}
