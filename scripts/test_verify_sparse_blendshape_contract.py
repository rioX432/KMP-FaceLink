"""Mutation controls for the sparse blend shape contract guard.

A guard nobody tests is a guard nobody can trust, and this one proved it three times: it passed
while real violations sat in directories it never opened, its subscript pattern could not match
`map[order[i]]`, and it had no idea that a zero-initialised `FloatArray(52)` reaches the same place
with no elvis in it.

Every rejected case below is a form that actually appeared in this repository. Every accepted case
is a form that must not be flagged. The last region pins what the guard is known to miss, so the
limit is a test someone can read rather than a sentence someone can skip.
"""

from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

from scripts.verify_sparse_blendshape_contract import (
    ALLOW_MARKER,
    tracked_kotlin_sources,
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
        # `ExpressionCorrelator`'s form.
        self.assertTrue(violations_in("val current = blendShapes[rule.target] ?: 0f", "x.kt"))

    def test_rejects_absent_read_with_a_nested_subscript(self) -> None:
        # `LiveLinkBlendShapes.lower()`'s form. The old pattern used a negated `]` class inside the
        # brackets, so an inner `]` ended the match early and the whole line read as clean - in this
        # SDK's own public API, through four review rounds.
        self.assertTrue(violations_in("out[i] = blendShapes[order[i]] ?: 0f", "x.kt"))

    def test_rejects_absent_read_with_a_chained_receiver_and_subscript(self) -> None:
        self.assertTrue(violations_in("val v = face.blendShapes[shape.arKitName] ?: 0f", "x.kt"))

    def test_rejects_absent_read_written_as_a_get_call(self) -> None:
        # `Calibrator`'s form.
        self.assertTrue(violations_in("val n = neutralBaseline?.get(shape) ?: 0f", "x.kt"))

    def test_rejects_absent_read_via_get_or_default(self) -> None:
        self.assertTrue(violations_in("val v = blendShapes.getOrDefault(shape, 0f)", "x.kt"))

    def test_rejects_absent_read_via_get_or_else(self) -> None:
        self.assertTrue(violations_in("val v = blendShapes.getOrElse(shape) { 0f }", "x.kt"))

    def test_rejects_absent_read_written_as_zero_point_zero(self) -> None:
        self.assertTrue(violations_in("val v = map[shape] ?: 0.0f", "x.kt"))

    def test_rejects_absent_read_with_padded_spacing(self) -> None:
        self.assertTrue(violations_in("val v = map[ shape ]  ?:  0f", "x.kt"))

    def test_rejects_a_dense_container_sized_by_the_vocabulary(self) -> None:
        # The React Native bridge's form, and the Unity facade's, and the C bridge's. No elvis
        # anywhere: Kotlin zero-initialises the array, so every shape the estimator did not produce
        # is left behind as a measured-looking zero.
        source = """
            val blendShapes = FloatArray(BLEND_SHAPE_COUNT)
            for (entry in data.blendShapes) {
                blendShapes[entry.key.ordinal] = entry.value
            }
        """
        self.assertTrue(violations_in(source, "x.kt"))

    def test_rejects_a_dense_container_written_with_the_literal_size(self) -> None:
        self.assertTrue(violations_in("val out = FloatArray(52)\nval x: BlendShape", "x.kt"))

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

    def test_accepts_a_dense_container_in_a_file_with_no_blend_shapes(self) -> None:
        self.assertEqual([], violations_in("val buffer = FloatArray(COUNT)", "x.kt"))

    def test_accepts_a_dense_container_named_only_in_a_comment(self) -> None:
        # `LiveLinkBlendShapes`' KDoc says "a fresh `FloatArray(52)`". The guard reported the
        # sentence. A comment cannot invent a value.
        source = "/** @return a fresh `FloatArray(52)` in wire order. */\nval x: BlendShape"
        self.assertEqual([], violations_in(source, "x.kt"))

    def test_reports_the_right_line_after_comments_are_blanked(self) -> None:
        source = "// a comment\n/* two\n   lines */\nval v = map[shape] ?: 0f"
        self.assertEqual(["x.kt:4: reads an absent shape as 0f"], violations_in(source, "x.kt"))

    def test_exempt_marker_silences_a_whole_file(self) -> None:
        source = f"// {ALLOW_MARKER} fixed-length wire format\nval v = map[shape] ?: 0f"
        self.assertEqual([], violations_in(source, "x.kt"))

    def test_exempt_marker_is_file_scoped_not_line_scoped(self) -> None:
        # Documented behaviour: the marker anywhere in the file silences the whole file. Pinned so
        # a reader is not surprised, and so a change to line scoping is deliberate.
        before = "val a = map[shape] ?: 0f\nval b = map[shape] ?: 0f"
        # The keys have to be ones the guard actually recognises, or this passes whether the marker
        # works or not. An earlier version used `map[x]`/`map[y]`, which it never flagged either
        # way — the same "test that holds nothing" this file exists to prevent.
        self.assertEqual(2, len(violations_in(before, "x.kt")))
        source = f"val a = map[shape] ?: 0f\n// {ALLOW_MARKER} reason\nval b = map[shape] ?: 0f"
        self.assertEqual([], violations_in(source, "x.kt"))

    # endregion

    # region scope

    def test_scans_every_tracked_kotlin_file(self) -> None:
        """Grounded in the repository, not in the guard's own idea of its scope.

        The previous version of this test compared the guard's scope against `settings.gradle.kts` -
        the same list the guard derived that scope from. It could only ever pass, and it did pass
        while `react-native-kmp-facelink` went unread. Comparing against `git ls-files` instead
        means a file cannot be missed by sitting outside a list.
        """
        listed = subprocess.check_output(["git", "ls-files", "-z", "*.kt"], cwd=ROOT)
        expected = {ROOT / item.decode("utf-8") for item in listed.split(b"\0") if item}
        self.assertEqual(expected, set(tracked_kotlin_sources()))

    def test_scans_the_directories_that_were_historically_missed(self) -> None:
        """Named explicitly, so a future change to the scan cannot quietly re-shrink coverage."""
        scanned = {str(path.relative_to(ROOT)) for path in tracked_kotlin_sources()}
        for missed in (
            "react-native-kmp-facelink/android/src/main/java/com/kmpfacelink/FaceLinkModule.kt",
            "kmp-facelink-cbridge/src/kmpfacelink_bridge_android.kt",
        ):
            self.assertIn(missed, scanned, f"{missed} is outside the scan again")

    def test_the_guard_passes_on_the_current_tree(self) -> None:
        result = subprocess.run(
            ["python3", "scripts/verify_sparse_blendshape_contract.py"],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    # endregion

    # region what this guard cannot see

    def test_known_blind_spot_a_value_carried_through_a_local_variable(self) -> None:
        """Pinned as a miss, not as a pass.

        Nothing here resolves types, so a substitution one assignment away from the map read is
        invisible. Closing it needs a detekt rule with type resolution. Until then the hole is
        written down here rather than implied to be covered.
        """
        source = """
            val measured = blendShapes[shape]
            val value = if (measured == null) 0f else measured
        """
        self.assertEqual([], violations_in(source, "x.kt"), "if this fails, the guard improved")

    def test_known_blind_spot_a_dense_container_sized_by_an_unrecognised_name(self) -> None:
        source = "val out = FloatArray(vocabulary)\nval x: BlendShape"
        self.assertEqual([], violations_in(source, "x.kt"), "if this fails, the guard improved")

    # endregion


if __name__ == "__main__":
    unittest.main()
