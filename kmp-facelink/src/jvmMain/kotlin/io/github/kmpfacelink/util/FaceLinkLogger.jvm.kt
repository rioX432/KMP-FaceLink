package io.github.kmpfacelink.util

import java.util.logging.Level
import java.util.logging.Logger

public actual object FaceLinkLogger {
    private val logger: Logger = Logger.getLogger("FaceLink")

    public actual fun w(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.log(Level.WARNING, "[$tag] $message", throwable)
        } else {
            logger.warning("[$tag] $message")
        }
    }

    public actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            logger.log(Level.SEVERE, "[$tag] $message", throwable)
        } else {
            logger.severe("[$tag] $message")
        }
    }

    public actual fun d(tag: String, message: String) {
        logger.fine("[$tag] $message")
    }
}
