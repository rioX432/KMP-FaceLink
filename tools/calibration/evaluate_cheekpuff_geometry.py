#!/usr/bin/env python3
"""Evaluate an unshipped cheek-puff geometry implementation on a paired capture session.

This tool ports the frozen feature extraction, evidence gate, 350 ms causal onset,
and 0.20 release hysteresis from the reviewed research implementation. It is not
present in the Avvy release branch. A neutral baseline is fixed from explicit
calibration windows; evaluation is restricted to separately declared windows.

ARKit ``cheekPuff`` is a paired reference signal, not physical ground truth.
The comparison reports response and agreement only.  It must not be described
as accuracy, sensitivity, specificity, or proof that either estimator is
correct.

Example (times are seconds relative to the first exported frame):

    python tools/calibration/evaluate_cheekpuff_geometry.py SESSION \
      --calibration-window neutral:0.25:1.75 \
      --evaluation-window puff_1:2.25:4.75 \
      --evaluation-window puff_2:7.25:9.75 \
      --output REPORT_DIR

Authoritative visual-QC windows should be passed by exported frame index so
that rounded presentation timestamps cannot exclude an endpoint frame:

    python tools/calibration/evaluate_cheekpuff_geometry.py SESSION \
      --calibration-frame-window neutral:10:45 \
      --evaluation-frame-window puff_1:90:162 \
      --evaluation-frame-window puff_2:228:282 \
      --output REPORT_DIR

Current landmark artifacts include MediaPipe's facial transformation matrix.
For older artifacts that contain landmarks only, this tool uses an identity
rotation and records the limitation prominently.  That fallback is suitable
only for controlled frontal review; it is not a production-parity
pose-normalization test.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import numpy as np

from calibration_contract import (
    AnalysisWindow,
    FrameAnalysisWindow,
    clip01,
    paired_difference_statistics,
    parse_frame_window,
    parse_window,
    sign,
)


DISCLAIMER = (
    "ARKit cheekPuff is a paired reference signal, not physical ground truth. "
    "These results describe response and agreement between estimator outputs; "
    "they do not establish physical accuracy, sensitivity, specificity, or "
    "causality. ARKit ran live while MediaPipe ran later on RGB saved from the "
    "same capture callback, so the estimators did not receive an identical "
    "image input."
)

IDENTITY_ROTATION_LIMITATION = (
    "The landmark artifact contains no facial transformation matrix. Identity "
    "rotation was used, so the score reproduces the frozen research formula and "
    "gate but not its pose de-rotation path. Restrict interpretation to a "
    "controlled frontal capture; do not use this run to claim yaw robustness."
)

# MediaPipe mesh indices used by the frozen unshipped Kotlin implementation.
LANDMARK_COUNT = 478
NASION = 168
EYE_INNER_L = 133
EYE_INNER_R = 362
MOUTH_CORNER_L = 61
MOUTH_CORNER_R = 291
LIP_UPPER_INNER = 13
LIP_LOWER_INNER = 14
NOSE_WING_IMG_LEFT = np.asarray([218, 237, 239, 44, 79], dtype=np.intp)
NOSE_WING_IMG_RIGHT = np.asarray([438, 457, 459, 274, 309], dtype=np.intp)

# Keep these constants synchronized with CheekPuffGeometricDetector.kt.
NARROW_SAT = 0.20
LIFT_SAT = 0.05
SHIFT_SAT = 0.15
LIFT_MIN = 0.30
SIDE_MIN = 0.30
GAP_GATE = 0.08
THRESHOLD = 0.35
OFF_THRESHOLD = 0.20
LIFT_NARROW_MAX = 1.0
ON_DELAY_MS = 350
MIN_CALIBRATION_FRAMES = 15


@dataclass(frozen=True)
class ResolvedAnalysisWindow:
    """Auditable window metadata after selection against an artifact."""

    label: str
    source: str
    start_seconds: float
    end_seconds: float
    actual_start_seconds: float
    actual_end_seconds: float
    start_frame: int
    end_frame: int
    expected_frame_count: int
    actual_frame_count: int


@dataclass(frozen=True)
class WindowSelection:
    """Resolved metadata plus its exact artifact-row mask."""

    window: ResolvedAnalysisWindow
    mask: np.ndarray


@dataclass(frozen=True)
class Features:
    """Pose-normalized/IOD-scaled features consumed by the detector."""

    mouth_width: float
    lip_gap: float
    nose_left_y: float
    nose_right_y: float
    mouth_center_x: float


@dataclass(frozen=True)
class Baseline:
    """Median neutral reference fixed before evaluation begins."""

    mouth_width: float
    lip_gap: float
    nose_left_y: float
    nose_right_y: float
    mouth_center_x: float


@dataclass(frozen=True)
class DetectorOutput:
    """One frame of production-like score and gate diagnostics."""

    cheek_puff: float
    raw_score: float
    amp_score: float
    side: float
    narrow: float
    lift_raw: float
    shift: float
    core: float
    lift: float
    side_abs: float
    lip_closed: bool
    lift_ok: bool
    evidence_gate: bool
    detected: bool


@dataclass(frozen=True)
class LandmarkArtifact:
    """Validated arrays exported by process_captures.py."""

    landmarks: np.ndarray
    frame_indices: np.ndarray
    timestamps: np.ndarray
    transforms: np.ndarray | None
    rotation_mode: str


def extract_features(
    landmarks: np.ndarray,
    aspect: float,
    *,
    rotation_t: np.ndarray | None = None,
    canonical_to_camera: np.ndarray | None = None,
) -> Features:
    """Port Kotlin feature extraction for one ``[478, 3]`` landmark frame.

    ``rotation_t`` is the row-major R^T consumed by Kotlin.  A Python
    FaceLandmarker 4x4 canonical-to-camera transform can instead be supplied as
    ``canonical_to_camera``; row-vector multiplication by R is equivalent to
    Kotlin's column-vector multiplication by R^T.
    """
    points = np.asarray(landmarks, dtype=np.float64)
    if points.shape != (LANDMARK_COUNT, 3):
        raise ValueError(
            f"expected landmarks shape ({LANDMARK_COUNT}, 3), got {points.shape}"
        )
    if not np.isfinite(points).all():
        raise ValueError("landmarks contain NaN or infinity")
    if not math.isfinite(aspect) or aspect <= 0.0:
        raise ValueError(f"aspect must be finite and positive, got {aspect}")
    if rotation_t is not None and canonical_to_camera is not None:
        raise ValueError("supply rotation_t or canonical_to_camera, not both")

    metric = np.empty_like(points)
    metric[:, 0] = points[:, 0]
    metric[:, 1] = -(points[:, 1] * aspect)
    metric[:, 2] = -points[:, 2]
    centered = metric - metric[NASION]

    if rotation_t is not None:
        rot_t = np.asarray(rotation_t, dtype=np.float64)
        if rot_t.shape != (3, 3):
            raise ValueError(f"rotation_t must have shape (3, 3), got {rot_t.shape}")
        q = (rot_t @ centered.T).T
    elif canonical_to_camera is not None:
        transform = np.asarray(canonical_to_camera, dtype=np.float64)
        if transform.shape == (4, 4):
            rotation = transform[:3, :3]
        elif transform.shape == (3, 3):
            rotation = transform
        else:
            raise ValueError(
                "canonical_to_camera must have shape (4, 4) or (3, 3), "
                f"got {transform.shape}"
            )
        q = centered @ rotation
    else:
        q = centered

    iod = float(np.linalg.norm(q[EYE_INNER_L] - q[EYE_INNER_R]))
    if not math.isfinite(iod) or iod <= 1e-12:
        raise ValueError(f"invalid inter-ocular distance: {iod}")

    corner_left = q[MOUTH_CORNER_L]
    corner_right = q[MOUTH_CORNER_R]
    return Features(
        mouth_width=float(np.linalg.norm(corner_left - corner_right) / iod),
        lip_gap=float(np.linalg.norm(q[LIP_UPPER_INNER] - q[LIP_LOWER_INNER]) / iod),
        nose_left_y=float(q[NOSE_WING_IMG_LEFT, 1].mean() / iod),
        nose_right_y=float(q[NOSE_WING_IMG_RIGHT, 1].mean() / iod),
        mouth_center_x=float((corner_left[0] + corner_right[0]) / 2.0 / iod),
    )


def median_baseline(features: Iterable[Features]) -> Baseline:
    """Build the fixed neutral baseline exactly from declared calibration rows."""
    rows = list(features)
    if not rows:
        raise ValueError("calibration selection contains no feature rows")
    return Baseline(
        mouth_width=float(np.median([row.mouth_width for row in rows])),
        lip_gap=float(np.median([row.lip_gap for row in rows])),
        nose_left_y=float(np.median([row.nose_left_y for row in rows])),
        nose_right_y=float(np.median([row.nose_right_y for row in rows])),
        mouth_center_x=float(np.median([row.mouth_center_x for row in rows])),
    )


class ProductionLikeDetector:
    """Fixed-baseline port of production scoring and causal state transitions."""

    def __init__(self, baseline: Baseline) -> None:
        if baseline.mouth_width <= 0.0:
            raise ValueError("baseline mouth width must be positive")
        self.baseline = baseline
        self.run_start_ms: int | None = None
        self.detected = False
        self.last_timestamp_ms: int | None = None
        self.last_output: DetectorOutput | None = None

    def reset_state(self) -> None:
        """Reset debounce/hysteresis without changing the fixed baseline."""
        self.run_start_ms = None
        self.detected = False
        self.last_timestamp_ms = None
        self.last_output = None

    def process(self, features: Features, timestamp_ms: int) -> DetectorOutput:
        """Process one chronological frame using Kotlin-equivalent semantics."""
        if self.last_timestamp_ms == timestamp_ms and self.last_output is not None:
            return self.last_output
        if self.last_timestamp_ms is not None and timestamp_ms < self.last_timestamp_ms:
            raise ValueError("timestamps must be nondecreasing")
        self.last_timestamp_ms = timestamp_ms

        base = self.baseline
        narrow = (base.mouth_width - features.mouth_width) / base.mouth_width
        lift_raw = (
            (features.nose_left_y - base.nose_left_y)
            + (features.nose_right_y - base.nose_right_y)
        ) / 2.0
        shift = features.mouth_center_x - base.mouth_center_x

        core = clip01(narrow / NARROW_SAT)
        lift = clip01(lift_raw / LIFT_SAT)
        side_abs = clip01(abs(shift) / SHIFT_SAT)
        lift_ok = lift >= LIFT_MIN and lift_raw <= LIFT_NARROW_MAX * narrow
        lip_closed = features.lip_gap < GAP_GATE
        evidence_gate = lip_closed and (lift_ok or side_abs >= SIDE_MIN)
        raw = max(core, side_abs) if evidence_gate else 0.0
        amp = max(core, side_abs) if lip_closed else 0.0

        if raw >= THRESHOLD:
            if self.run_start_ms is None:
                self.run_start_ms = timestamp_ms
            if timestamp_ms - self.run_start_ms >= ON_DELAY_MS:
                self.detected = True
        else:
            self.run_start_ms = None
            if self.detected and amp < OFF_THRESHOLD:
                self.detected = False

        output = DetectorOutput(
            cheek_puff=float(amp if self.detected else 0.0),
            raw_score=float(raw),
            amp_score=float(amp),
            side=float(sign(shift) * side_abs),
            narrow=float(narrow),
            lift_raw=float(lift_raw),
            shift=float(shift),
            core=float(core),
            lift=float(lift),
            side_abs=float(side_abs),
            lip_closed=bool(lip_closed),
            lift_ok=bool(lift_ok),
            evidence_gate=bool(evidence_gate),
            detected=bool(self.detected),
        )
        self.last_output = output
        return output


def sha256_file(path: Path) -> str:
    """Return a file digest for result provenance."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_landmark_artifact(path: Path) -> LandmarkArtifact:
    """Load and strictly validate ``mediapipe_landmarks.npz``."""
    if not path.is_file():
        raise ValueError(f"landmark artifact not found: {path}")
    with np.load(path, allow_pickle=False) as artifact:
        required = {"landmarks", "frame_indices", "timestamps"}
        missing = sorted(required - set(artifact.files))
        if missing:
            raise ValueError(
                "landmark artifact is missing required keys: " + ", ".join(missing)
            )
        landmarks = np.asarray(artifact["landmarks"], dtype=np.float32)
        frame_indices = np.asarray(artifact["frame_indices"], dtype=np.int64)
        timestamps = np.asarray(artifact["timestamps"], dtype=np.float64)

        transform_key = next(
            (
                key
                for key in (
                    "facial_transformation_matrices",
                    "transforms",
                    "transform",
                )
                if key in artifact.files
            ),
            None,
        )
        transforms = (
            np.asarray(artifact[transform_key], dtype=np.float64)
            if transform_key is not None
            else None
        )

    count = len(landmarks)
    if landmarks.shape != (count, LANDMARK_COUNT, 3):
        raise ValueError(
            f"landmarks must have shape [N, {LANDMARK_COUNT}, 3], got {landmarks.shape}"
        )
    if frame_indices.shape != (count,) or timestamps.shape != (count,):
        raise ValueError(
            "frame_indices and timestamps must both have shape [N] matching landmarks"
        )
    if count == 0:
        raise ValueError("landmark artifact contains zero frames")
    if not np.isfinite(landmarks).all() or not np.isfinite(timestamps).all():
        raise ValueError("landmark artifact contains NaN or infinity")
    if np.any(np.diff(timestamps) <= 0.0):
        raise ValueError("landmark timestamps must be strictly increasing")
    if len(np.unique(frame_indices)) != count:
        raise ValueError("landmark frame_indices must be unique")
    if np.any(np.diff(frame_indices) <= 0):
        raise ValueError("landmark frame_indices must be strictly increasing")

    if transforms is None:
        rotation_mode = "identity_missing_facial_transformation_matrix"
    else:
        if transforms.shape != (count, 4, 4):
            raise ValueError(
                f"facial transforms must have shape [N, 4, 4], got {transforms.shape}"
            )
        if not np.isfinite(transforms).all():
            raise ValueError("facial transforms contain NaN or infinity")
        rotation_mode = "mediapipe_canonical_to_camera_4x4"

    return LandmarkArtifact(
        landmarks=landmarks,
        frame_indices=frame_indices,
        timestamps=timestamps,
        transforms=transforms,
        rotation_mode=rotation_mode,
    )


