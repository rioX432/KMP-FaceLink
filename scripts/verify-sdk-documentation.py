#!/usr/bin/env python3
"""Verify source-bound SDK documentation contracts that must not silently drift."""

from __future__ import annotations

import re
import sys
from pathlib import Path


CURRENT_DOCUMENTS = (
    ".github/workflows/ci.yml",
    ".github/workflows/publish-github-packages.yml",
    "AGENTS.md",
    "README.md",
    "docs/api-overview.md",
    "docs/migration.md",
    "docs/quality-gates.md",
    "docs/quick-start-android.md",
    "docs/quick-start-integration.md",
    "docs/quick-start-ios.md",
    "docs/troubleshooting.md",
    "kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/FaceLandmark.kt",
    "kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/FaceTrackingData.kt",
    "kmp-facelink/src/jvmMain/kotlin/io/github/kmpfacelink/internal/onnx/OnnxModelLoader.kt",
    "kmp-facelink/src/jvmMain/resources/META-INF/LICENSES/OpenSeeFace.txt",
    "scripts/setup-openseeface-models.sh",
    "tools/calibration/README.md",
    "tools/calibration/CAPTURE_RUNBOOK.md",
)

OPENSEEFACE_REVISION = "85aa70fc67582d046e771ea73625182a0d8f7475"
OPENSEEFACE_DETECTION_SHA256 = "0e8e4806766d85ab067a52c7af0dcb59eb7f9dfe580b44f20a8e6ab712d89809"
OPENSEEFACE_LANDMARK_SHA256 = "5aa0ab2594acaf2e1d3916a8de592cffeb1f1a7a7f778cad2f37948bff6549d1"


def catalog_version(catalog: str, name: str) -> str:
    match = re.search(rf'(?m)^{re.escape(name)}\s*=\s*"([^"]+)"$', catalog)
    if match is None:
        raise ValueError(f"version catalog does not define {name}")
    return match.group(1)


def project_version(properties: str) -> str:
    match = re.search(r"(?m)^VERSION_NAME=(\S+)$", properties)
    if match is None:
        raise ValueError("gradle.properties does not define VERSION_NAME")
    return match.group(1)


