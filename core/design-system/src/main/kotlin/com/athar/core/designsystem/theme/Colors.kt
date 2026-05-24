package com.athar.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Athar palette tokens — Master Brief §2.4 (revised 2026-05-24).
 *
 * The accent (`ember`) is brand gold matching the Athar wordmark dots motif. The token
 * name predates the finalized brand; the rendered color is `#B8893C`.
 *
 * Rules: one accent per screen. Olive/dust/crimson are STATE colors, not decorative.
 * Dark mode flips ink ↔ parchment but preserves ember.
 */
@Immutable
data class AtharColors(
    val ink: Color,
    val parchment: Color,
    val surface: Color,
    val muted: Color,
    val divider: Color,
    val ember: Color,
    val olive: Color,
    val dust: Color,
    val crimson: Color,
    val isDark: Boolean,
)

internal val AtharLightColors = AtharColors(
    ink = Color(0xFF0E0F12),
    parchment = Color(0xFFF4F1EB),
    surface = Color(0xFFFFFFFF),
    muted = Color(0xFF6B6B68),
    divider = Color(0xFFE5E2DB),
    ember = Color(0xFFB8893C), // brand gold from the Athar logo
    olive = Color(0xFF5C6B3A),
    dust = Color(0xFFB58A2C),
    crimson = Color(0xFF8E1F1F),
    isDark = false,
)

internal val AtharDarkColors = AtharColors(
    ink = Color(0xFFF4F1EB),       // flipped — text on dark
    parchment = Color(0xFF0E0F12), // flipped — background
    surface = Color(0xFF161719),
    muted = Color(0xFF8E8E8B),
    divider = Color(0xFF24252A),
    ember = Color(0xFFD0A055),     // brand gold lifted for dark surfaces
    olive = Color(0xFF7D8C56),
    dust = Color(0xFFC9A046),
    crimson = Color(0xFFB23434),
    isDark = true,
)
