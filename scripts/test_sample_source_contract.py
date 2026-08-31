"""Dependency-free mutation controls for the sample source contract."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.verify_sample_xcode_project import (
    parse_xcodegen_version,
    projects_match,
)

ROOT = Path(__file__).resolve().parents[1]


class SampleSourceContractTest(unittest.TestCase):
    def test_version_parser_rejects_empty_output(self) -> None:
        self.assertEqual("2.45.4", parse_xcodegen_version("Version: 2.45.4\n"))
        with self.assertRaises(ValueError):
            parse_xcodegen_version("  ")

    def test_project_drift_comparison_detects_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            expected = root / "expected"
            generated = root / "generated"
            expected.write_bytes(b"project-v1")
            generated.write_bytes(b"project-v1")
            self.assertTrue(projects_match(expected, generated))
            generated.write_bytes(b"project-v2")
            self.assertFalse(projects_match(expected, generated))

    def test_platform_shell_owns_exactly_one_mode_selector(self) -> None:
        scaffold = self._read(
            "sampleShared/src/commonMain/kotlin/io/github/kmpfacelink/sample/ui/"
            "TrackingScreenScaffold.kt"
        )
        ios_face = self._read(
            "sampleShared/src/iosMain/kotlin/io/github/kmpfacelink/sample/ui/"
            "FaceTrackingViewController.kt"
        )
        ios_hand = self._read(
            "sampleShared/src/iosMain/kotlin/io/github/kmpfacelink/sample/ui/"
            "HandTrackingViewController.kt"
        )
        self.assertIn("showModeToggle: Boolean = true", scaffold)
        self.assertIn("if (showModeToggle)", scaffold)
        self.assertIn("showModeToggle = false", ios_face)
        self.assertIn("showModeToggle = false", ios_hand)

    def test_initial_filter_and_empty_landmark_state_are_explicit(self) -> None:
        chips = self._read(
            "sampleShared/src/commonMain/kotlin/io/github/kmpfacelink/sample/ui/"
            "SmoothingFilterChips.kt"
        )
        state = self._read(
            "sampleShared/src/commonMain/kotlin/io/github/kmpfacelink/sample/ui/"
            "SampleUiState.kt"
        )
        content = self._read("iosApp/FaceLink/ContentView.swift")
        self.assertIn("sampleSmoothingOption(initialConfig)", chips)
        self.assertNotIn('mutableStateOf("EMA")', chips)
        self.assertIn("CURRENT_ADAPTER_UNAVAILABLE", state)
        self.assertNotIn("BlendShapeEnhancerConfig", content)

    def test_home_copy_and_workflow_do_not_overstate_or_skip_shared_ui(self) -> None:
        shared_home = self._read(
            "sampleShared/src/commonMain/kotlin/io/github/kmpfacelink/sample/ui/HomeScreen.kt"
        )
        ios_home = self._read("iosApp/FaceLink/HomeView.swift")
        workflow = self._read(".github/workflows/device-tests.yml")
        self.assertNotIn("52 ARKit blend shapes", shared_home)
        self.assertNotIn("52 ARKit blend shapes", ios_home)
        self.assertIn("'sampleShared/**'", workflow)

    def test_sample_gate_executes_every_required_lane(self) -> None:
        build = self._read("build.gradle.kts")
        for required in (
            'tasks.register("sampleQualityCheck")',
            '":androidApp:assembleDebug"',
            '":androidApp:assembleDebugAndroidTest"',
            '":sampleShared:compileKotlinIosSimulatorArm64"',
            '":sampleShared:iosSimulatorArm64Test"',
            "buildSampleIosSimulator",
            "verifySampleXcodeProject",
            "testSampleSourceContract",
        ):
            self.assertIn(required, build)

    def test_default_ios_sample_excludes_experimental_voice_toolchain(self) -> None:
        project = self._read("iosApp/project.yml")
        content = self._read("iosApp/FaceLink/ContentView.swift")
        self.assertIn("VoiceTrackingView.swift", project)
        self.assertIn("ActionsTrackingView.swift", project)
        self.assertNotIn("KMPFaceLinkVoice.framework", project)
        self.assertNotIn("KMPFaceLinkActions.framework", project)
        self.assertNotIn("KMPFaceLink.framework", project)
        self.assertEqual(1, project.count("SampleShared.framework"))
        self.assertNotIn("case voice", content)
        self.assertNotIn("case actions", content)
        self.assertNotIn("case holistic", content)

    def test_active_ios_sample_uses_single_kotlin_native_umbrella(self) -> None:
        project_spec = self._read("iosApp/project.yml")
        checked_project = self._read("iosApp/FaceLink.xcodeproj/project.pbxproj")
        excluded = {
            "ActionsTrackingView.swift",
            "HandTrackingView.swift",
            "HolisticTrackingView.swift",
            "SmoothingSettingsView.swift",
            "VoiceTrackingView.swift",
        }
        for source_name in excluded:
            self.assertIn(f"- {source_name}", project_spec)
            self.assertNotIn(source_name, checked_project)
        for source in (ROOT / "iosApp" / "FaceLink").rglob("*.swift"):
            if source.name not in excluded:
                self.assertNotIn("import KMPFaceLink", source.read_text(encoding="utf-8"))

    @staticmethod
    def _read(relative_path: str) -> str:
        return (ROOT / relative_path).read_text(encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
