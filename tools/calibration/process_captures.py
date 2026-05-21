#!/usr/bin/env python3
"""
Process captured RGB frames with MediaPipe Face Landmarker.

Reads JPEG frames from a capture session directory and produces
mediapipe_blendshapes.csv alongside the existing arkit_blendshapes.csv,
enabling paired ARKit-vs-MediaPipe comparison.

Usage:
    python process_captures.py <session_dir>
    python process_captures.py <session_dir> --model path/to/face_landmarker.task

Requirements:
    pip install -r requirements.txt
    Download the face landmarker model:
        wget -O face_landmarker_v2.task \\
          https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
"""

from __future__ import annotations

import argparse
import csv
import sys
import time
from pathlib import Path

import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision as mp_vision

# Default model path relative to this script
DEFAULT_MODEL_PATH = Path(__file__).parent / "face_landmarker_v2.task"


def create_face_landmarker(model_path: Path) -> mp_vision.FaceLandmarker:
    """Create a MediaPipe FaceLandmarker from the given model file."""
    if not model_path.exists():
        print(f"Error: Model file not found at {model_path}", file=sys.stderr)
        print(
            "Download it with:\n"
            "  wget -O face_landmarker_v2.task \\\n"
            "    https://storage.googleapis.com/mediapipe-models/"
            "face_landmarker/face_landmarker/float16/latest/"
            "face_landmarker.task",
            file=sys.stderr,
        )
        sys.exit(1)

    base_options = mp_python.BaseOptions(model_asset_path=str(model_path))
    options = mp_vision.FaceLandmarkerOptions(
        base_options=base_options,
        output_face_blendshapes=True,
        output_facial_transformation_matrixes=False,
        num_faces=1,
    )
    return mp_vision.FaceLandmarker.create_from_options(options)


def process_session(session_dir: Path, landmarker: mp_vision.FaceLandmarker) -> None:
    """Process all frames in a session directory and write mediapipe_blendshapes.csv."""
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
    column_names: list[str] | None = None
    skipped = 0

    for i, frame_path in enumerate(frame_files):
        image = mp.Image.create_from_file(str(frame_path))
        result = landmarker.detect(image)

        if not result.face_blendshapes or len(result.face_blendshapes) == 0:
            skipped += 1
            continue

        blendshapes = result.face_blendshapes[0]
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

    print(
        f"Done. {len(rows)} frames processed, {skipped} skipped "
        f"({elapsed:.1f}s). Output: {output_path}"
    )


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
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()

    session_dir = args.session_dir.resolve()
    if not session_dir.is_dir():
        print(f"Error: {session_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    landmarker = create_face_landmarker(args.model.resolve())
    try:
        process_session(session_dir, landmarker)
    finally:
        landmarker.close()


if __name__ == "__main__":
    main()
