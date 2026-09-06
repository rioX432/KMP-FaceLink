#!/usr/bin/env bash
# Download the exact OpenSeeFace FP32 model artifacts used by the JVM backend.

set -euo pipefail
cd "$(dirname "$0")/.."

readonly OPENSEEFACE_REVISION="85aa70fc67582d046e771ea73625182a0d8f7475"
readonly BASE_URL="https://raw.githubusercontent.com/emilianavt/OpenSeeFace/${OPENSEEFACE_REVISION}/models"
readonly MODELS_DIR="kmp-facelink/src/jvmMain/resources/models/openseeface"

mkdir -p "$MODELS_DIR"

download_and_verify() {
    local name="$1"
    local expected_sha256="$2"
    local destination="$MODELS_DIR/$name"
    local temporary="$destination.download"

    if [ -f "$destination" ]; then
        local existing_sha256
        existing_sha256="$(shasum -a 256 "$destination" | awk '{print $1}')"
        if [ "$existing_sha256" = "$expected_sha256" ]; then
            echo "Verified existing model: $name ($existing_sha256)"
            return
        fi
        echo "Existing model digest differs; replacing $name" >&2
    fi

    curl -fL --retry 3 --output "$temporary" "$BASE_URL/$name"
    local actual_sha256
    actual_sha256="$(shasum -a 256 "$temporary" | awk '{print $1}')"
    if [ "$actual_sha256" != "$expected_sha256" ]; then
        echo "ERROR: digest mismatch for $name" >&2
        echo "expected=$expected_sha256" >&2
        echo "actual=$actual_sha256" >&2
        rm -f "$temporary"
        exit 1
    fi
    mv "$temporary" "$destination"
    echo "Downloaded and verified: $name ($actual_sha256)"
}

download_and_verify \
    "mnv3_detection_opt.onnx" \
    "0e8e4806766d85ab067a52c7af0dcb59eb7f9dfe580b44f20a8e6ab712d89809"
download_and_verify \
    "lm_model3_opt.onnx" \
    "5aa0ab2594acaf2e1d3916a8de592cffeb1f1a7a7f778cad2f37948bff6549d1"

echo "Pinned OpenSeeFace FP32 models are ready."
