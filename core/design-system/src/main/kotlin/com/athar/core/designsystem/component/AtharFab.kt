package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Single floating action button — ember circle, no icon, no label, no shadow.
 *
 * One accent per screen (Master Brief §2.4). The "+" glyph is rendered in parchment
 * on ember; the entire surface is the touch target (≥48dp).
 */
@Composable
fun AtharFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: String = "+",
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(theme.colors.ember)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(
            text = glyph,
            style = theme.typography.title,
            color = theme.colors.parchment,
        )
    }
}

@Preview
@Composable
private fun PreviewFab() {
    AtharTheme {
        AtharFab(onClick = {})
    }
}
