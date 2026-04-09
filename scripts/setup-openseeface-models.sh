#!/usr/bin/env bash
# Download OpenSeeFace ONNX models and generate INT8 quantized variants.
#
# FP32 models are downloaded from the OpenSeeFace GitHub repository.
# INT8 variants are produced by the quantization script using onnxruntime.
#
# Usage:
#   ./scripts/setup-openseeface-models.sh          # download + quantize
#   ./scripts/setup-openseeface-models.sh --skip-quantize  # download only

set -euo pipefail
cd "$(dirname "$0")/.."

MODELS_DIR="kmp-facelink/src/jvmMain/resources/models/openseeface"
BASE_URL="https://github.com/emilianavt/OpenSeeFace/raw/master/models"

DETECTION_MODEL="mnv3_detection_opt.onnx"
LANDMARK_MODEL="lm_model3_opt.onnx"

mkdir -p "$MODELS_DIR"

download_model() {
    local name="$1"
    local dest="$MODELS_DIR/$name"
    if [ -f "$dest" ]; then
        echo "Already exists: $dest"
    else
        echo "Downloading $name ..."
        curl -fsSL -o "$dest" "$BASE_URL/$name"
        echo "Downloaded: $dest ($(wc -c < "$dest" | tr -d ' ') bytes)"
    fi
}

download_model "$DETECTION_MODEL"
download_model "$LANDMARK_MODEL"

if [ "${1:-}" = "--skip-quantize" ]; then
    echo "Skipping quantization (--skip-quantize)."
    exit 0
fi

echo ""
echo "Running INT8 quantization ..."
if command -v python3 &>/dev/null; then
    python3 scripts/quantize_openseeface.py
else
    echo "WARNING: python3 not found. Install Python and onnxruntime to quantize models."
    echo "  pip install -r scripts/requirements-quantize.txt"
    echo "  python scripts/quantize_openseeface.py"
fi
