package io.github.kmpfacelink.capture.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.capture.AndroidCaptureSession
import io.github.kmpfacelink.capture.AuthorizedAndroidPerformanceRunner
import io.github.kmpfacelink.capture.CaptureFailureCategory
import io.github.kmpfacelink.capture.CapturePreflightObservation
import io.github.kmpfacelink.capture.CapturePreflightPolicy
import io.github.kmpfacelink.capture.CaptureStorageProfile
import io.github.kmpfacelink.capture.CaptureStudyProtocol
import io.github.kmpfacelink.capture.CoefficientOnlyPreflightObservation
import io.github.kmpfacelink.capture.InferenceRecordFormat
import io.github.kmpfacelink.capture.InferenceRecordReader
import io.github.kmpfacelink.capture.ParticipantQualificationEvidence
import io.github.kmpfacelink.capture.SourceProvenance
import io.github.kmpfacelink.capture.TakeManifest
import io.github.kmpfacelink.capture.TakeVerifier
import io.github.kmpfacelink.capture.captureFailure
import io.github.kmpfacelink.capture.toCaptureFailureReceipt
import io.github.kmpfacelink.capture.ui.CapturePreviewCheckScreen
import io.github.kmpfacelink.capture.ui.CaptureScreen
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** Explicit developer-only shell route. The preview screen itself has no record or benchmark UI. */
internal enum class CaptureLaunchRoute { CAPTURE, PREVIEW_CHECK }

internal fun captureLaunchRoute(previewCheckRequested: Boolean): CaptureLaunchRoute =
    if (previewCheckRequested) CaptureLaunchRoute.PREVIEW_CHECK else CaptureLaunchRoute.CAPTURE

/** Preview-only is never a developer Session-C launch, even when its Intent has unrelated extras. */
internal fun studyProtocolForLaunchRoute(
    route: CaptureLaunchRoute,
    requested: String?,
): CaptureStudyProtocol = if (route == CaptureLaunchRoute.PREVIEW_CHECK) {
    CaptureStudyProtocol.guided22()
} else {
    captureStudyProtocol(requested)
}

internal const val PREVIEW_CHECK_INTENT_EXTRA: String = "preview_check"
internal const val STUDY_PROTOCOL_INTENT_EXTRA: String = "study_protocol"
internal const val LEARNED_PATCH_SESSION_C_INTENT_VALUE: String = "learned_patch_session_c_v1"
internal const val PARTICIPANT_QUALIFICATION_INTENT_VALUE: String =
    "participant_qualification_12_v1"
internal const val SESSION_C_AUTHORIZATION_INTENT_EXTRA: String = "session_c_authorization"
private const val SESSION_C_CHEEK_MODEL_SHA256 = "a304d132e36cad9f6dcc9a8c1c78f34201cbee90e640f39625a2fbe05fd12dd3"
private const val SESSION_C_MOUTH_MODEL_SHA256 = "70a340a7ee3ef25a526da8292fe06aecc2353e4d85c68f02c671cd189d8821b1"

/** Developer-only selection. Unknown values fail closed before camera or storage is opened. */
internal fun captureStudyProtocol(requested: String?): CaptureStudyProtocol = when (requested) {
    null -> CaptureStudyProtocol.guided22()
    LEARNED_PATCH_SESSION_C_INTENT_VALUE -> CaptureStudyProtocol.learnedPatchSessionC12()
    PARTICIPANT_QUALIFICATION_INTENT_VALUE -> CaptureStudyProtocol.learnedPatchSessionC12()
    else -> throw captureFailure(CaptureFailureCategory.CONFIGURATION)
}

internal fun isParticipantQualificationRoute(requested: String?): Boolean =
    requested == PARTICIPANT_QUALIFICATION_INTENT_VALUE

internal data class ParticipantQualificationPrecameraReceipt(
    val evidence: ParticipantQualificationEvidence,
    val availableBytes: Long,
    val requiredBytes: Long = SESSION_C_MINIMUM_AVAILABLE_BYTES,
    val worstCaseBytes: Long = PARTICIPANT_QUALIFICATION_WORST_CASE_BYTES,
)

