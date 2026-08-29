from __future__ import annotations

import importlib.util
import pathlib
import unittest


SCRIPT = pathlib.Path(__file__).with_name("verify-sdk-documentation.py")
SPEC = importlib.util.spec_from_file_location("verify_sdk_documentation", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def valid_documents() -> dict[str, str]:
    documents = {path: "" for path in MODULE.CURRENT_DOCUMENTS}
    documents["AGENTS.md"] = (
        "MediaPipe 0.10.35, CameraX 1.5.1\n"
        "./gradlew sdkQualityCheck\n"
        "kmp-facelink-capture\n"
        "./scripts/setup-openseeface-models.sh\n"
    )
    documents["README.md"] = (
        "shared API does not imply equal estimator capability\n"
        "ARFaceTrackingConfiguration.isSupported\n"
        "docs/quality-gates.md\n"
        f"{MODULE.OPENSEEFACE_REVISION}\n"
        "mechanics only\n"
    )
    documents["docs/quick-start-android.md"] = (
        "kmp-facelink:0.3.0\n"
        "Vision **0.10.35**\n"
        "`enhancerConfig` | `None`\n"
    )
    documents["docs/quick-start-integration.md"] = 'kmp-facelink = "0.3.0"'
    documents["docs/quick-start-ios.md"] = "ARFaceTrackingConfiguration.isSupported"
    documents["docs/api-overview.md"] = (
        'event.actionId\nactionId = "victory"\nPinned ONNX resource missing/mismatched'
    )
    documents["docs/troubleshooting.md"] = (
        "ARFaceTrackingConfiguration.isSupported\n./scripts/setup-openseeface-models.sh"
    )
    documents["docs/migration.md"] = (
        "Current unreleased worktree (not published)\n"
        "already-compiled consumers must\n"
    )
    documents["docs/quality-gates.md"] = (
        "Tier 1\nTier 2 same recording\nTier 3\n"
        "Independent and physical qualification\n"
        "synthetic zero-input contract tests\n"
    )
    documents["tools/calibration/README.md"] = (
        "Legacy Paired-Capture Research Tool\n"
        "Withdrawn optimization guidance\n"
    )
    documents["tools/calibration/CAPTURE_RUNBOOK.md"] = (
        "Archived ARKit / MediaPipe\n"
        "current KMP-FaceLink guided capture\n"
    )
    setup_contract = "\n".join(
        (
            MODULE.OPENSEEFACE_REVISION,
            MODULE.OPENSEEFACE_DETECTION_SHA256,
            MODULE.OPENSEEFACE_LANDMARK_SHA256,
        )
    )
    documents["scripts/setup-openseeface-models.sh"] = setup_contract
    documents[
        "kmp-facelink/src/jvmMain/kotlin/io/github/kmpfacelink/internal/onnx/OnnxModelLoader.kt"
    ] = "\n".join(
        (MODULE.OPENSEEFACE_DETECTION_SHA256, MODULE.OPENSEEFACE_LANDMARK_SHA256)
    )
    documents["kmp-facelink/src/jvmMain/resources/META-INF/LICENSES/OpenSeeFace.txt"] = (
        f"{MODULE.OPENSEEFACE_REVISION}\nBSD 2-Clause License"
    )
    documents[".github/workflows/ci.yml"] = "./scripts/setup-openseeface-models.sh"
    documents[".github/workflows/publish-github-packages.yml"] = (
        "./scripts/setup-openseeface-models.sh"
    )
    return documents


class DocumentationContractTest(unittest.TestCase):
    def check(self, documents: dict[str, str]) -> list[str]:
        return MODULE.contract_violations(
            documents,
            mediapipe="0.10.35",
            camera_x="1.5.1",
            version="0.3.0",
        )

    def test_current_contract_passes(self):
        self.assertEqual([], self.check(valid_documents()))

    def test_version_drift_fails(self):
        documents = valid_documents()
        documents["docs/quick-start-android.md"] = documents["docs/quick-start-android.md"].replace(
            "0.10.35", "0.10.33"
        )
        self.assertTrue(any("Vision **0.10.35**" in item for item in self.check(documents)))

    def test_truedepth_requirement_fails(self):
        documents = valid_documents()
        documents["README.md"] += "TrueDepth front camera (face tracking)"
        self.assertTrue(any("TrueDepth" in item for item in self.check(documents)))

    def test_measurement_tier_omission_fails(self):
        documents = valid_documents()
        documents["docs/quality-gates.md"] = documents["docs/quality-gates.md"].replace(
            "Tier 3", "Release"
        )
        self.assertTrue(any("Tier 3" in item for item in self.check(documents)))

    def test_model_digest_drift_fails(self):
        documents = valid_documents()
        documents["scripts/setup-openseeface-models.sh"] = documents[
            "scripts/setup-openseeface-models.sh"
        ].replace(MODULE.OPENSEEFACE_LANDMARK_SHA256, "0" * 64)
        self.assertTrue(any("scripts/setup-openseeface-models.sh" in item for item in self.check(documents)))

    def test_publication_without_model_setup_fails(self):
        documents = valid_documents()
        documents[".github/workflows/publish-github-packages.yml"] = "./gradlew publish"
        self.assertTrue(any("publish-github-packages.yml" in item for item in self.check(documents)))


if __name__ == "__main__":
    unittest.main()
