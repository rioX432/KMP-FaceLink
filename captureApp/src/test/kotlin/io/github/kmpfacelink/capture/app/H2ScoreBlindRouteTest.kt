package io.github.kmpfacelink.capture.app

import io.github.kmpfacelink.capture.AndroidH2CallbackRoute
import io.github.kmpfacelink.capture.H2AndroidJoinedOutput
import io.github.kmpfacelink.capture.H2AndroidRouteStatus
import io.github.kmpfacelink.capture.H2AvatarRouteState
import io.github.kmpfacelink.capture.H2BlindAvatarRenderer
import io.github.kmpfacelink.capture.H2CandidateRole
import io.github.kmpfacelink.capture.H2ClockMapping
import io.github.kmpfacelink.capture.H2ConfirmationContract
import io.github.kmpfacelink.capture.H2CuePresentation
import io.github.kmpfacelink.capture.H2CuePresentationAcknowledgement
import io.github.kmpfacelink.capture.H2CueSegment
import io.github.kmpfacelink.capture.H2CueSegmentName
import io.github.kmpfacelink.capture.H2SurfaceVisibilityMode
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class H2ScoreBlindRouteTest {
    @Test
    fun h2RequiresAnExplicitDistinctDefaultOffLaunchValue() {
        assertEquals(
            CaptureLaunchRoute.H2_SCORE_FREE,
            captureLaunchRoute(previewCheckRequested = false, H2_SCORE_FREE_INTENT_VALUE),
        )
        assertEquals(
            CaptureLaunchRoute.CAPTURE,
            captureLaunchRoute(previewCheckRequested = false, requestedStudyProtocol = null),
        )
        assertEquals(
            CaptureLaunchRoute.PREVIEW_CHECK,
            captureLaunchRoute(previewCheckRequested = true, H2_SCORE_FREE_INTENT_VALUE),
        )
        assertFalse(isH2ScoreFreeRoute(null))
        assertFailsWith<IllegalStateException> {
            studyProtocolForLaunchRoute(CaptureLaunchRoute.H2_SCORE_FREE, H2_SCORE_FREE_INTENT_VALUE)
        }
    }

    @Test
    fun everyExactBlockHasActionSpecificParticipantCopyWithoutResultFeedback() {
        assertEquals(
            H2ConfirmationContract.blocks.map { it.id },
            h2ParticipantInstructions.map { it.blockId },
        )
        val visibleCopy = h2ParticipantInstructions.joinToString("\n") {
            "${it.title}\n${it.instruction}"
        }.lowercase()
        listOf(
            "c3b",
            "c2",
            "primary",
            "secondary",
            "threshold",
            "confidence",
            "score",
            "pass",
            "fail",
            "comparison",
            "activation",
            "success",
            "retake",
        ).forEach { forbidden ->
            assertFalse(forbidden in visibleCopy, "participant copy leaked '$forbidden'")
        }
        assertEquals(22, h2ParticipantInstructions.size)
    }

    @Test
    fun buildEmbedsImmutableAncestorsWhilePhysicalExecutionStaysOff() {
        assertEquals(H2ConfirmationContract.PROTOCOL_ID, BuildConfig.H2_PROTOCOL_ID)
        assertEquals(
            H2ConfirmationContract.PROTOCOL_PREREG_SHA256,
            BuildConfig.H2_PROTOCOL_PREREG_SHA256,
        )
        assertEquals(
            H2ConfirmationContract.PROTOCOL_V2_CORRECTION_SHA256,
            BuildConfig.H2_PROTOCOL_V2_CORRECTION_SHA256,
        )
        assertEquals(
            H2ConfirmationContract.V1_SCAFFOLD_RECEIPT_SHA256,
            BuildConfig.H2_V1_SCAFFOLD_RECEIPT_SHA256,
        )
        assertEquals(
            H2ConfirmationContract.CONTRACT_IDENTITY_DESIGN_SHA256,
            BuildConfig.H2_CONTRACT_IDENTITY_DESIGN_SHA256,
        )
        assertEquals(
            H2ConfirmationContract.IMPLEMENTATION_AUTHORITY_SHA256,
            BuildConfig.H2_IMPLEMENTATION_AUTHORITY_SHA256,
        )
        assertEquals(H2ConfirmationContract.primary.packetSha256, BuildConfig.H2_PRIMARY_PACKET_SHA256)
        assertEquals(H2ConfirmationContract.primary.policy.sha256, BuildConfig.H2_PRIMARY_POLICY_SHA256)
        assertEquals(H2ConfirmationContract.comparator.packetSha256, BuildConfig.H2_COMPARATOR_PACKET_SHA256)
        assertEquals(H2ConfirmationContract.comparator.policy.sha256, BuildConfig.H2_COMPARATOR_POLICY_SHA256)
        assertFalse(BuildConfig.H2_PHYSICAL_EXECUTION_ENABLED)
        assertFalse(BuildConfig.H2_PRIMARY_MODEL_BUNDLED)
        assertEquals(true, BuildConfig.H2_ORDINARY_PRODUCT_ROUTE_UNCHANGED)
        assertEquals(true, BuildConfig.H2_CANONICAL_52_UNCHANGED)
    }

    @Test
    fun exactCanonicalPolicyBytesReproduceBothFrozenHashes() {
        assertEquals(
            H2ConfirmationContract.primary.policy.sha256,
            sha256(H2ConfirmationContract.primary.policy.canonicalJsonUtf8Lf.encodeToByteArray()),
        )
        assertEquals(
            H2ConfirmationContract.comparator.policy.sha256,
            sha256(H2ConfirmationContract.comparator.policy.canonicalJsonUtf8Lf.encodeToByteArray()),
        )
    }

    @Test
    fun currentH2ActivityRouteCannotRequestCameraOrCreateAConnectedSession() {
        val locked = AndroidH2CallbackRoute.locked()
        assertFalse(locked.status.executable)
        assertFalse(h2CameraRequestAllowed(CaptureLaunchRoute.H2_SCORE_FREE, locked.status))
        assertEquals(
            true,
            h2CameraRequestAllowed(
                CaptureLaunchRoute.CAPTURE,
                H2AndroidRouteStatus(false, false, false),
            ),
        )
        assertFailsWith<IllegalStateException> {
            locked.beginReadiness(
                H2ClockMapping(H2ClockMapping.MONOTONIC_CLOCK_ID, 0, 0, 0, 0),
                primaryAvailable = true,
                comparatorAvailable = true,
            )
        }
    }

    @Test
    fun participantComposeSelectionCanExposePrimaryOnlyOrNeither() {
        val clock = H2ClockMapping(H2ClockMapping.MONOTONIC_CLOCK_ID, 1, 1, 1, 1)
        val primary = checkNotNull(
            H2BlindAvatarRenderer(H2CandidateRole.PRIMARY).render(
                H2AvatarRouteState(active = true, available = true),
                clock,
                0,
                H2ConfirmationContract.blocks[1].id,
                2,
            ),
        )
        assertEquals(
            primary,
            h2ParticipantFrameForVisibility(primary, H2SurfaceVisibilityMode.PRIMARY_ONLY),
        )
        assertNull(h2ParticipantFrameForVisibility(primary, H2SurfaceVisibilityMode.NEITHER))
        assertNull(h2ParticipantFrameForVisibility(primary, H2SurfaceVisibilityMode.UNDECIDED))
        val status = H2AndroidRouteStatus(true, true, true)
        val state = checkNotNull(
            H2AuthorizedSurfaceState.createOrNull(
                status,
                H2SurfaceVisibilityMode.PRIMARY_ONLY,
                { _, _ -> true },
                { cue, drawn ->
                    H2CuePresentationAcknowledgement(
                        cue.blockId,
                        cue.segment.name,
                        cue.segment.startMillis,
                        drawn,
                    )
                },
            ),
        )
        state.accept(H2AndroidJoinedOutput(1, 1, 2, primary, null, primary))
        assertEquals(primary, state.primaryFrame)
        assertEquals(true, state.acknowledgePrimaryDraw(3))
        assertEquals(false, state.acknowledgePrimaryDraw(4))
        state.acceptCue(
            H2CuePresentation(
                H2ConfirmationContract.blocks[1].id,
                H2CueSegment(H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL, 0, 3_000),
                0,
                1,
            ),
        )
        assertEquals(true, state.acknowledgeCueDraw(2))
        assertEquals(false, state.acknowledgeCueDraw(3))
        assertNull(
            H2AuthorizedSurfaceState.createOrNull(
                H2AndroidRouteStatus(false, false, false),
                H2SurfaceVisibilityMode.PRIMARY_ONLY,
                { _, _ -> error("locked state must not acknowledge") },
                { _, _ -> error("locked state must not acknowledge") },
            ),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
