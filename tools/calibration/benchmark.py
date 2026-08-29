#!/usr/bin/env python3
"""
Per-blendshape agreement benchmark comparing paired ARKit and MediaPipe output.

Loads paired CSVs from capture sessions, aligns timestamps, computes
per-blendshape difference metrics, and generates a ranked agreement report.

ARKit is a reference signal here, not ground truth. Its live result is paired
with RGB saved from the same ``didUpdate`` callback and processed by MediaPipe
later; this does not mean identical RGB input was fed to both estimators.

Usage:
    python benchmark.py --captures captures/ --output reports/
    python benchmark.py --captures captures/ --output reports/ --plots

Requirements:
    pip install -r requirements.txt
"""

from __future__ import annotations

import argparse
import math
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd

# Agreement review thresholds (by mean absolute difference).
# These are triage bands, not validated accuracy grades.
THRESHOLD_CLOSE = 0.03
THRESHOLD_MODERATE = 0.08
THRESHOLD_LARGE = 0.15

# Low-correlation review threshold (by Pearson correlation).
THRESHOLD_LOW_CORRELATION = 0.5

# Timestamp alignment tolerance in seconds (~1 frame at 60fps)
ALIGNMENT_TOLERANCE_SEC = 0.016

# Columns to exclude from blendshape comparison
METADATA_COLUMNS = {"timestamp", "frame_index", "_neutral"}
PRESENCE_METADATA_COLUMNS = {"timestamp", "frame_index"}


def canonicalize_blendshape_name(name: str) -> str:
    """Convert ARKit's raw ``_L``/``_R`` suffixes to MediaPipe names.

    ``ARFaceAnchor.BlendShapeLocation.rawValue`` uses names such as
    ``browDown_L`` while MediaPipe emits ``browDownLeft``. Non-sided names and
    metadata columns are returned unchanged.
    """
    if name.endswith("_L"):
        return f"{name[:-2]}Left"
    if name.endswith("_R"):
        return f"{name[:-2]}Right"
    return name


def canonicalize_blendshape_columns(
    frame: pd.DataFrame, source: str
) -> pd.DataFrame:
    """Return a copy whose blendshape columns use the shared canonical schema."""
    canonical_columns = [canonicalize_blendshape_name(name) for name in frame.columns]
    duplicates = sorted(
        {
            name
            for index, name in enumerate(canonical_columns)
            if name in canonical_columns[:index]
        }
    )
    if duplicates:
        duplicate_list = ", ".join(duplicates)
        raise ValueError(
            f"{source} columns collide after canonicalization: {duplicate_list}"
        )

    canonical = frame.copy()
    canonical.columns = canonical_columns
    return canonical


def load_session(
    session_dir: Path,
) -> tuple[pd.DataFrame, pd.DataFrame] | None:
    """Load and align ARKit and MediaPipe CSVs from a session directory.

    Returns aligned (arkit, mediapipe) DataFrames, or None if files are missing.
    """
    arkit_path = session_dir / "arkit_blendshapes.csv"
    mediapipe_path = session_dir / "mediapipe_blendshapes.csv"

    if not arkit_path.exists() or not mediapipe_path.exists():
        return None

    try:
        arkit = canonicalize_blendshape_columns(pd.read_csv(arkit_path), "ARKit")
        mediapipe = canonicalize_blendshape_columns(
            pd.read_csv(mediapipe_path), "MediaPipe"
        )
    except ValueError as error:
        print(f"  Warning: {error} in {session_dir.name}, skipping", file=sys.stderr)
        return None

    if "timestamp" not in arkit.columns or "timestamp" not in mediapipe.columns:
        print(
            f"  Warning: missing timestamp column in {session_dir.name}, skipping",
            file=sys.stderr,
        )
        return None

    # Align by nearest timestamp within tolerance
    aligned_arkit_rows = []
    aligned_mediapipe_rows = []

    arkit_timestamps = arkit["timestamp"].values
    mediapipe_timestamps = mediapipe["timestamp"].values

    for mp_idx, mp_ts in enumerate(mediapipe_timestamps):
        diffs = np.abs(arkit_timestamps - mp_ts)
        nearest_idx = int(np.argmin(diffs))
        if diffs[nearest_idx] <= ALIGNMENT_TOLERANCE_SEC:
            aligned_arkit_rows.append(nearest_idx)
            aligned_mediapipe_rows.append(mp_idx)

    if not aligned_arkit_rows:
        print(
            f"  Warning: no aligned frames in {session_dir.name}, skipping",
            file=sys.stderr,
        )
        return None

    aligned_arkit = arkit.iloc[aligned_arkit_rows].reset_index(drop=True)
    aligned_mediapipe = mediapipe.iloc[aligned_mediapipe_rows].reset_index(drop=True)

    return aligned_arkit, aligned_mediapipe


