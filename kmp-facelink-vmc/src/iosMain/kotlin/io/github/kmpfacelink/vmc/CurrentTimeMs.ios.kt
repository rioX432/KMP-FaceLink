package io.github.kmpfacelink.vmc

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentTimeMs(): Long =
    (NSDate().timeIntervalSince1970 * MS_PER_SEC).toLong()

private const val MS_PER_SEC = 1000.0
