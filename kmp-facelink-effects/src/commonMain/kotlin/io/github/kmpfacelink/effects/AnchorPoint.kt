package io.github.kmpfacelink.effects

import io.github.kmpfacelink.ExperimentalFaceLinkApi

/**
 * Predefined face anchor points mapped to MediaPipe 478-point face mesh landmark indices.
 *
 * @property landmarkIndex The MediaPipe face mesh landmark index
 * @property label Human-readable label for this anchor point
 */
@ExperimentalFaceLinkApi
@Suppress("MagicNumber")
public enum class AnchorPoint(
    public val landmarkIndex: Int,
    public val label: String,
) {
    FOREHEAD(landmarkIndex = 10, label = "Forehead center"),
    NOSE_TIP(landmarkIndex = 1, label = "Nose tip"),
    NOSE_BRIDGE(landmarkIndex = 6, label = "Nose bridge"),
    CHIN(landmarkIndex = 152, label = "Chin"),
    LEFT_EAR(landmarkIndex = 234, label = "Left ear"),
    RIGHT_EAR(landmarkIndex = 454, label = "Right ear"),
    LEFT_EYE(landmarkIndex = 33, label = "Left eye outer corner"),
    RIGHT_EYE(landmarkIndex = 263, label = "Right eye outer corner"),
    LEFT_CHEEK(landmarkIndex = 50, label = "Left cheek"),
    RIGHT_CHEEK(landmarkIndex = 280, label = "Right cheek"),
    UPPER_LIP(landmarkIndex = 13, label = "Upper lip center"),
    LOWER_LIP(landmarkIndex = 14, label = "Lower lip center"),
    LEFT_TEMPLE(landmarkIndex = 127, label = "Left temple"),
    RIGHT_TEMPLE(landmarkIndex = 356, label = "Right temple"),
}
