package com.athar.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Athar spacing scale — Master Brief §2.4 (locked).
 *
 *   4 · 8 · 12 · 16 · 24 · 32 · 48 · 64. Nothing else.
 *
 * White space is not waste. Touch targets ≥ 48dp.
 */
@Immutable
data class AtharSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

val MinTouchTarget: Dp = 48.dp
val HairlineThickness: Dp = 0.5.dp
