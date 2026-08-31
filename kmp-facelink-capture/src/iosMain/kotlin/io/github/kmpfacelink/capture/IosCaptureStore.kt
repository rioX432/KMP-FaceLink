@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress(
    // Root ownership must be released for every constructor failure, regardless of whether the
    // failing operation was directory I/O, manifest decoding, or stale-take recovery.
    "TooGenericExceptionCaught",
    "InstanceOfCheckForException",
)

package io.github.kmpfacelink.capture

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.gmtime_r
import platform.posix.strftime
import platform.posix.timespec
import platform.posix.tm

/** Publishes only takes whose payload, results and final manifest all completed. */
internal class IosCaptureStore(
    rootDirectory: String,
    protocolId: String,
    storageProfile: CaptureStorageProfile,
) {
    private val root = try {
        ensureCaptureDirectory(joinCapturePath(rootDirectory, ROOT_DIR))
    } catch (failure: Throwable) {
        if (failure is Error) throw failure
        throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
    }
    private val rootLease = acquireCaptureRootLease(root)
    val studyRun: StudyRunIdentity
    val takesRoot: String
    private val tmpRoot: String
    internal val recoveredIncompleteTakeCount: Int

    init {
        try {
            studyRun = loadOrCreateStudyRun(protocolId, storageProfile)
            takesRoot = ensureCaptureDirectory(joinCapturePath(root, TAKES_DIR))
            tmpRoot = ensureCaptureDirectory(joinCapturePath(root, TMP_DIR))
            recoveredIncompleteTakeCount = recoverIosIncompleteTakes(tmpRoot)
        } catch (failure: Throwable) {
            runCatching { rootLease.release() }.onFailure(failure::addSuppressed)
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
        }
    }

    fun newTakeId(scenario: CaptureScenario, attempt: Int, startedAtMs: Long): String =
        "${takeTimestamp(startedAtMs)}_${scenario.id}_$attempt"

    fun createTmpDir(takeId: String): String {
        try {
            val path = joinCapturePath(tmpRoot, takeId)
            check(!NSFileManager.defaultManager.fileExistsAtPath(path)) { "take $takeId already exists" }
            return ensureCaptureDirectory(path)
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STORAGE_SETUP, failure)
        }
    }

    fun publish(tmpDirectory: String, takeId: String) {
        try {
            val target = joinCapturePath(takesRoot, takeId)
            check(!NSFileManager.defaultManager.fileExistsAtPath(target)) { "take $takeId is already published" }
            check(moveCapturePath(tmpDirectory, target)) { "could not publish $tmpDirectory to $target" }
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.STREAM_WRITE, failure)
        }
    }

    fun discard(tmpDirectory: String) {
        try {
            check(removeCapturePath(tmpDirectory)) { "could not discard incomplete take $tmpDirectory" }
        } catch (failure: Throwable) {
            if (failure is Error || failure is CaptureFailureException) throw failure
            throw captureFailure(CaptureFailureCategory.CLEANUP, failure)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun publishedTakeNames(): List<String> =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(takesRoot, error = null) as? List<String> ?: emptyList()

    fun close() {
        rootLease.release()
    }

    private fun loadOrCreateStudyRun(
        protocolId: String,
        storageProfile: CaptureStorageProfile,
    ): StudyRunIdentity {
        val path = joinCapturePath(root, ACTIVE_STUDY_RUN_FILE)
        val restored = if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            runCatching { rawFrameJson.decodeFromString<StudyRunIdentity>(readCaptureText(path)) }.getOrNull()
        } else {
            null
        }
        return activeOrNewStudyRun(restored, protocolId, storageProfile) {
            StudyRunIdentity(
                schemaVersion = StudyRunIdentity.SCHEMA_VERSION,
                runId = NSUUID().UUIDString,
                protocolId = protocolId,
                storageProfile = storageProfile,
                createdAtIso = captureIsoTimestamp(captureCurrentTimeMillis()),
            ).also { created ->
                publishCaptureText(root, ACTIVE_STUDY_RUN_FILE, rawFrameJson.encodeToString(created))
            }
        }
    }

    private fun takeTimestamp(millis: Long): String = formatUtc(millis, "%Y%m%d-%H%M%S")

    private companion object {
        const val ROOT_DIR = "capture"
        const val TAKES_DIR = "takes"
        const val TMP_DIR = "tmp"
        const val ACTIVE_STUDY_RUN_FILE = "active-study-run-v1.json"
    }
}

/** Reclaims only entries inside the capture-owned tmp root; published takes are never scanned. */
@Suppress("UNCHECKED_CAST")
internal fun recoverIosIncompleteTakes(tmpRoot: String): Int {
    val names = NSFileManager.defaultManager.contentsOfDirectoryAtPath(tmpRoot, error = null) as? List<String>
        ?: emptyList()
    names.forEach { name ->
        require('/' !in name && name != "." && name != "..") { "invalid incomplete take name: $name" }
        val path = joinCapturePath(tmpRoot, name)
        check(removeCapturePath(path)) { "could not recover incomplete take $path" }
    }
    return names.size
}

internal fun captureCurrentTimeMillis(): Long = memScoped {
    val value = alloc<timespec>()
    check(clock_gettime(CLOCK_REALTIME.toUInt(), value.ptr) == 0) {
        "could not read the wall clock for capture provenance"
    }
    value.tv_sec * MS_PER_SECOND + value.tv_nsec / NS_PER_MILLISECOND
}

internal fun captureIsoTimestamp(millis: Long): String = formatUtc(millis, "%Y-%m-%dT%H:%M:%SZ")

private fun formatUtc(millis: Long, pattern: String): String = memScoped {
    val seconds = alloc<LongVar>()
    seconds.ptr[0] = millis / MS_PER_SECOND
    val utc = alloc<tm>()
    checkNotNull(gmtime_r(seconds.ptr, utc.ptr)) { "could not convert capture timestamp" }
    val buffer = allocArray<ByteVar>(TIMESTAMP_BUFFER_BYTES)
    check(strftime(buffer, TIMESTAMP_BUFFER_BYTES.toULong(), pattern, utc.ptr) > 0uL) {
        "could not format capture timestamp"
    }
    buffer.toKString()
}

private const val MS_PER_SECOND = 1_000L
private const val NS_PER_MILLISECOND = 1_000_000L
private const val TIMESTAMP_BUFFER_BYTES = 32
