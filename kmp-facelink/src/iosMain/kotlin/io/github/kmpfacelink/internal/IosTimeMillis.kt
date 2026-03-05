@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Returns the current wall-clock time in milliseconds since the Unix epoch.
 *
 * Uses [clock_gettime] with [CLOCK_REALTIME] to avoid the floating-point precision
 * loss of `NSDate.timeIntervalSince1970 * 1000`.
 */
internal fun currentTimeMillis(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME.toUInt(), ts.ptr)
    ts.tv_sec * 1000L + ts.tv_nsec / NANOS_PER_MILLI
}
