#!/usr/bin/env python3
"""Verify that the checked iOS sample project matches its pinned XcodeGen input."""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path

EXPECTED_XCODEGEN_VERSION = "2.45.4"


def parse_xcodegen_version(output: str) -> str:
    """Return the semantic version printed by XcodeGen."""
    version = output.strip().removeprefix("Version:").strip()
    if not version:
        raise ValueError("XcodeGen returned an empty version")
    return version


def projects_match(expected: Path, generated: Path) -> bool:
    """Compare the deterministic project file bytes."""
    return expected.read_bytes() == generated.read_bytes()


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    xcodegen = shutil.which("xcodegen")
    if xcodegen is None:
        raise SystemExit(
            "xcodegen is required for the iOS sample drift check; "
            f"install the pinned {EXPECTED_XCODEGEN_VERSION} release on the macOS runner"
        )

    version_run = subprocess.run(
        [xcodegen, "--version"],
        check=True,
        capture_output=True,
        text=True,
    )
    actual_version = parse_xcodegen_version(version_run.stdout or version_run.stderr)
    if actual_version != EXPECTED_XCODEGEN_VERSION:
        raise SystemExit(
            f"xcodegen {EXPECTED_XCODEGEN_VERSION} is required, found {actual_version}"
        )

    expected_project = repo_root / "iosApp" / "FaceLink.xcodeproj" / "project.pbxproj"
    with tempfile.TemporaryDirectory(prefix="facelink-sample-xcodegen-") as temp_dir:
        temp_ios_app = Path(temp_dir) / "iosApp"
        shutil.copytree(repo_root / "iosApp" / "FaceLink", temp_ios_app / "FaceLink")
        shutil.copy2(repo_root / "iosApp" / "project.yml", temp_ios_app / "project.yml")
        subprocess.run(
            [xcodegen, "generate", "--spec", "project.yml"],
            cwd=temp_ios_app,
            check=True,
        )
        generated_project = temp_ios_app / "FaceLink.xcodeproj" / "project.pbxproj"
        if not projects_match(expected_project, generated_project):
            raise SystemExit(
                "iosApp/FaceLink.xcodeproj is stale; regenerate it with "
                f"XcodeGen {EXPECTED_XCODEGEN_VERSION} from iosApp/project.yml"
            )

    print(
        "iOS sample project matches iosApp/project.yml "
        f"with XcodeGen {EXPECTED_XCODEGEN_VERSION}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
