@file:OptIn(ExperimentalForeignApi::class)

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
import platform.Foundation.NSFileManager
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.gmtime_r
import platform.posix.strftime
import platform.posix.timespec
import platform.posix.tm

/** Publishes only takes whose payload, results and final manifest all completed. */
internal class IosCaptureStore(rootDirectory: String) {
    private val root = ensureCaptureDirectory(joinCapturePath(rootDirectory, ROOT_DIR))
    val takesRoot: String = ensureCaptureDirectory(joinCapturePath(root, TAKES_DIR))
    private val tmpRoot: String = ensureCaptureDirectory(joinCapturePath(root, TMP_DIR))

    fun newTakeId(scenario: CaptureScenario, attempt: Int, startedAtMs: Long): String =
        "${takeTimestamp(startedAtMs)}_${scenario.id}_$attempt"

    fun createTmpDir(takeId: String): String {
        val path = joinCapturePath(tmpRoot, takeId)
        check(!NSFileManager.defaultManager.fileExistsAtPath(path)) { "take $takeId already exists" }
        return ensureCaptureDirectory(path)
    }

    fun publish(tmpDirectory: String, takeId: String) {
        val target = joinCapturePath(takesRoot, takeId)
        check(!NSFileManager.defaultManager.fileExistsAtPath(target)) { "take $takeId is already published" }
        check(moveCapturePath(tmpDirectory, target)) { "could not publish $tmpDirectory to $target" }
    }

    fun discard(tmpDirectory: String) {
        check(removeCapturePath(tmpDirectory)) { "could not discard incomplete take $tmpDirectory" }
    }

    @Suppress("UNCHECKED_CAST")
    fun publishedTakeNames(): List<String> =
        NSFileManager.defaultManager.contentsOfDirectoryAtPath(takesRoot, error = null) as? List<String> ?: emptyList()

    private fun takeTimestamp(millis: Long): String = formatUtc(millis, "%Y%m%d-%H%M%S")

    private companion object {
        const val ROOT_DIR = "capture"
        const val TAKES_DIR = "takes"
        const val TMP_DIR = "tmp"
    }
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