/** Pure pre-camera guard for the simple local route; it never accepts an external run identity. */
internal fun requireParticipantQualificationPrecamera(
    protocol: CaptureStudyProtocol,
    gitSha: String,
    dirty: Boolean,
    evidence: ParticipantQualificationEvidence,
    availableBytes: Long,
): ParticipantQualificationPrecameraReceipt {
    require(protocol == CaptureStudyProtocol.learnedPatchSessionC12()) {
        "participant qualification requires the exact frozen 12-slot protocol"
    }
    require(gitSha.matches(Regex("[0-9a-f]{40}"))) { "reviewed source commit is missing" }
    require(!dirty) { "participant qualification requires a clean reviewed source build" }
    requireCaptureProtocolCapacity(protocol, availableBytes)
    return ParticipantQualificationPrecameraReceipt(
        evidence = evidence,
        availableBytes = availableBytes,
    )
}

/** Session C can retain up to 12 bounded canonical takes; fail before camera permission. */
internal fun requireCaptureProtocolCapacity(
    protocol: CaptureStudyProtocol,
    availableBytes: Long,
) {
    if (protocol.id == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
        if (availableBytes < SESSION_C_MINIMUM_AVAILABLE_BYTES) {
            throw captureFailure(CaptureFailureCategory.CAPACITY)
        }
    }
}

/** Pure launch precondition: validates Session C before the Activity can request camera permission. */
internal fun requireSessionCAuthorizationBeforeCapacity(
    protocol: CaptureStudyProtocol,
    authorizationBytes: () -> ByteArray?,
    expectation: () -> SessionCAuthorizationExpectation,
    availableBytes: () -> Long,
): SessionCExecutionAuthorization? {
    if (protocol.id != CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) return null
    val authorization = SessionCExecutionAuthorizationVerifier.verify(
        requireSessionCAuthorizationBytes(authorizationBytes()),
        expectation(),
    )
    requireCaptureProtocolCapacity(protocol, availableBytes())
    return authorization
}

/** Pure callback-time guard. It intentionally rebuilds time-sensitive expectation values. */
internal fun revalidateSessionCAuthorizationBeforeOpen(
    protocol: CaptureStudyProtocol,
    authorizationBytes: () -> ByteArray?,
    expectation: () -> SessionCAuthorizationExpectation,
): SessionCExecutionAuthorization? {
    if (protocol.id != CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) return null
    return SessionCExecutionAuthorizationVerifier.verify(
        requireSessionCAuthorizationBytes(authorizationBytes()),
        expectation(),
    )
}

/** The only null-to-failure conversion for a Session-C raw authorization supplier. */
internal fun requireSessionCAuthorizationBytes(bytes: ByteArray?): ByteArray =
    bytes ?: error("Session-C authorization is required before camera setup")

/** Keeps the signed ID as the only Session-C store identity selected by this Activity route. */
internal fun requiredSessionCStudyRunId(
    protocol: CaptureStudyProtocol,
    authorization: SessionCExecutionAuthorization?,
): String? = if (protocol.id == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
    requireNotNull(authorization) { "Session-C authorization result is missing" }.runId
} else {
    null
}

internal const val SESSION_C_MINIMUM_AVAILABLE_BYTES: Long = 4L shl 30
internal const val PARTICIPANT_QUALIFICATION_WORST_CASE_BYTES: Long = 3_367_001_472L

private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { byte -> "%02x".format(byte) }

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

@androidx.compose.runtime.Composable
private fun CaptureMessage(text: String) {
    Text(text, Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp))
}

