package com.athar.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.common.money.Money
import com.athar.core.designsystem.component.AtharAmountField
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import com.athar.core.domain.model.InvestmentPool
import java.math.BigDecimal
import java.util.UUID

@Composable
fun InvestmentsContent(viewModel: InvestmentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme = AtharTheme
    var creatingPool by remember { mutableStateOf(false) }
    var addContributorTo by remember { mutableStateOf<PoolRow?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
        AtharCard {
            Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharText(text = stringResource(R.string.plan_investments_title), style = theme.typography.headline)
                AtharText(
                    text = stringResource(R.string.plan_investments_subtitle),
                    style = theme.typography.body,
                    color = theme.colors.muted,
                )
                PrimaryButton(text = stringResource(R.string.plan_investments_add_pool), onClick = { creatingPool = true })
            }
        }

        if (state.pools.isEmpty() && !state.isLoading) {
            AtharText(
                text = stringResource(R.string.plan_investments_empty),
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        } else {
            state.pools.forEach { row ->
                PoolCard(
                    row = row,
                    onAddContributor = { addContributorTo = row },
                    onDeletePool = { viewModel.onEvent(InvestmentsEvent.DeletePool(row.pool.id)) },
                    onDeleteContributor = { id -> viewModel.onEvent(InvestmentsEvent.DeleteContribution(id)) },
                    onUpdatePercent = { pct -> viewModel.onEvent(InvestmentsEvent.UpdatePoolReturnPercent(row.pool.id, pct)) },
                )
            }
        }
    }

    if (creatingPool) {
        PoolEditor(onDismiss = { creatingPool = false }, onSave = {
            viewModel.onEvent(InvestmentsEvent.SavePool(it))
            creatingPool = false
        })
    }
    addContributorTo?.let { pool ->
        ContributorEditor(
            pool = pool,
            onDismiss = { addContributorTo = null },
            onSave = { owner, amount ->
                viewModel.onEvent(InvestmentsEvent.SaveContribution(pool.pool.id, owner, amount))
                addContributorTo = null
            },
        )
    }
}

