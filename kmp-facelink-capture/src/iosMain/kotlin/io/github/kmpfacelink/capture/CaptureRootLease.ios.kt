package io.github.kmpfacelink.capture

import platform.Foundation.NSLock

internal actual fun acquireCaptureRootLease(canonicalRoot: String): CaptureRootLease =
    IosCaptureRootLeases.acquire(canonicalRoot)

private object IosCaptureRootLeases {
    private val lock = NSLock()
    private val roots = mutableSetOf<String>()

    fun acquire(root: String): CaptureRootLease {
        lock.lock()
        try {
            if (!roots.add(root)) throw captureFailure(CaptureFailureCategory.ROOT_IN_USE)
            return IosCaptureRootLease(root)
        } finally {
            lock.unlock()
        }
    }

    private class IosCaptureRootLease(private val root: String) : CaptureRootLease {
        private var released = false

        override fun release() {
            lock.lock()
            try {
                if (!released) {
                    check(roots.remove(root)) { "capture root lease was not registered" }
                    released = true
                }
            } finally {
                lock.unlock()
            }
        }
    }
}
