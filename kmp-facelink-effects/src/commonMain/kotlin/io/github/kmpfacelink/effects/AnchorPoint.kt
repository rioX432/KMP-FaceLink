package io.github.kmpfacelink.effects

import io.github.kmpfacelink.ExperimentalFaceLinkApi

// MediaPipe 478-point face mesh landmark indices
private const val LANDMARK_FOREHEAD = 10
private const val LANDMARK_NOSE_TIP = 1
private const val LANDMARK_NOSE_BRIDGE = 6
private const val LANDMARK_CHIN = 152
private const val LANDMARK_LEFT_EAR = 234
private const val LANDMARK_RIGHT_EAR = 454
private const val LANDMARK_LEFT_EYE = 33
private const val LANDMARK_RIGHT_EYE = 263
private const val LANDMARK_LEFT_CHEEK = 50
private const val LANDMARK_RIGHT_CHEEK = 280
private const val LANDMARK_UPPER_LIP = 13
private const val LANDMARK_LOWER_LIP = 14
private const val LANDMARK_LEFT_TEMPLE = 127
private const val LANDMARK_RIGHT_TEMPLE = 356

/**
 * Predefined face anchor points mapped to MediaPipe 478-point face mesh landmark indices.
 *
 * @property landmarkIndex The MediaPipe face mesh landmark index
 * @property label Human-readable label for this anchor point
 */
@ExperimentalFaceLinkApi
public enum class AnchorPoint(
    public val landmarkIndex: Int,
    public val label: String,
) {
    FOREHEAD(landmarkIndex = LANDMARK_FOREHEAD, label = "Forehead center"),
    NOSE_TIP(landmarkIndex = LANDMARK_NOSE_TIP, label = "Nose tip"),
    NOSE_BRIDGE(landmarkIndex = LANDMARK_NOSE_BRIDGE, label = "Nose bridge"),
    CHIN(landmarkIndex = LANDMARK_CHIN, label = "Chin"),
    LEFT_EAR(landmarkIndex = LANDMARK_LEFT_EAR, label = "Left ear"),
    RIGHT_EAR(landmarkIndex = LANDMARK_RIGHT_EAR, label = "Right ear"),
    LEFT_EYE(landmarkIndex = LANDMARK_LEFT_EYE, label = "Left eye outer corner"),
    RIGHT_EYE(landmarkIndex = LANDMARK_RIGHT_EYE, label = "Right eye outer corner"),
    LEFT_CHEEK(landmarkIndex = LANDMARK_LEFT_CHEEK, label = "Left cheek"),
    RIGHT_CHEEK(landmarkIndex = LANDMARK_RIGHT_CHEEK, label = "Right cheek"),
    UPPER_LIP(landmarkIndex = LANDMARK_UPPER_LIP, label = "Upper lip center"),
    LOWER_LIP(landmarkIndex = LANDMARK_LOWER_LIP, label = "Lower lip center"),
    LEFT_TEMPLE(landmarkIndex = LANDMARK_LEFT_TEMPLE, label = "Left temple"),
    RIGHT_TEMPLE(landmarkIndex = LANDMARK_RIGHT_TEMPLE, label = "Right temple"),
}
