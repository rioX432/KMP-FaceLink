package io.github.kmpfacelink.capture

import io.github.kmpfacelink.model.CalibrationConfig
import io.github.kmpfacelink.model.FaceTrackerConfig
import io.github.kmpfacelink.model.InferenceRunningMode
import kotlin.test.Test
import kotlin.test.assertFailsWith

class PerformanceBenchmarkConfigTest {

    @Test
    fun videoIsRejectedUntilRepetitionStateIsPreregistered() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceBenchmarkConfig(
                mode = InferenceRunningMode.VIDEO,
                preferGpu = false,
                expectedResolvedDelegate = "CPU",
                evidence = evidence(),
            )
        }
    }

    @Test
    fun learnedCalibrationIsRejectedFromControlledComputeRun() {
        assertFailsWith<IllegalArgumentException> {
            PerformanceBenchmarkConfig(
                mode = InferenceRunningMode.IMAGE,
                preferGpu = false,
                expectedResolvedDelegate = "CPU",
                evidence = evidence(),
                processingConfig = FaceTrackerConfig(
                    calibrationConfig = CalibrationConfig(enabled = true),
                ),
            )
        }
    }

    private fun evidence() = ReviewEvidenceBinding(
        sourcePacketId = "test",
        sourcePacketManifestSha256 = "a".repeat(64),
        sourceManifestSha256 = "b".repeat(64),
        sourceAuthorizationSha256 = "c".repeat(64),
        binaryPacketId = "binary-test",
        binaryPacketManifestSha256 = "d".repeat(64),
        binarySha256 = "e".repeat(64),
        runAuthorizationSha256 = "f".repeat(64),
        runAuthorizationSigningKeyId = "0".repeat(64),
    )
}
