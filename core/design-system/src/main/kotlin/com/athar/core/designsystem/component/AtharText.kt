package com.athar.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Thin Material Text wrapper that defaults to Athar body style and ink color.
 *
 * Use this instead of `androidx.compose.material3.Text` everywhere — it ensures the
 * Thmanyah type system is the default and that color comes from the Athar palette.
 */
@Composable
fun AtharText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AtharTheme.typography.body,
    color: Color = AtharTheme.colors.ink,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        maxLines = maxLines,
    )
}
