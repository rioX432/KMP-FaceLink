package io.github.kmpfacelink.util

import java.util.logging.Level
import java.util.logging.Logger

internal actual object FaceLinkLogger {
    private val logger: Logger = Logger.getLogger("FaceLink")

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.log(Level.WARNING, "[$tag] $message", throwable)
        } else {
            logger.warning("[$tag] $message")
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.log(Level.SEVERE, "[$tag] $message", throwable)
        } else {
            logger.severe("[$tag] $message")
        }
    }

    actual fun d(tag: String, message: String) {
        logger.fine("[$tag] $message")
    }
}
