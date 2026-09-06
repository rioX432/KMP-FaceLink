"""Dependency-free Tier 1 tests for calibration parser and metric contracts."""

from __future__ import annotations

import argparse
import math
import sys
import unittest
from pathlib import Path


CALIBRATION_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CALIBRATION_DIR))

import calibration_contract as contract  # noqa: E402


class CalibrationContractTest(unittest.TestCase):
    def test_valid_seconds_window(self) -> None:
        actual = contract.parse_window("puff_1:2.25:4.75")
        self.assertEqual(actual, contract.AnalysisWindow("puff_1", 2.25, 4.75))

    def test_invalid_seconds_window_is_rejected(self) -> None:
        for value in ("puff:3:2", "puff:nan:3", ":0:1"):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                contract.parse_window(value)

    def test_valid_inclusive_frame_window(self) -> None:
        actual = contract.parse_frame_window("neutral:10:45")
        self.assertEqual(actual, contract.FrameAnalysisWindow("neutral", 10, 45))

    def test_invalid_frame_window_is_rejected(self) -> None:
        for value in ("puff:3:2", "puff:-1:2", "puff:one:2"):
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                contract.parse_frame_window(value)

    def test_paired_statistics_and_scalar_helpers(self) -> None:
        actual = contract.paired_difference_statistics(
            [0.0, 0.5, math.nan, 1.0], [0.0, 0.25, 0.5, 0.5]
        )
        self.assertEqual(actual["paired_count"], 3)
        self.assertAlmostEqual(actual["mean_absolute_difference"], 0.25)
        self.assertAlmostEqual(actual["root_mean_square_difference"], math.sqrt(0.3125 / 3))
        self.assertAlmostEqual(actual["mean_signed_difference_geometric_minus_arkit"], 0.25)
        self.assertAlmostEqual(actual["pearson_correlation"], 1.0)
        self.assertEqual([contract.clip01(-1), contract.clip01(0.5), contract.clip01(2)], [0, 0.5, 1])
        self.assertEqual([contract.sign(-1), contract.sign(0), contract.sign(1)], [-1, 0, 1])
        with self.assertRaises(ValueError):
            contract.paired_difference_statistics([0.0], [0.0, 1.0])


if __name__ == "__main__":
    unittest.main()
