#!/usr/bin/env python3
"""Bind the shipped Face Landmarker task to its verified blendshape label contract."""

from __future__ import annotations

import hashlib
import re
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TASK_SHA256 = "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff"
TASK_PATHS = (
    ROOT / "androidApp/src/main/assets/models/face_landmarker_v2_with_blendshapes.task",
    ROOT / "captureApp/src/main/assets/models/face_landmarker_v2_with_blendshapes.task",
)
BLENDSHAPE_SOURCE = (
    ROOT
    / "kmp-facelink/src/commonMain/kotlin/io/github/kmpfacelink/model/BlendShape.kt"
)
MODEL_ENTRY = "face_blendshapes.tflite"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_labels(source: Path) -> list[str]:
    text = source.read_text(encoding="utf-8")
    enum_body = re.search(r"public enum class BlendShape \{(.*?)\/\*\*", text, re.DOTALL)
    if enum_body is None:
        raise AssertionError(f"cannot find BlendShape enum body in {source}")
    constants = re.findall(r"^\s*([A-Z][A-Z0-9_]*)[,;]\s*$", enum_body.group(1), re.MULTILINE)
    return [to_camel_case(name) for name in constants]


def to_camel_case(name: str) -> str:
    first, *rest = name.lower().split("_")
    return first + "".join(part.capitalize() for part in rest)


def model_payload(task: Path) -> bytes:
    with zipfile.ZipFile(task) as archive:
        return archive.read(MODEL_ENTRY)


def validate_labels(payload: bytes, canonical: list[str]) -> None:
    if len(canonical) != 52:
        raise AssertionError(f"canonical BlendShape count changed: {len(canonical)}")
    expected = ["_neutral", *(label for label in canonical if label != "tongueOut")]
    missing = [label for label in expected if label.encode("ascii") not in payload]
    if missing:
        raise AssertionError(f"shipped task is missing expected labels: {missing}")
    if b"tongueOut" in payload:
        raise AssertionError("shipped task unexpectedly contains tongueOut")
    if len(expected) != 52:
        raise AssertionError(f"expected _neutral + 51 labels, got {len(expected)}")
    for required in ("cheekPuff", "cheekSquintLeft", "cheekSquintRight"):
        if required not in expected:
            raise AssertionError(f"required cheek label missing from expected contract: {required}")


def validate_task(task: Path, source: Path, expected_sha256: str = TASK_SHA256) -> None:
    actual_hash = sha256(task)
    if actual_hash != expected_sha256:
        raise AssertionError(f"{task}: SHA-256 {actual_hash} != {expected_sha256}")
    validate_labels(model_payload(task), canonical_labels(source))


def main() -> None:
    for task in TASK_PATHS:
        validate_task(task, BLENDSHAPE_SOURCE)
    print("Face Landmarker asset contract passed: _neutral + 51; cheek labels present; tongueOut absent")


if __name__ == "__main__":
    main()
