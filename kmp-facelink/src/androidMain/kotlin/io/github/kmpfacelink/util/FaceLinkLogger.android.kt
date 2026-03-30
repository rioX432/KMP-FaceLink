package io.github.kmpfacelink.util

import android.util.Log

internal actual object FaceLinkLogger {
    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
