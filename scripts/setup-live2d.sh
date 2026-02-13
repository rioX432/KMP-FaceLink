#!/usr/bin/env bash
#
# Downloads Live2D Cubism Framework (open source) and Hiyori sample model.
# CubismCore (proprietary) must be downloaded manually from Live2D's website.
#
# Usage:
#   ./scripts/setup-live2d.sh
#   ./scripts/setup-live2d.sh --core-java /path/to/CubismSdkForJava.zip
#   ./scripts/setup-live2d.sh --core-native /path/to/CubismSdkForNative.zip

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

FRAMEWORK_TAG="5-r.4.1"

# Destination directories
ANDROID_DIR="$ROOT_DIR/live2d/android"
IOS_DIR="$ROOT_DIR/live2d/ios"
ANDROID_MODEL_DIR="$ROOT_DIR/androidApp/src/main/assets/live2d/Hiyori"
IOS_MODEL_DIR="$ROOT_DIR/iosApp/FaceLink/Resources/Live2D/Hiyori"

# Parse arguments
CORE_JAVA_ZIP=""
CORE_NATIVE_ZIP=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --core-java)
            CORE_JAVA_ZIP="$2"
            shift 2
            ;;
        --core-native)
            CORE_NATIVE_ZIP="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=== Live2D Setup ==="
echo ""

# ---------------------------------------------------------------------------
# 1. CubismJavaFramework (open source, GitHub)
# ---------------------------------------------------------------------------
setup_java_framework() {
    local dest="$ANDROID_DIR/Framework"
    if [[ -d "$dest/.git" ]]; then
        echo "[OK] CubismJavaFramework already exists at $dest"
        return
    fi
    echo "[DL] Cloning CubismJavaFramework ($FRAMEWORK_TAG)..."
    rm -rf "$dest"
    mkdir -p "$ANDROID_DIR"
    git clone --depth 1 --branch "$FRAMEWORK_TAG" \
        https://github.com/Live2D/CubismJavaFramework.git "$dest"
    echo "[OK] CubismJavaFramework ready"
}

# ---------------------------------------------------------------------------
# 2. CubismNativeFramework (open source, GitHub)
# ---------------------------------------------------------------------------
setup_native_framework() {
    local dest="$IOS_DIR/Framework"
    if [[ -d "$dest/.git" ]]; then
        echo "[OK] CubismNativeFramework already exists at $dest"
        return
    fi
    echo "[DL] Cloning CubismNativeFramework ($FRAMEWORK_TAG)..."
    rm -rf "$dest"
    mkdir -p "$IOS_DIR"
    git clone --depth 1 --branch "$FRAMEWORK_TAG" \
        https://github.com/Live2D/CubismNativeFramework.git "$dest"
    echo "[OK] CubismNativeFramework ready"
}

