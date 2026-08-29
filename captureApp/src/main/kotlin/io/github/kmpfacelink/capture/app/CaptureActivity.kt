package io.github.kmpfacelink.capture.app

import android.Manifest
import android.content.pm.PackageManager
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
import io.github.kmpfacelink.capture.CapturePreflightObservation
import io.github.kmpfacelink.capture.CapturePreflightPolicy
import io.github.kmpfacelink.capture.SourceProvenance
import io.github.kmpfacelink.capture.TakeVerifier
import io.github.kmpfacelink.capture.ui.CapturePreviewCheckScreen
import io.github.kmpfacelink.capture.ui.CaptureScreen
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Explicit developer-only shell route. The preview screen itself has no record or benchmark UI. */
internal enum class CaptureLaunchRoute { CAPTURE, PREVIEW_CHECK }

internal fun captureLaunchRoute(previewCheckRequested: Boolean): CaptureLaunchRoute =
    if (previewCheckRequested) CaptureLaunchRoute.PREVIEW_CHECK else CaptureLaunchRoute.CAPTURE

internal const val PREVIEW_CHECK_INTENT_EXTRA: String = "preview_check"

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
        // Enforced from API 35 anyway; calling it makes the intent explicit and gives the same
        // layout on older releases. The insets are then paid back below — the bottom row is the
        // record controls, and a button under the gesture bar is a mis-tap during a take.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val current = session
                when {
                    permissionDenied -> Message("収録にはカメラの許可が必要です。")
                    startFailure != null -> Message("カメラを開始できませんでした。アプリを閉じて、もう一度お試しください。")
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
                    else -> Message("カメラを準備しています…")
                }
            }
        }
        requestCamera()
    }

    private fun requestCamera() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) openSession() else permission.launch(Manifest.permission.CAMERA)
    }

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
        val opened = AndroidCaptureSession(this, this, provenance)
        session = opened
        lifecycleScope.launch {
            val result = runCatching { opened.start() }
            result.onSuccess { ready = true }
            result.onFailure { startFailure = it.message ?: it::class.simpleName }
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
            runAuthorizationBytes = null
            runAuthorizationStatus = "実行認可を拒否しました: ${error.message ?: error::class.simpleName}"
            return "performance benchmark blocked: ${error.message ?: error::class.simpleName}"
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
            "performance benchmark failed: ${error.message ?: error::class.simpleName}"
        } finally {
            runCatching { session.resumeAfterOfflineBenchmark() }
                .onFailure { startFailure = "性能計測後のカメラ復帰に失敗: ${it.message}" }
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
                runAuthorizationStatus =
                    "実行認可を拒否しました: ${error.message ?: error::class.simpleName}"
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
                verifiedReferenceTakeId = outcome.takeId
            } else {
                preflightFailure = "撮影データを確認できませんでした。" +
                    " 同じ回を撮り直すか、もう一度確認してください。"
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
                onFailure = { "verification failed: ${it.message ?: it::class.simpleName}" },
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

    private fun referenceTake(session: AndroidCaptureSession) = session.takesDirectory.listFiles()
        ?.filter { it.isDirectory && it.name.contains("_neutral_10s_") }
        ?.minByOrNull { it.name }

    @androidx.compose.runtime.Composable
    private fun Message(text: String) {
        Text(text, Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp))
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
