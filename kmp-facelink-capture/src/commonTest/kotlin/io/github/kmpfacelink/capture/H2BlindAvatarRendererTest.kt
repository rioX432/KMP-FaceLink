package io.github.kmpfacelink.capture

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H2BlindAvatarRendererTest {
    @Test
    fun twoRoleSpecificSurfacesRasterizeBilateralStateAndRetainOneClock() {
        val clock = clock()
        val primary = checkNotNull(H2BlindAvatarRenderer(H2CandidateRole.PRIMARY).render(
            H2AvatarRouteState(true, true), clock, 0, H2ConfirmationContract.blocks[3].id, 1_100,
        ))
        val comparator = H2BlindAvatarRenderer(H2CandidateRole.COMPARATOR).render(
            H2AvatarRouteState(true, true), clock, 1, H2ConfirmationContract.blocks[3].id, 1_100,
        )!!
        assertEquals(H2BlindSurfaceLabel.A, primary.label)
        assertEquals(H2BlindSurfaceLabel.B, comparator.label)
        assertEquals(H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER, primary.event.surface)
        assertEquals(H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER, comparator.event.surface)
        assertEquals(1_100, primary.event.monotonicNanos)
        assertEquals(primary.clock, comparator.clock)
        assertTrue(!primary.rgba.contentEquals(comparator.rgba))
        assertBilateralCheeksMatch(primary)
        assertBilateralCheeksMatch(comparator)
    }

    @Test
    fun visibilityModeCanNeverReturnTwoSurfacesAndUndecidedReturnsNone() {
        val host = H2OneAtATimeAvatarHost()
        val argsClock = clock()
        assertEquals(listOf("UNDECIDED", "NEITHER", "PRIMARY_ONLY"), H2SurfaceVisibilityMode.entries.map { it.name })
        val ready = H2AvatarRouteState(active = true, available = true)
        assertNull(host.render(H2SurfaceVisibilityMode.UNDECIDED, ready, argsClock, 0, block(), 1_100))
        assertNull(host.render(H2SurfaceVisibilityMode.NEITHER, ready, argsClock, 0, block(), 1_100))
        assertEquals(
            H2CandidateRole.PRIMARY,
            assertNotNull(host.render(H2SurfaceVisibilityMode.PRIMARY_ONLY, ready, argsClock, 0, block(), 1_100)).role,
        )
        assertNull(
            host.render(
                H2SurfaceVisibilityMode.PRIMARY_ONLY,
                H2AvatarRouteState(active = true, available = false),
                argsClock,
                0,
                block(),
                1_100,
            ),
        )
    }

    @Test
    fun repeatedRasterIsByteStableAndInactiveDiffers() {
        val renderer = H2BlindAvatarRenderer(H2CandidateRole.PRIMARY)
        val first = checkNotNull(renderer.render(H2AvatarRouteState(true, true), clock(), 0, block(), 1_100))
        val second = checkNotNull(renderer.render(H2AvatarRouteState(true, true), clock(), 0, block(), 1_100))
        val inactive = checkNotNull(renderer.render(H2AvatarRouteState(false, true), clock(), 0, block(), 1_100))
        assertNull(renderer.render(H2AvatarRouteState(true, false), clock(), 0, block(), 1_100))
        assertContentEquals(first.rgba, second.rgba)
        assertTrue(!first.rgba.contentEquals(inactive.rgba))
        assertEquals(H2VisibleOutputState.VISIBLE_OFF, inactive.event.state)
    }

    private fun assertBilateralCheeksMatch(frame: H2RenderedAvatarFrame) {
        val left = pixel(frame, 18, 39)
        val right = pixel(frame, 46, 39)
        assertContentEquals(left, right)
    }

    private fun pixel(frame: H2RenderedAvatarFrame, x: Int, y: Int): ByteArray {
        val offset = (y * frame.width + x) * 4
        return frame.rgba.copyOfRange(offset, offset + 4)
    }

    private fun block() = H2ConfirmationContract.blocks[3].id

    private fun clock() = H2ClockMapping(
        H2ClockMapping.MONOTONIC_CLOCK_ID,
        cameraOriginNanos = 1_000,
        cueOriginNanos = 1_000,
        actionObserverOriginNanos = 1_000,
        visibleOutputOriginNanos = 1_000,
    )
}
