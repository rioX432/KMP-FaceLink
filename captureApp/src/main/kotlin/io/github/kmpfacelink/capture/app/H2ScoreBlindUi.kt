@file:Suppress("MatchingDeclarationName")

package io.github.kmpfacelink.capture.app

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import io.github.kmpfacelink.capture.H2AndroidJoinedOutput
import io.github.kmpfacelink.capture.H2AndroidRouteStatus
import io.github.kmpfacelink.capture.H2ConfirmationBlock
import io.github.kmpfacelink.capture.H2ConfirmationContract
import io.github.kmpfacelink.capture.H2CuePresentation
import io.github.kmpfacelink.capture.H2CuePresentationAcknowledgement
import io.github.kmpfacelink.capture.H2SurfaceVisibilityMode

/** Participant-facing instructions contain actions and progress, never measurements or results. */
internal data class H2ParticipantInstruction(
    val blockId: String,
    val title: String,
    val instruction: String,
)

internal val h2ParticipantInstructions: List<H2ParticipantInstruction> =
    H2ConfirmationContract.blocks.map(::h2ParticipantInstruction)

/** State bridge from Android H2 callback output to the one permitted Compose surface. */
internal class H2AuthorizedSurfaceState private constructor(
    val visibility: H2SurfaceVisibilityMode,
    private val acknowledgePresentation: (H2AndroidJoinedOutput, Long) -> Boolean,
    private val acknowledgeCue: (H2CuePresentation, Long) -> H2CuePresentationAcknowledgement,
) {
    private var output: H2AndroidJoinedOutput? = null
    private var acknowledgedSequence: Long? = null

    var primaryFrame by mutableStateOf<io.github.kmpfacelink.capture.H2RenderedAvatarFrame?>(null)
        private set
    var currentCue by mutableStateOf<H2CuePresentation?>(null)
        private set

    fun accept(output: H2AndroidJoinedOutput) {
        require(output.participant == null || output.participant === output.primary)
        this.output = output
        primaryFrame = if (visibility == H2SurfaceVisibilityMode.PRIMARY_ONLY) output.participant else null
    }

    fun acknowledgePrimaryDraw(drawnMonotonicNanos: Long): Boolean {
        val joined = output ?: return false
        val sequence = joined.participant?.event?.sequence ?: return false
        if (sequence == acknowledgedSequence) return false
        val accepted = acknowledgePresentation(joined, drawnMonotonicNanos)
        if (accepted) acknowledgedSequence = sequence
        return accepted
    }

    fun acceptCue(cue: H2CuePresentation) {
        check(currentCue == null) { "previous H2 cue has no Compose draw acknowledgement" }
        currentCue = cue
    }

    fun acknowledgeCueDraw(drawnMonotonicNanos: Long): Boolean {
        val cue = currentCue ?: return false
        acknowledgeCue(cue, drawnMonotonicNanos)
        currentCue = null
        return true
    }

    companion object {
        fun createOrNull(
            status: H2AndroidRouteStatus,
            visibility: H2SurfaceVisibilityMode,
            acknowledgePresentation: (H2AndroidJoinedOutput, Long) -> Boolean,
            acknowledgeCue: (H2CuePresentation, Long) -> H2CuePresentationAcknowledgement,
        ): H2AuthorizedSurfaceState? = if (
            status.executable && visibility != H2SurfaceVisibilityMode.UNDECIDED
        ) {
            H2AuthorizedSurfaceState(visibility, acknowledgePresentation, acknowledgeCue)
        } else {
            null
        }
    }
}

/** Current Activity stays locked; an authorized branch consumes callback-backed state here. */
@Composable
internal fun H2ScoreBlindRouteShell(
    status: H2AndroidRouteStatus,
    surfaceState: H2AuthorizedSurfaceState?,
) {
    if (status.executable) {
        val connected = checkNotNull(surfaceState) { "authorized H2 callback state is absent" }
        Column {
            connected.currentCue?.let { cue ->
                Text(
                    cue.segment.name.participantCopy(),
                    Modifier.drawWithContent {
                        drawContent()
                        connected.acknowledgeCueDraw(SystemClock.elapsedRealtimeNanos())
                    },
                )
            }
            H2PrimaryAvatarSurface(
                connected.primaryFrame,
                connected.visibility,
                onDrawn = { connected.acknowledgePrimaryDraw(SystemClock.elapsedRealtimeNanos()) },
            )
        }
    } else {
        require(surfaceState == null)
        H2ScoreBlindLockedPanel()
    }
}

private fun io.github.kmpfacelink.capture.H2CueSegmentName.participantCopy(): String = when (this) {
    io.github.kmpfacelink.capture.H2CueSegmentName.READINESS_AVAILABILITY -> "Look forward and relax."
    io.github.kmpfacelink.capture.H2CueSegmentName.TAKE_CALIBRATION_NEUTRAL -> "Keep a relaxed neutral face."
    io.github.kmpfacelink.capture.H2CueSegmentName.READY_PREPARE -> "Prepare for the next action."
    io.github.kmpfacelink.capture.H2CueSegmentName.ACTION_HOLD_1,
    io.github.kmpfacelink.capture.H2CueSegmentName.ACTION_HOLD_2,
    -> "Perform the instructed action and hold."
    io.github.kmpfacelink.capture.H2CueSegmentName.RELEASE_1,
    io.github.kmpfacelink.capture.H2CueSegmentName.RELEASE_2,
    -> "Release and relax."
    io.github.kmpfacelink.capture.H2CueSegmentName.ORDINARY_USE_OBSERVATION -> "Continue naturally."
    io.github.kmpfacelink.capture.H2CueSegmentName.NATURAL_SPEECH -> "Speak naturally. Audio is not recorded."
}

