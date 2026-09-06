#!/usr/bin/env python3
"""Fail if any source path invents a value for a blend shape an estimator did not emit.

The contract this guards is that a blend shape map carries only what an estimator measured. A
present `0f` means the estimator looked and saw no activation; an absent key means it produced
nothing for that shape. Anything that reads an absent shape as a value, or writes a value for every
shape in the vocabulary, erases that distinction — and erases it silently, because the result is
indistinguishable from a frame in which everything really was measured at rest.

Three things this script got wrong before, each found by a reader rather than by itself:

1. It scanned a hand-picked list of modules, then a list derived from `settings.gradle.kts`. Both
   were scoped to where problems had already been found. `react-native-kmp-facelink` is not a Gradle
   module, so it was never opened - and it holds a dense fill. Worse, the test that checked the
   scope compared it against the same list the guard derived the scope from, so it could only ever
   pass. There is no scope list any more: every tracked Kotlin file is read.

2. Its subscript pattern was `\\[[^\\]]+\\]`, which cannot match a nested subscript. So
   `blendShapes[order[i]] ?: 0f` - a dense fill in this SDK's own public API - read as clean.

3. It only knew the elvis form. A zero-initialised `FloatArray(52)` filled from a sparse map lands
   in the same place with no elvis anywhere, which is how the React Native bridge does it.

The elvis and `getOr*` checks are heuristics and cannot be otherwise here: this script does not
resolve types, so it decides by how the receiver and key are spelled. The fill-loop and
dense-container checks are anchored on `BlendShape.entries` and on the vocabulary size, which only
a blend shape walk mentions.

**A clean run means no recognisable violation remains. It is not a proof that the contract holds.**
The forms this script is known to miss are pinned in the mutation controls beside it, as tests that
record the limit rather than hide it.
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# One level of nesting, which is what `blendShapes[order[i]]` needs and what the previous pattern
# could not express.
_SUBSCRIPT = r"\[(?:[^\[\]\n]|\[[^\[\]\n]*\])+\]"
_ZERO = r"0(?:\.0)?f"
_MAPLIKE = r"\w*[Bb]lend[Ss]hapes?|scratch|shapes|\w*[Bb]aseline|\w*[Nn]eutral"
_SHAPE_KEY = r"(?:\w+\.)?(?:shape|blendShape|target)\b"

# A loop over the whole vocabulary. Whether it assigns is decided by scanning its body, because a
# regex that has to balance braces will silently stop matching the moment a body nests one level
# deeper than the author of the regex imagined.
FILL_LOOP_HEAD = re.compile(r"for\s*\(\s*[^)]*\bBlendShape\.entries\s*\)\s*\{")
ASSIGNS_INTO_MAP = re.compile(rf"{_SUBSCRIPT}\s*=[^=]")

# Reading an absent shape as a measured value, in each spelling that has actually appeared here.
ABSENT_AS_VALUE = re.compile(
    rf"\b(?:{_MAPLIKE})\s*(?:\?)?(?:{_SUBSCRIPT}|\.get\([^)\n]+\))\s*\?:\s*{_ZERO}"
    rf"|(?:\[\s*|\.get\(\s*){_SHAPE_KEY}[^\]\n)]*(?:\]|\))\s*\?:\s*{_ZERO}"
    rf"|\b(?:{_MAPLIKE})\s*\.getOrDefault\([^)\n]*,\s*{_ZERO}\s*\)"
    rf"|\b(?:{_MAPLIKE})\s*\.getOrElse\([^)\n]*\)\s*\{{\s*{_ZERO}\s*\}}"
)

# A fixed-width container sized by the vocabulary. Kotlin zero-initialises it, so every shape the
# estimator did not produce leaves a measured-looking zero behind with no elvis in sight.
DENSE_CONTAINER = re.compile(
    r"\b(?:FloatArray|DoubleArray)\s*\(\s*(?:\w*BLEND_SHAPE\w*|\w*COUNT\w*|52)\s*\)"
)

# Files that state why a dense form is correct for them are allowed. The marker applies to the whole
# file, so keep it on files whose entire job is the dense form.
ALLOW_MARKER = "sparse-contract-exempt:"


def tracked_kotlin_sources() -> list[Path]:
    """Every tracked Kotlin file in the repository.

    Not every Gradle module - every file. A blend shape map can be mishandled anywhere it can be
    reached, including in a directory no Gradle module owns.
    """
    output = subprocess.check_output(["git", "ls-files", "-z", "*.kt"], cwd=ROOT)
    return [ROOT / item.decode("utf-8") for item in output.split(b"\0") if item]


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


# Comments cannot invent a value, but they can describe one. `LiveLinkBlendShapes`' own KDoc says
# "a fresh `FloatArray(52)`", and the guard reported the sentence as a violation. Blanking comments
# keeps the line numbers intact so a real finding still points at the right line.
_LINE_COMMENT = re.compile(r"//[^\n]*")
_BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)


def _without_comments(text: str) -> str:
    blanked = _BLOCK_COMMENT.sub(lambda m: re.sub(r"[^\n]", " ", m.group()), text)
    return _LINE_COMMENT.sub(lambda m: " " * len(m.group()), blanked)


def violations_in(text: str, label: str) -> list[str]:
    """Findings for one source text. Split out so the mutation controls can call it directly.

    The exemption marker is looked for before comments are dropped, because that is where it lives.
    """
    if ALLOW_MARKER in text:
        return []
    text = _without_comments(text)
    found = []
    for match in FILL_LOOP_HEAD.finditer(text):
        body = _balanced_body(text, match.end() - 1)
        if ASSIGNS_INTO_MAP.search(body):
            line = text[: match.start()].count("\n") + 1
            found.append(f"{label}:{line}: assigns a value for every BlendShape")
    for match in ABSENT_AS_VALUE.finditer(text):
        line = text[: match.start()].count("\n") + 1
        found.append(f"{label}:{line}: reads an absent shape as 0f")
    # `BlendShape`, `blendShapes` and `BLEND_SHAPE_COUNT` all contain this.
    if "lendShape" in text or "LEND_SHAPE" in text:
        for match in DENSE_CONTAINER.finditer(text):
            line = text[: match.start()].count("\n") + 1
            found.append(f"{label}:{line}: allocates a slot for every BlendShape")
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
            f"\nIf a dense form is genuinely correct for a file, say why in it and mark the file "
            f"`{ALLOW_MARKER} <reason>`.",
            file=sys.stderr,
        )
        return 1
    print(f"PASS: {len(sources)} tracked Kotlin sources hold no recognisable invented blend shape value")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
