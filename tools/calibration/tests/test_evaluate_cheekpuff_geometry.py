"""Parity and schema tests for evaluate_cheekpuff_geometry.py."""

from __future__ import annotations

import argparse
import csv
import gzip
import os
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np


CALIBRATION_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CALIBRATION_DIR))

import evaluate_cheekpuff_geometry as geometry  # noqa: E402


GOLDEN_DIR_ENV = os.environ.get("FACELINK_CALIBRATION_GOLDENS")
GOLDEN_DIR = Path(GOLDEN_DIR_ENV).resolve() if GOLDEN_DIR_ENV else None
ASPECT = 1920.0 / 1080.0
TOLERANCE = 1e-4


def read_csv(name: str) -> list[dict[str, str]]:
    assert GOLDEN_DIR is not None
    with (GOLDEN_DIR / name).open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source))


def read_gzip_floats(name: str) -> np.ndarray:
    assert GOLDEN_DIR is not None
    with gzip.open(GOLDEN_DIR / name, "rb") as source:
        return np.frombuffer(source.read(), dtype="<f4")


def baselines() -> dict[str, geometry.Baseline]:
    return {
        row["clip"]: geometry.Baseline(
            mouth_width=float(row["mouth_width"]),
            lip_gap=0.0,
            nose_left_y=float(row["noseL_y"]),
            nose_right_y=float(row["noseR_y"]),
            mouth_center_x=float(row["mouth_cx"]),
        )
        for row in read_csv("baselines.csv")
    }


class WindowParsingTest(unittest.TestCase):
    def test_valid_window(self) -> None:
        actual = geometry.parse_window("puff_1:2.25:4.75")
        self.assertEqual(actual.label, "puff_1")
        self.assertEqual(actual.start_seconds, 2.25)
        self.assertEqual(actual.end_seconds, 4.75)

    def test_invalid_window_is_rejected(self) -> None:
        with self.assertRaises(argparse.ArgumentTypeError):
            geometry.parse_window("puff:3:2")


