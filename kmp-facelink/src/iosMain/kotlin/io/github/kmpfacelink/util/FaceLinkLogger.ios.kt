package io.github.kmpfacelink.util

public actual object FaceLinkLogger {
    public actual fun w(tag: String, message: String, throwable: Throwable?) {
        log("W", tag, message, throwable)
    }

    public actual fun e(tag: String, message: String, throwable: Throwable?) {
        log("E", tag, message, throwable)
    }

    public actual fun d(tag: String, message: String) {
        log("D", tag, message, null)
    }

    /**
     * NSLog's "%@" vararg substitution crashes intermittently when passed Kotlin
     * String objects: the ObjC runtime's description lookup (objc_opt_respondsToSelector)
     * races with Kotlin/Native's GC over the string's memory layout, causing SIGSEGV.
     * println() goes through the Kotlin/Native runtime directly with no ObjC interop,
     * avoiding the crash entirely.
     */
    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val suffix = if (throwable != null) " — $throwable" else ""
        println("[$level] $tag: $message$suffix")
    }
}
