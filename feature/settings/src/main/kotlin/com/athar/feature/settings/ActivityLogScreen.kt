package com.athar.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.repo.ActivityAction
import com.athar.core.domain.repo.ActivityLogEntry
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun ActivityLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityLogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = AtharTheme

    Box(modifier = modifier
        .fillMaxSize()
        .background(theme.colors.parchment)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = theme.spacing.m, vertical = theme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BackButton(onClick = onBack)
                AtharText(text = "السجل", style = theme.typography.headline)
                Box(modifier = Modifier.size(MinTouchTarget))
            }

            if (state.entries.isEmpty() && !state.isLoading) {
                AtharEmptyState(
                    text = "لا توجد إجراءات بعد.",
                    subtle = "كل تعديل أو حذف لحركة يُسجَّل هنا.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = theme.spacing.m),
                    verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: ActivityLogEntry) {
    val theme = AtharTheme
    val accent = when (entry.action) {
        ActivityAction.CREATE -> theme.colors.olive
        ActivityAction.UPDATE -> theme.colors.dust
        ActivityAction.DELETE -> theme.colors.crimson
        ActivityAction.CONFIRM -> theme.colors.olive
        ActivityAction.DISMISS -> theme.colors.muted
    }
    val actionLabel = when (entry.action) {
        ActivityAction.CREATE -> "أُنشئت"
        ActivityAction.UPDATE -> "عُدِّلت"
        ActivityAction.DELETE -> "حُذِفت"
        ActivityAction.CONFIRM -> "تأكيد"
        ActivityAction.DISMISS -> "تجاهل"
    }
    AtharCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    AtharText(text = "$actionLabel · ", style = theme.typography.body, color = accent)
                    AtharText(
                        text = entry.summary.ifBlank { "(لا تفاصيل)" },
                        style = theme.typography.body,
                    )
                }
                AtharText(
                    text = formatTime(entry),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
        }
    }
}

private fun formatTime(entry: ActivityLogEntry): String {
    val ld = entry.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ld.year}/${ld.monthNumber.toString().padStart(2, '0')}/${ld.dayOfMonth.toString().padStart(2, '0')} · " +
        "${ld.hour.toString().padStart(2, '0')}:${ld.minute.toString().padStart(2, '0')}"
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .clip(RoundedCornerShape(theme.spacing.s))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = "‹", style = theme.typography.title, color = theme.colors.ink)
    }
}
