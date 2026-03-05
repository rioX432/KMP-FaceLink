@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.stream

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.CLOCK_REALTIME
import platform.posix.clock_gettime
import platform.posix.timespec

private const val NANOS_PER_MILLI = 1_000_000L

internal actual fun currentTimeMillis(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_REALTIME, ts.ptr)
    ts.tv_sec * 1000L + ts.tv_nsec / NANOS_PER_MILLI
}
