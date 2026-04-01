package io.github.kmpfacelink.util

import android.util.Log

public actual object FaceLinkLogger {
    public actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }

    public actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    public actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }
}