def jpeg_dimensions(path: Path) -> tuple[int, int]:
    """Read JPEG width and height without adding an image-library dependency."""
    start_of_frame = {
        0xC0,
        0xC1,
        0xC2,
        0xC3,
        0xC5,
        0xC6,
        0xC7,
        0xC9,
        0xCA,
        0xCB,
        0xCD,
        0xCE,
        0xCF,
    }
    with path.open("rb") as source:
        if source.read(2) != b"\xff\xd8":
            raise ValueError(f"not a JPEG file: {path}")
        while True:
            byte = source.read(1)
            if not byte:
                break
            if byte != b"\xff":
                continue
            marker_byte = source.read(1)
            while marker_byte == b"\xff":
                marker_byte = source.read(1)
            if not marker_byte:
                break
            marker = marker_byte[0]
            if marker in {0xD8, 0xD9} or 0xD0 <= marker <= 0xD7:
                continue
            length_bytes = source.read(2)
            if len(length_bytes) != 2:
                break
            segment_length = int.from_bytes(length_bytes, "big")
            if segment_length < 2:
                raise ValueError(f"invalid JPEG segment in {path}")
            if marker in start_of_frame:
                payload = source.read(5)
                if len(payload) != 5:
                    break
                height = int.from_bytes(payload[1:3], "big")
                width = int.from_bytes(payload[3:5], "big")
                if width <= 0 or height <= 0:
                    raise ValueError(f"invalid JPEG dimensions in {path}")
                return width, height
            source.seek(segment_length - 2, 1)
    raise ValueError(f"could not read JPEG dimensions: {path}")


