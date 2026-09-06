package io.github.kmpfacelink.internal

import android.os.Build
import com.google.mediapipe.tasks.core.Delegate
import io.github.kmpfacelink.api.PlatformContext
import io.github.kmpfacelink.model.DimensionValue
import io.github.kmpfacelink.model.EstimatorArtifact
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.FaceTrackingBackend
import io.github.kmpfacelink.model.PlatformIdentity
import io.github.kmpfacelink.model.SessionInstanceId
import io.github.kmpfacelink.model.TrackingContextEpoch
import java.security.MessageDigest
import java.security.SecureRandom

/** Non-biometric tracker-instance identity. Tests can inject a deterministic value. */
internal fun newSecureSessionInstanceId(): SessionInstanceId {
    val bytes = ByteArray(SESSION_ID_BYTES)
    SecureRandom().nextBytes(bytes)
    return SessionInstanceId(bytes.toHex())
}

/** SHA-256 of the exact model asset loaded by MediaPipe. */
internal fun modelAssetSha256(
    platformContext: PlatformContext,
    modelAssetPath: String,
): String {
    val digest = MessageDigest.getInstance(SHA_256)
    platformContext.context.assets.open(modelAssetPath).use { input ->
        val buffer = ByteArray(HASH_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

/** Builds the immutable Android context that is captured before one input submission. */
@Suppress("LongParameterList")
internal fun buildAndroidTrackingEpoch(
    epochId: ULong,
    sessionInstanceId: SessionInstanceId,
    config: FaceTrackerConfig,
    resolvedDelegate: Delegate,
    modelSha256: String,
    inputWidth: Int,
    inputHeight: Int,
    mirroredBeforeInference: Boolean,
): TrackingContextEpoch = TrackingContextEpoch(
    epochId = epochId,
    sessionInstanceId = sessionInstanceId,
    estimator = EstimatorArtifact(
        family = FaceTrackingBackend.MEDIAPIPE,
        libraryVersion = DimensionValue.Known(MEDIAPIPE_TASKS_VERSION),
        modelSha256 = DimensionValue.Known(modelSha256),
    ),
    platform = PlatformIdentity(
        platform = ANDROID_PLATFORM,
        osVersion = DimensionValue.Known("sdk=${Build.VERSION.SDK_INT};release=${Build.VERSION.RELEASE}"),
        deviceModel = DimensionValue.Known("${Build.MANUFACTURER}/${Build.MODEL}"),
        estimatorConfigurationId = DimensionValue.Known(config.estimatorConfigurationFingerprint()),
    ),
    runningMode = DimensionValue.Known(config.performanceConfig.runningMode),
    resolvedDelegate = DimensionValue.Known(resolvedDelegate.name),
    inputWidth = DimensionValue.Known(inputWidth),
    inputHeight = DimensionValue.Known(inputHeight),
    mirroredBeforeInference = DimensionValue.Known(mirroredBeforeInference),
    configuredTransforms = config.configuredTransformSpecs(),
    activeBlendShapes = config.activeBlendShapes,
)

private fun FaceTrackerConfig.estimatorConfigurationFingerprint(): String = sha256(
    buildString {
        append("MEDIAPIPE_FACE_LANDMARKER_OPTIONS_V1|")
        append(modelAssetPath).append('|')
        append(detectionConfig.minDetectionConfidence.bits()).append('|')
        append(detectionConfig.minTrackingConfidence.bits()).append('|')
        append(detectionConfig.minPresenceConfidence.bits()).append('|')
        append(detectionConfig.maxFaces)
    },
)

private fun Float.bits(): String = toRawBits().toUInt().toString(radix = HEX_RADIX)

private fun sha256(value: String): String =
    MessageDigest.getInstance(SHA_256).digest(value.encodeToByteArray()).toHex()

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    byte.toUByte().toString(radix = HEX_RADIX).padStart(length = HEX_BYTE_WIDTH, padChar = '0')
}

private const val ANDROID_PLATFORM: String = "android"
private const val MEDIAPIPE_TASKS_VERSION: String = "0.10.35"
private const val SHA_256: String = "SHA-256"
private const val SESSION_ID_BYTES: Int = 16
private const val HASH_BUFFER_BYTES: Int = 8192
private const val HEX_RADIX: Int = 16
private const val HEX_BYTE_WIDTH: Int = 2
