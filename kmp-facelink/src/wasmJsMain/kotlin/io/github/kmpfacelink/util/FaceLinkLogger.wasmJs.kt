package io.github.kmpfacelink.util

/**
 * Wasm/JS logger delegating to `console.warn`, `console.error`, and `console.log`.
 */
internal actual object FaceLinkLogger {
    actual fun w(tag: String, message: String, throwable: Throwable?) {
        val msg = "[$tag] $message"
        if (throwable != null) {
            console.warn("$msg: ${throwable.message}")
        } else {
            console.warn(msg)
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        val msg = "[$tag] $message"
        if (throwable != null) {
            console.error("$msg: ${throwable.message}")
        } else {
            console.error(msg)
        }
    }

    actual fun d(tag: String, message: String) {
        console.log("[$tag] $message")
    }
}

private external object console {
    fun warn(message: String)
    fun error(message: String)
    fun log(message: String)
}
