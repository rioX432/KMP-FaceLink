#!/usr/bin/env python3
"""
Process captured RGB frames with MediaPipe Face Landmarker.

Reads JPEG frames from a capture session directory and produces
mediapipe_blendshapes.csv alongside the existing arkit_blendshapes.csv,
enabling paired ARKit-vs-MediaPipe comparison.

Usage:
    python process_captures.py <session_dir>
    python process_captures.py <session_dir> --model path/to/face_landmarker.task
    python process_captures.py <session_dir> --delegate gpu

Requirements:
    pip install -r requirements.txt
    Use the exact model asset shipped by the Android app unless --model explicitly
    selects another recorded and hashed model.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import platform
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

# Default to the exact model asset shipped by the Android app.
DEFAULT_MODEL_PATH = (
    Path(__file__).resolve().parents[2]
    / "androidApp"
    / "src"
    / "main"
    / "assets"
    / "models"
    / "face_landmarker_v2_with_blendshapes.task"
)


def create_face_landmarker(
    model_path: Path,
    delegate: mp_python.BaseOptions.Delegate = mp_python.BaseOptions.Delegate.CPU,
) -> mp_vision.FaceLandmarker:
    """Create a MediaPipe FaceLandmarker from the given model file."""
    if not model_path.exists():
        print(f"Error: Model file not found at {model_path}", file=sys.stderr)
        print(
            "Pass --model with the exact Face Landmarker model used by the app "
            "and record its SHA-256.",
            file=sys.stderr,
        )
        sys.exit(1)

    base_options = mp_python.BaseOptions(
        model_asset_path=str(model_path),
        delegate=delegate,
    )
    options = mp_vision.FaceLandmarkerOptions(
        base_options=base_options,
        output_face_blendshapes=True,
        output_facial_transformation_matrixes=True,
        num_faces=1,
    )
    return mp_vision.FaceLandmarker.create_from_options(options)


def process_session(
    session_dir: Path, landmarker: mp_vision.FaceLandmarker
) -> dict[str, object]:
    """Process frames and write aligned blendshape and landmark artifacts."""
    frames_dir = session_dir / "frames"
    if not frames_dir.is_dir():
        print(f"Error: No frames/ directory in {session_dir}", file=sys.stderr)
        sys.exit(1)

    # Collect and sort frame files
    frame_files = sorted(frames_dir.glob("*.jpg"))
    if not frame_files:
        print(f"Error: No .jpg files in {frames_dir}", file=sys.stderr)
        sys.exit(1)

    # Load timestamps for alignment
    timestamps = load_timestamps(session_dir)

    print(f"Processing {len(frame_files)} frames from {session_dir.name}...")
    start_time = time.monotonic()

    # Detect blendshapes for each frame
    rows: list[dict[str, object]] = []
    landmarks: list[np.ndarray] = []
    facial_transformation_matrices: list[np.ndarray] = []
    landmark_frame_indices: list[int] = []
    landmark_timestamps: list[float] = []
    column_names: list[str] | None = None
    skipped = 0

    for i, frame_path in enumerate(frame_files):
        image = mp.Image.create_from_file(str(frame_path))
        result = landmarker.detect(image)

        if not result.face_blendshapes or len(result.face_blendshapes) == 0:
            skipped += 1
            continue
        if not result.face_landmarks or len(result.face_landmarks) == 0:
            raise ValueError(
                f"Blendshapes were detected for {frame_path.name}, but face "
                "landmarks were missing"
            )
        if (
            not result.facial_transformation_matrixes
            or len(result.facial_transformation_matrixes) == 0
        ):
            raise ValueError(
                f"Blendshapes were detected for {frame_path.name}, but the "
                "facial transformation matrix was missing"
            )

        blendshapes = result.face_blendshapes[0]
        face_landmarks = result.face_landmarks[0]
        if len(face_landmarks) != 478:
            raise ValueError(
                f"Expected 478 face landmarks for {frame_path.name}, "
                f"got {len(face_landmarks)}"
            )
        facial_transformation_matrix = np.asarray(
            result.facial_transformation_matrixes[0], dtype=np.float32
        )
        if facial_transformation_matrix.shape != (4, 4):
            raise ValueError(
                f"Expected a 4x4 facial transformation matrix for "
                f"{frame_path.name}, got {facial_transformation_matrix.shape}"
            )
        row: dict[str, object] = {}

        # Use frame index to look up timestamp
        frame_index = extract_frame_index(frame_path)
        row["frame_index"] = frame_index
        row["timestamp"] = timestamps.get(frame_index, 0.0)

        for category in blendshapes:
            name = category.category_name
            row[name] = round(category.score, 6)

        # Capture column names from the first successful detection
        if column_names is None:
            column_names = [cat.category_name for cat in blendshapes]

        rows.append(row)
        landmarks.append(
            np.asarray(
                [(point.x, point.y, point.z) for point in face_landmarks],
                dtype=np.float32,
            )
        )
        facial_transformation_matrices.append(facial_transformation_matrix)
        landmark_frame_indices.append(frame_index)
        landmark_timestamps.append(float(row["timestamp"]))

        # Progress reporting
        if (i + 1) % 100 == 0:
            elapsed = time.monotonic() - start_time
            fps = (i + 1) / elapsed
            print(f"  {i + 1}/{len(frame_files)} frames ({fps:.1f} fps)")

    elapsed = time.monotonic() - start_time

    if not rows or column_names is None:
        print("Error: No faces detected in any frame.", file=sys.stderr)
        sys.exit(1)

    # Write CSV
    output_path = session_dir / "mediapipe_blendshapes.csv"
    write_csv(output_path, rows, column_names)
    landmarks_path = session_dir / "mediapipe_landmarks.npz"
    landmark_array = np.stack(landmarks).astype(np.float32, copy=False)
    facial_transformation_matrix_array = np.stack(
        facial_transformation_matrices
    ).astype(np.float32, copy=False)
    np.savez_compressed(
        landmarks_path,
        landmarks=landmark_array,
        facial_transformation_matrices=facial_transformation_matrix_array,
        frame_indices=np.asarray(landmark_frame_indices, dtype=np.int32),
        timestamps=np.asarray(landmark_timestamps, dtype=np.float64),
    )
    artifact_sha256 = sha256_file(landmarks_path)

    print(
        f"Done. {len(rows)} frames processed, {skipped} skipped "
        f"({elapsed:.1f}s). Output: {output_path}"
    )
    return {
        "input_frames": len(frame_files),
        "processed_frames": len(rows),
        "skipped_frames": skipped,
        "elapsed_seconds": round(elapsed, 3),
        "landmarks_file": landmarks_path.name,
        "landmarks_shape": list(landmark_array.shape),
        "landmarks_dtype": str(landmark_array.dtype),
        "facial_transformation_matrices_shape": list(
            facial_transformation_matrix_array.shape
        ),
        "facial_transformation_matrices_dtype": str(
            facial_transformation_matrix_array.dtype
        ),
        "facial_transformation_matrices_sha256": sha256_array(
            facial_transformation_matrix_array
        ),
        "artifact_sha256": artifact_sha256,
        # Kept as a schema-v2 compatibility alias for the NPZ file digest.
        "landmarks_sha256": artifact_sha256,
    }


def sha256_file(path: Path) -> str:
    """Return the SHA-256 digest of a file without loading it all into memory."""
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_array(array: np.ndarray) -> str:
    """Hash an array's dtype, shape, and C-contiguous bytes deterministically."""
    contiguous = np.ascontiguousarray(array)
    digest = hashlib.sha256()
    digest.update(str(contiguous.dtype).encode("ascii"))
    digest.update(np.asarray(contiguous.shape, dtype="<i8").tobytes())
    digest.update(contiguous.tobytes(order="C"))
    return digest.hexdigest()


