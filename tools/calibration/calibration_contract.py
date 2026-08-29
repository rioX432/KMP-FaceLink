#!/usr/bin/env python3
"""Dependency-free contracts shared by the optional calibration evaluator.

This module intentionally uses only the Python standard library so Tier 1 CI can
exercise parser and paired-difference semantics without installing scientific
packages or opening measurement data.
"""

from __future__ import annotations

import argparse
import math
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class AnalysisWindow:
    """Inclusive window in seconds relative to the first landmark frame."""

    label: str
    start_seconds: float
    end_seconds: float


@dataclass(frozen=True)
class FrameAnalysisWindow:
    """Inclusive window in exported frame-index space."""

    label: str
    start_frame: int
    end_frame: int


def parse_window(value: str) -> AnalysisWindow:
    """Parse ``LABEL:START_SECONDS:END_SECONDS``."""
    parts = value.split(":")
    if len(parts) != 3:
        raise argparse.ArgumentTypeError(
            "window must use LABEL:START_SECONDS:END_SECONDS"
        )
    label = parts[0].strip()
    if not label:
        raise argparse.ArgumentTypeError("window label must not be empty")
    try:
        start = float(parts[1])
        end = float(parts[2])
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "window start and end must be numeric"
        ) from error
    if not math.isfinite(start) or not math.isfinite(end):
        raise argparse.ArgumentTypeError("window bounds must be finite")
    if start < 0.0:
        raise argparse.ArgumentTypeError("window start must be zero or greater")
    if end <= start:
        raise argparse.ArgumentTypeError("window end must be greater than start")
    return AnalysisWindow(label, start, end)


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
        start = int(parts[1])
        end = int(parts[2])
    except ValueError as error:
        raise argparse.ArgumentTypeError(
            "frame-window start and end must be integers"
        ) from error
    if start < 0:
        raise argparse.ArgumentTypeError(
            "frame-window start must be zero or greater"
        )
    if end < start:
        raise argparse.ArgumentTypeError(
            "frame-window end must be greater than or equal to start"
        )
    return FrameAnalysisWindow(label, start, end)


def clip01(value: float) -> float:
    """Match the Kotlin detector's scalar ``coerceIn(0, 1)``."""
    return min(1.0, max(0.0, value))


def sign(value: float) -> float:
    """Return -1, 0, or +1."""
    if value > 0.0:
        return 1.0
    if value < 0.0:
        return -1.0
    return 0.0


def paired_difference_statistics(
    geometric: Iterable[float], reference: Iterable[float]
) -> dict[str, float | int | None]:
    """Describe finite paired differences without assigning either signal as truth."""
    geometric_values = list(geometric)
    reference_values = list(reference)
    if len(geometric_values) != len(reference_values):
        raise ValueError("paired signals must have equal lengths")
    pairs = [
        (float(first), float(second))
        for first, second in zip(geometric_values, reference_values)
        if math.isfinite(float(first)) and math.isfinite(float(second))
    ]
    if not pairs:
        return {
            "paired_count": 0,
            "mean_absolute_difference": None,
            "root_mean_square_difference": None,
            "mean_signed_difference_geometric_minus_arkit": None,
            "pearson_correlation": None,
        }
    first = [pair[0] for pair in pairs]
    second = [pair[1] for pair in pairs]
    differences = [left - right for left, right in pairs]
    count = len(pairs)
    first_mean = sum(first) / count
    second_mean = sum(second) / count
    first_variance = sum((value - first_mean) ** 2 for value in first) / count
    second_variance = sum((value - second_mean) ** 2 for value in second) / count
    if first_variance < 1e-18 or second_variance < 1e-18:
        correlation = None
    else:
        covariance = sum(
            (left - first_mean) * (right - second_mean)
            for left, right in pairs
        ) / count
        correlation = covariance / math.sqrt(first_variance * second_variance)
    return {
        "paired_count": count,
        "mean_absolute_difference": sum(abs(value) for value in differences) / count,
        "root_mean_square_difference": math.sqrt(
            sum(value**2 for value in differences) / count
        ),
        "mean_signed_difference_geometric_minus_arkit": sum(differences) / count,
        "pearson_correlation": correlation,
    }
