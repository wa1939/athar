package com.athar.feature.widgets

import androidx.compose.ui.graphics.Color

/**
 * Athar's design tokens, duplicated here for Glance widgets because Glance
 * doesn't share Compose's MaterialTheme tree with the host app. Keep in sync
 * with `core/design-system/.../AtharTheme.kt` palette — update both if either
 * changes (see `Athar_Master_Brief.md` §2.4).
 */
internal object WidgetColors {
    val Ink = Color(0xFF0E0F12)
    val Parchment = Color(0xFFF4F1EB)
    val Surface = Color(0xFFFFFFFF)
    val Muted = Color(0xFF6B6B68)
    val Divider = Color(0xFFE5E2DB)
    val Ember = Color(0xFFC2541C)
    val Olive = Color(0xFF5C6B3A)
    val Dust = Color(0xFFB58A2C)
    val Crimson = Color(0xFF8E1F1F)
}