def resolve_aspect(
    session_dir: Path, explicit_aspect: float | None
) -> tuple[float, str]:
    """Use an explicit aspect or infer image height/width from the first frame."""
    if explicit_aspect is not None:
        if not math.isfinite(explicit_aspect) or explicit_aspect <= 0.0:
            raise ValueError("--aspect must be finite and positive")
        return float(explicit_aspect), "explicit_cli_height_over_width"

    frame = next(iter(sorted((session_dir / "frames").glob("*.jpg"))), None)
    if frame is None:
        raise ValueError(
            "cannot infer aspect: session has no frames/*.jpg; pass --aspect HEIGHT/WIDTH"
        )
    width, height = jpeg_dimensions(frame)
    return height / width, f"inferred_from_{frame.name}_{width}x{height}"


def load_arkit_reference(path: Path) -> tuple[np.ndarray, np.ndarray]:
    """Load timestamp and cheekPuff from the live ARKit export."""
    if not path.is_file():
        raise ValueError(f"ARKit CSV not found: {path}")
    timestamps: list[float] = []
    values: list[float] = []
    with path.open(newline="", encoding="utf-8") as source:
        reader = csv.DictReader(source)
        fieldnames = set(reader.fieldnames or [])
        required = {"timestamp", "cheekPuff"}
        if not required.issubset(fieldnames):
            raise ValueError(
                f"ARKit CSV must contain timestamp and cheekPuff, got {reader.fieldnames}"
            )
        for row in reader:
            timestamps.append(float(row["timestamp"]))
            values.append(float(row["cheekPuff"]))
    ts = np.asarray(timestamps, dtype=np.float64)
    reference = np.asarray(values, dtype=np.float64)
    if len(ts) == 0:
        raise ValueError("ARKit CSV contains no rows")
    if not np.isfinite(ts).all() or not np.isfinite(reference).all():
        raise ValueError("ARKit CSV contains NaN or infinity")
    order = np.argsort(ts, kind="stable")
    return ts[order], reference[order]