def find_common_blendshapes(
    arkit: pd.DataFrame, mediapipe: pd.DataFrame
) -> list[str]:
    """Find blendshape columns present in both DataFrames."""
    arkit_cols = set(arkit.columns) - METADATA_COLUMNS
    mediapipe_cols = set(mediapipe.columns) - METADATA_COLUMNS
    common = sorted(arkit_cols & mediapipe_cols)
    return common


def compute_metrics(arkit: pd.Series, mediapipe: pd.Series) -> dict[str, float]:
    """Compute per-blendshape agreement metrics between paired series."""
    errors = (arkit - mediapipe).astype(float)
    abs_errors = errors.abs()

    mae = float(abs_errors.mean())
    rmse = float(math.sqrt((errors**2).mean()))
    max_error = float(abs_errors.max())

    arkit_range = float(arkit.max() - arkit.min())
    mediapipe_range = float(mediapipe.max() - mediapipe.min())
    range_ratio = mediapipe_range / arkit_range if arkit_range > 1e-9 else 0.0

    # Pearson correlation (handle constant series)
    if arkit.std() < 1e-9 or mediapipe.std() < 1e-9:
        correlation = 0.0
    else:
        correlation = float(arkit.corr(mediapipe))
        if math.isnan(correlation):
            correlation = 0.0

    return {
        "mae": mae,
        "rmse": rmse,
        "correlation": correlation,
        "max_error": max_error,
        "arkit_range": arkit_range,
        "mediapipe_range": mediapipe_range,
        "range_ratio": range_ratio,
    }


def classify_agreement(mae: float, correlation: float) -> str:
    """Assign a descriptive review band without claiming physical accuracy."""
    if correlation < THRESHOLD_LOW_CORRELATION:
        return "low-correlation"
    if mae < THRESHOLD_CLOSE:
        return "close"
    if mae < THRESHOLD_MODERATE:
        return "moderate-difference"
    if mae < THRESHOLD_LARGE:
        return "large-difference"
    return "very-large-difference"


def run_benchmark(
    captures_dir: Path,
) -> tuple[dict[str, dict[str, float]], int, int, set[str], set[str]]:
    """Run benchmark across all sessions in the captures directory.

    Returns:
        (metrics_by_blendshape, total_sessions, total_frames,
         arkit_only_columns, mediapipe_only_columns)
    """
    session_dirs = sorted(
        d for d in captures_dir.iterdir() if d.is_dir() and d.name.startswith("session_")
    )

    if not session_dirs:
        print(f"Error: no session_* directories found in {captures_dir}", file=sys.stderr)
        sys.exit(1)

    # Accumulate per-blendshape paired values across all sessions
    all_arkit: dict[str, list[float]] = {}
    all_mediapipe: dict[str, list[float]] = {}
    total_sessions = 0
    total_frames = 0
    arkit_only_columns: set[str] = set()
    mediapipe_only_columns: set[str] = set()

    for session_dir in session_dirs:
        print(f"Loading {session_dir.name}...")
        result = load_session(session_dir)
        if result is None:
            continue

        arkit_df, mediapipe_df = result
        blendshapes = find_common_blendshapes(arkit_df, mediapipe_df)
        arkit_columns = set(arkit_df.columns) - PRESENCE_METADATA_COLUMNS
        mediapipe_columns = set(mediapipe_df.columns) - PRESENCE_METADATA_COLUMNS
        arkit_only_columns.update(arkit_columns - mediapipe_columns)
        mediapipe_only_columns.update(mediapipe_columns - arkit_columns)

        for bs in blendshapes:
            if bs not in all_arkit:
                all_arkit[bs] = []
                all_mediapipe[bs] = []
            all_arkit[bs].extend(arkit_df[bs].tolist())
            all_mediapipe[bs].extend(mediapipe_df[bs].tolist())

        total_sessions += 1
        total_frames += len(arkit_df)
        print(f"  Aligned {len(arkit_df)} frames, {len(blendshapes)} blendshapes")

    if total_sessions == 0:
        print("Error: no valid sessions found", file=sys.stderr)
        sys.exit(1)

    # Compute metrics per blendshape
    metrics: dict[str, dict[str, float]] = {}
    for bs in sorted(all_arkit.keys()):
        arkit_series = pd.Series(all_arkit[bs], dtype=float)
        mediapipe_series = pd.Series(all_mediapipe[bs], dtype=float)
        metrics[bs] = compute_metrics(arkit_series, mediapipe_series)

    print(f"\nProcessed {total_sessions} sessions, {total_frames} total frames")
    return (
        metrics,
        total_sessions,
        total_frames,
        arkit_only_columns,
        mediapipe_only_columns,
    )


