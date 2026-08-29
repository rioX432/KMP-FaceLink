package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.AppliedTransform
import io.github.kmpfacelink.model.BlendShape
import io.github.kmpfacelink.model.BlendShapeEnhancerConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.SmoothingConfig
import io.github.kmpfacelink.model.TransformKind
import io.github.kmpfacelink.model.TransformSpec
import io.github.kmpfacelink.model.TransformStateDependence
import io.github.kmpfacelink.model.sha256Hex

/** Ordered transform configuration shared by runtime epochs and face provenance. */
internal fun FaceTrackerConfig.configuredTransformSpecs(): List<TransformSpec> = buildList {
    enhancerTransformSpec()?.let(::add)
    calibratorTransformSpec()?.let(::add)
    smootherTransformSpec()?.let(::add)
    add(activeShapeFilterTransformSpec())
    interpolatorTransformSpec()?.let(::add)
}

internal fun FaceTrackerConfig.fullPrimaryAppliedTransforms(enhancerApplied: Boolean): List<AppliedTransform> =
    buildList {
        if (enhancerApplied) enhancerTransformSpec()?.asApplied()?.let(::add)
        calibratorTransformSpec()?.asApplied()?.let(::add)
        smootherTransformSpec()?.asApplied()?.let(::add)
        add(activeShapeFilterTransformSpec().asApplied())
    }

internal fun FaceTrackerConfig.secondaryAppliedTransforms(): List<AppliedTransform> = buildList {
    smootherTransformSpec()?.asApplied()?.let(::add)
    add(activeShapeFilterTransformSpec().asApplied())
}

internal fun FaceTrackerConfig.interpolatorAppliedTransform(): AppliedTransform? =
    interpolatorTransformSpec()?.asApplied()

private fun FaceTrackerConfig.enhancerTransformSpec(): TransformSpec? = when (val enhancer = enhancerConfig) {
    BlendShapeEnhancerConfig.None -> null
    is BlendShapeEnhancerConfig.Default -> TransformSpec(
        kind = TransformKind.ENHANCER,
        implementationId = "experimental-landmark-enhancer-v1",
        configurationFingerprint = stableHash(
            buildString {
                append("ENHANCER_V1|")
                append(enhancer.geometricBlendWeight.bits()).append('|')
                append(enhancer.enableCorrelation).append('|')
                appendShapeFloatMap(enhancer.sensitivityOverrides)
                append('|')
                appendShapeFloatMap(enhancer.deadZoneOverrides)
            },
        ),
    )
}

private fun FaceTrackerConfig.calibratorTransformSpec(): TransformSpec? =
    if (!calibrationConfig.enabled) {
        null
    } else {
        TransformSpec(
            kind = TransformKind.CALIBRATOR,
            implementationId = "session-range-calibrator-v1",
            configurationFingerprint = stableHash(
                "CALIBRATOR_V1|${calibrationConfig.warmupFrames}|${calibrationConfig.rangeDecayRate.bits()}",
            ),
            stateDependence = TransformStateDependence.SESSION_LEARNED_STATE,
        )
    }

private fun FaceTrackerConfig.smootherTransformSpec(): TransformSpec? = when (val smoothing = smoothingConfig) {
    SmoothingConfig.None -> null
    is SmoothingConfig.Ema -> TransformSpec(
        kind = TransformKind.SMOOTHER,
        implementationId = "ema-v1",
        configurationFingerprint = stableHash("EMA_V1|${smoothing.alpha.bits()}"),
    )
    is SmoothingConfig.OneEuro -> TransformSpec(
        kind = TransformKind.SMOOTHER,
        implementationId = "one-euro-v1",
        configurationFingerprint = stableHash("ONE_EURO_V1|${smoothing.fingerprintFields()}"),
    )
    is SmoothingConfig.CategoryAware -> TransformSpec(
        kind = TransformKind.SMOOTHER,
        implementationId = "category-aware-one-euro-v1",
        configurationFingerprint = stableHash(
            listOf(
                smoothing.mouthConfig,
                smoothing.eyeConfig,
                smoothing.browConfig,
                smoothing.cheekConfig,
                smoothing.jawConfig,
                smoothing.otherConfig,
            ).joinToString(separator = "|", prefix = "CATEGORY_ONE_EURO_V1|") { it.fingerprintFields() },
        ),
    )
    is SmoothingConfig.NeuroPredictive -> TransformSpec(
        kind = TransformKind.SMOOTHER,
        implementationId = "neuro-predictive-placeholder-v1",
        configurationFingerprint = stableHash("NEURO_PREDICTIVE_V1|${smoothing.windowSize}"),
    )
    is SmoothingConfig.Kalman -> TransformSpec(
        kind = TransformKind.SMOOTHER,
        implementationId = "kalman-v1",
        configurationFingerprint = stableHash(
            "KALMAN_V1|${smoothing.processNoise.bits()}|${smoothing.measurementNoise.bits()}|" +
                smoothing.predictionFrames,
        ),
    )
}

private fun FaceTrackerConfig.activeShapeFilterTransformSpec(): TransformSpec = TransformSpec(
    kind = TransformKind.ACTIVE_SHAPE_FILTER,
    implementationId = "active-shape-filter-v1",
    configurationFingerprint = stableHash(
        activeBlendShapes.sortedBy { it.arKitName }
            .joinToString(",", prefix = "ACTIVE_V1|") { it.arKitName },
    ),
)

private fun FaceTrackerConfig.interpolatorTransformSpec(): TransformSpec? =
    if (!frameSkipConfig.enabled) {
        null
    } else {
        TransformSpec(
            kind = TransformKind.INTERPOLATOR,
            implementationId = "linear-extrapolator-v1",
            configurationFingerprint = stableHash("INTERPOLATOR_V1|${frameSkipConfig.level.name}"),
        )
    }

private fun TransformSpec.asApplied(): AppliedTransform = AppliedTransform(kind, configurationFingerprint)

private fun StringBuilder.appendShapeFloatMap(values: Map<BlendShape, Float>) {
    values.entries.sortedBy { it.key.arKitName }.joinTo(this, separator = ",") { entry ->
        "${entry.key.arKitName}:${entry.value.bits()}"
    }
}

private fun Float.bits(): String = toRawBits().toUInt().toString(radix = 16)

private fun SmoothingConfig.OneEuro.fingerprintFields(): String =
    "${minCutoff.bits()},${beta.bits()},${dCutoff.bits()},${predictionMs.bits()}"

private fun stableHash(value: String): String = sha256Hex(value.encodeToByteArray())
