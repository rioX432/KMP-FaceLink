package io.github.kmpfacelink.networking.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Android [ServiceDiscovery] implementation backed by [NsdManager].
 *
 * Requires a [Context] to obtain the system [NsdManager] service.
 * All state mutations are synchronized to prevent race conditions.
 *
 * @param context Android context for accessing NsdManager system service
 */
public actual class ServiceDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _events = MutableStateFlow<DiscoveryEvent?>(null)
    public actual val events: StateFlow<DiscoveryEvent?> = _events.asStateFlow()

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    public actual val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    public actual val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    public actual fun startDiscovery(serviceType: String) {
        val listener = createDiscoveryListener(serviceType)
        synchronized(activeListeners) {
            activeListeners.add(listener)
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    public actual fun startDiscoveryAll() {
        ServiceType.ALL.forEach { type -> startDiscovery(type) }
    }

    public actual fun stopDiscovery() {
        synchronized(activeListeners) {
            activeListeners.forEach { listener ->
                runCatching { nsdManager.stopServiceDiscovery(listener) }
            }
            activeListeners.clear()
        }
        _isDiscovering.value = false
        _services.value = emptyList()
    }

    private fun createDiscoveryListener(serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isDiscovering.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                handleServiceLost(serviceInfo, serviceType)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                updateDiscoveringState()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _events.value = DiscoveryEvent.Error(
                    "Discovery start failed for $serviceType (error=$errorCode)"
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _events.value = DiscoveryEvent.Error(
                    "Discovery stop failed for $serviceType (error=$errorCode)"
                )
            }
        }
    }

    private fun handleServiceLost(serviceInfo: NsdServiceInfo, serviceType: String) {
        val name = serviceInfo.serviceName
        synchronized(_services) {
            _services.value = _services.value.filter { it.name != name }
        }
        _events.value = DiscoveryEvent.Lost(name = name, type = serviceType)
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveServiceModern(serviceInfo)
        } else {
            resolveServiceLegacy(serviceInfo)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceLegacy(serviceInfo: NsdServiceInfo) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                _events.value = DiscoveryEvent.Error(
                    "Resolve failed for ${info.serviceName} (error=$errorCode)"
                )
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                handleServiceResolved(info)
            }
        }
        nsdManager.resolveService(serviceInfo, listener)
    }

    private fun resolveServiceModern(serviceInfo: NsdServiceInfo) {
        val executor = Executors.newSingleThreadExecutor()
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                _events.value = DiscoveryEvent.Error(
                    "Resolve registration failed for ${serviceInfo.serviceName} (error=$errorCode)"
                )
            }

            override fun onServiceUpdated(info: NsdServiceInfo) {
                handleServiceResolved(info)
                nsdManager.unregisterServiceInfoCallback(this)
            }

            override fun onServiceLost() {
                // Service disappeared during resolution; nothing to do
            }

            override fun onServiceInfoCallbackUnregistered() {
                // Callback unregistered; nothing to do
            }
        }
        nsdManager.registerServiceInfoCallback(serviceInfo, executor, callback)
    }

    private fun handleServiceResolved(info: NsdServiceInfo) {
        val discovered = DiscoveredService(
            name = info.serviceName,
            type = info.serviceType,
            host = info.host?.hostAddress,
            port = info.port,
        )
        addService(discovered)
        _events.value = DiscoveryEvent.Found(discovered)
    }

    private fun addService(service: DiscoveredService) {
        synchronized(_services) {
            val current = _services.value.toMutableList()
            current.removeAll { it.name == service.name && it.type == service.type }
            current.add(service)
            _services.value = current
        }
    }

    private fun updateDiscoveringState() {
        synchronized(activeListeners) {
            _isDiscovering.value = activeListeners.isNotEmpty()
        }
    }
}