def generate_report(
    metrics: dict[str, dict[str, float]],
    total_sessions: int,
    total_frames: int,
    output_dir: Path,
    arkit_only_columns: set[str] | None = None,
    mediapipe_only_columns: set[str] | None = None,
) -> Path:
    """Generate agreement_report.md in the output directory."""
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = output_dir / "agreement_report.md"

    # Rank by MAE (worst first)
    ranked = sorted(metrics.items(), key=lambda x: x[1]["mae"], reverse=True)

    # Classify all blendshapes
    categories: dict[str, list[str]] = {
        "low-correlation": [],
        "close": [],
        "moderate-difference": [],
        "large-difference": [],
        "very-large-difference": [],
    }
    for bs, m in ranked:
        cat = classify_agreement(m["mae"], m["correlation"])
        categories[cat].append(bs)

    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    lines: list[str] = []
    lines.append("# ARKit vs MediaPipe Blendshape Agreement Report")
    lines.append("")
    lines.append(
        "> ARKit is the reference signal, not ground truth. These metrics describe "
        "agreement on paired frames and do not establish either system's physical accuracy."
    )
    lines.append("")
    lines.append("## Summary")
    lines.append("")
    lines.append(f"- **Date**: {now}")
    lines.append(f"- **Sessions**: {total_sessions}")
    lines.append(f"- **Total aligned frames**: {total_frames}")
    lines.append(f"- **Blendshapes compared**: {len(metrics)}")
    lines.append("")

    arkit_only = sorted(arkit_only_columns or set())
    mediapipe_only = sorted(mediapipe_only_columns or set())
    lines.append("### Output Schema Differences")
    lines.append("")
    lines.append(
        "These are presence differences, not accuracy judgments. "
        "Only columns shared by both systems are compared below."
    )
    lines.append("")
    lines.append(
        "- **ARKit only**: "
        + (", ".join(f"`{name}`" for name in arkit_only) if arkit_only else "*(none)*")
    )
    lines.append(
        "- **MediaPipe only**: "
        + (
            ", ".join(f"`{name}`" for name in mediapipe_only)
            if mediapipe_only
            else "*(none)*"
        )
    )
    lines.append("")

    # Category counts
    lines.append("### Category Distribution")
    lines.append("")
    lines.append("| Category | Count | Criteria |")
    lines.append("|---|---|---|")
    lines.append(
        f"| Low correlation (manual review) | {len(categories['low-correlation'])} "
        f"| correlation < {THRESHOLD_LOW_CORRELATION} |"
    )
    lines.append(
        f"| Close | {len(categories['close'])} "
        f"| mean absolute difference < {THRESHOLD_CLOSE} |"
    )
    lines.append(
        f"| Moderate difference | {len(categories['moderate-difference'])} "
        f"| mean absolute difference {THRESHOLD_CLOSE}-{THRESHOLD_MODERATE} |"
    )
    lines.append(
        f"| Large difference | {len(categories['large-difference'])} "
        f"| mean absolute difference {THRESHOLD_MODERATE}-{THRESHOLD_LARGE} |"
    )
    lines.append(
        f"| Very large difference | {len(categories['very-large-difference'])} "
        f"| mean absolute difference >= {THRESHOLD_LARGE} |"
    )
    lines.append("")

    # Full ranked table
    lines.append("## Ranked Blendshapes (largest mean absolute difference first)")
    lines.append("")
    lines.append("| Rank | Blendshape | MAE | RMSE | Correlation | Max Error "
                 "| ARKit Range | MP Range | Range Ratio | Category |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|")
    for rank, (bs, m) in enumerate(ranked, start=1):
        cat = classify_agreement(m["mae"], m["correlation"])
        lines.append(
            f"| {rank} | {bs} | {m['mae']:.4f} | {m['rmse']:.4f} "
            f"| {m['correlation']:.3f} | {m['max_error']:.4f} "
            f"| {m['arkit_range']:.3f} | {m['mediapipe_range']:.3f} "
            f"| {m['range_ratio']:.2f} | {cat} |"
        )
    lines.append("")

    # Per-category lists
    lines.append("## Blendshapes by Category")
    lines.append("")

    category_descriptions = {
        "low-correlation": "Low correlation (manual review)",
        "close": "Close",
        "moderate-difference": "Moderate difference",
        "large-difference": "Large difference",
        "very-large-difference": "Very large difference",
    }

    for cat_key in [
        "low-correlation",
        "close",
        "moderate-difference",
        "large-difference",
        "very-large-difference",
    ]:
        shapes = categories[cat_key]
        lines.append(f"### {category_descriptions[cat_key]} ({len(shapes)})")
        lines.append("")
        if shapes:
            for bs in sorted(shapes):
                m = metrics[bs]
                lines.append(f"- `{bs}` (MAE: {m['mae']:.4f}, r={m['correlation']:.3f})")
        else:
            lines.append("*(none)*")
        lines.append("")

    report_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"Report written to {report_path}")
    return report_path