@unittest.skipUnless(
    GOLDEN_DIR is not None and GOLDEN_DIR.is_dir(),
    "FACELINK_CALIBRATION_GOLDENS is not set to an authorized local golden-vector directory",
)
class KotlinGoldenParityTest(unittest.TestCase):
    """Exercise the exact vectors used by CheekPuffGeometricDetectorTest."""

    def test_frame_features_raw_amp_and_side_match_kotlin_goldens(self) -> None:
        rows = read_csv("frames.expected.csv")
        landmarks = read_gzip_floats("frames.landmarks.bin.gz").reshape(
            -1, geometry.LANDMARK_COUNT, 3
        )
        rotations_t = read_gzip_floats("frames.rotationT.bin.gz").reshape(-1, 3, 3)
        baseline_by_clip = baselines()

        self.assertEqual(len(rows), len(landmarks))
        for index, row in enumerate(rows):
            feature = geometry.extract_features(
                landmarks[index], ASPECT, rotation_t=rotations_t[index]
            )
            message = f"golden row {index}, clip={row['clip']}, frame={row['frame']}"
            self.assertAlmostEqual(
                feature.mouth_width,
                float(row["feat_mouth_width"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                feature.lip_gap,
                float(row["feat_lip_gap"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                feature.nose_left_y,
                float(row["feat_noseL_y"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                feature.nose_right_y,
                float(row["feat_noseR_y"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                feature.mouth_center_x,
                float(row["feat_mouth_cx"]),
                delta=TOLERANCE,
                msg=message,
            )

            detector = geometry.ProductionLikeDetector(baseline_by_clip[row["clip"]])
            output = detector.process(feature, int(row["t_ms"]))
            self.assertAlmostEqual(
                output.raw_score,
                float(row["exp_score"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                output.amp_score,
                float(row["exp_amp"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertAlmostEqual(
                output.side,
                float(row["exp_side"]),
                delta=TOLERANCE,
                msg=message,
            )
            self.assertEqual(
                output.evidence_gate,
                row["exp_gate"] == "1.0",
                msg=message,
            )

    def test_causal_debounce_and_hysteresis_match_kotlin_sequence(self) -> None:
        rows = read_csv("seq_04.expected.csv")
        landmarks = read_gzip_floats("seq_04.landmarks.bin.gz").reshape(
            -1, geometry.LANDMARK_COUNT, 3
        )
        rotations_t = read_gzip_floats("seq_04.rotationT.bin.gz").reshape(-1, 3, 3)
        detector = geometry.ProductionLikeDetector(baselines()["04"])

        self.assertEqual(len(rows), len(landmarks))
        for index, row in enumerate(rows):
            feature = geometry.extract_features(
                landmarks[index], ASPECT, rotation_t=rotations_t[index]
            )
            output = detector.process(feature, int(row["t_ms"]))
            message = f"sequence frame={index}, t_ms={row['t_ms']}"
            expected_raw = float(row["exp_raw"])
            expected_amp = float(row["exp_amp"])
            self.assertAlmostEqual(
                output.raw_score, expected_raw, delta=TOLERANCE, msg=message
            )
            self.assertAlmostEqual(
                output.amp_score, expected_amp, delta=TOLERANCE, msg=message
            )
            self.assertAlmostEqual(
                output.side, float(row["exp_side"]), delta=TOLERANCE, msg=message
            )
            if (
                abs(expected_raw - geometry.THRESHOLD) > 1e-3
                and abs(expected_amp - geometry.OFF_THRESHOLD) > 1e-3
            ):
                self.assertEqual(
                    output.detected, row["exp_detected"] == "1", msg=message
                )

    def test_python_4x4_matrix_convention_matches_kotlin_rotation_t(self) -> None:
        landmarks = read_gzip_floats("frames.landmarks.bin.gz").reshape(
            -1, geometry.LANDMARK_COUNT, 3
        )
        rotation_t = read_gzip_floats("frames.rotationT.bin.gz").reshape(-1, 3, 3)[0]
        transform = np.eye(4, dtype=np.float32)
        transform[:3, :3] = rotation_t.T

        kotlin_path = geometry.extract_features(
            landmarks[0], ASPECT, rotation_t=rotation_t
        )
        python_matrix_path = geometry.extract_features(
            landmarks[0], ASPECT, canonical_to_camera=transform
        )
        self.assertEqual(kotlin_path, python_matrix_path)


class ArtifactAndAlignmentTest(unittest.TestCase):
    def test_current_npz_schema_loads_as_identity_rotation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "mediapipe_landmarks.npz"
            landmarks = np.zeros((2, geometry.LANDMARK_COUNT, 3), dtype=np.float32)
            np.savez_compressed(
                path,
                landmarks=landmarks,
                frame_indices=np.asarray([3, 4], dtype=np.int32),
                timestamps=np.asarray([10.0, 10.033], dtype=np.float64),
            )
            artifact = geometry.load_landmark_artifact(path)
            self.assertEqual(
                artifact.rotation_mode,
                "identity_missing_facial_transformation_matrix",
            )
            self.assertIsNone(artifact.transforms)

    def test_v3_npz_schema_loads_facial_transformation_matrices(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "mediapipe_landmarks.npz"
            landmarks = np.zeros((2, geometry.LANDMARK_COUNT, 3), dtype=np.float32)
            transforms = np.repeat(
                np.eye(4, dtype=np.float32)[np.newaxis, :, :], 2, axis=0
            )
            np.savez_compressed(
                path,
                landmarks=landmarks,
                frame_indices=np.asarray([3, 4], dtype=np.int32),
                timestamps=np.asarray([10.0, 10.033], dtype=np.float64),
                facial_transformation_matrices=transforms,
            )
            artifact = geometry.load_landmark_artifact(path)
            self.assertEqual(
                artifact.rotation_mode,
                "mediapipe_canonical_to_camera_4x4",
            )
            np.testing.assert_array_equal(artifact.transforms, transforms)

    def test_nearest_reference_respects_tolerance(self) -> None:
        aligned, deltas = geometry.align_nearest_reference(
            np.asarray([1.0, 2.0, 3.0]),
            np.asarray([1.005, 2.030, 3.0]),
            np.asarray([0.1, 0.2, 0.3]),
            tolerance_seconds=0.016,
        )
        np.testing.assert_allclose(aligned[[0, 2]], [0.1, 0.3])
        self.assertTrue(np.isnan(aligned[1]))
        self.assertAlmostEqual(deltas[0], 0.005)


if __name__ == "__main__":
    unittest.main()
