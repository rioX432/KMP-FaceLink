package io.github.kmpfacelink.sample.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Adaptive color scheme that provides light and dark variants.
 *
 * Use [SampleColors.current] inside @Composable functions for theme-aware colors.
 * The companion object properties remain available for backward compatibility (dark-mode defaults).
 */
public object SampleColors {
    // Dark theme colors (backward-compatible defaults)
    public val Background: Color = Color(0xFF121212)
    public val Surface: Color = Color(0xFF1E1E1E)
    public val Overlay: Color = Color.Black.copy(alpha = 0.7f)
    public val OverlayLight: Color = Color.Black.copy(alpha = 0.5f)

    public val Primary: Color = Color(0xFF4488FF)
    public val PrimaryDim: Color = Color(0xFF4488FF).copy(alpha = 0.6f)

    public val TextPrimary: Color = Color.White
    public val TextSecondary: Color = Color.White.copy(alpha = 0.8f)
    public val TextTertiary: Color = Color.White.copy(alpha = 0.6f)

    public val ChipDefault: Color = Color.White.copy(alpha = 0.15f)
    public val ChipSelected: Color = Color.White.copy(alpha = 0.35f)

    public val BarLow: Color = Color(0xFF51CF66)
    public val BarMedium: Color = Color(0xFFFFA94D)
    public val BarHigh: Color = Color(0xFFFF6B6B)

    public val StatusActive: Color = Color(0xFF51CF66)
    public val StatusInactive: Color = Color.Gray

    public val LandmarkFace: Color = Color(0xFF00FF88)
    public val LandmarkEye: Color = Color(0xFF00CCFF)
    public val LandmarkBrow: Color = Color(0xFFFFCC00)
    public val LandmarkLip: Color = Color(0xFFFF6688)

    public val BoneColor: Color = Color(0xFF00FF88)
    public val JointColor: Color = Color(0xFF00CCFF)
    public val TipColor: Color = Color(0xFFFF6B6B)

    public val ErrorRed: Color = Color(0xFFFF4444)
    public val WarningYellow: Color = Color(0xFFFFCC00)

    /**
     * Returns theme-aware [SampleColorScheme] based on system dark/light mode.
     */
    public val current: SampleColorScheme
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
}

/**
 * Theme-aware color scheme for sample app surfaces and text.
 */
public data class SampleColorScheme(
    val background: Color,
    val surface: Color,
    val overlay: Color,
    val overlayLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val chipDefault: Color,
    val chipSelected: Color,
)

private val DarkColorScheme = SampleColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    overlay = Color.Black.copy(alpha = 0.7f),
    overlayLight = Color.Black.copy(alpha = 0.5f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.8f),
    textTertiary = Color.White.copy(alpha = 0.6f),
    chipDefault = Color.White.copy(alpha = 0.15f),
    chipSelected = Color.White.copy(alpha = 0.35f),
)

private val LightColorScheme = SampleColorScheme(
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    overlay = Color.White.copy(alpha = 0.85f),
    overlayLight = Color.White.copy(alpha = 0.7f),
    textPrimary = Color(0xFF1A1A1A),
    textSecondary = Color(0xFF1A1A1A).copy(alpha = 0.8f),
    textTertiary = Color(0xFF1A1A1A).copy(alpha = 0.6f),
    chipDefault = Color.Black.copy(alpha = 0.08f),
    chipSelected = Color.Black.copy(alpha = 0.18f),
)
