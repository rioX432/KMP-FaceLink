@file:Suppress(
    "TooManyFunctions",
    // State-specific composables keep their complete input contract explicit and previewable.
    "LongParameterList",
)

package io.github.kmpfacelink.capture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kmpfacelink.capture.CaptureFailureCategory
import io.github.kmpfacelink.capture.CaptureFailurePresentation
import io.github.kmpfacelink.capture.CaptureFailurePrimaryAction
import io.github.kmpfacelink.capture.CaptureScenario
import io.github.kmpfacelink.capture.CaptureSession
import io.github.kmpfacelink.capture.CaptureStudyProtocol
import io.github.kmpfacelink.capture.CaptureUiState
import io.github.kmpfacelink.capture.GuidedCaptureAction
import io.github.kmpfacelink.capture.GuidedCaptureFlow
import io.github.kmpfacelink.capture.GuidedCaptureStage
import io.github.kmpfacelink.capture.HardGate
import io.github.kmpfacelink.capture.ParticipantQualificationPhase
import io.github.kmpfacelink.capture.StudyTakeGroup
import io.github.kmpfacelink.capture.StudyTakeRecord
import io.github.kmpfacelink.capture.StudyTakeSlot
import io.github.kmpfacelink.capture.TakeScript
import io.github.kmpfacelink.capture.acceptedStudyTake
import io.github.kmpfacelink.capture.cancelledCaptureFailurePresentation
import io.github.kmpfacelink.capture.completedStudyTakeCount
import io.github.kmpfacelink.capture.deleteStudyTake
import io.github.kmpfacelink.capture.participantQualificationController
import io.github.kmpfacelink.capture.runTerminalAwareRetakeDelete
import io.github.kmpfacelink.capture.startPendingTakeIfEligible
import io.github.kmpfacelink.capture.studyTakeRecords
import io.github.kmpfacelink.capture.studyTakeSlots
import io.github.kmpfacelink.capture.toCaptureFailureReceipt
import io.github.kmpfacelink.capture.toPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Runs the registered study as a sequential, self-operated workflow.
 *
 * A platform-native preview from the recorder's existing camera session sits under the shared
 * target oval, detection state and prompts. It is render-only: it opens no second camera and writes
 * no additional image. During a take, every visible time and cue is derived from
 * [CaptureUiState.scriptElapsedMs], which is the recorder's own script clock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod", "ComplexCondition")
