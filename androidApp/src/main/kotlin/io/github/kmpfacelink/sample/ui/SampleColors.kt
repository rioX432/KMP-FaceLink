package io.github.kmpfacelink.sample.ui

import androidx.compose.ui.graphics.Color

internal object SampleColors {
    val Background = Color(0xFF121212)
    val Surface = Color(0xFF1E1E1E)
    val Overlay = Color.Black.copy(alpha = 0.7f)
    val OverlayLight = Color.Black.copy(alpha = 0.5f)

    val Primary = Color(0xFF4488FF)
    val PrimaryDim = Color(0xFF4488FF).copy(alpha = 0.6f)

    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.8f)
    val TextTertiary = Color.White.copy(alpha = 0.6f)

    val ChipDefault = Color.White.copy(alpha = 0.15f)
    val ChipSelected = Color.White.copy(alpha = 0.35f)

    val BarLow = Color(0xFF51CF66)
    val BarMedium = Color(0xFFFFA94D)
    val BarHigh = Color(0xFFFF6B6B)

    val StatusActive = Color(0xFF51CF66)
    val StatusInactive = Color.Gray

    val LandmarkFace = Color(0xFF00FF88)
    val LandmarkEye = Color(0xFF00CCFF)
    val LandmarkBrow = Color(0xFFFFCC00)
    val LandmarkLip = Color(0xFFFF6688)

    val BoneColor = Color(0xFF00FF88)
    val JointColor = Color(0xFF00CCFF)
    val TipColor = Color(0xFFFF6B6B)

    val ErrorRed = Color(0xFFFF4444)
    val WarningYellow = Color(0xFFFFCC00)
}
