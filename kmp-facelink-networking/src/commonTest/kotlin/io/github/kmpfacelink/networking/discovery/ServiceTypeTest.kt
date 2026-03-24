package io.github.kmpfacelink.networking.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceTypeTest {

    @Test
    fun kmpFaceLinkServiceType() {
        assertEquals("_kmpfacelink._udp.", ServiceType.KMP_FACELINK)
    }

    @Test
    fun ifacialMocapServiceType() {
        assertEquals("_ifacialmocap._udp.", ServiceType.IFACIALMOCAP)
    }

    @Test
    fun vtubeStudioServiceType() {
        assertEquals("_vstudio._tcp.", ServiceType.VTUBE_STUDIO)
    }

    @Test
    fun vmcProtocolServiceType() {
        assertEquals("_vmc._udp.", ServiceType.VMC_PROTOCOL)
    }

    @Test
    fun allContainsAllServiceTypes() {
        val all = ServiceType.ALL
        assertTrue(all.contains(ServiceType.KMP_FACELINK))
        assertTrue(all.contains(ServiceType.IFACIALMOCAP))
        assertTrue(all.contains(ServiceType.VTUBE_STUDIO))
        assertTrue(all.contains(ServiceType.VMC_PROTOCOL))
        assertEquals(4, all.size)
    }

    @Test
    fun serviceTypesFollowDnsSdConvention() {
        for (type in ServiceType.ALL) {
            assertTrue(type.startsWith("_"), "Service type must start with underscore: $type")
            assertTrue(
                type.endsWith("._udp.") || type.endsWith("._tcp."),
                "Service type must end with ._udp. or ._tcp.: $type",
            )
        }
    }
}
