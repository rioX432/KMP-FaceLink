"""Mutation controls for the sparse blend shape contract guard.

A guard nobody tests is a guard nobody can trust. The first version of this one passed while three
real violations sat in modules it never scanned, and its pattern could not match a subscript like
`map[rule.target]`. Each case below is a form that appeared in this codebase or a near neighbour of
one, so a future narrowing of the pattern fails here instead of in review.
"""

from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

from scripts.verify_sparse_blendshape_contract import (
    ALLOW_MARKER,
    SCANNED_ROOTS,
    violations_in,
)

ROOT = Path(__file__).resolve().parents[1]


class SparseBlendShapeContractTest(unittest.TestCase):

    # region forms that must be rejected

    def test_rejects_fill_loop_over_the_vocabulary(self) -> None:
        source = """
            for (shape in BlendShape.entries) {
                if (shape !in scratch) {
                    scratch[shape] = 0f
                }
            }
        """
        self.assertTrue(violations_in(source, "x.kt"))

    def test_rejects_fill_loop_that_reads_both_sides(self) -> None:
        # The frame interpolator's form.
        source = """
            for (shape in BlendShape.entries) {
                val prevVal = previous[shape] ?: 0f
                scratchMap[shape] = prevVal
            }
        """
        self.assertTrue(violations_in(source, "x.kt"))

    def test_rejects_absent_read_with_a_bare_identifier(self) -> None:
        self.assertTrue(violations_in("val v = map[shape] ?: 0f", "x.kt"))

    def test_rejects_absent_read_with_a_property_subscript(self) -> None:
        # `ExpressionCorrelator`'s form. The first version of the pattern missed this entirely.
        self.assertTrue(violations_in("val current = blendShapes[rule.target] ?: 0f", "x.kt"))

    def test_rejects_absent_read_with_a_chained_receiver_and_subscript(self) -> None:
        # `BinaryFrameCodec`'s form.
        self.assertTrue(violations_in("val v = face.blendShapes[shape.arKitName] ?: 0f", "x.kt"))

    def test_rejects_absent_read_written_as_a_get_call(self) -> None:
        # `Calibrator`'s form. The subscript-only pattern read straight past it, and it sat sixty
        # lines above a site the same run did flag.
        self.assertTrue(violations_in("val n = neutralBaseline?.get(shape) ?: 0f", "x.kt"))

    def test_rejects_absent_read_written_as_zero_point_zero(self) -> None:
        self.assertTrue(violations_in("val v = map[shape] ?: 0.0f", "x.kt"))

    def test_rejects_absent_read_with_padded_spacing(self) -> None:
        self.assertTrue(violations_in("val v = map[ shape ]  ?:  0f", "x.kt"))

    # endregion

    # region forms that must be accepted

    def test_accepts_reading_an_absent_shape_as_null(self) -> None:
        self.assertEqual([], violations_in("val v = map[shape] ?: continue", "x.kt"))

    def test_accepts_a_loop_that_only_reads(self) -> None:
        source = """
            for (shape in BlendShape.entries) {
                total += measured.getValue(shape)
            }
        """
        self.assertEqual([], violations_in(source, "x.kt"))

    def test_accepts_a_loop_over_the_measured_keys(self) -> None:
        source = """
            for ((shape, value) in current) {
                scratchMap[shape] = value
            }
        """
        self.assertEqual([], violations_in(source, "x.kt"))

    def test_accepts_a_non_zero_default(self) -> None:
        # Substituting something other than a measured-looking zero is a different decision and is
        # not what this guard is about.
        self.assertEqual([], violations_in("val v = map[shape] ?: Float.NaN", "x.kt"))

    def test_exempt_marker_silences_a_whole_file(self) -> None:
        source = f"// {ALLOW_MARKER} fixed-length wire format\nval v = map[shape] ?: 0f"
        self.assertEqual([], violations_in(source, "x.kt"))

    def test_exempt_marker_is_file_scoped_not_line_scoped(self) -> None:
        # Documented behaviour: the marker anywhere in the file silences the whole file. Pinned so
        # that a reader is not surprised, and so a change to line scoping is deliberate.
        source = f"val a = map[x] ?: 0f\n// {ALLOW_MARKER} reason\nval b = map[y] ?: 0f"
        self.assertEqual([], violations_in(source, "x.kt"))

    # endregion

    # region scope

    def test_scans_every_gradle_module(self) -> None:
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        included = {
            line.split('"')[1].lstrip(":")
            for line in settings.splitlines()
            if line.strip().startswith("include(")
        }
        missing = included - set(SCANNED_ROOTS)
        self.assertEqual(
            set(),
            missing,
            f"these modules are not scanned, so a violation there would pass: {sorted(missing)}",
        )

    def test_the_guard_passes_on_the_current_tree(self) -> None:
        result = subprocess.run(
            ["python3", "scripts/verify-sparse-blendshape-contract.py"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    # endregion


if __name__ == "__main__":
    unittest.main()