def write_processing_metadata(
    session_dir: Path,
    model_path: Path,
    delegate: str,
    stats: dict[str, object],
) -> Path:
    """Write the reproducibility sidecar for generated MediaPipe output."""
    metadata = {
        "schema_version": 3,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "mediapipe_version": getattr(mp, "__version__", "unknown"),
        "python_version": platform.python_version(),
        "delegate": delegate,
        "model_path": str(model_path),
        "model_sha256": sha256_file(model_path),
        **stats,
    }
    output_path = session_dir / "mediapipe_processing.json"
    output_path.write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return output_path


def load_timestamps(session_dir: Path) -> dict[int, float]:
    """Load frame_index -> timestamp mapping from timestamps.csv."""
    timestamps_path = session_dir / "timestamps.csv"
    if not timestamps_path.exists():
        return {}

    result: dict[int, float] = {}
    with open(timestamps_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            idx = int(row["frame_index"])
            ts = float(row["timestamp"])
            result[idx] = ts
    return result


def extract_frame_index(frame_path: Path) -> int:
    """Extract the numeric index from a frame filename like '000042.jpg'."""
    return int(frame_path.stem)


def write_csv(
    output_path: Path,
    rows: list[dict[str, object]],
    column_names: list[str],
) -> None:
    """Write blendshape data to CSV with consistent column ordering."""
    fieldnames = ["frame_index", "timestamp"] + column_names

    with open(output_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Run MediaPipe Face Landmarker on captured RGB frames.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "session_dir",
        type=Path,
        help="Path to a capture session directory containing frames/",
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=DEFAULT_MODEL_PATH,
        help="Path to face_landmarker.task model file",
    )
    parser.add_argument(
        "--delegate",
        choices=("cpu", "gpu"),
        default="cpu",
        help=(
            "MediaPipe inference delegate (default: cpu). CPU is the reproducible "
            "offline default and avoids Metal initialization failures on macOS."
        ),
    )
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()

    session_dir = args.session_dir.resolve()
    if not session_dir.is_dir():
        print(f"Error: {session_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    delegate = (
        mp_python.BaseOptions.Delegate.GPU
        if args.delegate == "gpu"
        else mp_python.BaseOptions.Delegate.CPU
    )
    model_path = args.model.resolve()
    landmarker = create_face_landmarker(model_path, delegate)
    try:
        stats = process_session(session_dir, landmarker)
    finally:
        landmarker.close()

    metadata_path = write_processing_metadata(
        session_dir, model_path, args.delegate, stats
    )
    print(f"Reproducibility metadata: {metadata_path}")


if __name__ == "__main__":
    main()
