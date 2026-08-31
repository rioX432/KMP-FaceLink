package io.github.kmpfacelink.capture

import kotlinx.serialization.Serializable

/** Deliberate storage choice for one guided take; callers must not get an implicit raw default. */
@Serializable
public enum class CaptureStorageProfile {
    /** Results/coefficient streams only: no frame payload, directory, or raw manifest. */
    COEFFICIENTS_ONLY_V1,

    /** Canonical, bounded 240×320 RGBA payload intended for a declared offline reader. */
    CANONICAL_RGBA_240X320_V1,

    /** Decoded only to inspect historical schema-3 takes; never eligible for replay. */
    LEGACY_DIAGNOSTIC_ONLY,
}

/** Pixel relationship to the platform's live estimator, recorded instead of inferred by readers. */
@Serializable
public enum class CapturePixelSemantics {
    NONE,
    EXACT_ANDROID_INFERENCE_INPUT,
    CANONICALIZED_IOS_CAPTURE_COPY,
    LEGACY_DIAGNOSTIC_ONLY,
}

/** Required schema-4 storage identity. Geometry is source → stored; no implicit resize is allowed. */
@Serializable
public data class CaptureStorageReceipt(
    public val profile: CaptureStorageProfile,
    public val pixelSemantics: CapturePixelSemantics,
    public val sourceWidth: Int,
    public val sourceHeight: Int,
    public val storedWidth: Int,
    public val storedHeight: Int,
    public val orientationPolicy: String,
    public val mirrorPolicy: String,
    public val canonicalizationAlgorithm: String,
) {
    public companion object {
        public const val CANONICAL_WIDTH: Int = 240
        public const val CANONICAL_HEIGHT: Int = 320
        public const val CANONICAL_BYTES: Int = CANONICAL_WIDTH * CANONICAL_HEIGHT * 4
        public const val MAX_CANONICAL_FRAMES: Int = 873

        /** Covers the longest registered 30 s take at 60 fps (1,800 callbacks) with margin. */
        public const val MAX_RECORDS_PER_TAKE: Int = 2048

        public fun coefficientsOnly(sourceWidth: Int, sourceHeight: Int): CaptureStorageReceipt =
            CaptureStorageReceipt(
                CaptureStorageProfile.COEFFICIENTS_ONLY_V1,
                CapturePixelSemantics.NONE,
                sourceWidth,
                sourceHeight,
                0,
                0,
                "NOT_STORED",
                "NOT_STORED",
                "NONE",
            )

        public fun legacyDiagnosticOnly(): CaptureStorageReceipt = CaptureStorageReceipt(
            CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY,
            CapturePixelSemantics.LEGACY_DIAGNOSTIC_ONLY,
            0, 0, 0, 0, "UNKNOWN", "UNKNOWN", "LEGACY_UNKNOWN",
        )
    }

    public fun requireSchema4Consistency(framesStored: Int) {
        when (profile) {
            CaptureStorageProfile.COEFFICIENTS_ONLY_V1 -> require(
                pixelSemantics == CapturePixelSemantics.NONE && framesStored == 0 &&
                    storedWidth == 0 && storedHeight == 0,
            ) { "coefficient-only takes must not retain pixels" }
            CaptureStorageProfile.CANONICAL_RGBA_240X320_V1 -> require(
                pixelSemantics != CapturePixelSemantics.NONE &&
                    storedWidth == CANONICAL_WIDTH && storedHeight == CANONICAL_HEIGHT &&
                    framesStored in 0..MAX_CANONICAL_FRAMES,
            ) { "canonical takes must retain at most 873 240x320 RGBA frames" }
            CaptureStorageProfile.LEGACY_DIAGNOSTIC_ONLY -> error("legacy storage is diagnostic only")
        }
    }

    /** Fail before admitting the 874th 240×320 payload (256 MiB per take). */
    public fun requireNextCanonicalFrame(framesStored: Int) {
        require(framesStored < MAX_CANONICAL_FRAMES) {
            "canonical storage cap of $MAX_CANONICAL_FRAMES frames reached"
        }
    }

    /** Bound result-stream growth independently of whether a take retains raw pixels. */
    public fun requireNextRecord(recordsAdmitted: Int) {
        require(recordsAdmitted < MAX_RECORDS_PER_TAKE) {
            "take record cap of $MAX_RECORDS_PER_TAKE reached"
        }
    }

    /** Exact Android replay deliberately refuses an iOS canonical copy or any legacy take. */
    public fun requireExactAndroidReplay() {
        require(profile == CaptureStorageProfile.CANONICAL_RGBA_240X320_V1) {
            "exact Android replay requires canonical stored pixels"
        }
        require(pixelSemantics == CapturePixelSemantics.EXACT_ANDROID_INFERENCE_INPUT) {
            "exact Android replay rejects $pixelSemantics"
        }
        requireSchema4Consistency(1)
    }
}
