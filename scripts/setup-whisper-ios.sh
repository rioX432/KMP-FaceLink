#!/usr/bin/env bash
#
# Builds whisper.cpp as static libraries for iOS (device + simulator).
# Output: whisper/ios/{device,simulator}/libwhisper_all.a + headers
#
# Usage:
#   ./scripts/setup-whisper-ios.sh
#   ./scripts/setup-whisper-ios.sh --tag v1.8.3
#
# Prerequisites: Xcode command line tools, CMake

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

WHISPER_TAG="v1.8.3"
WHISPER_SRC="$ROOT_DIR/whisper.cpp"
OUTPUT_DIR="$ROOT_DIR/whisper/ios"
IOS_DEPLOYMENT_TARGET="16.4"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag)
            WHISPER_TAG="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=== Whisper.cpp iOS Setup ==="
echo "Tag: $WHISPER_TAG"
echo ""

# ---------------------------------------------------------------------------
# 1. Clone whisper.cpp
# ---------------------------------------------------------------------------
clone_whisper() {
    if [[ -d "$WHISPER_SRC/.git" ]]; then
        echo "[OK] whisper.cpp already exists at $WHISPER_SRC"
        return
    fi
    echo "[DL] Cloning whisper.cpp ($WHISPER_TAG)..."
    git clone --depth 1 --branch "$WHISPER_TAG" \
        https://github.com/ggerganov/whisper.cpp.git "$WHISPER_SRC"
    echo "[OK] whisper.cpp cloned"
}

# ---------------------------------------------------------------------------
# 2. Build static libraries for a given platform
# ---------------------------------------------------------------------------
build_platform() {
    local platform="$1"  # "device" or "simulator"
    local sysroot build_dir release_dir

    if [[ "$platform" == "device" ]]; then
        sysroot="iphoneos"
        release_dir="Release-iphoneos"
    else
        sysroot="iphonesimulator"
        release_dir="Release-iphonesimulator"
    fi
    build_dir="$ROOT_DIR/build-whisper-ios-$platform"

    if [[ -f "$OUTPUT_DIR/$platform/libwhisper_all.a" ]]; then
        echo "[OK] $platform libraries already built"
        return
    fi

    echo "[BUILD] Configuring for $platform..."
    cmake -B "$build_dir" -G Xcode \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_OSX_SYSROOT="$sysroot" \
        -DCMAKE_OSX_ARCHITECTURES=arm64 \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="$IOS_DEPLOYMENT_TARGET" \
        -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGNING_REQUIRED=NO \
        -DCMAKE_XCODE_ATTRIBUTE_CODE_SIGN_IDENTITY="" \
        -DBUILD_SHARED_LIBS=OFF \
        -DGGML_METAL=ON \
        -DGGML_METAL_EMBED_LIBRARY=ON \
        -DGGML_BLAS=ON \
        -DGGML_BLAS_VENDOR=Apple \
        -DGGML_NATIVE=OFF \
        -DGGML_OPENMP=OFF \
        -DWHISPER_BUILD_EXAMPLES=OFF \
        -DWHISPER_BUILD_TESTS=OFF \
        -S "$WHISPER_SRC"

    echo "[BUILD] Building $platform (Release)..."
    cmake --build "$build_dir" --config Release -- -quiet

    echo "[COMBINE] Merging static libraries for $platform..."
    mkdir -p "$OUTPUT_DIR/$platform"

    # Collect all .a files produced by the build
    local libs=()
    for lib in \
        "$build_dir/src/$release_dir/libwhisper.a" \
        "$build_dir/ggml/src/$release_dir/libggml.a" \
        "$build_dir/ggml/src/$release_dir/libggml-base.a" \
        "$build_dir/ggml/src/$release_dir/libggml-cpu.a" \
        "$build_dir/ggml/src/ggml-metal/$release_dir/libggml-metal.a" \
        "$build_dir/ggml/src/ggml-blas/$release_dir/libggml-blas.a"; do
        if [[ -f "$lib" ]]; then
            libs+=("$lib")
        fi
    done

    if [[ ${#libs[@]} -eq 0 ]]; then
        echo "[ERR] No static libraries found in $build_dir"
        exit 1
    fi

    libtool -static -o "$OUTPUT_DIR/$platform/libwhisper_all.a" "${libs[@]}"
    echo "[OK] $platform → $OUTPUT_DIR/$platform/libwhisper_all.a"
}

# ---------------------------------------------------------------------------
# 3. Copy headers
# ---------------------------------------------------------------------------
copy_headers() {
    local include_dir="$OUTPUT_DIR/include"
    if [[ -f "$include_dir/whisper.h" ]]; then
        echo "[OK] Headers already copied"
        return
    fi

    echo "[COPY] Copying headers..."
    mkdir -p "$include_dir"
    cp "$WHISPER_SRC/include/whisper.h" "$include_dir/"
    cp "$WHISPER_SRC/ggml/include/ggml.h" "$include_dir/"
    cp "$WHISPER_SRC/ggml/include/ggml-alloc.h" "$include_dir/"
    cp "$WHISPER_SRC/ggml/include/ggml-backend.h" "$include_dir/"
    cp "$WHISPER_SRC/ggml/include/ggml-cpu.h" "$include_dir/"
    echo "[OK] Headers copied to $include_dir"
}

# ---------------------------------------------------------------------------
# 4. Cleanup build directories
# ---------------------------------------------------------------------------
cleanup() {
    echo "[CLEAN] Removing build directories..."
    rm -rf "$ROOT_DIR/build-whisper-ios-device"
    rm -rf "$ROOT_DIR/build-whisper-ios-simulator"
    echo "[OK] Cleaned up"
}

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------
clone_whisper
build_platform "device"
build_platform "simulator"
copy_headers
cleanup

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Output:"
echo "  whisper/ios/device/libwhisper_all.a     - iOS device (arm64)"
echo "  whisper/ios/simulator/libwhisper_all.a  - iOS simulator (arm64)"
echo "  whisper/ios/include/                    - C headers"
echo ""
echo "Next: run ./gradlew :kmp-facelink-voice:cinteropWhisperIosArm64"
