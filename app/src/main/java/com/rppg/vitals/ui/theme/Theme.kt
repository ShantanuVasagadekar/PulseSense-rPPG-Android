package com.rppg.vitals.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ──────────────── Dark Health-Tech Color Palette ────────────────

// Background tones
val BackgroundDeep = Color(0xFF080D0D)
val BackgroundCard = Color(0xFF0F1A1A)
val BackgroundElevated = Color(0xFF152020)
val BackgroundSurface = Color(0xFF1A2828)

// Accent: Cyan/Teal health-tech
val AccentPrimary = Color(0xFF00D4AA)      // Vibrant teal
val AccentSecondary = Color(0xFF00B894)    // Muted teal
val AccentGlow = Color(0xFF00FFD0)         // Bright for glow effects
val AccentSubtle = Color(0xFF1A4040)       // Subtle teal for backgrounds

// Status colors
val StatusGood = Color(0xFF00D4AA)         // Teal = GOOD
val StatusFair = Color(0xFFF39C12)         // Amber = FAIR
val StatusPoor = Color(0xFFE74C3C)         // Red = POOR
val StatusNoSignal = Color(0xFF636E72)     // Gray = NO SIGNAL

// Text
val TextPrimary = Color(0xFFF0FAFA)        // Near-white with cool tint
val TextSecondary = Color(0xFF8FA8A8)      // Muted
val TextTertiary = Color(0xFF4D6666)       // Very muted

// On-device badge
val BadgeGreen = Color(0xFF00B894)
val BadgeBg = Color(0xFF0D2020)

// Error
val ErrorColor = Color(0xFFE74C3C)

// ──────────────── Material 3 Dark Scheme ────────────────

private val DarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = BackgroundDeep,
    primaryContainer = AccentSubtle,
    onPrimaryContainer = AccentGlow,
    secondary = AccentSecondary,
    onSecondary = BackgroundDeep,
    background = BackgroundDeep,
    onBackground = TextPrimary,
    surface = BackgroundCard,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundElevated,
    onSurfaceVariant = TextSecondary,
    error = ErrorColor,
    onError = Color.White,
    outline = AccentSubtle,
)

@Composable
fun RPPGVitalsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = VitalsTypography,
        content = content
    )
}
