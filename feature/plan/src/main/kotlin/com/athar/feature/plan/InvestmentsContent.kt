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
                AtharText(text = "الاستثمارات", style = theme.typography.headline)
                AtharText(
                    text = "مجموعة المساهمين، نسبة كل واحد، وعائده النسبي.",
                    style = theme.typography.body,
                    color = theme.colors.muted,
                )
                PrimaryButton(text = "إضافة مجموعة", onClick = { creatingPool = true })
            }
        }

        if (state.pools.isEmpty() && !state.isLoading) {
            AtharText(
                text = "أضف مجموعة استثمارية لرؤية النِّسَب والعوائد.",
                style = theme.typography.body,
                color = theme.colors.muted,
            )
        } else {
            state.pools.forEach { row ->
                PoolCard(row = row, onAddContributor = { addContributorTo = row })
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
private fun PoolCard(row: PoolRow, onAddContributor: () -> Unit) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = row.pool.name, style = theme.typography.headline)
            AtharText(
                text = "الفترة · ${row.pool.period}",
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharNumber(money = row.totalCorpus)
                AtharText(text = "إجمالي رأس المال", style = theme.typography.caption, color = theme.colors.muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                AtharNumber(money = row.pool.totalReturn, color = theme.colors.olive)
                AtharText(text = "إجمالي العائد", style = theme.typography.caption, color = theme.colors.muted)
            }
            row.contributors.forEach { c ->
                ContributorRowView(contributor = c)
            }
            PrimaryButton(text = "إضافة مساهم", onClick = onAddContributor)
        }
    }
}

@Composable
private fun ContributorRowView(contributor: ContributorRow) {
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
                AtharText(text = "عائد ", style = theme.typography.caption, color = theme.colors.muted)
                AtharNumber(money = contributor.shareReturn, color = theme.colors.olive)
            }
        }
    }
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
            AtharText(text = "مجموعة استثمارية جديدة", style = theme.typography.headline)
            AtharTextField(name, { name = it }, label = "الاسم", modifier = Modifier.fillMaxWidth())
            AtharTextField(period, { period = it }, label = "الفترة (مثال: ٣ أشهر)", modifier = Modifier.fillMaxWidth())
            AtharAmountField(totalReturn, { totalReturn = it }, label = "إجمالي العائد", modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = "حفظ",
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
            AtharText(text = "مساهم في ${pool.pool.name}", style = theme.typography.headline)
            AtharTextField(owner, { owner = it }, label = "الاسم", modifier = Modifier.fillMaxWidth())
            AtharAmountField(amount, { amount = it }, label = "المبلغ", modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                text = "حفظ",
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
