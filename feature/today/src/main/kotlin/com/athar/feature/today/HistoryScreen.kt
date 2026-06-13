package com.athar.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharListRow
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val theme = AtharTheme
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val categoryLabels by viewModel.categoryLabels.collectAsStateWithLifecycle()
    val backfill by viewModel.lastBackfill.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(backfill) {
        if (backfill != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearBackfill()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.s),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                BackChip(onBack)
                AtharText(
                    text = stringResource(R.string.history_overline),
                    style = theme.typography.overline,
                    color = theme.colors.muted,
                )
            }

            backfill?.let {
                CategoryBackfillToast(
                    pattern = it.pattern,
                    count = it.count,
                    onDismiss = viewModel::clearBackfill,
                )
            }

            AtharTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = stringResource(R.string.history_search_label),
                placeholder = stringResource(R.string.history_search_placeholder),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Text,
            )

            FilterChipRow(
                labels = listOf(
                    HistoryStatusFilter.ALL to stringResource(R.string.history_filter_all),
                    HistoryStatusFilter.CONFIRMED to stringResource(R.string.history_filter_confirmed),
                    HistoryStatusFilter.PENDING to stringResource(R.string.history_filter_pending),
                    HistoryStatusFilter.DISMISSED to stringResource(R.string.history_filter_dismissed),
                ),
                selected = status,
                onSelect = viewModel::setStatus,
            )

            FilterChipRow(
                labels = listOf(
                    HistoryTypeFilter.ALL to stringResource(R.string.history_type_all),
                    HistoryTypeFilter.EXPENSE to stringResource(R.string.history_type_expense),
                    HistoryTypeFilter.INCOME to stringResource(R.string.history_type_income),
                    HistoryTypeFilter.TRANSFER to stringResource(R.string.history_type_transfer),
                ),
                selected = type,
                onSelect = viewModel::setType,
            )

            FilterChipRow(
                labels = listOf(
                    HistorySourceFilter.ALL to stringResource(R.string.history_source_all),
                    HistorySourceFilter.SMS to stringResource(R.string.history_source_sms),
                    HistorySourceFilter.NOTIFICATION to stringResource(R.string.history_source_notification),
                    HistorySourceFilter.MANUAL to stringResource(R.string.history_source_manual),
                    HistorySourceFilter.IMPORT to stringResource(R.string.history_source_import),
                    HistorySourceFilter.RECURRING to stringResource(R.string.history_source_recurring),
                    HistorySourceFilter.SHARE to stringResource(R.string.history_source_share),
                ),
                selected = source,
                onSelect = viewModel::setSource,
            )

            FilterChipRow(
                labels = listOf(
                    HistoryCategoryFilter.ALL to stringResource(R.string.history_category_all),
                    HistoryCategoryFilter.UNCATEGORIZED to stringResource(R.string.history_category_uncategorized),
                    HistoryCategoryFilter.CATEGORIZED to stringResource(R.string.history_category_categorized),
                ),
                selected = category,
                onSelect = viewModel::setCategory,
            )

            AtharText(
                text = stringResource(R.string.history_count, items.size),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )

            if (items.isEmpty()) {
                AtharCard {
                    AtharText(
                        text = stringResource(R.string.history_empty),
                        style = theme.typography.body,
                        color = theme.colors.muted,
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(theme.spacing.xs)) {
                    items(items, key = { it.id }) { tx ->
                        TxRow(
                            tx = tx,
                            categoryLabel = transactionCategoryLabel(tx, categoryLabels),
                            onClick = { editing = tx },
                        )
                    }
                }
            }
        }
    }

    editing?.let { tx ->
        key(tx.id) {
            EditTransactionSheet(
                transaction = tx,
                onDismiss = { editing = null },
                onSave = { updated, learnRule ->
                    viewModel.updateTransaction(updated, learnRule)
                    editing = null
                },
                onDelete = { id ->
                    viewModel.deleteTransaction(id)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun TxRow(tx: Transaction, categoryLabel: String, onClick: () -> Unit) {
    val theme = AtharTheme
    val statusTint = when (tx.status) {
        TxStatus.CONFIRMED -> theme.colors.olive
        TxStatus.PENDING -> theme.colors.dust
        TxStatus.DISMISSED -> theme.colors.muted
    }
    val typeChip = when (tx.type) {
        TxType.INCOME -> "+"
        TxType.EXPENSE -> "-"
        TxType.TRANSFER -> "↔"
    }
    AtharListRow(
        title = tx.merchant,
        subtitle = stringResource(
            R.string.history_row_subtitle,
            tx.date.toString(),
            typeChip,
            statusLabel(tx.status),
            sourceLabel(tx.source),
            categoryLabel,
        ),
        trailing = tx.amount,
        onClick = onClick,
    )
}

@Composable
private fun <T> FilterChipRow(
    labels: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(theme.spacing.xs),
    ) {
        labels.forEach { (key, label) ->
            val isSel = key == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(if (isSel) theme.colors.ember else theme.colors.surface)
                    .clickable { onSelect(key) }
                    .padding(horizontal = theme.spacing.s, vertical = theme.spacing.xs),
            ) {
                AtharText(
                    text = label,
                    style = theme.typography.caption,
                    color = if (isSel) theme.colors.parchment else theme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: TxStatus): String = when (status) {
    TxStatus.CONFIRMED -> stringResource(R.string.history_filter_confirmed)
    TxStatus.PENDING -> stringResource(R.string.history_filter_pending)
    TxStatus.DISMISSED -> stringResource(R.string.history_filter_dismissed)
}

@Composable
private fun sourceLabel(source: IngestSource): String = when (source) {
    IngestSource.SMS -> stringResource(R.string.history_source_sms)
    IngestSource.NOTIFICATION -> stringResource(R.string.history_source_notification)
    IngestSource.MANUAL -> stringResource(R.string.history_source_manual)
    IngestSource.IMPORT -> stringResource(R.string.history_source_import)
    IngestSource.RECURRING -> stringResource(R.string.history_source_recurring)
    IngestSource.SHARE -> stringResource(R.string.history_source_share)
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    val theme = AtharTheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.surface)
            .clickable(onClick = onBack)
            .padding(horizontal = theme.spacing.s, vertical = theme.spacing.xs),
    ) {
        AtharText(
            text = stringResource(R.string.history_back),
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
    }
}
