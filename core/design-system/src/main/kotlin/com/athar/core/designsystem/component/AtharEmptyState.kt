package com.athar.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.athar.core.designsystem.theme.AtharTheme

/**
 * Empty-state primitive — a small ember dot above one or two muted lines of copy.
 *
 * Master Brief §4.8 #2: "No empty states with cartoon illustrations. Say something
 * useful in two lines." The single ember dot replaces decoration without becoming one.
 */
@Composable
fun AtharEmptyState(
    text: String,
    modifier: Modifier = Modifier,
    subtle: String? = null,
) {
    val theme = AtharTheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = theme.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(theme.colors.ember),
        )
        AtharText(
            text = text,
            style = theme.typography.body,
            color = theme.colors.muted,
            textAlign = TextAlign.Center,
        )
        if (subtle != null) {
            AtharText(
                text = subtle,
                style = theme.typography.caption,
                color = theme.colors.muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewEmpty() {
    AtharTheme {
        AtharEmptyState(
            text = "لا حركات اليوم.",
            subtle = "أضف بعض الحركات أو اسمح بقراءة الرسائل.",
        )
    }
}
