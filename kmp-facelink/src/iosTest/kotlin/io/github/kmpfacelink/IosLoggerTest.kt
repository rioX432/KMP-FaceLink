package io.github.kmpfacelink

import io.github.kmpfacelink.util.FaceLinkLogger
import kotlin.test.Test

/**
 * Tests for iOS [FaceLinkLogger] backed by NSLog.
 *
 * These tests verify that logging calls do not throw on iOS.
 * Actual log output goes to NSLog and cannot be captured here.
 */
class IosLoggerTest {

    @Test
    fun debugLogDoesNotThrow() {
        FaceLinkLogger.d("TestTag", "debug message")
    }

    @Test
    fun warningLogDoesNotThrow() {
        FaceLinkLogger.w("TestTag", "warning message")
    }

    @Test
    fun warningLogWithThrowableDoesNotThrow() {
        FaceLinkLogger.w("TestTag", "warning with error", RuntimeException("test"))
    }

    @Test
    fun errorLogDoesNotThrow() {
        FaceLinkLogger.e("TestTag", "error message")
    }

    @Test
    fun errorLogWithThrowableDoesNotThrow() {
        FaceLinkLogger.e("TestTag", "error with cause", IllegalStateException("test error"))
    }
}
