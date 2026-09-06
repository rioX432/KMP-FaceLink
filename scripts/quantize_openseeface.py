#!/usr/bin/env python3
"""
Experimental OpenSeeFace ONNX quantization utility.

Produces INT8 dynamic-quantized variants of the FP32 detection and landmark
models. Generated artifacts are not selected by the runtime or published. They
must pass independent output-agreement and device-performance qualification
before any runtime integration; this utility makes no speed or quality claim.

Usage:
    python scripts/quantize_openseeface.py

Inputs (expected in kmp-facelink/src/jvmMain/resources/models/openseeface/):
    mnv3_detection_opt.onnx   — FP32 face detection model
    lm_model3_opt.onnx        — FP32 landmark model

Outputs (written alongside the inputs):
    mnv3_detection_opt_int8.onnx  — INT8 dynamic-quantized detection model
    lm_model3_opt_int8.onnx       — INT8 dynamic-quantized landmark model

Requirements:
    pip install -r scripts/requirements-quantize.txt
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import onnx
from onnxruntime.quantization import QuantType, quantize_dynamic


# Model directory relative to the repository root
MODELS_DIR = Path("kmp-facelink/src/jvmMain/resources/models/openseeface")

# Models to quantize: (input_filename, output_filename)
MODELS = [
    ("mnv3_detection_opt.onnx", "mnv3_detection_opt_int8.onnx"),
    ("lm_model3_opt.onnx", "lm_model3_opt_int8.onnx"),
]

# Operator types eligible for INT8 quantization in MobileNetV3 / landmark models
OP_TYPES_TO_QUANTIZE = ["MatMul", "Gemm", "Conv"]


def find_repo_root() -> Path:
    """Walk upward from the script location to find the repository root."""
    candidate = Path(__file__).resolve().parent.parent
    if (candidate / "settings.gradle.kts").exists():
        return candidate
    # Fallback: current working directory
    cwd = Path.cwd()
    if (cwd / "settings.gradle.kts").exists():
        return cwd
    print("ERROR: Could not locate repository root. Run from the repo root or scripts/.")
    sys.exit(1)


def validate_model(model_path: Path) -> None:
    """Load and validate the ONNX model to ensure it is well-formed."""
    model = onnx.load(str(model_path))
    onnx.checker.check_model(model)
    print(f"  Validated: {model_path.name} (opset {model.opset_import[0].version})")


def quantize_model(input_path: Path, output_path: Path) -> None:
    """Apply INT8 dynamic quantization to an ONNX model."""
    print(f"  Quantizing {input_path.name} -> {output_path.name} ...")
    quantize_dynamic(
        model_input=str(input_path),
        model_output=str(output_path),
        weight_type=QuantType.QInt8,
        op_types_to_quantize=OP_TYPES_TO_QUANTIZE,
        per_channel=True,
    )
    # Report size reduction
    original_size = input_path.stat().st_size
    quantized_size = output_path.stat().st_size
    ratio = quantized_size / original_size * 100
    print(f"  Size: {original_size:,} -> {quantized_size:,} bytes ({ratio:.1f}%)")


def main() -> None:
    repo_root = find_repo_root()
    models_dir = repo_root / MODELS_DIR

    if not models_dir.exists():
        print(f"ERROR: Models directory not found: {models_dir}")
        print("Ensure FP32 models are placed in the directory before running this script.")
        print("Download from: https://github.com/emilianavt/OpenSeeFace/tree/master/models")
        sys.exit(1)

    print(f"Models directory: {models_dir}")
    print()

    for input_name, output_name in MODELS:
        input_path = models_dir / input_name
        output_path = models_dir / output_name

        if not input_path.exists():
            print(f"WARNING: Skipping {input_name} (file not found)")
            continue

        print(f"Processing {input_name}:")
        validate_model(input_path)
        quantize_model(input_path, output_path)
        validate_model(output_path)
        print()

    print("Quantization complete.")


if __name__ == "__main__":
    main()
