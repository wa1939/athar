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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.designsystem.theme.MinTouchTarget
import com.athar.core.domain.model.SmsParseStatus
import com.athar.core.domain.repo.SmsAuditEntry

@Composable
fun SmsAuditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsAuditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = AtharTheme
    var filter by remember { mutableStateOf<SmsParseStatus?>(null) }

    val filtered = remember(state.entries, filter) {
        when (filter) {
            null -> state.entries
            else -> state.entries.filter { it.status == filter }
        }
    }

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
                AtharText(text = stringResource(R.string.settings_sms_audit_title), style = theme.typography.headline)
                Box(modifier = Modifier.size(MinTouchTarget))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = theme.spacing.m),
                verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                StatsCard(state = state)

                AtharSegmentedControl(
                    segments = listOf(
                        AtharSegment<SmsParseStatus?>(null, stringResource(R.string.settings_sms_audit_filter_all)),
                        AtharSegment<SmsParseStatus?>(SmsParseStatus.PARSED, stringResource(R.string.settings_sms_audit_filter_parsed)),
                        AtharSegment<SmsParseStatus?>(SmsParseStatus.FAILED, stringResource(R.string.settings_sms_audit_filter_failed)),
                        AtharSegment<SmsParseStatus?>(SmsParseStatus.IGNORED, stringResource(R.string.settings_sms_audit_filter_ignored)),
                    ),
                    selected = filter,
                    onSelect = { filter = it },
                )

                if (filtered.isEmpty()) {
                    AtharEmptyState(
                        text = stringResource(R.string.settings_sms_audit_empty),
                        subtle = stringResource(R.string.settings_sms_audit_empty_subtle),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(theme.spacing.xs),
                    ) {
                        items(filtered, key = { it.id }) { entry ->
                            EntryCard(entry = entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(state: SmsAuditState) {
    val theme = AtharTheme
    AtharCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat(label = stringResource(R.string.settings_sms_audit_filter_parsed), value = state.totalParsed, color = theme.colors.olive)
            Stat(label = stringResource(R.string.settings_sms_audit_filter_failed), value = state.totalFailed, color = theme.colors.ember)
            Stat(label = stringResource(R.string.settings_sms_audit_filter_ignored), value = state.totalIgnored, color = theme.colors.muted)
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    val theme = AtharTheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AtharText(text = value.toString(), style = theme.typography.title, color = color)
        AtharText(text = label, style = theme.typography.caption, color = theme.colors.muted)
    }
}

@Composable
private fun EntryCard(entry: SmsAuditEntry) {
    val theme = AtharTheme
    val accent = when (entry.status) {
        SmsParseStatus.PARSED -> theme.colors.olive
        SmsParseStatus.FAILED -> theme.colors.ember
        SmsParseStatus.IGNORED -> theme.colors.muted
    }
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
                AtharText(text = entry.sender, style = theme.typography.headline)
            }
            AtharText(
                text = entry.body.take(160),
                style = theme.typography.caption,
                color = theme.colors.muted,
                maxLines = 4,
            )
            val errorText = entry.error
            if (errorText != null) {
                AtharText(text = errorText, style = theme.typography.caption, color = theme.colors.crimson)
            }
        }
    }
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

