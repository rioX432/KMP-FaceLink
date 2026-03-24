package io.github.kmpfacelink.networking.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiscoveredServiceTest {

    @Test
    fun createWithAllFields() {
        val service = DiscoveredService(
            name = "My VTube Studio",
            type = "_ifacialmocap._udp.",
            host = "192.168.1.100",
            port = 49983,
        )
        assertEquals("My VTube Studio", service.name)
        assertEquals("_ifacialmocap._udp.", service.type)
        assertEquals("192.168.1.100", service.host)
        assertEquals(49983, service.port)
    }

    @Test
    fun createWithNullHost() {
        val service = DiscoveredService(
            name = "Unresolved",
            type = "_kmpfacelink._udp.",
            host = null,
            port = 0,
        )
        assertNull(service.host)
        assertEquals(0, service.port)
    }

    @Test
    fun equalityByContent() {
        val a = DiscoveredService("A", "_vmc._udp.", "10.0.0.1", 39539)
        val b = DiscoveredService("A", "_vmc._udp.", "10.0.0.1", 39539)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
