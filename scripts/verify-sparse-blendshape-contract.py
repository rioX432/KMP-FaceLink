#!/usr/bin/env python3
"""Fail if any source path invents a value for a blend shape an estimator did not emit.

The contract this guards is that a blend shape map carries only what an estimator measured. A
present `0f` means the estimator looked and saw no activation; an absent key means it produced
nothing for that shape. Anything that reads an absent shape as a value, or writes a value for every
shape in the vocabulary, erases that distinction — and erases it silently, because the result is
indistinguishable from a frame in which everything really was measured at rest.

That is not hypothetical. Eight separate places did this, and every one was found by a reader
rather than by a test: the JVM mapper, the Android mapper, the iOS tracker, the frame interpolator,
the calibrator, two delta calculators, and the correlation pass. Several were found only after the
earlier ones were fixed and reviewed twice.

The first version of this script scanned three modules — the ones where problems had already been
found — and its pattern only matched simple identifiers. It passed while three real violations sat
in modules it never opened. Scoping a guard to where you already know the bug is means the guard
can only confirm what you knew. This version scans every module and matches a `.get()`
call as well as a subscript.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def scanned_roots() -> tuple[str, ...]:
    """Every Gradle module, read from settings.gradle.kts rather than hand-listed.

    A blend shape map can be mishandled anywhere it can be reached. The first version of this guard
    named three modules by hand — the ones where problems had already been found — and passed while
    real violations sat in modules it never opened. Deriving the list means a new module is covered
    the day it is added, without anyone remembering to come here.
    """
    settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
    modules = re.findall(r'^\s*include\("([^"]+)"\)', settings, re.M)
    return tuple(sorted({module.lstrip(":") for module in modules} | {"kmp-facelink-cbridge"}))


SCANNED_ROOTS = scanned_roots()

# A loop over the whole vocabulary. Whether it assigns is decided by scanning its body, because a
# regex that has to balance braces will silently stop matching the moment a body nests one level
# deeper than the author of the regex imagined.
FILL_LOOP_HEAD = re.compile(r"for\s*\(\s*[^)]*\bBlendShape\.entries\s*\)\s*\{")
ASSIGNS_INTO_MAP = re.compile(r"\[[^\]\n]+\]\s*=[^=]")

# Reading an absent shape as a measured value.
#
# This one is a heuristic and cannot be anything else at this level: `x[k] ?: 0f` is the same
# text whether `x` is a blend shape map, a joint depth map, or an emotion score map, and nothing
# here resolves types. Requiring the receiver or the key to name a shape keeps out the unrelated
# maps that a bare subscript pattern flags, at the cost of missing a blend shape map subscripted
# by a variable named something else. The fill-loop check above has no such weakness — it is
# anchored on `BlendShape.entries`, which only a blend shape map walk mentions.
#
# So: a clean run means no *recognisable* absent-read remains, not that none can exist. Do not
# describe this script as proving the contract holds.
ABSENT_AS_VALUE = re.compile(
    r"\b(?:\w*[Bb]lend[Ss]hapes?|scratch|shapes|\w*[Bb]aseline|\w*[Nn]eutral)\s*"
    r"(?:\?)?(?:\[[^\]\n]+\]|\.get\([^)\n]+\))\s*\?:\s*0(?:\.0)?f"
    r"|(?:\[\s*|\.get\(\s*)(?:\w+\.)?(?:shape|blendShape|target)\b[^\]\n)]*(?:\]|\))\s*\?:\s*0(?:\.0)?f"
)

# Files that state why a dense map is correct for them are allowed. The marker applies to the whole
# file, so keep it on files whose entire job is the dense form.
ALLOW_MARKER = "sparse-contract-exempt:"


def tracked_kotlin_sources() -> list[Path]:
    output = subprocess.check_output(["git", "ls-files", "-z", *SCANNED_ROOTS], cwd=ROOT)
    return [
        ROOT / item.decode("utf-8")
        for item in output.split(b"\0")
        if item.endswith(b".kt")
    ]


def _balanced_body(text: str, open_brace: int) -> str:
    """The text of the block starting at `open_brace`, however deeply it nests."""
    depth = 0
    for index in range(open_brace, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace + 1 : index]
    return text[open_brace + 1 :]


def violations_in(text: str, label: str) -> list[str]:
    """Findings for one source text. Split out so the mutation controls can call it directly."""
    if ALLOW_MARKER in text:
        return []
    found = []
    for match in FILL_LOOP_HEAD.finditer(text):
        body = _balanced_body(text, match.end() - 1)
        if ASSIGNS_INTO_MAP.search(body):
            line = text[: match.start()].count("\n") + 1
            found.append(f"{label}:{line}: assigns a value for every BlendShape")
    for match in ABSENT_AS_VALUE.finditer(text):
        line = text[: match.start()].count("\n") + 1
        found.append(f"{label}:{line}: reads an absent shape as 0f")
    return found


def main() -> int:
    sources = tracked_kotlin_sources()
    rejected = [
        problem
        for path in sources
        for problem in violations_in(path.read_text(encoding="utf-8"), str(path.relative_to(ROOT)))
    ]
    if rejected:
        print("Blend shape maps must carry only measured shapes:", file=sys.stderr)
        for problem in rejected:
            print(f"  {problem}", file=sys.stderr)
        print(
            f"\nIf a dense map is genuinely correct for a file, say why in it and mark the file "
            f"`{ALLOW_MARKER} <reason>`.",
            file=sys.stderr,
        )
        return 1
    print(f"PASS: {len(sources)} tracked Kotlin sources invent no absent blend shape values")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