def contract_violations(
    documents: dict[str, str],
    *,
    mediapipe: str,
    camera_x: str,
    version: str,
) -> list[str]:
    violations: list[str] = []

    def require(path: str, token: str) -> None:
        if token not in documents[path]:
            violations.append(f"{path}: missing required text {token!r}")

    def reject(path: str, token: str) -> None:
        if token in documents[path]:
            violations.append(f"{path}: forbidden stale text {token!r}")

    require("AGENTS.md", f"MediaPipe {mediapipe}, CameraX {camera_x}")
    require("AGENTS.md", "./gradlew sdkQualityCheck")
    require("AGENTS.md", "kmp-facelink-capture")
    require("AGENTS.md", "./scripts/setup-openseeface-models.sh")
    reject("AGENTS.md", "30-60fps real-time tracking")
    reject("AGENTS.md", "kmp-facelink-actions` include `wasmJs")

    require("README.md", "shared API does not imply equal estimator capability")
    require("README.md", "ARFaceTrackingConfiguration.isSupported")
    require("README.md", "docs/quality-gates.md")
    require("README.md", OPENSEEFACE_REVISION)
    require("README.md", "mechanics only")
    reject("README.md", "TrueDepth front camera (face tracking)")
    reject("README.md", "recording (+ Wasm)")

    require("docs/quick-start-android.md", f"kmp-facelink:{version}")
    require("docs/quick-start-android.md", f"Vision **{mediapipe}**")
    require("docs/quick-start-android.md", "`enhancerConfig` | `None`")

    require("docs/quick-start-integration.md", f'kmp-facelink = "{version}"')
    require("docs/quick-start-ios.md", "ARFaceTrackingConfiguration.isSupported")
    reject("docs/quick-start-ios.md", "TrueDepth front camera only")
    reject("docs/quick-start-ios.md", "Device with TrueDepth")

    require("docs/api-overview.md", "event.actionId")
    require("docs/api-overview.md", "actionId = \"victory\"")
    require("docs/api-overview.md", "Pinned ONNX resource missing/mismatched")
    reject("docs/api-overview.md", "event.bindingId")
    reject("docs/api-overview.md", "No TrueDepth camera")

    require("docs/troubleshooting.md", "ARFaceTrackingConfiguration.isSupported")
    require("docs/troubleshooting.md", "./scripts/setup-openseeface-models.sh")
    reject("docs/troubleshooting.md", "requires a TrueDepth front camera")

    face_landmark = "kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/FaceLandmark.kt"
    face_tracking = "kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/FaceTrackingData.kt"
    for path in (face_landmark, face_tracking):
        require(path, "current iOS ARKit adapter")
        require(path, "image-normalized")
        reject(path, "ARKit does not expose raw face mesh vertices")
        reject(path, "mapped from ARFaceAnchor geometry vertices")
    require(face_landmark, "face-local 3D coordinate contract")
    require(face_landmark, "image-normalized x/y coordinates and adapter-defined z")
    require(face_landmark, "0.0 does not distinguish measured zero from missing metadata")
    reject(face_landmark, "On Android (MediaPipe) this comes from the model output")
    require(face_tracking, "x/y coordinates use the SDK's image-normalized")
    require(face_tracking, "ARKit exposes native `ARFaceGeometry` vertices")

    require("docs/migration.md", "Current unreleased worktree (not published)")
    require("docs/migration.md", "already-compiled consumers must")
    for tier in ("Tier 1", "Tier 2", "Tier 3"):
        require("docs/quality-gates.md", tier)
    require("docs/quality-gates.md", "same recording")
    require("docs/quality-gates.md", "Independent and physical qualification")
    require("docs/quality-gates.md", "synthetic zero-input contract tests")

    setup_path = "scripts/setup-openseeface-models.sh"
    loader_path = (
        "kmp-facelink/src/jvmMain/kotlin/io/github/kmpfacelink/internal/onnx/"
        "OnnxModelLoader.kt"
    )
    license_path = "kmp-facelink/src/jvmMain/resources/META-INF/LICENSES/OpenSeeFace.txt"
    for token in (
        OPENSEEFACE_REVISION,
        OPENSEEFACE_DETECTION_SHA256,
        OPENSEEFACE_LANDMARK_SHA256,
    ):
        require(setup_path, token)
    for token in (OPENSEEFACE_DETECTION_SHA256, OPENSEEFACE_LANDMARK_SHA256):
        require(loader_path, token)
    require(license_path, OPENSEEFACE_REVISION)
    require(license_path, "BSD 2-Clause License")
    require(".github/workflows/ci.yml", "./scripts/setup-openseeface-models.sh")
    require(".github/workflows/publish-github-packages.yml", "./scripts/setup-openseeface-models.sh")

    require("tools/calibration/README.md", "Legacy Paired-Capture Research Tool")
    require("tools/calibration/README.md", "Withdrawn optimization guidance")
    reject("tools/calibration/README.md", "Use these to update `BlendShapeEnhancerConfig.kt` defaults")
    require("tools/calibration/CAPTURE_RUNBOOK.md", "Archived ARKit / MediaPipe")
    require("tools/calibration/CAPTURE_RUNBOOK.md", "current KMP-FaceLink guided capture")

    return violations


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    try:
        documents = {
            path: (root / path).read_text(encoding="utf-8") for path in CURRENT_DOCUMENTS
        }
        catalog = (root / "gradle/libs.versions.toml").read_text(encoding="utf-8")
        properties = (root / "gradle.properties").read_text(encoding="utf-8")
        violations = contract_violations(
            documents,
            mediapipe=catalog_version(catalog, "mediapipe"),
            camera_x=catalog_version(catalog, "camerax"),
            version=project_version(properties),
        )
    except (OSError, ValueError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1

    if violations:
        for violation in violations:
            print(f"FAIL: {violation}", file=sys.stderr)
        return 1
    print(f"PASS: {len(documents)} selected documentation and model-supply tokens match source configuration")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
