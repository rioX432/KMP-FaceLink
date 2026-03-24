@file:OptIn(ExperimentalForeignApi::class)

package io.github.kmpfacelink.networking.discovery

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.darwin.NSObject

/**
 * iOS [ServiceDiscovery] implementation backed by [NSNetServiceBrowser] (Bonjour).
 *
 * All delegate callbacks are dispatched on the main run loop.
 * State mutations are thread-safe via atomic StateFlow updates.
 */
public actual class ServiceDiscovery {

    private val _events = MutableStateFlow<DiscoveryEvent?>(null)
    public actual val events: StateFlow<DiscoveryEvent?> = _events.asStateFlow()

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    public actual val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    public actual val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val activeBrowsers = mutableListOf<NSNetServiceBrowser>()
    private val pendingResolves = mutableSetOf<NSNetService>()
    private val browserDelegate = BrowserDelegate()

    public actual fun startDiscovery(serviceType: String) {
        val browser = NSNetServiceBrowser()
        browser.delegate = browserDelegate
        browser.scheduleInRunLoop(NSRunLoop.mainRunLoop, NSRunLoopCommonModes)
        activeBrowsers.add(browser)
        browser.searchForServicesOfType(serviceType, inDomain = "local.")
    }

    public actual fun startDiscoveryAll() {
        ServiceType.ALL.forEach { type -> startDiscovery(type) }
    }

    public actual fun stopDiscovery() {
        activeBrowsers.forEach { browser ->
            browser.stop()
            browser.delegate = null
        }
        activeBrowsers.clear()
        pendingResolves.forEach { it.stop() }
        pendingResolves.clear()
        _isDiscovering.value = false
        _services.value = emptyList()
    }

    private fun resolveService(netService: NSNetService) {
        val resolveDelegate = ResolveDelegate(netService)
        netService.delegate = resolveDelegate
        pendingResolves.add(netService)
        netService.resolveWithTimeout(RESOLVE_TIMEOUT)
    }

    private fun addService(service: DiscoveredService) {
        val current = _services.value.toMutableList()
        current.removeAll { it.name == service.name && it.type == service.type }
        current.add(service)
        _services.value = current
    }

    private fun removeService(name: String, type: String) {
        _services.value = _services.value.filter {
            !(it.name == name && it.type == type)
        }
        _events.value = DiscoveryEvent.Lost(name = name, type = type)
    }

    private inner class BrowserDelegate : NSObject(), NSNetServiceBrowserDelegateProtocol {

        override fun netServiceBrowserWillSearch(browser: NSNetServiceBrowser) {
            _isDiscovering.value = true
        }

        override fun netServiceBrowserDidStopSearch(browser: NSNetServiceBrowser) {
            _isDiscovering.value = activeBrowsers.any { it !== browser }
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean,
        ) {
            resolveService(didFindService)
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didRemoveService: NSNetService,
            moreComing: Boolean,
        ) {
            removeService(
                name = didRemoveService.name,
                type = didRemoveService.type,
            )
        }

        @ObjCSignatureOverride
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didNotSearch: Map<Any?, *>,
        ) {
            val errorMsg = didNotSearch.entries.joinToString { "${it.key}=${it.value}" }
            _events.value = DiscoveryEvent.Error("Discovery failed: $errorMsg")
        }
    }

    private inner class ResolveDelegate(
        private val netService: NSNetService,
    ) : NSObject(), NSNetServiceDelegateProtocol {

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            pendingResolves.remove(netService)
            val host = sender.hostName
            val discovered = DiscoveredService(
                name = sender.name,
                type = sender.type,
                host = host,
                port = sender.port.toInt(),
            )
            addService(discovered)
            _events.value = DiscoveryEvent.Found(discovered)
        }

        override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
            pendingResolves.remove(netService)
            _events.value = DiscoveryEvent.Error(
                "Resolve failed for ${sender.name}: $didNotResolve"
            )
        }
    }

    private companion object {
        const val RESOLVE_TIMEOUT = 5.0
    }
}
