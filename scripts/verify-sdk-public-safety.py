#!/usr/bin/env python3
"""Fail if Git tracks private face-capture payloads or private signing material."""

from __future__ import annotations

import subprocess
import sys
from pathlib import PurePosixPath


FORBIDDEN_COMPONENTS = frozenset(
    {
        "private-data",
        "biometric-data",
        "face-recordings",
        "raw-captures",
        "raw-takes",
        "participant-data",
    }
)
FORBIDDEN_BASENAMES = frozenset(
    {
        "index.bin.gz",
        "landmarks.bin.gz",
        "blendshapes.bin.gz",
        "transform.bin.gz",
        "frames.rgba.gz",
        "run-authorization-private-key.pem",
    }
)
FORBIDDEN_SUFFIXES = (
    ".mov",
    ".mp4",
    ".heic",
    ".npy",
    ".npz",
    ".p12",
    ".mobileprovision",
)


def rejection_reason(path_text: str) -> str | None:
    path = PurePosixPath(path_text)
    lowered_parts = {part.lower() for part in path.parts}
    basename = path.name.lower()
    if lowered_parts & FORBIDDEN_COMPONENTS:
        return "private biometric directory"
    if basename in FORBIDDEN_BASENAMES:
        return "private biometric/signing artifact"
    if basename.endswith(FORBIDDEN_SUFFIXES):
        return "capture media or private credential suffix"
    return None


def tracked_paths() -> list[str]:
    output = subprocess.check_output(["git", "ls-files", "-z"])
    return [item.decode("utf-8") for item in output.split(b"\0") if item]


def main() -> int:
    paths = tracked_paths()
    rejected = [(path, reason) for path in paths if (reason := rejection_reason(path))]
    if rejected:
        for path, reason in rejected:
            print(f"FAIL {path}: {reason}", file=sys.stderr)
        return 1
    print(f"PASS: {len(paths)} tracked paths contain no forbidden private payload")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
