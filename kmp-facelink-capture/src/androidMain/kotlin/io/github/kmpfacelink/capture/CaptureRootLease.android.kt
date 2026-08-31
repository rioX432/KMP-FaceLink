package io.github.kmpfacelink.capture

internal actual fun acquireCaptureRootLease(canonicalRoot: String): CaptureRootLease =
    AndroidCaptureRootLeases.acquire(canonicalRoot)

private object AndroidCaptureRootLeases {
    private val roots = mutableSetOf<String>()

    fun acquire(root: String): CaptureRootLease = synchronized(roots) {
        if (!roots.add(root)) throw captureFailure(CaptureFailureCategory.ROOT_IN_USE)
        AndroidCaptureRootLease(root)
    }

    private class AndroidCaptureRootLease(private val root: String) : CaptureRootLease {
        private var released = false

        override fun release() = synchronized(roots) {
            if (!released) {
                check(roots.remove(root)) { "capture root lease was not registered" }
                released = true
            }
        }
    }
}