@androidx.compose.runtime.Composable
private fun ParticipantQualificationPrecameraPanel(
    receipt: ParticipantQualificationPrecameraReceipt,
    onConfirm: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("参加者Qualification（12テイク）", style = MaterialTheme.typography.headlineMedium)
        Text(
            "所要時間の目安は動作264秒＋準備・確認です。撮影中も顔を大きく表示します。",
            Modifier.padding(top = 16.dp),
        )
        Text(
            "空き容量: ${receipt.availableBytes.toStorageText()}\n" +
                "開始条件: ${receipt.requiredBytes.toStorageText()}\n" +
                "12テイク最大見積: ${receipt.worstCaseBytes.toStorageText()}",
            Modifier.padding(top = 16.dp),
        )
        Text(
            "この画面ではスコアを計算・表示しません。完了後に別のoffline検証で集計します。",
            Modifier.padding(top = 16.dp),
        )
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) { Text("内容を確認してカメラを開始") }
    }
}

private fun Long.toStorageText(): String = "%,d bytes (%.2f GiB)".format(
    this,
    toDouble() / (1L shl 30),
)

private fun referenceTake(session: AndroidCaptureSession) = session.takesDirectory.listFiles()
    ?.filter { it.isDirectory && it.name.contains("_neutral_10s_") }
    ?.minByOrNull { it.name }

/**
 * The recorder.
 *
 * Nothing but a shell: the camera, the inference and the file format live in `kmp-facelink-capture`
 * so that the iOS recorder can be the same program, and so the recording logic is testable without
 * an Activity.
 *
 * The session is opened once the camera permission is granted and released in `onDestroy`. It is
 * held here rather than in a ViewModel because it owns the camera binding, which is tied to this
 * lifecycle.
 */
class CaptureActivity : ComponentActivity() {

    private var session: AndroidCaptureSession? = null
    private var sourceProvenance: SourceProvenance? = null
    private var selectedStudyProtocol: CaptureStudyProtocol = CaptureStudyProtocol.guided22()
    private var participantQualificationRoute: Boolean = false
    private var participantQualificationReceipt: ParticipantQualificationPrecameraReceipt? = null
    private var participantQualificationConfirmed by mutableStateOf(false)