def generate_plots(
    metrics: dict[str, dict[str, float]],
    captures_dir: Path,
    output_dir: Path,
) -> None:
    """Generate scatter plots for each blendshape (ARKit vs MediaPipe)."""
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print(
            "Error: matplotlib is required for --plots. "
            "Install with: pip install matplotlib",
            file=sys.stderr,
        )
        sys.exit(1)

    plots_dir = output_dir / "plots"
    plots_dir.mkdir(parents=True, exist_ok=True)

    # Reload all session data for plotting
    session_dirs = sorted(
        d for d in captures_dir.iterdir() if d.is_dir() and d.name.startswith("session_")
    )

    all_arkit: dict[str, list[float]] = {}
    all_mediapipe: dict[str, list[float]] = {}

    for session_dir in session_dirs:
        result = load_session(session_dir)
        if result is None:
            continue
        arkit_df, mediapipe_df = result
        blendshapes = find_common_blendshapes(arkit_df, mediapipe_df)
        for bs in blendshapes:
            if bs not in all_arkit:
                all_arkit[bs] = []
                all_mediapipe[bs] = []
            all_arkit[bs].extend(arkit_df[bs].tolist())
            all_mediapipe[bs].extend(mediapipe_df[bs].tolist())

    ranked = sorted(metrics.items(), key=lambda x: x[1]["mae"], reverse=True)

    print(f"Generating {len(ranked)} scatter plots...")

    for bs, m in ranked:
        if bs not in all_arkit:
            continue

        arkit_vals = np.array(all_arkit[bs])
        mp_vals = np.array(all_mediapipe[bs])

        fig, ax = plt.subplots(figsize=(6, 6))

        ax.scatter(arkit_vals, mp_vals, alpha=0.15, s=4, color="#4A90D9")

        # Diagonal reference line
        lo = min(arkit_vals.min(), mp_vals.min())
        hi = max(arkit_vals.max(), mp_vals.max())
        ax.plot([lo, hi], [lo, hi], "r--", linewidth=1, alpha=0.7, label="y=x")

        cat = classify_agreement(m["mae"], m["correlation"])
        ax.set_title(f"{bs}\nMAE={m['mae']:.4f}  r={m['correlation']:.3f}  [{cat}]")
        ax.set_xlabel("ARKit reference")
        ax.set_ylabel("MediaPipe")
        ax.legend(loc="upper left")
        ax.set_aspect("equal", adjustable="box")

        fig.tight_layout()
        fig.savefig(plots_dir / f"{bs}.png", dpi=100)
        plt.close(fig)

    print(f"Plots saved to {plots_dir}/")


def parse_args() -> argparse.Namespace:
    """Parse command-line arguments."""
    parser = argparse.ArgumentParser(
        description="Per-blendshape agreement benchmark: ARKit vs MediaPipe.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "--captures",
        type=Path,
        default=Path("captures"),
        help="Directory containing session_* subdirectories (default: captures/)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("tools/calibration/reports"),
        help="Output directory for report and plots (default: tools/calibration/reports/)",
    )
    parser.add_argument(
        "--plots",
        action="store_true",
        help="Generate scatter plots (requires matplotlib)",
    )
    return parser.parse_args()


def main() -> None:
    """Entry point."""
    args = parse_args()

    captures_dir = args.captures.resolve()
    if not captures_dir.is_dir():
        print(f"Error: {captures_dir} is not a directory", file=sys.stderr)
        sys.exit(1)

    output_dir = args.output.resolve()

    (
        metrics,
        total_sessions,
        total_frames,
        arkit_only_columns,
        mediapipe_only_columns,
    ) = run_benchmark(captures_dir)
    generate_report(
        metrics,
        total_sessions,
        total_frames,
        output_dir,
        arkit_only_columns,
        mediapipe_only_columns,
    )

    if args.plots:
        generate_plots(metrics, captures_dir, output_dir)

    # Print the top-10 largest differences to stdout.
    ranked = sorted(metrics.items(), key=lambda x: x[1]["mae"], reverse=True)
    print("\nTop 10 largest blendshape differences by MAE:")
    print(f"{'Rank':<5} {'Blendshape':<30} {'MAE':<8} {'Corr':<8} {'Category'}")
    print("-" * 70)
    for i, (bs, m) in enumerate(ranked[:10], start=1):
        cat = classify_agreement(m["mae"], m["correlation"])
        print(f"{i:<5} {bs:<30} {m['mae']:<8.4f} {m['correlation']:<8.3f} {cat}")


if __name__ == "__main__":
    main()
