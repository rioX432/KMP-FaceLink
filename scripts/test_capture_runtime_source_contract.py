import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
SWIFT_RUNTIME = ROOT / "captureIosApp/FaceLinkCapture/CaptureRootView.swift"
CAPTURE_SCREEN = ROOT / (
    "kmp-facelink-capture/src/commonMain/kotlin/io/github/kmpfacelink/"
    "capture/ui/CaptureScreen.kt"
)
GUIDED_FLOW = ROOT / (
    "kmp-facelink-capture/src/commonMain/kotlin/io/github/kmpfacelink/"
    "capture/GuidedCaptureFlow.kt"
)
RETAKE_GUARD = ROOT / (
    "kmp-facelink-capture/src/commonMain/kotlin/io/github/kmpfacelink/"
    "capture/RetakeDeleteGuard.kt"
)


def function_slice(source: str, start: str, next_start: str) -> str:
    begin = source.index(start)
    end = source.index(next_start, begin + len(start))
    return source[begin:end]


class CaptureRuntimeSourceContractTest(unittest.TestCase):
    def test_arkit_start_enables_callbacks_before_run_and_rolls_back_fail_closed(self) -> None:
        source = SWIFT_RUNTIME.read_text(encoding="utf-8")
        start = function_slice(source, "func start() throws", "func stop()")
        lock = start.index("callbackLock.lock()")
        eligible = start.index("running = true")
        unlock_for_run = start.index("callbackLock.unlock()", eligible)
        run = start.index("arSession.run(configuration)")
        self.assertLess(lock, eligible)
        self.assertLess(eligible, unlock_for_run)
        self.assertLess(unlock_for_run, run)
        self.assertIn("let remainedActive = running", start[run:])

        rollback = start[start.index("} catch {", run) :]
        report_and_stop = rollback.index("failSourceLocked()")
        unlock = rollback.index("callbackLock.unlock()")
        rethrow = rollback.index("throw error")
        self.assertLess(report_and_stop, unlock)
        self.assertLess(unlock, rethrow)
        self.assertNotIn("localizedDescription", start)

    def test_arkit_failure_and_interruption_report_before_runtime_stop(self) -> None:
        source = SWIFT_RUNTIME.read_text(encoding="utf-8")
        self.assertIn("func session(_ session: ARSession, didFailWithError error: Error)", source)
        self.assertIn("func sessionWasInterrupted(_ session: ARSession)", source)
        observer = function_slice(
            source,
            "private func failSourceFromObserver()",
            "private func failSourceLocked()",
        )
        self.assertIn("callbackLock.lock()", observer)
        self.assertIn("guard running else { return }", observer)
        self.assertIn("failSourceLocked()", observer)

        terminal = function_slice(source, "private func failSourceLocked()", "private func stopLocked()")
        report = terminal.index("captureSession.reportFrameSourceFailure")
        stable_category = terminal.index('message: "frame_source"')
        stop = terminal.index("stopLocked()")
        self.assertLess(report, stable_category)
        self.assertLess(stable_category, stop)
        self.assertNotIn("localizedDescription", terminal)

    def test_terminal_ui_precedes_preflight_and_gates_sheets(self) -> None:
        source = CAPTURE_SCREEN.read_text(encoding="utf-8")
        branch = source.index(
            "guided.stage == GuidedCaptureStage.TERMINATED -> CaptureTerminatedPanel"
        )
        preflight = source.index("preflightRequired -> PreflightPanel")
        self.assertLess(branch, preflight)
        self.assertIn(
            "if (showProgress && guided.stage != GuidedCaptureStage.TERMINATED)",
            source,
        )
        self.assertIn(
            "retakeTarget?.takeIf { guided.stage != GuidedCaptureStage.TERMINATED }",
            source,
        )

    def test_terminal_flow_is_absorbing(self) -> None:
        source = GUIDED_FLOW.read_text(encoding="utf-8")
        on_failed = function_slice(source, "fun onFailed(", "fun retry()")
        guard = on_failed.index("if (stage == GuidedCaptureStage.TERMINATED) return snapshot")
        mutation = on_failed.index("stage = when")
        self.assertLess(guard, mutation)

    def test_preview_only_surface_has_no_recorder_call(self) -> None:
        source = CAPTURE_SCREEN.read_text(encoding="utf-8")
        preview_only = function_slice(
            source,
            "public fun CapturePreviewCheckScreen(",
            "private fun GuidedIntroductionPanel(",
        )
        self.assertNotIn("session.record(", preview_only)

    def test_retake_delete_guards_before_launch_and_after_suspension(self) -> None:
        source = CAPTURE_SCREEN.read_text(encoding="utf-8")
        retake = function_slice(
            source,
            "retakeTarget?.takeIf { guided.stage != GuidedCaptureStage.TERMINATED }",
            "/**\n * Camera/tracking smoke screen",
        )
        prelaunch = retake.index(
            "guidedFlow.snapshot.stage == GuidedCaptureStage.TERMINATED"
        )
        launch = retake.index("scope.launch")
        self.assertLess(prelaunch, launch)
        self.assertIn("runTerminalAwareRetakeDelete(", retake)

        guard = RETAKE_GUARD.read_text(encoding="utf-8")
        delete = guard.index("val result = runCatching { delete() }")
        post_suspend = guard.index("if (isTerminal()) return false", delete)
        callbacks = guard.index("result.fold(")
        self.assertLess(delete, post_suspend)
        self.assertLess(post_suspend, callbacks)


if __name__ == "__main__":
    unittest.main()