def align_nearest_reference(
    target_timestamps: np.ndarray,
    reference_timestamps: np.ndarray,
    reference_values: np.ndarray,
    tolerance_seconds: float,
) -> tuple[np.ndarray, np.ndarray]:
    """Align ARKit reference values to landmark rows by nearest timestamp."""
    if tolerance_seconds < 0.0 or not math.isfinite(tolerance_seconds):
        raise ValueError("alignment tolerance must be finite and nonnegative")
    positions = np.searchsorted(reference_timestamps, target_timestamps)
    aligned = np.full(len(target_timestamps), np.nan, dtype=np.float64)
    deltas = np.full(len(target_timestamps), np.nan, dtype=np.float64)
    for index, (timestamp, position) in enumerate(zip(target_timestamps, positions)):
        candidates: list[int] = []
        if position < len(reference_timestamps):
            candidates.append(int(position))
        if position > 0:
            candidates.append(int(position - 1))
        if not candidates:
            continue
        nearest = min(
            candidates,
            key=lambda candidate: abs(reference_timestamps[candidate] - timestamp),
        )
        delta = abs(float(reference_timestamps[nearest] - timestamp))
        if delta <= tolerance_seconds:
            aligned[index] = float(reference_values[nearest])
            deltas[index] = delta
    return aligned, deltas


def _resolved_seconds_window(
    spec: AnalysisWindow,
    artifact: LandmarkArtifact,
    relative_times: np.ndarray,
    kind: str,
) -> WindowSelection:
    """Resolve a legacy seconds window and expose the selected endpoint rows."""
    duration_seconds = float(relative_times[-1])
    if spec.end_seconds > duration_seconds + 1e-6:
        raise ValueError(
            f"{kind} window {spec.label!r} ends at {spec.end_seconds:.9f}s "
            f"after the landmark timeline ends at {duration_seconds:.9f}s"
        )
    mask = (relative_times >= spec.start_seconds) & (
        relative_times <= spec.end_seconds
    )
    positions = np.flatnonzero(mask)
    if len(positions) == 0:
        raise ValueError(
            f"{kind} window {spec.label!r} contains no landmark frames: "
            f"{spec.start_seconds:.9f}-{spec.end_seconds:.9f}s"
        )
    first = int(positions[0])
    last = int(positions[-1])
    count = int(len(positions))
    return WindowSelection(
        window=ResolvedAnalysisWindow(
            label=spec.label,
            source="seconds",
            start_seconds=float(spec.start_seconds),
            end_seconds=float(spec.end_seconds),
            actual_start_seconds=float(relative_times[first]),
            actual_end_seconds=float(relative_times[last]),
            start_frame=int(artifact.frame_indices[first]),
            end_frame=int(artifact.frame_indices[last]),
            expected_frame_count=count,
            actual_frame_count=count,
        ),
        mask=mask,
    )


def _resolved_frame_window(
    spec: FrameAnalysisWindow,
    artifact: LandmarkArtifact,
    relative_times: np.ndarray,
    kind: str,
) -> WindowSelection:
    """Resolve a frame-index window, rejecting missing or out-of-range rows."""
    available_start = int(artifact.frame_indices[0])
    available_end = int(artifact.frame_indices[-1])
    if spec.start_frame < available_start or spec.end_frame > available_end:
        raise ValueError(
            f"{kind} frame window {spec.label!r} requests "
            f"{spec.start_frame}:{spec.end_frame} outside available frame range "
            f"{available_start}:{available_end}"
        )

    expected = spec.end_frame - spec.start_frame + 1
    mask = (artifact.frame_indices >= spec.start_frame) & (
        artifact.frame_indices <= spec.end_frame
    )
    selected = artifact.frame_indices[mask]
    actual = int(len(selected))
    if actual != expected:
        available = {int(value) for value in selected}
        missing = [
            value
            for value in range(spec.start_frame, spec.end_frame + 1)
            if value not in available
        ]
        preview = ", ".join(str(value) for value in missing[:12])
        suffix = "..." if len(missing) > 12 else ""
        raise ValueError(
            f"{kind} frame window {spec.label!r} expects {expected} frames but "
            f"selected {actual}; missing frame indices: {preview}{suffix}"
        )

    positions = np.flatnonzero(mask)
    first = int(positions[0])
    last = int(positions[-1])
    actual_start = float(relative_times[first])
    actual_end = float(relative_times[last])
    return WindowSelection(
        window=ResolvedAnalysisWindow(
            label=spec.label,
            source="frame_index",
            start_seconds=actual_start,
            end_seconds=actual_end,
            actual_start_seconds=actual_start,
            actual_end_seconds=actual_end,
            start_frame=spec.start_frame,
            end_frame=spec.end_frame,
            expected_frame_count=expected,
            actual_frame_count=actual,
        ),
        mask=mask,
    )