# ---------------------------------------------------------------------------
# 3. CubismCore for Java (proprietary, manual download required)
# ---------------------------------------------------------------------------
setup_java_core() {
    local dest="$ANDROID_DIR/Core"
    if [[ -d "$dest" && ( -f "$dest/Live2DCubismCore.aar" || -f "$dest/live2dcubismcore.jar" ) ]]; then
        echo "[OK] CubismCore (Java) already exists at $dest"
        return
    fi

    if [[ -n "$CORE_JAVA_ZIP" ]]; then
        echo "[DL] Extracting CubismCore (Java) from $CORE_JAVA_ZIP..."
        local tmp_dir
        tmp_dir="$(mktemp -d)"
        unzip -q "$CORE_JAVA_ZIP" -d "$tmp_dir"

        # Find the Core directory (may contain .aar or .jar)
        local core_src
        core_src="$(find "$tmp_dir" -path "*/Core/android/Live2DCubismCore.aar" -print -quit)"
        if [[ -z "$core_src" ]]; then
            core_src="$(find "$tmp_dir" -path "*/Core/live2dcubismcore.jar" -print -quit)"
        fi
        if [[ -z "$core_src" ]]; then
            echo "[ERR] Could not find CubismCore (.aar or .jar) in the ZIP"
            rm -rf "$tmp_dir"
            return 1
        fi

        local core_dir
        # For .aar: go up two levels (Core/android/xxx.aar → Core/)
        # For .jar: go up one level (Core/xxx.jar → Core/)
        if [[ "$core_src" == *"/android/"* ]]; then
            core_dir="$(dirname "$(dirname "$core_src")")"
        else
            core_dir="$(dirname "$core_src")"
        fi

        mkdir -p "$dest"
        cp -r "$core_dir"/* "$dest/"
        rm -rf "$tmp_dir"
        echo "[OK] CubismCore (Java) extracted"
    else
        echo "[SKIP] CubismCore (Java) not found."
        echo "       Download the SDK from: https://www.live2d.com/en/sdk/download/java/"
        echo "       Then re-run: ./scripts/setup-live2d.sh --core-java /path/to/CubismSdkForJava.zip"
    fi
}

# ---------------------------------------------------------------------------
# 4. CubismCore for Native/iOS (proprietary, manual download required)
# ---------------------------------------------------------------------------
setup_native_core() {
    local dest="$IOS_DIR/Core"
    if [[ -d "$dest" && -d "$dest/lib" ]]; then
        echo "[OK] CubismCore (Native) already exists at $dest"
        return
    fi

    if [[ -n "$CORE_NATIVE_ZIP" ]]; then
        echo "[DL] Extracting CubismCore (Native) from $CORE_NATIVE_ZIP..."
        local tmp_dir
        tmp_dir="$(mktemp -d)"
        unzip -q "$CORE_NATIVE_ZIP" -d "$tmp_dir"

        # Find the Core directory inside the extracted SDK
        local core_header
        core_header="$(find "$tmp_dir" -path "*/Core/include/Live2DCubismCore.h" -print -quit)"
        if [[ -z "$core_header" ]]; then
            echo "[ERR] Could not find Core/include/Live2DCubismCore.h in the ZIP"
            rm -rf "$tmp_dir"
            return 1
        fi

        local core_dir
        core_dir="$(dirname "$(dirname "$core_header")")"
        mkdir -p "$dest"
        cp -r "$core_dir"/* "$dest/"
        rm -rf "$tmp_dir"
        echo "[OK] CubismCore (Native) extracted"
    else
        echo "[SKIP] CubismCore (Native) not found."
        echo "       Download the SDK from: https://www.live2d.com/en/sdk/download/native/"
        echo "       Then re-run: ./scripts/setup-live2d.sh --core-native /path/to/CubismSdkForNative.zip"
    fi
}

# ---------------------------------------------------------------------------
# 5. Hiyori model (from CubismNativeSamples on GitHub)
# ---------------------------------------------------------------------------
setup_hiyori_model() {
    if [[ -f "$ANDROID_MODEL_DIR/Hiyori.model3.json" && -f "$IOS_MODEL_DIR/Hiyori.model3.json" ]]; then
        echo "[OK] Hiyori model already exists"
        return
    fi

    echo "[DL] Downloading Hiyori model from CubismNativeSamples..."
    local tmp_dir
    tmp_dir="$(mktemp -d)"

    # Sparse checkout only the Hiyori model
    git clone --depth 1 --filter=blob:none --sparse --branch "$FRAMEWORK_TAG" \
        https://github.com/Live2D/CubismNativeSamples.git "$tmp_dir/samples"
    (cd "$tmp_dir/samples" && git sparse-checkout set Samples/Resources/Hiyori)

    local model_src="$tmp_dir/samples/Samples/Resources/Hiyori"
    if [[ ! -d "$model_src" ]]; then
        echo "[ERR] Hiyori model not found in CubismNativeSamples"
        rm -rf "$tmp_dir"
        return 1
    fi

    # Copy to Android assets
    mkdir -p "$ANDROID_MODEL_DIR"
    cp -r "$model_src"/* "$ANDROID_MODEL_DIR/"

    # Copy to iOS resources
    mkdir -p "$IOS_MODEL_DIR"
    cp -r "$model_src"/* "$IOS_MODEL_DIR/"

    rm -rf "$tmp_dir"
    echo "[OK] Hiyori model ready"
}

# ---------------------------------------------------------------------------
# Run all steps
# ---------------------------------------------------------------------------
setup_java_framework
setup_native_framework
setup_java_core
setup_native_core
setup_hiyori_model

# ---------------------------------------------------------------------------
# 6. Generate iOS xcconfig for Live2D SDK availability
# ---------------------------------------------------------------------------
generate_ios_xcconfig() {
    local xcconfig="$ROOT_DIR/iosApp/Live2DConfig.generated.xcconfig"

    if [[ -d "$IOS_DIR/Core/lib" && -d "$IOS_DIR/Framework" ]]; then
        echo "[GEN] Generating Live2DConfig.generated.xcconfig (SDK available)..."
        cat > "$xcconfig" <<'XCEOF'
// Auto-generated by setup-live2d.sh — do not edit manually
LIVE2D_AVAILABLE = 1

HEADER_SEARCH_PATHS = $(inherited) $(SRCROOT)/../live2d/ios/Core/include $(SRCROOT)/../live2d/ios/Framework/src
LIBRARY_SEARCH_PATHS = $(inherited) $(SRCROOT)/../live2d/ios/Core/lib/ios/$(CONFIGURATION)-$(PLATFORM_NAME)
OTHER_LDFLAGS = $(inherited) -lLive2DCubismCore
SWIFT_ACTIVE_COMPILATION_CONDITIONS = $(inherited) LIVE2D_AVAILABLE
GCC_PREPROCESSOR_DEFINITIONS = $(inherited) LIVE2D_AVAILABLE=1
XCEOF
        echo "[OK] xcconfig generated"
    else
        echo "[SKIP] iOS SDK incomplete — removing generated xcconfig"
        rm -f "$xcconfig"
    fi
}

generate_ios_xcconfig

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Directory structure:"
echo "  live2d/android/Framework/  - CubismJavaFramework (open source)"
echo "  live2d/android/Core/       - CubismCore for Java (proprietary)"
echo "  live2d/ios/Framework/      - CubismNativeFramework (open source)"
echo "  live2d/ios/Core/           - CubismCore for Native (proprietary)"
echo "  androidApp/.../live2d/Hiyori/  - Hiyori model (Android assets)"
echo "  iosApp/.../Live2D/Hiyori/      - Hiyori model (iOS resources)"

# Check for missing Core
if [[ ! -f "$ANDROID_DIR/Core/android/Live2DCubismCore.aar" && ! -f "$ANDROID_DIR/Core/live2dcubismcore.jar" ]]; then
    echo ""
    echo "WARNING: CubismCore (Java) is missing. The Android sample will not compile with Live2D."
    echo "         Download from: https://www.live2d.com/en/sdk/download/java/"
fi
if [[ ! -d "$IOS_DIR/Core/lib" ]]; then
    echo ""
    echo "WARNING: CubismCore (Native) is missing. Live2D avatar mode will be disabled on iOS."
    echo "         Download from: https://www.live2d.com/en/sdk/download/native/"
fi
