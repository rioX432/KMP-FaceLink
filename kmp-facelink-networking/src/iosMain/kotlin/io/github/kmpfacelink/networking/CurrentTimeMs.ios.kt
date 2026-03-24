package io.github.kmpfacelink.networking

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

private const val MS_PER_SEC = 1000.0

public actual fun currentTimeMs(): Long =
    (NSDate().timeIntervalSince1970 * MS_PER_SEC).toLong()
