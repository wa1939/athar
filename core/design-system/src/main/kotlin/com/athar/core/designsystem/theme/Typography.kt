package com.athar.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.athar.core.designsystem.R

/**
 * Thmanyah typeface families — Master Brief §2.4 (revised 2026-05-24).
 *
 * - [ThmanyahSans]: Arabic + Latin UI body. Default for every surface.
 * - [ThmanyahSerifDisplay]: landmark numbers (Today hero figure, Trends headlines).
 * - [ThmanyahSerifText]: long-form (activity log, ADRs in-app).
 *
 * TODO(typography): verify OTF includes `tnum`/`lnum` opentype features.
 *   If absent, swap [TabularFigures] usage for a monospace fallback.
 */
val ThmanyahSans = FontFamily(
    Font(R.font.thmanyahsans_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.thmanyahsans_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.thmanyahsans_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.thmanyahsans_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.thmanyahsans_black, FontWeight.Black, FontStyle.Normal),
)

val ThmanyahSerifDisplay = FontFamily(
    Font(R.font.thmanyahserifdisplay_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.thmanyahserifdisplay_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.thmanyahserifdisplay_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.thmanyahserifdisplay_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.thmanyahserifdisplay_black, FontWeight.Black, FontStyle.Normal),
)

val ThmanyahSerifText = FontFamily(
    Font(R.font.thmanyahseriftext_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.thmanyahseriftext_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.thmanyahseriftext_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.thmanyahseriftext_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.thmanyahseriftext_black, FontWeight.Black, FontStyle.Normal),
)

/** OpenType features for tabular money columns. Master Brief §2.4. */
const val TabularFigures: String = "tnum, lnum"

/**
 * Athar type scale — locked sizes 11 · 13 · 15 · 17 · 22 · 28 · 40 · 64.
 * Body default is 15sp. Landmark figure (Today hero) is 64sp.
 */
@Immutable
data class AtharTypography(
    val landmark: TextStyle,       // 64sp · Serif Display · single hero number
    val display: TextStyle,        // 40sp · Sans · headline
    val title: TextStyle,          // 28sp · Sans · section header
    val subtitle: TextStyle,       // 22sp · Sans · screen subtitle
    val headline: TextStyle,       // 17sp · Sans Medium · row title
    val body: TextStyle,           // 15sp · Sans · default body
    val caption: TextStyle,        // 13sp · Sans · metadata
    val overline: TextStyle,       // 11sp · Sans Medium · ALL-CAPS labels
    val money: TextStyle,          // 17sp · Sans + tabular figures · inline money
    val moneyLandmark: TextStyle,  // 64sp · Serif Display + tabular · Today hero figure
)

internal val AtharTypographyDefaults = AtharTypography(
    landmark = TextStyle(
        fontFamily = ThmanyahSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        fontFeatureSettings = TabularFigures,
    ),
    display = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 40.sp,
        lineHeight = 48.sp,
    ),
    title = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    subtitle = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    headline = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    body = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    caption = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    overline = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    money = TextStyle(
        fontFamily = ThmanyahSans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontFeatureSettings = TabularFigures,
    ),
    moneyLandmark = TextStyle(
        fontFamily = ThmanyahSerifDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        fontFeatureSettings = TabularFigures,
    ),
)

/** Bridge to Material 3 Typography for components that consume MaterialTheme.typography. */
internal fun AtharTypography.toMaterial(): Typography = Typography(
    displayLarge = display,
    displayMedium = display,
    displaySmall = title,
    headlineLarge = title,
    headlineMedium = subtitle,
    headlineSmall = headline,
    titleLarge = subtitle,
    titleMedium = headline,
    titleSmall = body.copy(fontWeight = FontWeight.Medium),
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = headline.copy(fontWeight = FontWeight.Medium),
    labelMedium = caption,
    labelSmall = overline,
)
