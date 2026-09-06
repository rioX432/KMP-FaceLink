@file:Suppress("LongParameterList", "MagicNumber")

package io.github.kmpfacelink.capture

/** Human-facing labels deliberately reveal neither candidate identity nor result values. */
public enum class H2BlindSurfaceLabel { A, B }

/** V2 allows neither surface or primary only; comparator-only and side-by-side are unrepresentable. */
public enum class H2SurfaceVisibilityMode { UNDECIDED, NEITHER, PRIMARY_ONLY }

/** Active may be retained internally while unavailable; unavailable state cannot produce pixels/events. */
public data class H2AvatarRouteState(public val active: Boolean, public val available: Boolean)

public class H2RenderedAvatarFrame internal constructor(
    public val role: H2CandidateRole,
    public val label: H2BlindSurfaceLabel,
    public val width: Int,
    public val height: Int,
    rgba: ByteArray,
    public val active: Boolean,
    public val clock: H2ClockMapping,
    public val event: H2VisibleOutputEvent,
) {
    public val rgba: ByteArray = rgba.copyOf()
    init {
        require(rgba.size == width * height * 4)
        require(event.candidateRole == role)
        require((event.state == H2VisibleOutputState.VISIBLE_ON) == active)
    }
}

/** Deterministic bilateral test-avatar rasterizer used by the H2-only render surfaces. */
public class H2BlindAvatarRenderer(public val role: H2CandidateRole) {
    public fun render(
        route: H2AvatarRouteState,
        clock: H2ClockMapping,
        eventSequence: Long,
        blockId: String,
        monotonicNanos: Long,
    ): H2RenderedAvatarFrame? {
        if (!route.available) return null
        val active = route.active
        val surface = when (role) {
            H2CandidateRole.PRIMARY -> H2VisibleSurface.H2_PRIMARY_C3B_AVATAR_RENDER
            H2CandidateRole.COMPARATOR -> H2VisibleSurface.H2_SECONDARY_C2_PLUS_C1_AVATAR_RENDER
        }
        val label = if (role == H2CandidateRole.PRIMARY) H2BlindSurfaceLabel.A else H2BlindSurfaceLabel.B
        val rgba = ByteArray(WIDTH * HEIGHT * 4)
        fill(rgba, BACKGROUND)
        disk(rgba, FACE_X, FACE_Y, FACE_RADIUS, FACE)
        disk(rgba, LEFT_EYE_X, EYE_Y, EYE_RADIUS, INK)
        disk(rgba, RIGHT_EYE_X, EYE_Y, EYE_RADIUS, INK)
        val cheekRadius = if (active) ACTIVE_CHEEK_RADIUS else INACTIVE_CHEEK_RADIUS
        val cheekColor = if (role == H2CandidateRole.PRIMARY) PRIMARY_CHEEK else COMPARATOR_CHEEK
        disk(rgba, LEFT_CHEEK_X, CHEEK_Y, cheekRadius, cheekColor)
        disk(rgba, RIGHT_CHEEK_X, CHEEK_Y, cheekRadius, cheekColor)
        val event = H2VisibleOutputEvent(
            sequence = eventSequence,
            blockId = blockId,
            candidateRole = role,
            surface = surface,
            monotonicNanos = monotonicNanos,
            state = if (active) H2VisibleOutputState.VISIBLE_ON else H2VisibleOutputState.VISIBLE_OFF,
        )
        return H2RenderedAvatarFrame(role, label, WIDTH, HEIGHT, rgba, active, clock, event)
    }

    private fun fill(target: ByteArray, color: IntArray) {
        for (offset in target.indices step 4) write(target, offset, color)
    }

    private fun disk(target: ByteArray, centerX: Int, centerY: Int, radius: Int, color: IntArray) {
        val radiusSquared = radius * radius
        for (y in (centerY - radius).coerceAtLeast(0)..(centerY + radius).coerceAtMost(HEIGHT - 1)) {
            for (x in (centerX - radius).coerceAtLeast(0)..(centerX + radius).coerceAtMost(WIDTH - 1)) {
                val dx = x - centerX
                val dy = y - centerY
                if (dx * dx + dy * dy <= radiusSquared) write(target, (y * WIDTH + x) * 4, color)
            }
        }
    }

    private fun write(target: ByteArray, offset: Int, color: IntArray) {
        target[offset] = color[0].toByte()
        target[offset + 1] = color[1].toByte()
        target[offset + 2] = color[2].toByte()
        target[offset + 3] = 0xff.toByte()
    }

    public companion object {
        public const val WIDTH: Int = 64
        public const val HEIGHT: Int = 64
        private const val FACE_X = 32
        private const val FACE_Y = 32
        private const val FACE_RADIUS = 27
        private const val LEFT_EYE_X = 23
        private const val RIGHT_EYE_X = 41
        private const val EYE_Y = 25
        private const val EYE_RADIUS = 2
        private const val LEFT_CHEEK_X = 18
        private const val RIGHT_CHEEK_X = 46
        private const val CHEEK_Y = 39
        private const val ACTIVE_CHEEK_RADIUS = 8
        private const val INACTIVE_CHEEK_RADIUS = 3
        private val BACKGROUND = intArrayOf(245, 246, 250)
        private val FACE = intArrayOf(255, 224, 189)
        private val INK = intArrayOf(40, 40, 48)
        private val PRIMARY_CHEEK = intArrayOf(244, 112, 137)
        private val COMPARATOR_CHEEK = intArrayOf(236, 132, 94)
    }
}

/** A single-output host makes side-by-side comparison unrepresentable. */
public class H2OneAtATimeAvatarHost {
    private val primary = H2BlindAvatarRenderer(H2CandidateRole.PRIMARY)

    public fun render(
        visibility: H2SurfaceVisibilityMode,
        primaryRoute: H2AvatarRouteState,
        clock: H2ClockMapping,
        eventSequence: Long,
        blockId: String,
        monotonicNanos: Long,
    ): H2RenderedAvatarFrame? = when (visibility) {
        H2SurfaceVisibilityMode.UNDECIDED,
        H2SurfaceVisibilityMode.NEITHER,
        -> null
        H2SurfaceVisibilityMode.PRIMARY_ONLY -> primary.render(
            primaryRoute,
            clock,
            eventSequence,
            blockId,
            monotonicNanos,
        )
    }
}