def resolve_windows(
    seconds_windows: list[AnalysisWindow],
    frame_windows: list[FrameAnalysisWindow],
    artifact: LandmarkArtifact,
    relative_times: np.ndarray,
    kind: str,
) -> list[WindowSelection]:
    """Resolve both compatible CLI window forms against exact artifact rows."""
    selections = [
        _resolved_seconds_window(window, artifact, relative_times, kind)
        for window in seconds_windows
    ]
    selections.extend(
        _resolved_frame_window(window, artifact, relative_times, kind)
        for window in frame_windows
    )
    return selections


def validate_resolved_windows(
    calibration: list[WindowSelection],
    evaluation: list[WindowSelection],
) -> None:
    """Validate required windows, labels, and causal calibration separation."""
    if not calibration:
        raise ValueError(
            "at least one --calibration-window or "
            "--calibration-frame-window is required"
        )
    if not evaluation:
        raise ValueError(
            "at least one --evaluation-window or "
            "--evaluation-frame-window is required"
        )

    seen: set[str] = set()
    for selection in [*calibration, *evaluation]:
        label = selection.window.label
        if label in seen:
            raise ValueError(f"duplicate window label: {label}")
        seen.add(label)

    calibration_end = max(
        selection.window.actual_end_seconds for selection in calibration
    )
    evaluation_start = min(
        selection.window.actual_start_seconds for selection in evaluation
    )
    if calibration_end > evaluation_start:
        raise ValueError(
            "calibration windows must end before or at the first evaluation "
            "window; this prevents future evaluation frames leaking into baseline"
        )


def union_selection_mask(
    row_count: int, windows: list[WindowSelection]
) -> np.ndarray:
    """Union exact masks from already-resolved windows."""
    mask = np.zeros(row_count, dtype=bool)
    for selection in windows:
        mask |= selection.mask
    return mask


def window_json(window: ResolvedAnalysisWindow) -> dict[str, Any]:
    """Serialize a resolved window with explicit selection provenance."""
    return {
        "label": window.label,
        "source": window.source,
        "start_seconds": window.start_seconds,
        "end_seconds": window.end_seconds,
        "actual_start_seconds": window.actual_start_seconds,
        "actual_end_seconds": window.actual_end_seconds,
        "start_frame": window.start_frame,
        "end_frame": window.end_frame,
        "expected_frame_count": window.expected_frame_count,
        "actual_frame_count": window.actual_frame_count,
        "bounds": "inclusive",
    }


def numeric_statistics(values: np.ndarray) -> dict[str, float | int | None]:
    """JSON-safe distribution summary over finite values."""
    finite = np.asarray(values, dtype=np.float64)
    finite = finite[np.isfinite(finite)]
    if len(finite) == 0:
        return {
            "count": 0,
            "mean": None,
            "median": None,
            "minimum": None,
            "maximum": None,
            "standard_deviation": None,
        }
    return {
        "count": int(len(finite)),
        "mean": float(np.mean(finite)),
        "median": float(np.median(finite)),
        "minimum": float(np.min(finite)),
        "maximum": float(np.max(finite)),
        "standard_deviation": float(np.std(finite)),
    }


def agreement_statistics(
    geometric: np.ndarray, reference: np.ndarray
) -> dict[str, float | int | None]:
    """Describe paired difference without assigning either signal as truth."""
    return paired_difference_statistics(geometric, reference)


def fraction(values: np.ndarray) -> float | None:
    """Mean of a nonempty boolean selection."""
    return float(np.mean(values.astype(np.float64))) if len(values) else None


def feature_rows(artifact: LandmarkArtifact, aspect: float) -> list[Features]:
    """Extract all features with optional per-frame pose de-rotation."""
    rows: list[Features] = []
    for index, landmarks in enumerate(artifact.landmarks):
        transform = (
            artifact.transforms[index] if artifact.transforms is not None else None
        )
        rows.append(
            extract_features(
                landmarks,
                aspect,
                canonical_to_camera=transform,
            )
        )
    return rows


def run_detector(
    features: list[Features],
    timestamps: np.ndarray,
    relative_times: np.ndarray,
    baseline: Baseline,
    calibration_end_seconds: float,
) -> list[DetectorOutput | None]:
    """Start causal evaluation only after the explicit baseline is complete."""
    detector = ProductionLikeDetector(baseline)
    outputs: list[DetectorOutput | None] = []
    for feature, timestamp, relative_time in zip(features, timestamps, relative_times):
        if relative_time < calibration_end_seconds:
            outputs.append(None)
            continue
        timestamp_ms = int(round(float(timestamp) * 1000.0))
        outputs.append(detector.process(feature, timestamp_ms))
    return outputs


def output_array(
    outputs: list[DetectorOutput | None], attribute: str, *, boolean: bool = False
) -> np.ndarray:
    """Project optional output objects to an aligned NumPy array."""
    if boolean:
        return np.asarray(
            [
                bool(getattr(row, attribute)) if row is not None else False
                for row in outputs
            ],
            dtype=bool,
        )
    return np.asarray(
        [
            float(getattr(row, attribute)) if row is not None else np.nan
            for row in outputs
        ],
        dtype=np.float64,
    )


