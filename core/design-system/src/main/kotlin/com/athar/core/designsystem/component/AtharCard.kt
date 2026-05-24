package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.athar.core.designsystem.theme.AtharTheme

/**
 * The single surface primitive — a flat parchment-on-surface card with rounded corners.
 *
 * No shadows (Master Brief §2.4). The card lives on top of the parchment background;
 * it gets a slightly different surface tone to lift visually without a drop shadow.
 */
@Composable
fun AtharCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val theme = AtharTheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(theme.colors.surface)
            .padding(theme.spacing.m),
    ) {
        content()
    }
}

@Preview
@Composable
private fun PreviewAtharCard() {
    AtharTheme {
        AtharCard {
            AtharText("Card surface")
        }
    }
}
