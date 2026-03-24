package io.github.kmpfacelink.networking.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DiscoveryEventTest {

    @Test
    fun foundEventContainsService() {
        val service = DiscoveredService(
            name = "Test",
            type = "_kmpfacelink._udp.",
            host = "192.168.1.1",
            port = 8080,
        )
        val event: DiscoveryEvent = DiscoveryEvent.Found(service)
        assertIs<DiscoveryEvent.Found>(event)
        assertEquals(service, event.service)
    }

    @Test
    fun lostEventContainsNameAndType() {
        val event: DiscoveryEvent = DiscoveryEvent.Lost(
            name = "Gone Service",
            type = "_ifacialmocap._udp.",
        )
        assertIs<DiscoveryEvent.Lost>(event)
        assertEquals("Gone Service", event.name)
        assertEquals("_ifacialmocap._udp.", event.type)
    }

    @Test
    fun errorEventContainsMessage() {
        val event: DiscoveryEvent = DiscoveryEvent.Error("Network error")
        assertIs<DiscoveryEvent.Error>(event)
        assertEquals("Network error", event.message)
    }
}