def summarize_evaluation_window(
    selection: WindowSelection,
    outputs: list[DetectorOutput | None],
    arkit_reference: np.ndarray,
) -> dict[str, Any]:
    """Build response/agreement statistics for one declared evaluation window."""
    window = selection.window
    mask = selection.mask
    geometry = output_array(outputs, "cheek_puff")[mask]
    raw = output_array(outputs, "raw_score")[mask]
    amp = output_array(outputs, "amp_score")[mask]
    side = output_array(outputs, "side")[mask]
    evidence_gate = output_array(outputs, "evidence_gate", boolean=True)[mask]
    active = output_array(outputs, "detected", boolean=True)[mask]
    reference = arkit_reference[mask]
    return {
        **window_json(window),
        "landmark_frame_count": int(np.count_nonzero(mask)),
        "arkit_paired_frame_count": int(np.count_nonzero(np.isfinite(reference))),
        "geometric_output": numeric_statistics(geometry),
        "geometric_raw_score": numeric_statistics(raw),
        "geometric_hold_amplitude": numeric_statistics(amp),
        "geometric_signed_image_side": numeric_statistics(side),
        "arkit_reference": numeric_statistics(reference),
        "paired_agreement": agreement_statistics(geometry, reference),
        "evidence_gate_fraction": fraction(evidence_gate),
        "raw_at_or_above_on_threshold_fraction": fraction(raw >= THRESHOLD),
        "causal_detector_active_fraction": fraction(active),
    }


def read_optional_json(path: Path) -> dict[str, Any] | None:
    """Read a metadata sidecar if it exists."""
    if not path.is_file():
        return None
    value = json.loads(path.read_text(encoding="utf-8"))
    return value if isinstance(value, dict) else {"value": value}


def production_source_path() -> Path:
    """Resolve the Kotlin source in the shared AnotherBall workspace."""
    anotherball_root = Path(__file__).resolve().parents[3]
    return (
        anotherball_root
        / "persona-android"
        / "core"
        / "face_tracking"
        / "src"
        / "main"
        / "java"
        / "com"
        / "anotherball"
        / "avvy"
        / "core"
        / "face_tracking"
        / "cheekpuff"
        / "CheekPuffGeometricDetector.kt"
    )


def write_frame_csv(
    path: Path,
    artifact: LandmarkArtifact,
    relative_times: np.ndarray,
    features: list[Features],
    outputs: list[DetectorOutput | None],
    arkit_reference: np.ndarray,
    timestamp_deltas: np.ndarray,
    calibration: list[WindowSelection],
    evaluation: list[WindowSelection],
) -> None:
    """Write auditable per-frame features, gates, scores, and paired reference."""
    fieldnames = [
        "frame_index",
        "timestamp",
        "time_seconds",
        "calibration_windows",
        "evaluation_windows",
        "mouth_width",
        "lip_gap",
        "nose_left_y",
        "nose_right_y",
        "mouth_center_x",
        "narrow",
        "lift_raw",
        "shift",
        "core",
        "lift",
        "side_abs",
        "side_signed",
        "lip_closed",
        "lift_ok",
        "evidence_gate",
        "raw_score",
        "amp_score",
        "detected",
        "geometric_cheek_puff",
        "arkit_cheek_puff_reference",
        "timestamp_delta_seconds",
    ]
    with path.open("w", newline="", encoding="utf-8") as destination:
        writer = csv.DictWriter(destination, fieldnames=fieldnames)
        writer.writeheader()
        for index, (frame_index, timestamp, relative, feature, output) in enumerate(
            zip(
                artifact.frame_indices,
                artifact.timestamps,
                relative_times,
                features,
                outputs,
            )
        ):
            calibration_labels = [
                selection.window.label
                for selection in calibration
                if bool(selection.mask[index])
            ]
            evaluation_labels = [
                selection.window.label
                for selection in evaluation
                if bool(selection.mask[index])
            ]
            row: dict[str, Any] = {
                "frame_index": int(frame_index),
                "timestamp": f"{float(timestamp):.6f}",
                "time_seconds": f"{float(relative):.6f}",
                "calibration_windows": ";".join(calibration_labels),
                "evaluation_windows": ";".join(evaluation_labels),
                **asdict(feature),
                "arkit_cheek_puff_reference": (
                    f"{arkit_reference[index]:.9f}"
                    if math.isfinite(float(arkit_reference[index]))
                    else ""
                ),
                "timestamp_delta_seconds": (
                    f"{timestamp_deltas[index]:.9f}"
                    if math.isfinite(float(timestamp_deltas[index]))
                    else ""
                ),
            }
            if output is not None:
                output_values = asdict(output)
                row.update(output_values)
                row["side_signed"] = output.side
                row["geometric_cheek_puff"] = output.cheek_puff
                row.pop("cheek_puff", None)
                row.pop("side", None)
            writer.writerow(row)