@Composable
private fun PoolCard(
    row: PoolRow,
    onAddContributor: () -> Unit,
    onDeletePool: () -> Unit,
    onDeleteContributor: (String) -> Unit,
    onUpdatePercent: (Double) -> Unit,
) {
    val theme = AtharTheme
    var editingReturnPct by remember(row.pool.id) { mutableStateOf(false) }
    var confirmingDelete by remember(row.pool.id) { mutableStateOf(false) }
    val pctOfCorpus: Double = if (row.totalCorpus.amount.signum() == 0) 0.0
        else row.pool.totalReturn.amount.toDouble() / row.totalCorpus.amount.toDouble() * 100.0
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtharText(text = row.pool.name, style = theme.typography.headline, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(theme.spacing.s))
                        .clickable { confirmingDelete = true }
                        .padding(theme.spacing.xs),
                ) {
                    AtharText(text = stringResource(R.string.plan_investments_pool_delete), style = theme.typography.caption, color = theme.colors.crimson)
                }
            }
            AtharText(
                text = stringResource(R.string.plan_investments_pool_period, row.pool.period),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharNumber(money = row.totalCorpus)
                AtharText(text = stringResource(R.string.plan_investments_pool_total_corpus), style = theme.typography.caption, color = theme.colors.muted)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingReturnPct = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                AtharNumber(money = row.pool.totalReturn, color = theme.colors.olive)
                AtharText(
                    text = stringResource(
                        R.string.plan_investments_pool_total_return,
                        "%.2f".format(pctOfCorpus),
                    ),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
            row.contributors.forEach { c ->
                ContributorRowView(contributor = c, onDelete = { onDeleteContributor(c.id) })
            }
            PrimaryButton(text = stringResource(R.string.plan_investments_add_contributor), onClick = onAddContributor)
        }
    }

    if (editingReturnPct) {
        ReturnPercentEditor(
            currentPercent = pctOfCorpus,
            onDismiss = { editingReturnPct = false },
            onSave = { pct ->
                onUpdatePercent(pct)
                editingReturnPct = false
            },
        )
    }
    if (confirmingDelete) {
        DeleteConfirmDialog(
            title = stringResource(R.string.plan_investments_delete_pool_title, row.pool.name),
            body = stringResource(R.string.plan_investments_delete_pool_body),
            onConfirm = {
                confirmingDelete = false
                onDeletePool()
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

@Composable
private fun ContributorRowView(contributor: ContributorRow, onDelete: () -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AtharText(text = contributor.name, style = theme.typography.body)
            AtharText(
                text = "%.1f%%".format(contributor.sharePercent),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AtharNumber(money = contributor.netTotal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AtharText(text = stringResource(R.string.plan_investments_contributor_return_label), style = theme.typography.caption, color = theme.colors.muted)
                AtharNumber(money = contributor.shareReturn, color = theme.colors.olive)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(theme.spacing.s))
                .clickable(onClick = onDelete)
                .padding(start = theme.spacing.s, top = theme.spacing.xs, bottom = theme.spacing.xs),
        ) {
            AtharText(text = stringResource(R.string.plan_investments_contributor_remove), style = theme.typography.caption, color = theme.colors.crimson)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReturnPercentEditor(
    currentPercent: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pctText by remember { mutableStateOf("%.2f".format(currentPercent)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = stringResource(R.string.plan_investments_return_editor_title), style = theme.typography.headline)
            AtharText(
                text = stringResource(R.string.plan_investments_return_editor_hint),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharTextField(pctText, { pctText = it }, label = stringResource(R.string.plan_investments_return_editor_label), modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = stringResource(R.string.plan_investments_return_editor_save),
                onClick = {
                    val pct = pctText.replace(",", ".").toDoubleOrNull()
                    if (pct != null) onSave(pct)
                },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { AtharText(text = title, style = theme.typography.headline) },
        text = { AtharText(text = body, style = theme.typography.body, color = theme.colors.muted) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                AtharText(text = stringResource(R.string.plan_investments_delete_confirm), style = theme.typography.headline, color = theme.colors.crimson)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                AtharText(text = stringResource(R.string.plan_investments_delete_cancel), style = theme.typography.headline, color = theme.colors.muted)
            }
        },
        containerColor = theme.colors.parchment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolEditor(onDismiss: () -> Unit, onSave: (InvestmentPool) -> Unit) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var totalReturn by remember { mutableStateOf("0") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = stringResource(R.string.plan_investments_pool_editor_title), style = theme.typography.headline)
            AtharTextField(name, { name = it }, label = stringResource(R.string.plan_investments_pool_label_name), modifier = Modifier.fillMaxWidth())
            AtharTextField(period, { period = it }, label = stringResource(R.string.plan_investments_pool_label_period), modifier = Modifier.fillMaxWidth())
            AtharAmountField(totalReturn, { totalReturn = it }, label = stringResource(R.string.plan_investments_pool_label_total_return), modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = stringResource(R.string.plan_investments_pool_action_save),
                onClick = {
                    val ret = runCatching { BigDecimal(totalReturn) }.getOrNull() ?: BigDecimal.ZERO
                    if (name.isNotBlank() && period.isNotBlank()) {
                        onSave(
                            InvestmentPool(
                                id = UUID.randomUUID().toString(),
                                name = name.trim(),
                                period = period.trim(),
                                totalReturn = Money.of(ret),
                            ),
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributorEditor(
    pool: PoolRow,
    onDismiss: () -> Unit,
    onSave: (owner: String, amount: Money) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var owner by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = stringResource(R.string.plan_investments_contributor_editor_title, pool.pool.name), style = theme.typography.headline)
            AtharTextField(owner, { owner = it }, label = stringResource(R.string.plan_investments_contributor_label_name), modifier = Modifier.fillMaxWidth())
            AtharAmountField(amount, { amount = it }, label = stringResource(R.string.plan_investments_contributor_label_amount), modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = stringResource(R.string.plan_investments_contributor_action_save),
                onClick = {
                    val amt = runCatching { BigDecimal(amount) }.getOrNull()
                    if (owner.isNotBlank() && amt != null && amt.signum() > 0) {
                        onSave(owner.trim(), Money.of(amt))
                    }
                },
            )
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.ember)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = theme.colors.parchment)
    }
}
