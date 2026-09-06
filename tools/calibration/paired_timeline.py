#!/usr/bin/env python3
"""Create slide-ready paired ARKit/MediaPipe timelines for one session.

The plot and JSON summary describe agreement between two estimator outputs.
ARKit is treated as a reference signal, not as physical ground truth. The live
ARKit result is paired with MediaPipe run later on RGB saved from the same
capture callback; the two estimators did not receive an identical image input.

Example:
    python paired_timeline.py captures/session_20260817_001122 \\
      --blendshapes cheekPuff mouthPucker \\
      --window neutral:0:2 \\
      --window puff_1:2:5 \\
      --window puff_2:7:10 \\
      --output reports/cheek_puff

For authoritative visual-QC ranges, use ``--frame-window`` so rounded display
timestamps cannot exclude an endpoint frame.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from benchmark import (
    ALIGNMENT_TOLERANCE_SEC,
    canonicalize_blendshape_name,
    find_common_blendshapes,
    load_session,
)


DISCLAIMER = (
    "ARKit is a reference signal, not ground truth. Results describe agreement "
    "between paired estimator outputs and do not establish physical accuracy or "
    "causality. ARKit ran live; MediaPipe ran offline on RGB saved from the same "
    "capture callback, so the estimators did not receive an identical image input."
)


@dataclass(frozen=True)
class AnalysisWindow:
    """A named inclusive time window relative to the first aligned pair."""

    label: str
    start_seconds: float
    end_seconds: float


@dataclass(frozen=True)
class FrameAnalysisWindow:
    """A named inclusive window in exported frame-index space."""

    label: str
    start_frame: int
    end_frame: int


@dataclass(frozen=True)
class ResolvedAnalysisWindow:
    """Exact aligned rows and auditable metadata for one window."""

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
    positions: tuple[int, ...]


def parse_window(value: str) -> AnalysisWindow:
    """Parse ``LABEL:START:END`` into an analysis window."""
    parts = value.split(":")
    if len(parts) != 3:
        raise argparse.ArgumentTypeError(
            "window must use LABEL:START_SECONDS:END_SECONDS"
        )

    label = parts[0].strip()
    if not label:
        raise argparse.ArgumentTypeError("window label must not be empty")

    try:
        start_seconds = float(parts[1])
        end_seconds = float(parts[2])
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "window start and end must be numbers"
        ) from error

    if not math.isfinite(start_seconds) or not math.isfinite(end_seconds):
        raise argparse.ArgumentTypeError("window start and end must be finite")
    if start_seconds < 0:
        raise argparse.ArgumentTypeError("window start must be zero or greater")
    if end_seconds <= start_seconds:
        raise argparse.ArgumentTypeError("window end must be greater than start")

    return AnalysisWindow(label, start_seconds, end_seconds)


def parse_frame_window(value: str) -> FrameAnalysisWindow:
    """Parse ``LABEL:START_FRAME:END_FRAME`` with inclusive bounds."""
    parts = value.split(":")
    if len(parts) != 3:
        raise argparse.ArgumentTypeError(
            "frame window must use LABEL:START_FRAME:END_FRAME"
        )
    label = parts[0].strip()
    if not label:
        raise argparse.ArgumentTypeError("frame-window label must not be empty")
    try:
        start_frame = int(parts[1])
        end_frame = int(parts[2])
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "frame-window start and end must be integers"
        ) from error
    if start_frame < 0:
        raise argparse.ArgumentTypeError(
            "frame-window start must be zero or greater"
        )
    if end_frame < start_frame:
        raise argparse.ArgumentTypeError(
            "frame-window end must be greater than or equal to start"
        )
    return FrameAnalysisWindow(label, start_frame, end_frame)


def parse_blendshape_names(values: list[str]) -> list[str]:
    """Return unique canonical names, accepting spaces or commas as separators."""
    names: list[str] = []
    for value in values:
        for raw_name in value.split(","):
            raw_name = raw_name.strip()
            if not raw_name:
                continue
            name = canonicalize_blendshape_name(raw_name)
            if name not in names:
                names.append(name)

    if not names:
        raise ValueError("select at least one blendshape")
    if len(names) > 4:
        raise ValueError(
            "select at most four blendshapes per slide-ready plot; run the tool "
            "again for additional shapes"
        )
    return names


def sha256_file(path: Path) -> str:
    """Return a SHA-256 digest without changing the input file."""
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def finite_or_none(value: float) -> float | None:
    """Convert a numeric value to JSON-safe float or null."""
    return float(value) if math.isfinite(float(value)) else None


def signal_statistics(values: pd.Series, times: pd.Series) -> dict[str, Any]:
    """Summarize one estimator signal for a selected window."""
    numeric = values.astype(float)
    peak_position = int(np.argmax(numeric.to_numpy()))
    return {
        "mean": float(numeric.mean()),
        "median": float(numeric.median()),
        "minimum": float(numeric.min()),
        "maximum": float(numeric.max()),
        "range": float(numeric.max() - numeric.min()),
        "standard_deviation": float(numeric.std(ddof=0)),
        "peak_time_seconds": float(times.iloc[peak_position]),
    }


def agreement_statistics(
    arkit: pd.Series, mediapipe: pd.Series
) -> dict[str, Any]:
    """Summarize paired differences without treating either signal as truth."""
    arkit_values = arkit.astype(float)
    mediapipe_values = mediapipe.astype(float)
    differences = mediapipe_values - arkit_values
    absolute_differences = differences.abs()

    if arkit_values.std(ddof=0) < 1e-9 or mediapipe_values.std(ddof=0) < 1e-9:
        correlation: float | None = None
    else:
        correlation = finite_or_none(arkit_values.corr(mediapipe_values))

    return {
        "mean_absolute_difference": float(absolute_differences.mean()),
        "root_mean_square_difference": float(
            math.sqrt(float((differences**2).mean()))
        ),
        "maximum_absolute_difference": float(absolute_differences.max()),
        "mean_signed_difference_mediapipe_minus_arkit": float(differences.mean()),
        "pearson_correlation": correlation,
    }


def _validated_frame_index_column(
    values: pd.Series, source: str
) -> np.ndarray:
    """Return strict integer, unique, chronological frame indices."""
    numeric = pd.to_numeric(values, errors="coerce").to_numpy(dtype=np.float64)
    if not np.isfinite(numeric).all():
        raise ValueError(f"{source} frame_index contains non-finite values")
    rounded = np.rint(numeric)
    if not np.array_equal(numeric, rounded):
        raise ValueError(f"{source} frame_index contains non-integer values")
    indices = rounded.astype(np.int64)
    if np.any(indices < 0):
        raise ValueError(f"{source} frame_index contains negative values")
    if len(np.unique(indices)) != len(indices):
        raise ValueError(f"{source} aligned frame_index values must be unique")
    if np.any(np.diff(indices) <= 0):
        raise ValueError(
            f"{source} aligned frame_index values must be strictly increasing"
        )
    return indices


def resolve_aligned_frame_indices(
    session_dir: Path,
    arkit: pd.DataFrame,
    mediapipe: pd.DataFrame,
) -> tuple[np.ndarray, str]:
    """Resolve exported frame indices or prove strict row/frame correspondence."""
    candidates: list[tuple[str, np.ndarray]] = []
    if "frame_index" in arkit.columns:
        candidates.append(
            (
                "arkit_csv",
                _validated_frame_index_column(arkit["frame_index"], "ARKit"),
            )
        )
    if "frame_index" in mediapipe.columns:
        candidates.append(
            (
                "mediapipe_csv",
                _validated_frame_index_column(
                    mediapipe["frame_index"], "MediaPipe"
                ),
            )
        )
    if candidates:
        first_source, first_indices = candidates[0]
        for source, indices in candidates[1:]:
            if not np.array_equal(first_indices, indices):
                raise ValueError(
                    "aligned ARKit and MediaPipe frame_index columns disagree"
                )
            first_source = f"{first_source}+{source}"
        return first_indices, first_source

    arkit_source = pd.read_csv(
        session_dir / "arkit_blendshapes.csv", usecols=["timestamp"]
    )["timestamp"].astype(float)
    mediapipe_source = pd.read_csv(
        session_dir / "mediapipe_blendshapes.csv", usecols=["timestamp"]
    )["timestamp"].astype(float)
    if not (
        len(arkit_source)
        == len(mediapipe_source)
        == len(arkit)
        == len(mediapipe)
    ):
        raise ValueError(
            "frame_index is absent and strict row/frame correspondence cannot "
            "be proven because source/aligned row counts differ"
        )
    arkit_aligned = arkit["timestamp"].astype(float).to_numpy()
    mediapipe_aligned = mediapipe["timestamp"].astype(float).to_numpy()
    arkit_original = arkit_source.to_numpy()
    mediapipe_original = mediapipe_source.to_numpy()
    if not np.allclose(arkit_aligned, arkit_original, rtol=0.0, atol=1e-12):
        raise ValueError(
            "frame_index is absent and aligned ARKit rows are not strict source "
            "row correspondence"
        )
    if not np.allclose(
        mediapipe_aligned, mediapipe_original, rtol=0.0, atol=1e-12
    ):
        raise ValueError(
            "frame_index is absent and aligned MediaPipe rows are not strict "
            "source row correspondence"
        )
    if np.any(np.abs(arkit_original - mediapipe_original) > ALIGNMENT_TOLERANCE_SEC):
        raise ValueError(
            "frame_index is absent and same-row timestamp pairs exceed the "
            "alignment tolerance"
        )
    return np.arange(len(arkit), dtype=np.int64), "strict_row_correspondence"


def _resolved_seconds_window(
    spec: AnalysisWindow,
    times: pd.Series,
    frame_indices: np.ndarray,
) -> ResolvedAnalysisWindow:
    """Resolve a legacy seconds window to exact aligned positions."""
    duration_seconds = float(times.iloc[-1])
    if spec.start_seconds > duration_seconds:
        raise ValueError(
            f"analysis window {spec.label!r} starts after the aligned timeline "
            f"ends at {duration_seconds:.9f}s"
        )
    if spec.end_seconds > duration_seconds + 1e-6:
        raise ValueError(
            f"analysis window {spec.label!r} ends at {spec.end_seconds:.9f}s, "
            f"after the aligned timeline ends at {duration_seconds:.9f}s"
        )
    mask = (times >= spec.start_seconds) & (times <= spec.end_seconds)
    positions = tuple(int(value) for value in np.flatnonzero(mask.to_numpy()))
    if not positions:
        raise ValueError(
            f"analysis window {spec.label!r} contains no aligned pairs: "
            f"{spec.start_seconds:g}-{spec.end_seconds:g}s"
        )
    start_frame = int(frame_indices[positions[0]])
    end_frame = int(frame_indices[positions[-1]])
    return ResolvedAnalysisWindow(
        label=spec.label,
        source="seconds",
        start_seconds=float(spec.start_seconds),
        end_seconds=float(spec.end_seconds),
        actual_start_seconds=float(times.iloc[positions[0]]),
        actual_end_seconds=float(times.iloc[positions[-1]]),
        start_frame=start_frame,
        end_frame=end_frame,
        expected_frame_count=end_frame - start_frame + 1,
        actual_frame_count=len(positions),
        positions=positions,
    )


def _resolved_frame_window(
    spec: FrameAnalysisWindow,
    times: pd.Series,
    frame_indices: np.ndarray,
) -> ResolvedAnalysisWindow:
    """Resolve an inclusive frame range, rejecting gaps and range errors."""
    available_start = int(frame_indices[0])
    available_end = int(frame_indices[-1])
    if spec.start_frame < available_start or spec.end_frame > available_end:
        raise ValueError(
            f"frame window {spec.label!r} requests "
            f"{spec.start_frame}:{spec.end_frame} outside available frame range "
            f"{available_start}:{available_end}"
        )
    expected = spec.end_frame - spec.start_frame + 1
    mask = (frame_indices >= spec.start_frame) & (frame_indices <= spec.end_frame)
    positions = tuple(int(value) for value in np.flatnonzero(mask))
    selected = frame_indices[np.asarray(positions, dtype=np.intp)]
    actual = len(positions)
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
            f"frame window {spec.label!r} expects {expected} pairs but selected "
            f"{actual}; missing frame indices: {preview}{suffix}"
        )
    actual_start = float(times.iloc[positions[0]])
    actual_end = float(times.iloc[positions[-1]])
    return ResolvedAnalysisWindow(
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
        positions=positions,
    )


def resolve_windows(
    seconds_windows: list[AnalysisWindow],
    frame_windows: list[FrameAnalysisWindow],
    times: pd.Series,
    frame_indices: np.ndarray,
) -> list[ResolvedAnalysisWindow]:
    """Resolve compatible window forms and reject labels repeated across forms."""
    resolved = [
        _resolved_seconds_window(window, times, frame_indices)
        for window in seconds_windows
    ]
    resolved.extend(
        _resolved_frame_window(window, times, frame_indices)
        for window in frame_windows
    )
    seen: set[str] = set()
    for window in resolved:
        if window.label in seen:
            raise ValueError(f"duplicate analysis-window label: {window.label}")
        seen.add(window.label)
    return resolved


def window_json(window: ResolvedAnalysisWindow) -> dict[str, Any]:
    """Serialize selection metadata without internal row positions."""
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


def summarize_window(
    window: ResolvedAnalysisWindow,
    times: pd.Series,
    arkit: pd.DataFrame,
    mediapipe: pd.DataFrame,
    blendshapes: list[str],
) -> dict[str, Any]:
    """Create JSON-ready per-shape statistics for one inclusive window."""
    positions = np.asarray(window.positions, dtype=np.intp)

    selected_times = times.iloc[positions].reset_index(drop=True)
    selected_arkit = arkit.iloc[positions].reset_index(drop=True)
    selected_mediapipe = mediapipe.iloc[positions].reset_index(drop=True)

    shape_summaries: dict[str, Any] = {}
    for blendshape in blendshapes:
        arkit_values = selected_arkit[blendshape]
        mediapipe_values = selected_mediapipe[blendshape]
        shape_summaries[blendshape] = {
            "arkit_reference": signal_statistics(arkit_values, selected_times),
            "mediapipe_offline": signal_statistics(
                mediapipe_values, selected_times
            ),
            "paired_agreement": agreement_statistics(
                arkit_values, mediapipe_values
            ),
        }

    return {
        **window_json(window),
        "aligned_pair_count": int(len(positions)),
        "blendshapes": shape_summaries,
    }


def generate_summary(
    session_dir: Path,
    arkit: pd.DataFrame,
    mediapipe: pd.DataFrame,
    times: pd.Series,
    frame_indices: np.ndarray,
    frame_index_source: str,
    blendshapes: list[str],
    windows: list[ResolvedAnalysisWindow],
    plot_files: list[str],
) -> dict[str, Any]:
    """Build the machine-readable summary document."""
    arkit_source_rows = len(
        pd.read_csv(session_dir / "arkit_blendshapes.csv", usecols=["timestamp"])
    )
    mediapipe_source_rows = len(
        pd.read_csv(session_dir / "mediapipe_blendshapes.csv", usecols=["timestamp"])
    )
    timestamp_delta = (
        mediapipe["timestamp"].astype(float) - arkit["timestamp"].astype(float)
    ).abs()
    duration_seconds = float(times.iloc[-1])

    full_positions = tuple(range(len(times)))
    full_window = ResolvedAnalysisWindow(
        label="full_session",
        source="aligned_full_session",
        start_seconds=0.0,
        end_seconds=duration_seconds,
        actual_start_seconds=0.0,
        actual_end_seconds=duration_seconds,
        start_frame=int(frame_indices[0]),
        end_frame=int(frame_indices[-1]),
        expected_frame_count=int(frame_indices[-1] - frame_indices[0] + 1),
        actual_frame_count=len(full_positions),
        positions=full_positions,
    )
    all_windows = [
        summarize_window(
            full_window,
            times,
            arkit,
            mediapipe,
            blendshapes,
        )
    ]
    all_windows.extend(
        summarize_window(
            window,
            times,
            arkit,
            mediapipe,
            blendshapes,
        )
        for window in windows
    )

    return {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "disclaimer": DISCLAIMER,
        "session": {
            "name": session_dir.name,
            "duration_seconds": duration_seconds,
            "selected_blendshapes": blendshapes,
            "source_files": {
                "arkit": {
                    "name": "arkit_blendshapes.csv",
                    "sha256": sha256_file(session_dir / "arkit_blendshapes.csv"),
                },
                "mediapipe": {
                    "name": "mediapipe_blendshapes.csv",
                    "sha256": sha256_file(
                        session_dir / "mediapipe_blendshapes.csv"
                    ),
                },
            },
        },
        "alignment": {
            "method": "nearest timestamp",
            "tolerance_seconds": ALIGNMENT_TOLERANCE_SEC,
            "frame_index_source": frame_index_source,
            "relative_time_origin": "first aligned pair; pair time is the midpoint of the two timestamps",
            "arkit_source_row_count": arkit_source_rows,
            "mediapipe_source_row_count": mediapipe_source_rows,
            "aligned_pair_count": int(len(arkit)),
            "mediapipe_alignment_coverage": float(
                len(arkit) / mediapipe_source_rows
            ),
            "mean_absolute_timestamp_delta_milliseconds": float(
                timestamp_delta.mean() * 1000.0
            ),
            "median_absolute_timestamp_delta_milliseconds": float(
                timestamp_delta.median() * 1000.0
            ),
            "maximum_absolute_timestamp_delta_milliseconds": float(
                timestamp_delta.max() * 1000.0
            ),
        },
        "analysis_windows": all_windows,
        "plot_files": plot_files,
    }


def create_plot(
    output_dir: Path,
    title: str,
    arkit: pd.DataFrame,
    mediapipe: pd.DataFrame,
    times: pd.Series,
    blendshapes: list[str],
    windows: list[ResolvedAnalysisWindow],
    formats: list[str],
    dpi: int,
) -> list[Path]:
    """Render a 16:9 multi-panel timeline in the requested formats."""
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError as error:
        raise RuntimeError(
            "matplotlib is required; install tools/calibration/requirements.txt"
        ) from error

    colors = {
        "arkit": "#FF4F87",
        "mediapipe": "#6548E8",
    }
    window_colors = ["#F3A71A", "#6548E8", "#2A9D76", "#168AAD"]
    figure, axes_value = plt.subplots(
        len(blendshapes),
        1,
        sharex=True,
        figsize=(12.8, 7.2),
    )
    axes = np.atleast_1d(axes_value)

    for axis, blendshape in zip(axes, blendshapes, strict=True):
        arkit_values = arkit[blendshape].astype(float)
        mediapipe_values = mediapipe[blendshape].astype(float)
        agreement = agreement_statistics(arkit_values, mediapipe_values)

        axis.plot(
            times,
            arkit_values,
            color=colors["arkit"],
            linewidth=2.0,
            label="ARKit (reference)",
        )
        axis.plot(
            times,
            mediapipe_values,
            color=colors["mediapipe"],
            linewidth=2.0,
            linestyle=(0, (5, 2)),
            label="MediaPipe (offline)",
        )
        for window_index, window in enumerate(windows):
            axis.axvspan(
                window.start_seconds,
                window.end_seconds,
                color=window_colors[window_index % len(window_colors)],
                alpha=0.07,
                linewidth=0,
            )

        correlation = agreement["pearson_correlation"]
        correlation_text = "n/a" if correlation is None else f"{correlation:.2f}"
        axis.text(
            0.99,
            0.91,
            "Mean |difference| "
            f"{agreement['mean_absolute_difference']:.3f}  •  "
            f"r {correlation_text}",
            transform=axis.transAxes,
            ha="right",
            va="top",
            fontsize=10.5,
            color="#4A4A50",
            bbox={
                "boxstyle": "round,pad=0.3",
                "facecolor": "white",
                "edgecolor": "none",
                "alpha": 0.82,
            },
        )
        axis.set_ylabel(blendshape, fontsize=11.5, fontweight="bold")
        axis.set_ylim(-0.02, 1.02)
        axis.set_yticks([0.0, 0.5, 1.0])
        axis.grid(axis="both", color="#D9D9E0", linewidth=0.8, alpha=0.7)
        axis.spines[["top", "right"]].set_visible(False)
        axis.spines[["left", "bottom"]].set_color("#A8A8B0")
        axis.tick_params(colors="#55555D", labelsize=10)

    if windows:
        top_axis = axes[0]
        for window_index, window in enumerate(windows):
            midpoint = (window.start_seconds + window.end_seconds) / 2.0
            top_axis.text(
                midpoint,
                1.015,
                window.label.replace("_", " "),
                transform=top_axis.get_xaxis_transform(),
                ha="center",
                va="bottom",
                fontsize=9.5,
                color=window_colors[window_index % len(window_colors)],
                fontweight="bold",
                clip_on=False,
            )

    axes[-1].set_xlabel(
        "Seconds from first aligned pair",
        fontsize=11.5,
        fontweight="bold",
    )
    axes[-1].set_xlim(float(times.iloc[0]), float(times.iloc[-1]))

    handles, labels = axes[0].get_legend_handles_labels()
    figure.legend(
        handles,
        labels,
        loc="upper right",
        bbox_to_anchor=(0.98, 0.955),
        frameon=False,
        fontsize=11.5,
        ncol=2,
    )
    figure.suptitle(
        title,
        x=0.085,
        y=0.965,
        ha="left",
        fontsize=22,
        fontweight="bold",
        color="#17171B",
    )
    figure.text(
        0.085,
        0.918,
        f"{len(arkit):,} aligned pairs  •  coefficient 0–1 "
        "(larger means stronger estimator activation)",
        ha="left",
        va="top",
        fontsize=11.5,
        color="#55555D",
    )
    figure.text(
        0.085,
        0.025,
        "Paired estimator outputs; ARKit is a reference signal, not ground truth. "
        "Windows are analyst-specified.",
        ha="left",
        va="bottom",
        fontsize=9.5,
        color="#66666E",
    )
    figure.subplots_adjust(
        left=0.105,
        right=0.975,
        top=0.845,
        bottom=0.115,
        hspace=0.25,
    )

    output_paths: list[Path] = []
    for output_format in formats:
        output_path = output_dir / f"paired_timeline.{output_format}"
        save_options: dict[str, Any] = {}
        if output_format == "png":
            save_options["dpi"] = dpi
        figure.savefig(output_path, **save_options)
        output_paths.append(output_path)

    plt.close(figure)
    return output_paths


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description=(
            "Generate a paired ARKit/MediaPipe timeline and JSON summary for "
            "one processed capture session."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "session",
        type=Path,
        help="Processed session containing ARKit and MediaPipe CSV files",
    )
    parser.add_argument(
        "--blendshapes",
        nargs="+",
        required=True,
        metavar="NAME",
        help="One to four canonical or raw ARKit blendshape names",
    )
    parser.add_argument(
        "--window",
        action="append",
        type=parse_window,
        default=[],
        metavar="LABEL:START:END",
        help=(
            "Inclusive analyst-specified window in seconds from the first "
            "aligned pair; repeat for multiple windows"
        ),
    )
    parser.add_argument(
        "--frame-window",
        action="append",
        type=parse_frame_window,
        default=[],
        metavar="LABEL:START:END",
        help=(
            "Inclusive analyst-specified window in exported frame-index "
            "space; repeatable and preferred for authoritative QC"
        ),
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Directory for paired_timeline files and paired_summary.json",
    )
    parser.add_argument(
        "--title",
        default="Paired face-estimator response over time",
        help="Plot title",
    )
    parser.add_argument(
        "--formats",
        nargs="+",
        choices=("png", "svg"),
        default=["png", "svg"],
        help="Plot formats (default: png svg)",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=160,
        help="PNG resolution in dots per inch (default: 160)",
    )
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()
    try:
        blendshapes = parse_blendshape_names(args.blendshapes)
        session_dir = args.session.resolve()
        if not session_dir.is_dir():
            raise ValueError(f"session is not a directory: {session_dir}")
        if args.dpi < 72:
            raise ValueError("--dpi must be at least 72")

        aligned = load_session(session_dir)
        if aligned is None:
            raise ValueError(
                "session must contain readable arkit_blendshapes.csv and "
                "mediapipe_blendshapes.csv with alignable timestamps"
            )
        arkit, mediapipe = aligned

        common_blendshapes = find_common_blendshapes(arkit, mediapipe)
        missing = [name for name in blendshapes if name not in common_blendshapes]
        if missing:
            raise ValueError(
                "blendshape(s) unavailable in both sources after canonicalization: "
                f"{', '.join(missing)}; available: "
                f"{', '.join(common_blendshapes)}"
            )

        paired_timestamps = (
            arkit["timestamp"].astype(float) + mediapipe["timestamp"].astype(float)
        ) / 2.0
        times = (paired_timestamps - paired_timestamps.iloc[0]).reset_index(drop=True)
        frame_indices, frame_index_source = resolve_aligned_frame_indices(
            session_dir, arkit, mediapipe
        )
        windows = resolve_windows(
            args.window,
            args.frame_window,
            times,
            frame_indices,
        )

        output_dir = args.output.resolve()
        output_dir.mkdir(parents=True, exist_ok=True)
        plot_paths = create_plot(
            output_dir,
            args.title,
            arkit,
            mediapipe,
            times,
            blendshapes,
            windows,
            list(dict.fromkeys(args.formats)),
            args.dpi,
        )
        summary = generate_summary(
            session_dir,
            arkit,
            mediapipe,
            times,
            frame_indices,
            frame_index_source,
            blendshapes,
            windows,
            [path.name for path in plot_paths],
        )
        summary_path = output_dir / "paired_summary.json"
        summary_path.write_text(
            json.dumps(summary, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
            encoding="utf-8",
        )
    except (OSError, RuntimeError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)

    print(f"Aligned pairs: {len(arkit)}")
    print(f"Blendshapes: {', '.join(blendshapes)}")
    for path in plot_paths:
        print(f"Plot written to {path}")
    print(f"Summary written to {summary_path}")


if __name__ == "__main__":
    main()
