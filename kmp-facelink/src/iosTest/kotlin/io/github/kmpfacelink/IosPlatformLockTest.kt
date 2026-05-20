package io.github.kmpfacelink

import io.github.kmpfacelink.util.PlatformLock
import io.github.kmpfacelink.util.withLock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for iOS [PlatformLock] backed by NSRecursiveLock.
 */
class IosPlatformLockTest {

    @Test
    fun lockCanBeCreated() {
        val lock = PlatformLock()
        assertNotNull(lock)
    }

    @Test
    fun basicLockAndUnlock() {
        val lock = PlatformLock()
        lock.lock()
        lock.unlock()
        // No exception means success
    }

    @Test
    fun withLockExecutesBlock() {
        val lock = PlatformLock()
        var executed = false
        lock.withLock {
            executed = true
        }
        assertEquals(true, executed)
    }

    @Test
    fun withLockReturnsValue() {
        val lock = PlatformLock()
        val result = lock.withLock { 42 }
        assertEquals(42, result)
    }

    @Test
    fun reentrantLockDoesNotDeadlock() {
        // iOS PlatformLock uses NSRecursiveLock which supports reentrancy
        val lock = PlatformLock()
        lock.withLock {
            lock.withLock {
                // Inner lock acquired without deadlock
            }
        }
    }

    @Test
    fun withLockUnlocksOnException() {
        val lock = PlatformLock()
        try {
            lock.withLock {
                throw RuntimeException("test error")
            }
        } catch (_: RuntimeException) {
            // Expected
        }
        // Lock should be released; acquiring it again should succeed
        lock.withLock {
            // No deadlock means the lock was properly released
        }
    }
}
