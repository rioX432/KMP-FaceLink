package io.github.kmpfacelink.util

import platform.Foundation.NSLog

public actual object FaceLinkLogger {
    public actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[W] %@: %@ — %@", tag, message, throwable.toString())
        } else {
            NSLog("[W] %@: %@", tag, message)
        }
    }

    public actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("[E] %@: %@ — %@", tag, message, throwable.toString())
        } else {
            NSLog("[E] %@: %@", tag, message)
        }
    }

    public actual fun d(tag: String, message: String) {
        NSLog("[D] %@: %@", tag, message)
    }
}
