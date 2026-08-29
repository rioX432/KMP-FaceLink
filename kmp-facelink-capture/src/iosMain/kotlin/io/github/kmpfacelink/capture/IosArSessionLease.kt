package io.github.kmpfacelink.capture

import platform.Foundation.NSLock

/** Process-local guard preventing the capture and diagnostic tools from owning ARSession together. */
internal object IosArSessionLeaseRegistry {
    private data class ActiveLease(val token: ULong, val owner: String)

    private val lock = NSLock()
    private var nextToken: ULong = 1u
    private var active: ActiveLease? = null

    fun acquire(owner: String): IosArSessionLease = lock.withCaptureLock {
        require(owner.isNotBlank()) { "ARSession lease owner must not be blank" }
        check(active == null) {
            "ARSession is already owned by ${active?.owner}; release it before starting $owner"
        }
        val lease = ActiveLease(nextToken++, owner)
        active = lease
        IosArSessionLease(lease.token)
    }

    fun release(token: ULong) {
        lock.withCaptureLock {
            val current = active ?: return@withCaptureLock
            check(current.token == token) { "ARSession lease token does not own the active session" }
            active = null
        }
    }

    internal fun activeOwner(): String? = lock.withCaptureLock { active?.owner }
}

internal class IosArSessionLease(private val token: ULong) {
    private var released = false

    fun release() {
        if (released) return
        IosArSessionLeaseRegistry.release(token)
        released = true
    }
}
