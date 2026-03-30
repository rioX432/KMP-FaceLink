package io.github.kmpfacelink.util

import platform.Foundation.NSThread

internal actual fun currentThreadId(): Long = NSThread.currentThread.hash.toLong()