public fun CaptureScreen(
    session: CaptureSession,
    modifier: Modifier = Modifier,
    onOpenDiagnostics: (() -> Unit)? = null,
    verifiedReferenceTakeId: String? = null,
    preflightInProgress: Boolean = false,
    preflightFailure: String? = null,
    onRunPreflight: (() -> Unit)? = null,
) {
    val scenarios = session.studyProtocol.scenarios
    val state by session.uiState.collectAsState()
    val qualification = remember(session) { participantQualificationController(session) }
    val qualificationState = qualification?.state?.collectAsState()?.value
    val qualificationMode = qualification != null
    val scope = rememberCoroutineScope()
    val takeRecords = remember(session) { studyTakeRecords(session) }
    val history by takeRecords.collectAsState()
    val slots = remember(scenarios) { studyTakeSlots(scenarios) }
    val guidedFlow = remember(session, scenarios) { GuidedCaptureFlow() }
    var guided by remember(session, scenarios) { mutableStateOf(guidedFlow.snapshot) }
    var selected by remember(session, scenarios) {
        mutableStateOf(nextIncompleteTake(slots, takeRecords.value))
    }
    var savedTake by remember(session) { mutableStateOf<StudyTakeRecord?>(null) }
    var retakeTarget by remember(session) { mutableStateOf<StudyTakeRecord?>(null) }
    var showProgress by remember(session) { mutableStateOf(false) }
    var busy by remember(session) { mutableStateOf(false) }
    var sawRecording by remember(session) { mutableStateOf(false) }
    var deleting by remember(session) { mutableStateOf(false) }
    var failure by remember(session) { mutableStateOf<String?>(null) }
    val qualificationTerminalFailure = CaptureFailurePresentation(
        participantMessageJa = "この確認撮影は続行できません。データを選び直さず終了してください。",
        primaryAction = CaptureFailurePrimaryAction.STOP_RUN,
    )
    val referenceTake = history
        .filter { it.scenario == CaptureScenario.NEUTRAL }
        .minByOrNull(StudyTakeRecord::takeId)
    val preflightRequired = onRunPreflight != null &&
        referenceTake != null &&
        referenceTake.takeId != verifiedReferenceTakeId

    LaunchedEffect(
        preflightRequired,
        preflightInProgress,
        preflightFailure,
        referenceTake?.takeId,
        guided.stage,
    ) {
        if (
            guided.stage != GuidedCaptureStage.TERMINATED &&
            preflightRequired &&
            !preflightInProgress &&
            preflightFailure == null
        ) {
            requireNotNull(onRunPreflight).invoke()
        }
    }

    LaunchedEffect(guided.stage) {
        if (guided.stage == GuidedCaptureStage.TERMINATED) {
            showProgress = false
            retakeTarget = null
            deleting = false
        }
    }

    LaunchedEffect(state.recording) {
        if (state.recording) sawRecording = true
    }

    LaunchedEffect(state.lastFailure?.code) {
        val presentation = state.lastFailure?.toPresentation() ?: return@LaunchedEffect
        if (
            presentation.primaryAction == CaptureFailurePrimaryAction.STOP_RUN &&
            guided.stage != GuidedCaptureStage.TERMINATED
        ) {
            guided = guidedFlow.onFailed(presentation)
        }
    }

    fun beginTake() {
        val slot = selected
        val started = startPendingTakeIfEligible(
            flow = guidedFlow,
            slotAvailable = slot != null,
            busy = busy,
            gatePassed = state.gate.passed,
            onAccepted = { guided = it },
            startRecorder = {
                val acceptedSlot = requireNotNull(slot)
                val scenario = acceptedSlot.scenario
                val knownIds = takeRecords.value.mapTo(hashSetOf(), StudyTakeRecord::takeId)
                busy = true
                sawRecording = false
                failure = null
                scope.launch {
                    val result = runCatching {
                        session.record(scenario, session.studyRun.storageProfile)
                    }
                    if (guidedFlow.snapshot.stage == GuidedCaptureStage.TERMINATED) {
                        busy = false
                        return@launch
                    }
                    val updated = takeRecords.value
                    result.onSuccess { outcome ->
                        if (qualification?.state?.value?.phase == ParticipantQualificationPhase.TERMINATED) {
                            failure = qualificationTerminalFailure.participantMessageJa
                            guided = guidedFlow.onFailed(qualificationTerminalFailure)
                        } else if (outcome.reason == TAKE_CANCELLED) {
                            val presentation = cancelledCaptureFailurePresentation()
                            failure = presentation.participantMessageJa
                            guided = guidedFlow.onFailed(presentation)
                        } else if (outcome.failure != null || outcome.error != null) {
                            val receipt = outcome.failure ?: CaptureFailureCategory.UNKNOWN.receipt()
                            failure = receipt.participantMessageJa
                            guided = guidedFlow.onFailed(receipt.toPresentation())
                        } else {
                            val published = updated
                                .firstOrNull {
                                    it.takeId !in knownIds &&
                                        it.scenario == scenario &&
                                        it.attempt == acceptedSlot.attempt
                                }
                            if (published == null) {
                                val receipt = CaptureFailureCategory.INTEGRITY.receipt()
                                failure = receipt.participantMessageJa
                                guided = guidedFlow.onFailed(receipt.toPresentation())
                            } else {
                                savedTake = published
                                guided = guidedFlow.onSaved()
                            }
                        }
                    }
                    result.onFailure { throwable ->
                        if (qualification?.state?.value?.phase == ParticipantQualificationPhase.TERMINATED) {
                            failure = qualificationTerminalFailure.participantMessageJa
                            guided = guidedFlow.onFailed(qualificationTerminalFailure)
                        } else {
                            val receipt = throwable.toCaptureFailureReceipt()
                            failure = receipt.participantMessageJa
                            guided = guidedFlow.onFailed(receipt.toPresentation())
                        }
                    }
                    busy = false
                }
            },
        )
        if (!started) guided = guidedFlow.snapshot
    }

    fun advanceToNext() {
        savedTake = null
        selected = nextIncompleteTake(slots, takeRecords.value)
        guided = if (selected == null) guidedFlow.complete() else guidedFlow.prepareNext()
    }

    fun pauseBetweenTakes() {
        if (savedTake != null) {
            savedTake = null
            selected = nextIncompleteTake(slots, takeRecords.value)
        }
        guided = if (selected == null) guidedFlow.complete() else guidedFlow.pause()
    }

    LaunchedEffect(state.gate.passed, selected?.key, busy, savedTake?.takeId, guided.stage) {
        if (
            selected == null || busy || savedTake != null ||
            guided.stage !in setOf(GuidedCaptureStage.ALIGNING, GuidedCaptureStage.COUNTDOWN)
        ) {
            return@LaunchedEffect
        }
        guided = guidedFlow.onGateChanged(state.gate.passed)
        while (guided.stage == GuidedCaptureStage.COUNTDOWN) {
            delay(ONE_SECOND_MS)
            if (!state.gate.passed) return@LaunchedEffect
            val (snapshot, action) = guidedFlow.tick()
            guided = snapshot
            if (action == GuidedCaptureAction.StartTake) {
                beginTake()
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(savedTake?.takeId, retakeTarget?.takeId, guided.stage, preflightRequired) {
        if (
            savedTake == null || retakeTarget != null || preflightRequired ||
            guided.stage != GuidedCaptureStage.SAVED
        ) {
            return@LaunchedEffect
        }
        while (guided.stage == GuidedCaptureStage.SAVED) {
            delay(ONE_SECOND_MS)
            val (snapshot, action) = guidedFlow.tick()
            guided = snapshot
            if (action == GuidedCaptureAction.Advance) {
                advanceToNext()
                return@LaunchedEffect
            }
        }
    }

    LaunchedEffect(selected?.key, history.size, qualificationState?.phase) {
        if (
            qualification != null && selected == null &&
            qualification.state.value.phase == ParticipantQualificationPhase.OPEN
        ) {
            runCatching { qualification.seal() }
        }
    }

    Box(modifier.fillMaxSize().safeDrawingPadding().background(MaterialTheme.colorScheme.background)) {
        when {
            qualificationState?.phase == ParticipantQualificationPhase.TERMINATED -> CaptureTerminatedPanel(
                failure = qualificationTerminalFailure,
            )
            guided.stage == GuidedCaptureStage.TERMINATED -> CaptureTerminatedPanel(
                failure = requireNotNull(guided.failure),
            )
            preflightRequired -> PreflightPanel(
                inProgress = preflightInProgress,
                failure = preflightFailure,
                onRetry = requireNotNull(onRunPreflight),
                onRetake = if (qualificationMode) null else ({ retakeTarget = referenceTake }),
            )
            guided.stage == GuidedCaptureStage.INTRO -> GuidedIntroductionPanel(
                total = slots.size,
                qualificationMode = qualificationMode,
                onConfirm = { guided = guidedFlow.confirmIntroduction() },
            )
            guided.stage == GuidedCaptureStage.PAUSED -> PausedPanel(
                selected = selected,
                completed = completedStudyTakeCount(slots, history),
                total = slots.size,
                onResume = { guided = guidedFlow.resume() },
            )
            guided.stage == GuidedCaptureStage.FAILED -> CaptureFailurePanel(
                failure = requireNotNull(guided.failure),
                onRetry = {
                    failure = null
                    guided = guidedFlow.retry()
                },
                onPause = {
                    guided = guidedFlow.retry()
                    pauseBetweenTakes()
                },
            )
            savedTake != null -> SavedPanel(
                slot = requireNotNull(selected),
                completed = completedStudyTakeCount(slots, history),
                total = slots.size,
                autoAdvanceSeconds = guided.secondsRemaining,
                onNext = ::advanceToNext,
                onRetake = if (qualificationMode) null else ({ retakeTarget = savedTake }),
                onPause = ::pauseBetweenTakes,
                onProgress = { showProgress = true },
            )
            busy && state.recording -> RecordingPanel(
                session = session,
                slot = requireNotNull(selected),
                state = state,
                index = slots.indexOf(selected).coerceAtLeast(0),
                total = slots.size,
                onStop = session::stop,
            )
            busy -> WaitingPanel(
                session = session,
                scenario = requireNotNull(selected).scenario,
                state = state,
                saving = sawRecording,
                onStop = session::stop,
            )
            selected == null && qualificationMode &&
                qualificationState?.phase != ParticipantQualificationPhase.SEALED -> QualificationSealingPanel()
            selected == null -> SessionCompletePanel(
                total = slots.size,
                onProgress = { showProgress = true },
                onRetake = if (qualificationMode) null else ({ retakeTarget = latestStudyTake(slots, history) }),
                onOpenDiagnostics = onOpenDiagnostics,
            )
            else -> AlignmentPanel(
                session = session,
                slot = requireNotNull(selected),
                state = state,
                index = slots.indexOf(selected).coerceAtLeast(0),
                total = slots.size,
                completed = completedStudyTakeCount(slots, history),
                failure = failure,
                autoStartSeconds = guided.secondsRemaining,
                onPause = ::pauseBetweenTakes,
                onProgress = { showProgress = true },
                onRetakeLatest = if (qualificationMode) null else latestStudyTake(slots, history)?.let { take ->
                    {
                        retakeTarget = take
                    }
                },
            )
        }

        if (showProgress && guided.stage != GuidedCaptureStage.TERMINATED) {
            ProgressSheet(
                slots = slots,
                history = history,
                selected = selected,
                onDismiss = { showProgress = false },
                onRetake = if (qualificationMode) null else ({ take ->
                    showProgress = false
                    retakeTarget = take
                }),
            )
        }
        retakeTarget?.takeIf { guided.stage != GuidedCaptureStage.TERMINATED }
            ?.takeIf { !qualificationMode }
            ?.let { take ->
            DeleteRetakeSheet(
                take = take,
                enabled = !deleting,
                onDismiss = { if (!deleting) retakeTarget = null },
                onDelete = {
                    if (
                        deleting ||
                        guidedFlow.snapshot.stage == GuidedCaptureStage.TERMINATED
                    ) {
                        return@DeleteRetakeSheet
                    }
                    deleting = true
                    scope.launch {
                        runTerminalAwareRetakeDelete(
                            isTerminal = {
                                guidedFlow.snapshot.stage == GuidedCaptureStage.TERMINATED
                            },
                            delete = { deleteStudyTake(session, take) },
                            onSuccess = {
                                savedTake = null
                                selected = slots.firstOrNull {
                                    it.scenario == take.scenario && it.attempt == take.attempt
                                } ?: nextIncompleteTake(slots, takeRecords.value)
                                guided = if (guided.stage == GuidedCaptureStage.INTRO) {
                                    guided
                                } else {
                                    guidedFlow.prepareNext()
                                }
                                retakeTarget = null
                            },
                            onFailure = {
                                failure = "撮り直しの準備ができませんでした。もう一度お試しください。"
                            },
                        )
                        if (guidedFlow.snapshot.stage != GuidedCaptureStage.TERMINATED) {
                            deleting = false
                        }
                    }
                },
            )
        }
    }
}

/**
 * Camera/tracking smoke screen that cannot create a take.
 *
 * Platform shells expose this only through an explicit developer launch mode. It starts the same
 * capture session so preview/session integration is real, but never calls [CaptureSession.record]
 * and therefore writes no take, frame payload, coefficient, landmark or transform artifact.
 */
@Composable
public fun CapturePreviewCheckScreen(
    session: CaptureSession,
    modifier: Modifier = Modifier,
) {
    val state by session.uiState.collectAsState()
    Column(
        modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "カメラ表示の確認",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "このモードでは撮影・保存を行いません",
            Modifier.padding(top = 4.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        FaceGuide(
            session,
            state,
            Modifier.fillMaxWidth().weight(1f),
            CaptureSurfaceMode.PREVIEW_ONLY,
        )
        Text(
            gateGuidance(state),
            Modifier.fillMaxWidth().padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (state.resultsObserved > 0) {
                "カメラ入力を受信中"
            } else {
                "カメラ入力を待っています"
            },
            Modifier.padding(top = 8.dp),
            color = if (state.resultsObserved > 0) PASSED_GREEN else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.lastFailure?.let { error ->
            Text(
                error.participantMessageJa,
                Modifier.fillMaxWidth().padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The fixed order registered for the cheek-puff study. */
public fun studyProtocol(): List<CaptureScenario> = CaptureStudyProtocol.guided22().scenarios

@Composable
private fun GuidedIntroductionPanel(
    total: Int,
    qualificationMode: Boolean,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("顔の動きを撮影します", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text(
            "顔を枠に合わせると、3秒後に自動で始まります。画面に出る動きをまねしてください。",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (qualificationMode) {
                "全部で12回、撮影動作は約4分24秒です。位置合わせや保存確認の時間と休憩が別に加わります。"
            } else {
                "全部で${total}回、表情を動かす時間は約8分です。途中で休憩でき、失敗しても同じ回からやり直せます。"
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "顔データはネットへ送らず、この端末内に保存します。",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("撮影を始める")
        }
    }
}

@Composable
private fun PreflightPanel(
    inProgress: Boolean,
    failure: String?,
    onRetry: () -> Unit,
    onRetake: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (inProgress) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
        }
        Text(
            if (failure == null) "最初の撮影データを確認しています" else "撮影データを確認できませんでした",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            failure ?: "残りへ進む前に、撮影データを端末内で自動確認します。顔データは端末外へ送りません。",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (failure != null && !inProgress) {
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("もう一度確認する")
            }
            onRetake?.let { callback ->
                TextButton(onClick = callback) { Text("中立テイクを撮り直す") }
            }
        }
    }
}

@Composable
private fun PausedPanel(
    selected: StudyTakeSlot?,
    completed: Int,
    total: Int,
    onResume: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("休憩中", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("$completed / $total 完了", style = MaterialTheme.typography.titleLarge)
        selected?.let {
            Spacer(Modifier.height(16.dp))
            Text("次: ${studyTakeTitleJa(it)}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onResume, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("撮影を再開")
        }
    }
}

@Composable
private fun CaptureFailurePanel(
    failure: CaptureFailurePresentation,
    onRetry: () -> Unit,
    onPause: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("このテイクは保存されていません", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            failure.participantMessageJa,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("同じテイクをもう一度準備")
        }
        TextButton(onClick = onPause) { Text("いったん休憩する") }
    }
}

@Composable
private fun CaptureTerminatedPanel(failure: CaptureFailurePresentation) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("撮影を中止しました", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            failure.participantMessageJa,
            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "この撮影画面からは再開できません。必要な対応後に、撮影を最初から開始してください。",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun AlignmentPanel(
    session: CaptureSession,
    slot: StudyTakeSlot,
    state: CaptureUiState,
    index: Int,
    total: Int,
    completed: Int,
    failure: String?,
    autoStartSeconds: Int?,
    onPause: () -> Unit,
    onProgress: () -> Unit,
    onRetakeLatest: (() -> Unit)?,
) {
    val scenario = slot.scenario
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StudyHeader(index + 1, total, completed, onProgress, onPause)
        Text(
            CapturePrompts.actionJa(scenario),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        StudyTakeSupplement(slot)
        Spacer(Modifier.height(12.dp))
        FaceGuide(session, state, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(12.dp))
        Text(
            gateGuidance(state),
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        state.lastFailure?.let { error ->
            Text(
                error.participantMessageJa,
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        failure?.let { Text("収録できませんでした: $it", color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        if (autoStartSeconds != null) {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    "そのまま — $autoStartSeconds 秒後に自動で撮影します",
                    Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            onRetakeLatest?.let { callback ->
                TextButton(onClick = callback) { Text("最新を撮り直す") }
            }
        }
    }
}

@Composable
private fun WaitingPanel(
    session: CaptureSession,
    scenario: CaptureScenario,
    state: CaptureUiState,
    saving: Boolean,
    onStop: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (saving) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("テイクを保存しています", style = MaterialTheme.typography.headlineSmall)
        } else {
            Text(studyTitleJa(scenario), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            FaceGuide(session, state, Modifier.fillMaxWidth().height(320.dp))
            Spacer(Modifier.height(16.dp))
            Text(gateGuidance(state), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            state.lastFailure?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    error.participantMessageJa,
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onStop) { Text("開始をやめる") }
        }
    }
}

@Composable
private fun RecordingPanel(
    session: CaptureSession,
    slot: StudyTakeSlot,
    state: CaptureUiState,
    index: Int,
    total: Int,
    onStop: () -> Unit,
) {
    val scenario = slot.scenario
    val elapsedMs = state.scriptElapsedMs ?: 0L
    val script = remember(scenario) { TakeScript(scenario) }
    val spanIndex = script.spans.indexOfFirst { elapsedMs < it.endMs }.coerceAtLeast(0)
    val span = script.spans.getOrNull(spanIndex)
    val cue = span?.cue ?: TakeScript.Cue.DONE
    val remaining = script.remainingSecondsIn(elapsedMs)
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("● 撮影中", color = RECORDING_RED, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("撮影 ${index + 1} / $total", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        FaceGuide(session, state, Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(10.dp))
        Text(
            CapturePrompts.cueJa(scenario, cue),
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
        )
        StudyTakeSupplement(slot)
        Text("$remaining", fontSize = 50.sp, fontWeight = FontWeight.Bold)
        CapturePrompts.nextCueJa(scenario, script.nextSpanAt(elapsedMs)?.cue)?.let { next ->
            Surface(
                Modifier.fillMaxWidth(),
                color = if (remaining <= TRANSITION_WARNING_SECONDS) {
                    WARNING_BACKGROUND
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    next,
                    Modifier.padding(12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = if (remaining <= TRANSITION_WARNING_SECONDS) 23.sp else 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onStop) { Text("収録を中止") }
    }
}

@Composable
private fun SavedPanel(
    slot: StudyTakeSlot,
    completed: Int,
    total: Int,
    autoAdvanceSeconds: Int?,
    onNext: () -> Unit,
    onRetake: (() -> Unit)?,
    onPause: () -> Unit,
    onProgress: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✓", color = PASSED_GREEN, fontSize = 72.sp, fontWeight = FontWeight.Bold)
        Text("テイクを保存しました", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(studyTakeTitleJa(slot), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("$completed / $total 完了", style = MaterialTheme.typography.titleMedium)
        autoAdvanceSeconds?.let {
            Spacer(Modifier.height(8.dp))
            Text("$it 秒後に次のテイクへ進みます", style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("次のテイクへ") }
        Spacer(Modifier.height(12.dp))
        onRetake?.let { callback ->
            OutlinedButton(onClick = callback, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("撮り直す")
            }
        }
        TextButton(onClick = onPause) { Text("ここで休憩") }
        TextButton(onClick = onProgress) { Text("$total 件の進捗を見る") }
    }
}

@Composable
private fun QualificationSealingPanel() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(
            "12回の撮影データを確定しています",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "確定後は撮り直しできません。判定値はこの画面に表示されません。",
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SessionCompletePanel(
    total: Int,
    onProgress: () -> Unit,
    onRetake: (() -> Unit)?,
    onOpenDiagnostics: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✓", color = PASSED_GREEN, fontSize = 80.sp, fontWeight = FontWeight.Bold)
        Text("収録が完了しました", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("$total / $total テイク保存済み", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onProgress) { Text("収録履歴を確認") }
        onRetake?.let { callback -> TextButton(onClick = callback) { Text("最新を撮り直す") } }
        onOpenDiagnostics?.let { callback ->
            TextButton(onClick = callback) { Text("開発者向け: 保存データを検証") }
        }
    }
}

@Composable
private fun StudyHeader(
    current: Int,
    total: Int,
    completed: Int,
    onProgress: () -> Unit,
    onPause: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("撮影 $current / $total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("完了 $completed 件", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onProgress) { Text("一覧") }
        TextButton(onClick = onPause) { Text("休憩") }
    }
}

@Composable
private fun StudyTakeSupplement(slot: StudyTakeSlot) {
    studyTakeSupplementJa(slot)?.let { supplement ->
        Surface(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = WARNING_BACKGROUND,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                supplement,
                Modifier.padding(10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun FaceGuide(
    session: CaptureSession,
    state: CaptureUiState,
    modifier: Modifier,
    mode: CaptureSurfaceMode = CaptureSurfaceMode.RECORDING_PROTOCOL,
) {
    val shape = RoundedCornerShape(20.dp)
    var resultAgeSeconds by remember(session) { mutableStateOf<Int?>(null) }
    LaunchedEffect(session, state.resultsObserved) {
        if (state.resultsObserved <= 0) {
            resultAgeSeconds = null
            return@LaunchedEffect
        }
        resultAgeSeconds = 0
        while (true) {
            delay(ONE_SECOND_MS)
            resultAgeSeconds = min(requireNotNull(resultAgeSeconds) + 1, MAX_RESULT_AGE_SECONDS)
        }
    }
    Box(modifier.clip(shape).background(GUIDE_BACKGROUND)) {
        CaptureCameraPreview(
            session = session,
            contentScale = GUIDED_CAPTURE_PREVIEW_CONTENT_SCALE,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val ovalHeight = size.height * GUIDE_HEIGHT_FRACTION
            val ovalWidth = min(size.width * GUIDE_WIDTH_FRACTION, ovalHeight * GUIDE_ASPECT)
            drawOval(
                color = if (state.gate.passed) PASSED_GREEN else GUIDE_LINE,
                topLeft = Offset((size.width - ovalWidth) / 2f, (size.height - ovalHeight) / 2f),
                size = Size(ovalWidth, ovalHeight),
                style = Stroke(width = GUIDE_STROKE_WIDTH),
            )
        }
        FaceMeshOverlay(
            landmarks = state.landmarks,
            aspect = state.aspect,
            modifier = Modifier.fillMaxSize(),
            contentScale = GUIDED_CAPTURE_PREVIEW_CONTENT_SCALE,
        )
        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            color = if (state.gate.hasFace) DETECTED_BADGE else SEARCHING_BADGE,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                if (state.gate.hasFace) "顔を検出しました" else "顔を探しています",
                Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            color = Color.Black.copy(alpha = 0.68f),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                gateDiagnosticSummary(state, mode, resultAgeSeconds),
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressSheet(
    slots: List<StudyTakeSlot>,
    history: List<StudyTakeRecord>,
    selected: StudyTakeSlot?,
    onDismiss: () -> Unit,
    onRetake: ((StudyTakeRecord) -> Unit)?,
) {
    val completed = completedStudyTakeCount(slots, history)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "収録の進捗  $completed / ${slots.size}",
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            items(slots, key = StudyTakeSlot::key) { slot ->
                val acceptedTake = acceptedStudyTake(history, slot)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            acceptedTake != null -> "✓"
                            slot == selected -> "▶"
                            else -> "○"
                        },
                        Modifier.padding(end = 12.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            studyTakeTitleJa(slot),
                            fontWeight = if (slot == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(groupLabelJa(slot.group), style = MaterialTheme.typography.labelSmall)
                        acceptedTake?.let { Text(it.takeId, style = MaterialTheme.typography.bodySmall) }
                    }
                    acceptedTake?.takeIf { onRetake != null }?.let { take ->
                        TextButton(onClick = { requireNotNull(onRetake).invoke(take) }) {
                            Text("撮り直す")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteRetakeSheet(
    take: StudyTakeRecord,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "このテイクを削除して撮り直しますか？",
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            studyTakeTitleJa(StudyTakeSlot(take.scenario, take.attempt, StudyTakeGroup.PROTOCOL)),
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "テイクID: ${take.takeId}",
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onDelete,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp),
        ) {
            Text("削除して撮り直す")
        }
        Spacer(Modifier.height(16.dp))
    }
}

internal fun nextIncompleteTake(
    slots: List<StudyTakeSlot>,
    history: List<StudyTakeRecord>,
): StudyTakeSlot? = slots.firstOrNull { acceptedStudyTake(history, it) == null }

internal fun studyTakeSupplementJa(slot: StudyTakeSlot): String? = when {
    slot.scenario == CaptureScenario.BLOW && slot.attempt == 1 ->
        "今回: ろうそくを吹き続けるように 息を吹き続ける"
    slot.scenario == CaptureScenario.BLOW && slot.attempt == 2 ->
        "今回: 「フッ」と短く 2〜3回 息を吹く"
    else -> null
}

private fun latestStudyTake(
    slots: List<StudyTakeSlot>,
    history: List<StudyTakeRecord>,
): StudyTakeRecord? {
    val slotKeys = slots.mapTo(hashSetOf(), StudyTakeSlot::key)
    return history
        .filter { "${it.scenario.id}#${it.attempt}" in slotKeys }
        .maxByOrNull(StudyTakeRecord::takeId)
}

private fun studyTakeTitleJa(slot: StudyTakeSlot): String {
    val title = studyTitleJa(slot.scenario)
    return if (slot.scenario in REPEATED_SCENARIOS) "$title（${slot.attempt}回目）" else title
}

private fun groupLabelJa(group: StudyTakeGroup): String = when (group) {
    StudyTakeGroup.PROTOCOL -> "収録プロトコル"
    StudyTakeGroup.EXTRA -> "追加収録"
}

private fun gateGuidance(state: CaptureUiState): String = when {
    !state.gate.hasFace -> "顔を枠の中に入れてください"
    !state.gate.centered -> "顔を枠の中央に合わせてください"
    state.distanceHint == HardGate.DistanceHint.MOVE_CLOSER -> "カメラに少し近づいてください"
    state.distanceHint == HardGate.DistanceHint.MOVE_BACK -> "カメラから少し離れてください"
    !state.gate.sized || !state.gate.distanced -> "顔の大きさを枠に合わせてください"
    !state.gate.bright -> "正面から明るく照らしてください"
    else -> "準備できました"
}

internal enum class CaptureSurfaceMode { PREVIEW_ONLY, RECORDING_PROTOCOL }

/** Ephemeral categories only; this string is never written to a take or exported. */
internal fun gateDiagnosticSummary(
    state: CaptureUiState,
    mode: CaptureSurfaceMode,
    resultAgeSeconds: Int?,
): String {
    val modeLabel = if (mode == CaptureSurfaceMode.PREVIEW_ONLY) "確認のみ" else "収録"
    val inputLabel = if (state.resultsObserved > 0) "入力あり" else "入力待ち"
    val faceLabel = if (state.gate.hasFace) "顔あり" else "顔なし"
    val ageLabel = resultAgeSeconds?.let { "${it}s" } ?: "未観測"
    val gateBits = buildString {
        append("F").append(state.gate.hasFace.asBit())
        append(" C").append(state.gate.centered.asBit())
        append(" S").append(state.gate.sized.asBit())
        append(" B").append(state.gate.bright.asBit())
        append(" D").append(state.gate.distanced.asBit())
    }
    return "$modeLabel / $inputLabel / age=$ageLabel / $faceLabel / $gateBits"
}

private fun Boolean.asBit(): Int = if (this) 1 else 0

@Suppress("CyclomaticComplexMethod")
private fun studyTitleJa(scenario: CaptureScenario): String = when (scenario) {
    CaptureScenario.NEUTRAL -> "中立"
    CaptureScenario.CHEEK_PUFF_LIGHT -> "両頬・弱"
    CaptureScenario.CHEEK_PUFF_MID -> "両頬・中"
    CaptureScenario.CHEEK_PUFF -> "両頬・強"
    CaptureScenario.CHEEK_PUFF_LEFT -> "左頬"
    CaptureScenario.CHEEK_PUFF_RIGHT -> "右頬"
    CaptureScenario.PUCKER -> "口すぼめ"
    CaptureScenario.BLOW -> "息を吹く"
    CaptureScenario.CHEEK_PUFF_YAW_RIGHT -> "右向き・両頬"
    CaptureScenario.CHEEK_PUFF_YAW_LEFT -> "左向き・両頬"
    CaptureScenario.JAW_OPEN -> "口を開く"
    CaptureScenario.SMILE -> "笑顔"
    CaptureScenario.SPEECH -> "発話"
    CaptureScenario.EYE_WIDEN -> "目を見開く"
    CaptureScenario.NOSE_SNEER -> "鼻にしわ"
    CaptureScenario.TONGUE_OUT -> "舌を出す"
    CaptureScenario.JAW_FORWARD -> "あごを前へ"
    else -> CapturePrompts.title(scenario)
}

private const val TAKE_CANCELLED = "take_cancelled"
private const val ONE_SECOND_MS = 1_000L
private const val MAX_RESULT_AGE_SECONDS = 99
private const val TRANSITION_WARNING_SECONDS = 3
private const val GUIDE_STROKE_WIDTH = 5f
private const val GUIDE_HEIGHT_FRACTION = 0.84f
private const val GUIDE_WIDTH_FRACTION = 0.72f
private const val GUIDE_ASPECT = 0.74f
private val GUIDE_BACKGROUND = Color(0xFF10151D)
private val GUIDE_LINE = Color(0xFFB8C3D1)
private val PASSED_GREEN = Color(0xFF2E7D32)
private val DETECTED_BADGE = Color(0xD92E7D32)
private val SEARCHING_BADGE = Color(0xD91F2937)
private val RECORDING_RED = Color(0xFFB3261E)
private val WARNING_BACKGROUND = Color(0xFFFFE0B2)
private val REPEATED_SCENARIOS = setOf(
    CaptureScenario.CHEEK_PUFF_LIGHT,
    CaptureScenario.CHEEK_PUFF_MID,
    CaptureScenario.CHEEK_PUFF,
    CaptureScenario.BLOW,
)