/** Explicit Activity permission gate; the shipped H2 status makes camera access unreachable. */
internal fun h2CameraRequestAllowed(
    route: CaptureLaunchRoute,
    status: H2AndroidRouteStatus,
): Boolean = route != CaptureLaunchRoute.H2_SCORE_FREE || status.executable

@Composable
internal fun H2ScoreBlindLockedPanel() {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text("H2 confirmation", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This source build is locked. A physical run requires separate reviewed-binary, " +
                "privacy, pilot, and capture approvals.",
            Modifier.padding(top = 16.dp),
        )
        Text(
            "The capture view will show only framing help, the current instruction, progress, " +
                "rest, stop, and operational errors. It will not show measurements or outcomes.",
            Modifier.padding(top = 16.dp),
        )
        Text(
            "Planned recording: 22 blocks, 9 minutes 38 seconds, plus optional rest.",
            Modifier.padding(top = 16.dp),
        )
        LazyColumn(Modifier.padding(top = 16.dp)) {
            items(h2ParticipantInstructions) { item ->
                Text("${item.blockId}: ${item.title}", style = MaterialTheme.typography.titleSmall)
                Text(item.instruction, Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun h2ParticipantInstruction(block: H2ConfirmationBlock): H2ParticipantInstruction = when (block.id) {
    "B01_STARTUP_NEUTRAL_READINESS" -> instruction(block, "Setup", "Look forward and relax your face.")
    "B02_OPENING_NEUTRAL_READY" -> instruction(block, "Relaxed face", "Continue looking forward with a relaxed face.")
    "B03_JAW_OPEN" -> instruction(block, "Open jaw", "After the prompt, open your jaw, hold, then relax.")
    "B04_PUKU_BILATERAL_FRONT_LIGHT_1",
    "B17_PUKU_BILATERAL_FRONT_LIGHT_2",
    -> instruction(block, "Light cheek puff", "Gently inflate your cheek area, hold, then relax. Repeat once.")
    "B05_PUKU_BILATERAL_FRONT_MID_1",
    "B18_PUKU_BILATERAL_FRONT_MID_2",
    -> instruction(block, "Medium cheek puff", "Inflate your cheek area normally, hold, then relax. Repeat once.")
    "B06_PUKU_BILATERAL_FRONT_STRONG_1",
    "B19_PUKU_BILATERAL_FRONT_STRONG_2",
    -> instruction(block, "Strong cheek puff", "Inflate your cheek area strongly, hold, then relax. Repeat once.")
    "B07_BLOW_HELD" -> instruction(block, "Held blow", "Blow steadily while holding the pose, then relax.")
    "B08_PUCKER_1", "B21_PUCKER_2" ->
        instruction(block, "Pucker", "Pucker your lips, hold, then relax.")
    "B09_SMILE_HOLD" -> instruction(block, "Smile", "Smile naturally, hold, then relax.")
    "B10_NATURAL_SPEECH" -> instruction(
        block,
        "Natural speech",
        "Speak naturally without imitating another instruction. Audio is not recorded.",
    )
    "B11_TONGUE_OUT" -> instruction(block, "Tongue", "Extend your tongue, hold, then relax.")
    "B12_NOSE_SNEER" -> instruction(block, "Nose sneer", "Sneer with your nose, hold, then relax.")
    "B13_PUKU_UNILATERAL_LEFT_FRONT" -> instruction(
        block,
        "Left-side cheek puff",
        "Inflate the left side of your cheek area, hold, then relax. Repeat once.",
    )
    "B14_PUKU_UNILATERAL_RIGHT_FRONT" -> instruction(
        block,
        "Right-side cheek puff",
        "Inflate the right side of your cheek area, hold, then relax. Repeat once.",
    )
    "B15_PUKU_BILATERAL_YAW_LEFT" -> instruction(
        block,
        "Cheek puff while turned left",
        "Turn left as instructed, inflate your cheek area, hold, then relax. Repeat once.",
    )
    "B16_PUKU_BILATERAL_YAW_RIGHT" -> instruction(
        block,
        "Cheek puff while turned right",
        "Turn right as instructed, inflate your cheek area, hold, then relax. Repeat once.",
    )
    "B20_BLOW_NATURAL" -> instruction(block, "Natural blow", "Blow naturally, then relax.")
    "B22_CLOSING_NEUTRAL_READY" -> instruction(block, "Relaxed face", "Look forward and relax your face.")
    else -> error("H2 participant copy is missing for ${block.id}")
}

private fun instruction(
    block: H2ConfirmationBlock,
    title: String,
    text: String,
): H2ParticipantInstruction = H2ParticipantInstruction(
    block.id,
    title,
    if (block.kind == io.github.kmpfacelink.capture.H2BlockKind.STARTUP_READINESS) {
        text
    } else {
        "Remain neutral and silent for the first 3 seconds. Then $text"
    },
)
