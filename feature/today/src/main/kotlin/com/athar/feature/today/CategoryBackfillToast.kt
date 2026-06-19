package com.athar.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme

@Composable
internal fun CategoryBackfillToast(
    pattern: String,
    count: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    val text = if (count > 0) {
        stringResource(R.string.category_backfill_applied, count, pattern)
    } else {
        stringResource(R.string.category_backfill_future, pattern)
    }
    AtharCard(modifier = modifier.clickable(onClick = onDismiss)) {
        AtharText(text = text, style = theme.typography.body, color = theme.colors.olive)
    }
}
