package com.athar.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalAtharColors = staticCompositionLocalOf { AtharLightColors }
private val LocalAtharSpacing = staticCompositionLocalOf { AtharSpacing() }
private val LocalAtharTypography = staticCompositionLocalOf { AtharTypographyDefaults }

object AtharTheme {
    val colors: AtharColors
        @Composable @ReadOnlyComposable get() = LocalAtharColors.current

    val spacing: AtharSpacing
        @Composable @ReadOnlyComposable get() = LocalAtharSpacing.current

    val typography: AtharTypography
        @Composable @ReadOnlyComposable get() = LocalAtharTypography.current
}

@Composable
fun AtharTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val atharColors = if (darkTheme) AtharDarkColors else AtharLightColors
    val materialScheme = atharColors.toMaterial3Scheme()

    CompositionLocalProvider(
        LocalAtharColors provides atharColors,
        LocalAtharSpacing provides AtharSpacing(),
        LocalAtharTypography provides AtharTypographyDefaults,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AtharTypographyDefaults.toMaterial(),
            content = content,
        )
    }
}

private fun AtharColors.toMaterial3Scheme(): ColorScheme {
    val builder = if (isDark) darkColorScheme() else lightColorScheme()
    return builder.copy(
        primary = ember,
        onPrimary = parchment,
        secondary = olive,
        onSecondary = parchment,
        tertiary = dust,
        onTertiary = ink,
        error = crimson,
        onError = parchment,
        background = parchment,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = divider,
        onSurfaceVariant = muted,
        outline = divider,
    )
}
