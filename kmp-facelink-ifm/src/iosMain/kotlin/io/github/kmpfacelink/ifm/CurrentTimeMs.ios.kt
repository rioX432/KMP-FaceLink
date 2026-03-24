package io.github.kmpfacelink.ifm

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@Suppress("MagicNumber")
internal actual fun currentTimeMs(): Long =
    (NSDate().timeIntervalSince1970 * 1000.0).toLong()
