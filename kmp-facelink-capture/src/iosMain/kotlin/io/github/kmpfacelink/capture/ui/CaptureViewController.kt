package io.github.kmpfacelink.capture.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import io.github.kmpfacelink.capture.CaptureFailureCategory
import io.github.kmpfacelink.capture.CaptureScenario
import io.github.kmpfacelink.capture.CaptureSession
import io.github.kmpfacelink.capture.IosCaptureSession
import io.github.kmpfacelink.capture.studyTakeRecords
import io.github.kmpfacelink.capture.toCaptureFailureReceipt
import io.github.kmpfacelink.capture.verifyCoefficientOnlyReference
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** Hosts the shared recording screen in a native iOS application. */
@Suppress("FunctionName")
public fun CaptureViewController(session: CaptureSession): UIViewController = ComposeUIViewController {
    MaterialTheme {
        IosCaptureScreen(session)
    }
}

@Composable
private fun IosCaptureScreen(session: CaptureSession) {
    val iosSession = session as? IosCaptureSession
    if (iosSession == null) {
        CaptureScreen(session)
        return
    }
    val scope = rememberCoroutineScope()
    var verifiedReferenceTakeId by remember(session) { mutableStateOf<String?>(null) }
    var preflightInProgress by remember(session) { mutableStateOf(false) }
    var preflightFailure by remember(session) { mutableStateOf<String?>(null) }

    fun runPreflight() {
        if (preflightInProgress) return
        val referenceTakeId = studyTakeRecords(iosSession).value
            .filter { it.scenario == CaptureScenario.NEUTRAL }
            .minByOrNull { it.takeId }
            ?.takeId
        if (referenceTakeId == null) {
            preflightFailure = CaptureFailureCategory.INTEGRITY.receipt().participantMessageJa
            return
        }
        preflightInProgress = true
        preflightFailure = null
        scope.launch {
            runCatching { iosSession.verifyCoefficientOnlyReference(referenceTakeId) }
                .onSuccess { receipt -> verifiedReferenceTakeId = receipt.takeId }
                .onFailure { failure ->
                    preflightFailure = failure.toCaptureFailureReceipt(CaptureFailureCategory.INTEGRITY)
                        .participantMessageJa
                }
            preflightInProgress = false
        }
    }

    CaptureScreen(
        session = iosSession,
        verifiedReferenceTakeId = verifiedReferenceTakeId,
        preflightInProgress = preflightInProgress,
        preflightFailure = preflightFailure,
        onRunPreflight = ::runPreflight,
    )
}

/** Hosts the no-record participant-preview check screen for a native iOS application. */
@Suppress("FunctionName")
public fun CapturePreviewCheckViewController(session: CaptureSession): UIViewController = ComposeUIViewController {
    MaterialTheme {
        CapturePreviewCheckScreen(session)
    }
}