def write_plot(
    path: Path,
    relative_times: np.ndarray,
    outputs: list[DetectorOutput | None],
    arkit_reference: np.ndarray,
    calibration: list[WindowSelection],
    evaluation: list[WindowSelection],
    rotation_mode: str,
) -> None:
    """Create a compact timeline suitable for review and slide drafting."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    geometry = output_array(outputs, "cheek_puff")
    raw = output_array(outputs, "raw_score")
    fig, axis = plt.subplots(figsize=(13.33, 7.5), dpi=144)
    axis.plot(
        relative_times,
        arkit_reference,
        color="#ff6b7a",
        linewidth=2.3,
        label="ARKit cheekPuff (reference)",
    )
    axis.plot(
        relative_times,
        geometry,
        color="#6d3df5",
        linewidth=2.3,
        label="Production-like geometric output",
    )
    axis.plot(
        relative_times,
        raw,
        color="#6d3df5",
        linewidth=1.0,
        alpha=0.35,
        linestyle="--",
        label="Geometric raw score (pre-debounce)",
    )
    for index, selection in enumerate(calibration):
        window = selection.window
        axis.axvspan(
            window.start_seconds,
            window.end_seconds,
            color="#8a8a8a",
            alpha=0.13,
            label="Calibration window" if index == 0 else None,
        )
    colors = ["#ffd166", "#70d6ff", "#caffbf", "#ffadad"]
    for index, selection in enumerate(evaluation):
        window = selection.window
        axis.axvspan(
            window.start_seconds,
            window.end_seconds,
            color=colors[index % len(colors)],
            alpha=0.16,
        )
        axis.text(
            (window.start_seconds + window.end_seconds) / 2.0,
            0.97,
            window.label,
            ha="center",
            va="top",
            fontsize=9,
            transform=axis.get_xaxis_transform(),
            bbox={
                "boxstyle": "round,pad=0.2",
                "facecolor": "white",
                "edgecolor": "none",
                "alpha": 0.78,
            },
        )
    axis.set_ylim(-0.03, 1.05)
    axis.set_xlabel("Seconds from first exported frame")
    axis.set_ylabel("Estimator output (0–1)")
    axis.set_title("Paired cheekPuff response — reference vs production-like geometry")
    axis.grid(axis="y", alpha=0.18)
    axis.legend(loc="upper right", frameon=False)
    limitation = (
        "Pose de-rotation: unavailable (identity used)"
        if rotation_mode.startswith("identity")
        else "Pose de-rotation: MediaPipe facial transform"
    )
    fig.text(
        0.01,
        0.015,
        "ARKit is a reference, not ground truth. " + limitation,
        ha="left",
        va="bottom",
        fontsize=8,
        color="#555555",
    )
    fig.tight_layout(rect=(0.0, 0.045, 1.0, 1.0))
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)


def evaluate_session(
    session_dir: Path,
    output_dir: Path,
    calibration: list[AnalysisWindow],
    evaluation: list[AnalysisWindow],
    *,
    calibration_frame_windows: list[FrameAnalysisWindow] | None = None,
    evaluation_frame_windows: list[FrameAnalysisWindow] | None = None,
    aspect: float | None,
    alignment_tolerance_ms: float,
    make_plot: bool,
    min_calibration_frames: int = MIN_CALIBRATION_FRAMES,
) -> dict[str, Any]:
    """Run the complete fixed-window paired geometry evaluation."""
    landmarks_path = session_dir / "mediapipe_landmarks.npz"
    arkit_path = session_dir / "arkit_blendshapes.csv"
    artifact = load_landmark_artifact(landmarks_path)
    resolved_aspect, aspect_source = resolve_aspect(session_dir, aspect)

    relative_times = artifact.timestamps - artifact.timestamps[0]
    duration_seconds = float(relative_times[-1])
    calibration_selections = resolve_windows(
        calibration,
        calibration_frame_windows or [],
        artifact,
        relative_times,
        "calibration",
    )
    evaluation_selections = resolve_windows(
        evaluation,
        evaluation_frame_windows or [],
        artifact,
        relative_times,
        "evaluation",
    )
    validate_resolved_windows(calibration_selections, evaluation_selections)

    features = feature_rows(artifact, resolved_aspect)
    calibration_mask = union_selection_mask(
        len(relative_times), calibration_selections
    )
    calibration_count = int(np.count_nonzero(calibration_mask))
    if calibration_count < min_calibration_frames:
        raise ValueError(
            f"calibration windows contain {calibration_count} frames; "
            f"at least {min_calibration_frames} are required"
        )
    baseline = median_baseline(
        feature for feature, selected in zip(features, calibration_mask) if selected
    )
    calibration_end_position = max(
        int(np.flatnonzero(selection.mask)[-1])
        for selection in calibration_selections
    )
    calibration_end = float(relative_times[calibration_end_position])
    calibration_end_timestamp = float(
        artifact.timestamps[calibration_end_position]
    )
    calibration_end_frame = int(
        artifact.frame_indices[calibration_end_position]
    )
    outputs = run_detector(
        features,
        artifact.timestamps,
        relative_times,
        baseline,
        calibration_end,
    )

    arkit_timestamps, arkit_values = load_arkit_reference(arkit_path)
    tolerance_seconds = alignment_tolerance_ms / 1000.0
    arkit_reference, timestamp_deltas = align_nearest_reference(
        artifact.timestamps,
        arkit_timestamps,
        arkit_values,
        tolerance_seconds,
    )

    output_dir.mkdir(parents=True, exist_ok=True)
    frame_csv_path = output_dir / "cheekpuff_geometry_frames.csv"
    summary_path = output_dir / "cheekpuff_geometry_summary.json"
    plot_path = output_dir / "cheekpuff_geometry_timeline.png"

    write_frame_csv(
        frame_csv_path,
        artifact,
        relative_times,
        features,
        outputs,
        arkit_reference,
        timestamp_deltas,
        calibration_selections,
        evaluation_selections,
    )
    if make_plot:
        write_plot(
            plot_path,
            relative_times,
            outputs,
            arkit_reference,
            calibration_selections,
            evaluation_selections,
            artifact.rotation_mode,
        )

    production_source = production_source_path()
    processing_metadata = read_optional_json(session_dir / "mediapipe_processing.json")
    capture_metadata = read_optional_json(session_dir / "metadata.json")
    calibration_reference = arkit_reference[calibration_mask]
    evaluation_summaries = [
        summarize_evaluation_window(
            selection,
            outputs,
            arkit_reference,
        )
        for selection in evaluation_selections
    ]

    summary: dict[str, Any] = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "disclaimer": DISCLAIMER,
        "method": {
            "name": "production_formula_fixed_explicit_neutral_baseline",
            "baseline_strategy": (
                "median of only the declared calibration windows; baseline is "
                "fixed before causal evaluation"
            ),
            "causal_evaluation_begins_at_seconds": calibration_end,
            "causal_evaluation_begins_at_capture_timestamp_seconds": (
                calibration_end_timestamp
            ),
            "causal_evaluation_begins_at_frame": calibration_end_frame,
            "rotation_mode": artifact.rotation_mode,
            "rotation_limitation": (
                IDENTITY_ROTATION_LIMITATION
                if artifact.rotation_mode.startswith("identity")
                else None
            ),
            "aspect_height_over_width": resolved_aspect,
            "aspect_source": aspect_source,
            "constants": {
                "narrow_sat": NARROW_SAT,
                "lift_sat": LIFT_SAT,
                "shift_sat": SHIFT_SAT,
                "lift_min": LIFT_MIN,
                "side_min": SIDE_MIN,
                "gap_gate": GAP_GATE,
                "on_threshold": THRESHOLD,
                "off_threshold": OFF_THRESHOLD,
                "lift_narrow_max": LIFT_NARROW_MAX,
                "on_delay_ms": ON_DELAY_MS,
            },
            "production_source": str(production_source),
            "production_source_sha256": (
                sha256_file(production_source) if production_source.is_file() else None
            ),
        },
        "input": {
            "session_dir": str(session_dir),
            "landmarks_file": landmarks_path.name,
            "landmarks_sha256": sha256_file(landmarks_path),
            "arkit_file": arkit_path.name,
            "arkit_sha256": sha256_file(arkit_path),
            "landmark_frame_count": int(len(artifact.landmarks)),
            "arkit_source_frame_count": int(len(arkit_timestamps)),
            "arkit_aligned_frame_count": int(
                np.count_nonzero(np.isfinite(arkit_reference))
            ),
            "duration_seconds": duration_seconds,
            "alignment_tolerance_ms": alignment_tolerance_ms,
            "maximum_aligned_timestamp_delta_ms": (
                float(np.nanmax(timestamp_deltas) * 1000.0)
                if np.isfinite(timestamp_deltas).any()
                else None
            ),
            "capture_metadata": capture_metadata,
            "mediapipe_processing_metadata": processing_metadata,
        },
        "calibration": {
            "windows": [
                window_json(selection.window)
                for selection in calibration_selections
            ],
            "frame_count": calibration_count,
            "baseline_features": asdict(baseline),
            "arkit_reference_in_calibration_windows": numeric_statistics(
                calibration_reference
            ),
        },
        "evaluation_windows": evaluation_summaries,
        "outputs": {
            "frame_csv": frame_csv_path.name,
            "timeline_plot": plot_path.name if make_plot else None,
        },
    }
    summary_path.write_text(
        json.dumps(summary, indent=2, sort_keys=True, allow_nan=False) + "\n",
        encoding="utf-8",
    )
    return summary


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description=(
            "Evaluate production-like cheek-puff geometry against paired ARKit "
            "cheekPuff reference output."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "session_dir",
        type=Path,
        help="Capture session containing ARKit CSV, frames, and landmark NPZ",
    )
    parser.add_argument(
        "--calibration-window",
        action="append",
        type=parse_window,
        default=[],
        metavar="LABEL:START:END",
        help="Explicit neutral baseline window; repeatable and required",
    )
    parser.add_argument(
        "--evaluation-window",
        action="append",
        type=parse_window,
        default=[],
        metavar="LABEL:START:END",
        help="Explicit expression review window; repeatable and required",
    )
    parser.add_argument(
        "--calibration-frame-window",
        action="append",
        type=parse_frame_window,
        default=[],
        metavar="LABEL:START:END",
        help=(
            "Inclusive neutral baseline window in exported frame-index space; "
            "repeatable and preferred for authoritative QC"
        ),
    )
    parser.add_argument(
        "--evaluation-frame-window",
        action="append",
        type=parse_frame_window,
        default=[],
        metavar="LABEL:START:END",
        help=(
            "Inclusive expression window in exported frame-index space; "
            "repeatable and preferred for authoritative QC"
        ),
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Output directory (default: SESSION/cheekpuff_geometry)",
    )
    parser.add_argument(
        "--aspect",
        type=float,
        help="Image height/width; inferred from the first JPEG when omitted",
    )
    parser.add_argument(
        "--alignment-tolerance-ms",
        type=float,
        default=16.0,
        help="Maximum nearest-timestamp delta for ARKit pairing (default: 16)",
    )
    parser.add_argument(
        "--min-calibration-frames",
        type=int,
        default=MIN_CALIBRATION_FRAMES,
        help="Minimum neutral frames accepted (default: 15, matching production)",
    )
    parser.add_argument(
        "--no-plot",
        action="store_true",
        help="Skip timeline PNG generation",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entry point."""
    args = parse_args()
    try:
        session_dir = args.session_dir.resolve()
        if not session_dir.is_dir():
            raise ValueError(f"session directory not found: {session_dir}")
        if args.min_calibration_frames <= 0:
            raise ValueError("--min-calibration-frames must be positive")
        output_dir = (
            args.output.resolve()
            if args.output is not None
            else session_dir / "cheekpuff_geometry"
        )
        summary = evaluate_session(
            session_dir,
            output_dir,
            args.calibration_window,
            args.evaluation_window,
            calibration_frame_windows=args.calibration_frame_window,
            evaluation_frame_windows=args.evaluation_frame_window,
            aspect=args.aspect,
            alignment_tolerance_ms=args.alignment_tolerance_ms,
            make_plot=not args.no_plot,
            min_calibration_frames=args.min_calibration_frames,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    print(f"Wrote {output_dir / 'cheekpuff_geometry_summary.json'}")
    print(f"Wrote {output_dir / 'cheekpuff_geometry_frames.csv'}")
    if not args.no_plot:
        print(f"Wrote {output_dir / 'cheekpuff_geometry_timeline.png'}")
    print(DISCLAIMER)
    if summary["method"]["rotation_limitation"]:
        print("Warning: " + summary["method"]["rotation_limitation"], file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