    /**
     * The open session, or null before the camera permission is granted.
     *
     * Exposed so an instrumented test can watch the pipeline come up on a real device: whether
     * frames reach inference at all is a property of the phone, not of the code, and it cannot be
     * checked on a host JVM.
     */
    val captureSession: AndroidCaptureSession? get() = session

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) openSession() else permissionDenied = true }

    private val runAuthorizationPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            runAuthorizationStatus = "実行認可は選択されていません。"
        } else {
            validateRunAuthorization(uri)
        }
    }

    private var permissionDenied by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var startFailure by mutableStateOf<String?>(null)
    private var verifying by mutableStateOf(false)
    private var verifyReport by mutableStateOf<String?>(null)
    private var verifiedReferenceTakeId by mutableStateOf<String?>(null)
    private var preflightFailure by mutableStateOf<String?>(null)
    private var performanceReport by mutableStateOf<String?>(null)
    private var showingDiagnostics by mutableStateOf(false)
    private var launchRoute by mutableStateOf(CaptureLaunchRoute.CAPTURE)
    private var runAuthorizationBytes by mutableStateOf<ByteArray?>(null)
    private var runAuthorizationStatus by mutableStateOf("性能計測はexact-binaryレビュー後の署名付き認可が必要です。")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchRoute = captureLaunchRoute(
            intent?.getBooleanExtra(PREVIEW_CHECK_INTENT_EXTRA, false) == true,
        )
        val requestedStudyProtocol = intent?.getStringExtra(STUDY_PROTOCOL_INTENT_EXTRA)
        participantQualificationRoute = launchRoute == CaptureLaunchRoute.CAPTURE &&
            isParticipantQualificationRoute(requestedStudyProtocol)
        val protocolSelection = runCatching {
            studyProtocolForLaunchRoute(
                launchRoute,
                requestedStudyProtocol,
            )
        }
        protocolSelection.onSuccess { protocol ->
            selectedStudyProtocol = protocol
            if (participantQualificationRoute) {
                runCatching {
                    requireParticipantQualificationPrecamera(
                        protocol = protocol,
                        gitSha = BuildConfig.GIT_SHA,
                        dirty = BuildConfig.GIT_DIRTY,
                        evidence = participantQualificationEvidence(),
                        availableBytes = filesDir.usableSpace,
                    )
                }.onSuccess { participantQualificationReceipt = it }
                    .onFailure { startFailure = it.toCaptureFailureReceipt().participantMessageJa }
            } else if (protocol.id == CaptureStudyProtocol.LEARNED_PATCH_SESSION_C_ID) {
                runCatching {
                    requireSessionCAuthorizationBeforeCapacity(
                        protocol,
                        ::sessionCAuthorizationBytes,
                        ::sessionCAuthorizationExpectation,
                        { filesDir.usableSpace },
                    )
                }.onFailure { startFailure = it.toCaptureFailureReceipt().participantMessageJa }
            }
        }
        protocolSelection.onFailure { startFailure = it.toCaptureFailureReceipt().participantMessageJa }
        // Enforced from API 35 anyway; calling it makes the intent explicit and gives the same
        // layout on older releases. The insets are then paid back below — the bottom row is the
        // record controls, and a button under the gesture bar is a mis-tap during a take.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val current = session
                when {
                    permissionDenied -> CaptureMessage("収録にはカメラの許可が必要です。")
                    startFailure != null -> CaptureMessage("撮影を開始できませんでした: $startFailure")
                    participantQualificationRoute && !participantQualificationConfirmed ->
                        ParticipantQualificationPrecameraPanel(
                            receipt = requireNotNull(participantQualificationReceipt),
                            onConfirm = {
                                participantQualificationConfirmed = true
                                requestCamera()
                            },
                        )
                    current != null && ready -> {
                        if (launchRoute == CaptureLaunchRoute.PREVIEW_CHECK) {
                            CapturePreviewCheckScreen(current)
                        } else if (showingDiagnostics) {
                            DiagnosticsScreen(current)
                        } else {
                            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                                CaptureScreen(
                                    current,
                                    modifier = Modifier.weight(1f),
                                    onOpenDiagnostics = { showingDiagnostics = true },
                                    verifiedReferenceTakeId = verifiedReferenceTakeId,
                                    preflightInProgress = verifying,
                                    preflightFailure = preflightFailure,
                                    onRunPreflight = { runReferencePreflight(current) },
                                )
                            }
                        }
                    }
                    else -> CaptureMessage("カメラを準備しています…")
                }
            }
        }
        if (
            protocolSelection.isSuccess &&
            startFailure == null &&
            !participantQualificationRoute
        ) {
            requestCamera()
        }
    }

    private fun requestCamera() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) openSession() else permission.launch(Manifest.permission.CAMERA)
    }

    private fun sessionCAuthorizationBytes(): ByteArray? =
        intent?.getStringExtra(SESSION_C_AUTHORIZATION_INTENT_EXTRA)?.encodeToByteArray()

    private fun sessionCAuthorizationExpectation(): SessionCAuthorizationExpectation =
        SessionCAuthorizationExpectation(
            sourceClosureSha256 = BuildConfig.REVIEW_SOURCE_MANIFEST_SHA256,
            apkSha256 = sha256(File(applicationInfo.sourceDir)),
            executionContractSha256 = BuildConfig.SESSION_C_EXECUTION_CONTRACT_SHA256,
            cheekModelSha256 = SESSION_C_CHEEK_MODEL_SHA256,
            mouthControlModelSha256 = SESSION_C_MOUTH_MODEL_SHA256,
            now = Instant.now(),
            permitSyntheticFixture = false,
        )

    private fun participantQualificationEvidence(): ParticipantQualificationEvidence =
        ParticipantQualificationEvidence(
            reviewPacketSha256 = BuildConfig.REVIEW_PACKET_SHA256,
            sourceManifestSha256 = BuildConfig.REVIEW_SOURCE_MANIFEST_SHA256,
            sourceAuthorizationSha256 = BuildConfig.REVIEW_AUTHORIZATION_SHA256,
            installedApkSha256 = sha256(File(applicationInfo.sourceDir)),
            cheekModelSha256 = SESSION_C_CHEEK_MODEL_SHA256,
            mouthControlModelSha256 = SESSION_C_MOUTH_MODEL_SHA256,
            mediaPipeTaskSha256 = assets.open(AndroidCaptureSession.DEFAULT_MODEL_ASSET).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
            },
            deviceContractSha256 = sha256(
                listOf(
                    "android-device-contract-v1",
                    Build.MANUFACTURER,
                    Build.MODEL,
                    Build.VERSION.SDK_INT.toString(),
                    Build.VERSION.RELEASE,
                ).joinToString("\n").encodeToByteArray(),
            ),
        )

    private fun openSession() {
        // The build's own provenance, so every take can be traced to the tree that produced it.
        val provenance = SourceProvenance(
            repo = "KMP-FaceLink",
            branch = BuildConfig.GIT_BRANCH,
            gitSha = BuildConfig.GIT_SHA,
            commitTimeIso = BuildConfig.GIT_COMMIT_TIME,
            dirty = BuildConfig.GIT_DIRTY,
        )
        sourceProvenance = provenance
        lifecycleScope.launch {
            val result = runCatching {
                val qualificationEvidence = if (participantQualificationRoute) {
                    val revalidated = requireParticipantQualificationPrecamera(
                        protocol = selectedStudyProtocol,
                        gitSha = BuildConfig.GIT_SHA,
                        dirty = BuildConfig.GIT_DIRTY,
                        evidence = participantQualificationEvidence(),
                        availableBytes = filesDir.usableSpace,
                    )
                    require(revalidated.evidence == participantQualificationReceipt?.evidence) {
                        "participant qualification build evidence changed before camera open"
                    }
                    revalidated.evidence
                } else {
                    null
                }
                val sessionCAuthorization = if (participantQualificationRoute) {
                    null
                } else {
                    revalidateSessionCAuthorizationBeforeOpen(
                        selectedStudyProtocol,
                        ::sessionCAuthorizationBytes,
                        ::sessionCAuthorizationExpectation,
                    )
                }
                AndroidCaptureSession(
                    this@CaptureActivity,
                    this@CaptureActivity,
                    provenance,
                    studyProtocol = selectedStudyProtocol,
                    requiredStudyRunId = if (participantQualificationRoute) {
                        null
                    } else {
                        requiredSessionCStudyRunId(selectedStudyProtocol, sessionCAuthorization)
                    },
                    participantQualificationEvidence = qualificationEvidence,
                ).also { opened ->
                    session = opened
                    opened.start()
                }
            }
            result.onSuccess { ready = true }
            result.onFailure { startFailure = it.toCaptureFailureReceipt().participantMessageJa }
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    /**
     * Replays the first neutral take through offline inference and prints the comparison.
     *
     * Run it after the first take of a session, before recording the rest: it is what says the
     * stored pixels are the pixels inference saw. Both running modes are reported because the live
     * take ran in LIVE_STREAM, which skips detection while tracking holds — so IMAGE differs by the
     * mode itself, and VIDEO should sit closer.
     */
    @androidx.compose.runtime.Composable
    @Suppress("LongMethod") // The diagnostic controls are kept together as one deliberate screen.
    private fun DiagnosticsScreen(session: AndroidCaptureSession) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            TextButton(
                onClick = { showingDiagnostics = false },
                modifier = Modifier.align(Alignment.Start),
            ) { Text("← 収録へ戻る") }
            Text("保存データの検証", style = MaterialTheme.typography.headlineMedium)
            Text(
                "最初の中立テイクをIMAGE/VIDEOで再推論します。収録対象の選択や進捗には影響しません。",
                Modifier.padding(vertical = 8.dp),
            )
            Button(
                enabled = !verifying,
                onClick = {
                    verifying = true
                    verifyReport = "検証しています…"
                    lifecycleScope.launch {
                        val outcome = withContext(Dispatchers.IO) { verifyReferenceTake(session) }
                        verifyReport = outcome.summary
                        if (outcome.passedPreflight) verifiedReferenceTakeId = outcome.takeId
                        verifying = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("最初の中立テイクを検証") }
            Button(
                enabled = !verifying,
                onClick = { runAuthorizationPicker.launch(arrayOf("text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("性能計測の実行認可を選択") }
            Text(
                runAuthorizationStatus,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Button(
                enabled = !verifying && runAuthorizationBytes != null,
                onClick = {
                    verifying = true
                    performanceReport = "性能を計測しています…"
                    lifecycleScope.launch {
                        performanceReport = runPerformanceBenchmark(session)
                        verifying = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    if (runAuthorizationBytes == null) {
                        "性能計測（実行認可が必要）"
                    } else {
                        "保存入力で性能計測（IMAGE / CPU）"
                    },
                )
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)) {
                item { verifyReport?.let { Text(it) } }
                item { performanceReport?.let { Text(it, Modifier.padding(top = 12.dp)) } }
            }
        }
    }

    private suspend fun runPerformanceBenchmark(session: AndroidCaptureSession): String {
        val reference = referenceTake(session) ?: return "中立テイクがまだありません。"
        if (sourceProvenance == null) return "ビルド由来を取得できません。"
        val authorization = runAuthorizationBytes ?: return "署名付き実行認可がありません。"
        val runner = performanceRunner()
        withContext(Dispatchers.IO) {
            runCatching {
                runner.validate(authorization)
            }
        }.getOrElse { error ->
            val failure = error.toCaptureFailureReceipt(CaptureFailureCategory.CONFIGURATION)
            runAuthorizationBytes = null
            runAuthorizationStatus = failure.participantMessageJa
            return "performance benchmark blocked: ${failure.code}"
        }
        return try {
            session.pauseForOfflineBenchmark()
            withContext(Dispatchers.IO) {
                // [AuthorizedAndroidPerformanceRunner.run] verifies the same bytes again directly
                // before the internal benchmark starts; the preview/UI preflight never authorizes
                // replay by itself.
                val artifact = runner.run(reference, authorization)
                val steady = artifact.manifest.phases.first { it.phase.name == "STEADY" }
                val estimator = steady.latencies.getValue(
                    io.github.kmpfacelink.capture.PerformanceLatencyBoundary.ESTIMATOR_CALL_ONLY,
                )
                val sdk = steady.latencies.getValue(
                    io.github.kmpfacelink.capture.PerformanceLatencyBoundary.SDK_PROCESS_TO_OUTPUT_READY,
                )
                val total = steady.latencies.getValue(
                    io.github.kmpfacelink.capture.PerformanceLatencyBoundary.ESTIMATOR_TO_SDK_OUTPUT_READY,
                )
                buildString {
                    append("保存入力のoffline replay / IMAGE / CPU\n")
                    append("steady calls=").append(steady.inferenceCalls).append('\n')
                    append("推定器呼び出し p50=").append(estimator.p50Ms)
                        .append("ms p95=").append(estimator.p95Ms).append("ms\n")
                    append("KMP処理のみ p50=").append(sdk.p50Ms)
                        .append("ms p95=").append(sdk.p95Ms).append("ms\n")
                    append("推定器→KMP出力準備 p50=").append(total.p50Ms)
                        .append("ms p95=").append(total.p95Ms).append("ms\n")
                    append("カメラ・非同期queue・Flow受信・描画は含みません。\n")
                    append("manifest SHA-256: ").append(artifact.manifestSha256).append('\n')
                    append("保存先: ").append(artifact.manifestFile.path)
                }
            }
        } catch (error: Exception) {
            val failure = error.toCaptureFailureReceipt()
            "performance benchmark failed: ${failure.code}"
        } finally {
            runCatching { session.resumeAfterOfflineBenchmark() }
                .onFailure {
                    startFailure = it.toCaptureFailureReceipt(CaptureFailureCategory.FRAME_SOURCE)
                        .participantMessageJa
                }
        }
    }

    private fun validateRunAuthorization(uri: android.net.Uri) {
        runAuthorizationBytes = null
        runAuthorizationStatus = "署名とインストール済みAPKを確認しています…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use(::readBoundedAuthorization)
                        ?: error("実行認可ファイルを開けません。")
                    val evidence = performanceRunner().validate(bytes)
                    bytes to evidence
                }
            }
            result.onSuccess { (bytes, evidence) ->
                runAuthorizationBytes = bytes
                runAuthorizationStatus =
                    "PASS: ${evidence.binaryPacketId} / APK " +
                    "${evidence.binarySha256.take(AUTHORIZATION_DIGEST_PREFIX_LENGTH)}…"
            }.onFailure { error ->
                runAuthorizationStatus = error.toCaptureFailureReceipt(
                    CaptureFailureCategory.CONFIGURATION,
                ).participantMessageJa
            }
        }
    }

    private fun performanceRunner(): AuthorizedAndroidPerformanceRunner =
        AuthorizedAndroidPerformanceRunner(
            platformContext = PlatformContext(this, this),
        )

    private fun readBoundedAuthorization(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MAX_AUTHORIZATION_BYTES) { "実行認可ファイルが大きすぎます。" }
        }
        return output.toByteArray()
    }

    private fun runReferencePreflight(session: AndroidCaptureSession) {
        if (verifying) return
        verifying = true
        preflightFailure = null
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { verifyReferenceTake(session) }
            verifyReport = outcome.summary
            if (outcome.passedPreflight) {
                val persisted = runCatching {
                    if (participantQualificationRoute) {
                        session.confirmParticipantQualificationReferencePreflight(
                            requireNotNull(outcome.takeId),
                        )
                    }
                }
                if (persisted.isSuccess) {
                    verifiedReferenceTakeId = outcome.takeId
                } else {
                    session.terminateParticipantQualification(
                        CaptureFailureCategory.INTEGRITY.receipt(),
                    )
                    preflightFailure = "撮影データの確認結果を固定できなかったため、この実行を停止しました。"
                }
            } else {
                if (participantQualificationRoute) {
                    session.terminateParticipantQualification(
                        CaptureFailureCategory.INTEGRITY.receipt(),
                    )
                    preflightFailure = "撮影データを確認できなかったため、この実行を停止しました。"
                } else {
                    preflightFailure = "撮影データを確認できませんでした。" +
                        " 同じ回を撮り直すか、もう一度確認してください。"
                }
            }
            verifying = false
        }
    }

    private fun verifyReferenceTake(session: AndroidCaptureSession): VerificationOutcome {
        val reference = referenceTake(session) ?: return VerificationOutcome(
            takeId = null,
            passedPreflight = false,
            summary = "中立テイクがまだありません。",
        )
        val manifest = runCatching {
            Json.decodeFromString<TakeManifest>(
                File(reference, InferenceRecordFormat.TAKE_MANIFEST_FILE).readText(),
            )
        }.getOrElse { failure ->
            return VerificationOutcome(
                takeId = reference.name,
                passedPreflight = false,
                summary = failure.toCaptureFailureReceipt(CaptureFailureCategory.INTEGRITY)
                    .participantMessageJa,
            )
        }
        if (manifest.storageReceipt.profile == CaptureStorageProfile.COEFFICIENTS_ONLY_V1) {
            return verifyCoefficientOnlyReference(reference, manifest)
        }
        val verifier = TakeVerifier(PlatformContext(this, this))
        val reports = listOf(InferenceRunningMode.IMAGE, InferenceRunningMode.VIDEO).map { mode ->
            runCatching { verifier.verify(reference, mode) }
        }
        val passed = reports.all(Result<TakeVerifier.Report>::isSuccess) &&
            CapturePreflightPolicy.passes(
                reports.mapNotNull(Result<TakeVerifier.Report>::getOrNull).map { report ->
                    CapturePreflightObservation(
                        mode = report.mode,
                        storedFrames = report.storedFrames,
                        liveResults = report.liveResults,
                        joined = report.joined,
                        faceAgreement = report.faceAgreement,
                    )
                },
            )
        val details = reports.joinToString("\n") { result ->
            result.fold(
                onSuccess = TakeVerifier.Report::summary,
                onFailure = {
                    "verification failed: " +
                        it.toCaptureFailureReceipt(CaptureFailureCategory.INTEGRITY).code
                },
            )
        }
        return VerificationOutcome(
            takeId = reference.name,
            passedPreflight = passed,
            summary = if (passed) {
                "PASS: 保存フレームと推論結果の対応を確認しました。\n$details"
            } else {
                "REVIEW REQUIRED: 保存と推論の対応条件を満たしませんでした。\n$details"
            },
        )
    }

    /**
     * Coefficient-only is the participant-facing default, so its first-take gate verifies exactly
     * what that profile promises: no pixel payload and complete, hash-bound result streams. It must
     * not invoke IMAGE/VIDEO replay, which is meaningful only for the explicit canonical-RGBA
     * profile.
     */
    private fun verifyCoefficientOnlyReference(
        reference: File,
        manifest: TakeManifest,
    ): VerificationOutcome {
        val failure = runCatching {
            manifest.storageReceipt.requireSchema4Consistency(manifest.framesStored)
            val reader = InferenceRecordReader(reference.absolutePath)
            val increasing = reader.headers.zipWithNext().all { (left, right) ->
                left.analysisFrameSeq < right.analysisFrameSeq &&
                    left.captureTimestampNs < right.captureTimestampNs
            }
            val expectedHashes = listOf(
                manifest.resultIndexSha256,
                manifest.landmarksSha256,
                manifest.blendshapesSha256,
                manifest.transformSha256,
            )
            val actualHashes = listOf(
                InferenceRecordFormat.INDEX_FILE,
                InferenceRecordFormat.LANDMARKS_FILE,
                InferenceRecordFormat.BLENDSHAPES_FILE,
                InferenceRecordFormat.TRANSFORM_FILE,
            ).map { name ->
                sha256(File(reference, name))
            }
            val observation = CoefficientOnlyPreflightObservation(
                schemaVersion = manifest.schemaVersion,
                profile = manifest.storageReceipt.profile,
                pixelSemantics = manifest.storageReceipt.pixelSemantics,
                framesStored = manifest.framesStored,
                framesDirectoryExists = File(reference, AndroidCaptureSession.FRAMES_DIR).exists(),
                analysisFramesAdmitted = manifest.analysisFramesAdmitted,
                manifestInferenceResults = manifest.inferenceResults,
                resultRecords = reader.headers.size,
                sequenceAndTimestampStrictlyIncreasing = increasing,
                resultStreamHashesMatch = actualHashes == expectedHashes,
            )
            require(CapturePreflightPolicy.passesCoefficientOnly(observation)) {
                "coefficient-only storage or result-stream contract failed"
            }
        }.exceptionOrNull()
        val passed = failure == null
        return VerificationOutcome(
            takeId = reference.name,
            passedPreflight = passed,
            summary = if (passed) {
                "PASS: 係数のみの収録に画像が無く、結果ストリームの件数・順序・hashが一致しました。"
            } else {
                failure.toCaptureFailureReceipt(CaptureFailureCategory.INTEGRITY)
                    .participantMessageJa
            },
        )
    }

    private data class VerificationOutcome(
        val takeId: String?,
        val passedPreflight: Boolean,
        val summary: String,
    )

    private companion object {
        const val AUTHORIZATION_DIGEST_PREFIX_LENGTH = 12
        const val MAX_AUTHORIZATION_BYTES = 16 * 1024
    }
}
