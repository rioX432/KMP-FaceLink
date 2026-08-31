#!/usr/bin/env python3
"""Fail if any source path fills absent blend shapes with a fabricated value.

The contract this guards is that a blend shape map carries only what an estimator measured. A
present `0f` means the estimator looked and saw no activation; an absent key means it produced
nothing for that shape. Anything that walks `BlendShape.entries` and writes a value for every one
of them erases that distinction, and it erases it silently: the result is indistinguishable from a
frame in which everything really was measured at rest.

That is not hypothetical. Four separate places did exactly this and each was found by a different
reader rather than by a test — the JVM mapper, the Android mapper, the iOS tracker, and the frame
interpolator. The last two were found only after the first two were fixed and reviewed. A rule
that is enforced by people reading code is a rule that comes back.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Directories whose job is to produce blend shape maps for consumers.
SCANNED_ROOTS = ("kmp-facelink/src", "kmp-facelink-avatar/src", "kmp-facelink-cbridge/src")

# A loop over the whole vocabulary that assigns into a map on the way through.
FILL_LOOP = re.compile(
    r"for\s*\(\s*\w+\s+in\s+BlendShape\.entries\s*\)\s*\{[^}]*?\[[^\]]+\]\s*=",
    re.DOTALL,
)

# `map[shape] ?: 0f` and friends: reading an absent shape as a measured value.
ABSENT_AS_VALUE = re.compile(r"\[\s*\w+\s*\]\s*\?:\s*0(?:\.0)?f")

# Lines carrying this marker are allowed: the file states why the dense form is correct there.
ALLOW_MARKER = "sparse-contract-exempt:"


def tracked_kotlin_sources() -> list[Path]:
    output = subprocess.check_output(["git", "ls-files", "-z", *SCANNED_ROOTS], cwd=ROOT)
    return [
        ROOT / item.decode("utf-8")
        for item in output.split(b"\0")
        if item.endswith(b".kt")
    ]


def violations(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    if ALLOW_MARKER in text:
        return []
    found = []
    for match in FILL_LOOP.finditer(text):
        line = text[: match.start()].count("\n") + 1
        found.append(f"{path.relative_to(ROOT)}:{line}: assigns a value for every BlendShape")
    for match in ABSENT_AS_VALUE.finditer(text):
        line = text[: match.start()].count("\n") + 1
        found.append(f"{path.relative_to(ROOT)}:{line}: reads an absent shape as 0f")
    return found


def main() -> int:
    sources = tracked_kotlin_sources()
    rejected = [problem for path in sources for problem in violations(path)]
    if rejected:
        print("Blend shape maps must carry only measured shapes:", file=sys.stderr)
        for problem in rejected:
            print(f"  {problem}", file=sys.stderr)
        print(
            f"\nIf a dense map is genuinely correct here, say why in the file and mark it "
            f"`{ALLOW_MARKER} <reason>`.",
            file=sys.stderr,
        )
        return 1
    print(f"PASS: {len(sources)} tracked Kotlin sources fabricate no absent blend shape values")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
