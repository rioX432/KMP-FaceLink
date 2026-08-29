@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kmpfacelink.internal

import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.TrackingContextEpoch
import io.github.kmpfacelink.model.TransformKind
import io.github.kmpfacelink.model.sha256Hex
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.UIKit.UIDevice

/** Opaque random identity for one iOS tracker instance. */
internal fun newIosSessionInstanceId(): SessionInstanceId {
    val bytes = ByteArray(SESSION_ID_BYTES)
    val status = bytes.usePinned { pinned ->
        SecRandomCopyBytes(
            kSecRandomDefault,
            bytes.size.toULong(),
            pinned.addressOf(0).reinterpret<UByteVar>(),
        )
    }
    check(status == errSecSuccess) { "Secure session identity generation failed: $status" }
    return SessionInstanceId(
        bytes.joinToString(separator = "") { byte ->
            byte.toUByte().toString(radix = HEX_RADIX).padStart(length = HEX_BYTE_WIDTH, padChar = '0')
        },
    )
}

/** ARKit runtime context captured at the start of an ARSession delegate event. */
internal fun buildIosTrackingEpoch(
    epochId: ULong,
    sessionInstanceId: SessionInstanceId,
    config: FaceTrackerConfig,
): TrackingContextEpoch = TrackingContextEpoch(
    epochId = epochId,
    sessionInstanceId = sessionInstanceId,
    estimator = EstimatorArtifact(
        family = FaceTrackingBackend.ARKIT,
        libraryVersion = DimensionValue.NotApplicable,
        modelSha256 = DimensionValue.NotApplicable,
    ),
    platform = PlatformIdentity(
        platform = IOS_PLATFORM,
        osVersion = DimensionValue.Known(UIDevice.currentDevice.systemVersion),
        // UIDevice.model is only a generic family name (for example, "iPhone"),
        // not a hardware model that can safely constrain evidence matching.
        deviceModel = DimensionValue.Unknown,
        estimatorConfigurationId = DimensionValue.Known(
            sha256Hex(ARKIT_CONFIGURATION_ID.encodeToByteArray()),
        ),
    ),
    runningMode = DimensionValue.NotApplicable,
    resolvedDelegate = DimensionValue.NotApplicable,
    inputWidth = DimensionValue.NotApplicable,
    inputHeight = DimensionValue.NotApplicable,
    mirroredBeforeInference = DimensionValue.NotApplicable,
    configuredTransforms = config.configuredTransformSpecs().filterNot { it.kind == TransformKind.ENHANCER },
    activeBlendShapes = config.activeBlendShapes,
)

private const val IOS_PLATFORM: String = "ios"
private const val ARKIT_CONFIGURATION_ID: String = "ARFACE_TRACKING_CONFIGURATION_V1|lightEstimation=false"
private const val SESSION_ID_BYTES: Int = 16
private const val HEX_RADIX: Int = 16
private const val HEX_BYTE_WIDTH: Int = 2
