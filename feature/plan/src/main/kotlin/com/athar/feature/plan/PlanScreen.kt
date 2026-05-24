package com.athar.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme
import java.math.BigDecimal

enum class PlanTab { BUDGET, WISHLIST, INVESTMENTS }

@Composable
fun PlanScreen(
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }
    var tab by remember { mutableStateOf(PlanTab.BUDGET) }
    val theme = AtharTheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.colors.parchment),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            AtharText(text = "الخطة", style = theme.typography.overline, color = theme.colors.muted)
            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(PlanTab.BUDGET, "الميزانية"),
                    AtharSegment(PlanTab.WISHLIST, "الرغبات"),
                    AtharSegment(PlanTab.INVESTMENTS, "الاستثمارات"),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                PlanTab.BUDGET -> BudgetContent(state = state, onClickRow = { editing = it })
                PlanTab.WISHLIST -> WishlistContent()
                PlanTab.INVESTMENTS -> InvestmentsContent()
            }
        }
    }

    editing?.let { row ->
        TargetEditor(
            row = row,
            onDismiss = { editing = null },
            onSave = { newTargetMinor ->
                viewModel.onEvent(PlanEvent.SaveTarget(row.category.id, newTargetMinor))
                editing = null
            },
        )
    }
}

@Composable
private fun BudgetContent(
    state: PlanState,
    onClickRow: (BudgetRow) -> Unit,
) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
        Header(state = state)
        if (state.rows.isEmpty() && !state.isLoading) {
            AtharEmptyState(
                text = "لا تصنيفات بعد.",
                subtle = "حدد الهدف الشهري لكل تصنيف. اضغط على البطاقة لتعديل.",
            )
        } else {
            state.rows.forEach { row ->
                BudgetRowView(row = row, onClick = { onClickRow(row) })
            }
        }
    }
}

@Composable
private fun Header(state: PlanState) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        AtharText(text = "الخطة", style = theme.typography.overline, color = theme.colors.muted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharNumber(money = state.totalActual)
            AtharText(text = "من", style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.totalTarget, color = theme.colors.muted)
        }
        AtharText(
            text = "${state.month.year}/${state.month.monthValue}",
            style = theme.typography.caption,
            color = theme.colors.muted,
        )
    }
}

@Composable
private fun BudgetRowView(row: BudgetRow, onClick: () -> Unit) {
    val theme = AtharTheme
    AtharCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AtharText(text = row.category.nameAr, style = theme.typography.headline)
                AtharText(text = row.category.name, style = theme.typography.caption, color = theme.colors.muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                AtharNumber(money = row.actual)
                if (row.target != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AtharText(
                            text = "هدف ",
                            style = theme.typography.caption,
                            color = theme.colors.muted,
                        )
                        AtharNumber(money = row.target, color = theme.colors.muted)
                    }
                    VariancePill(row = row)
                } else {
                    AtharText(text = "لا هدف", style = theme.typography.caption, color = theme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun VariancePill(row: BudgetRow) {
    val theme = AtharTheme
    val variance = row.variance ?: return
    val color = when {
        row.isOver -> theme.colors.ember
        row.isUnder -> theme.colors.olive
        else -> theme.colors.muted
    }
    val label = if (row.isOver) "تجاوز " else "تحت الحد "
    Row(verticalAlignment = Alignment.CenterVertically) {
        AtharText(text = label, style = theme.typography.caption, color = color)
        AtharNumber(money = if (variance.isNegative()) -variance else variance, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetEditor(
    row: BudgetRow,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember(row.category.id) {
        mutableStateOf(row.target?.amount?.toPlainString() ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = theme.colors.parchment,
        contentColor = theme.colors.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = "${row.category.nameAr} · هدف شهري", style = theme.typography.headline)
            AtharAmountField(
                value = amount,
                onValueChange = { amount = it },
                modifier = Modifier.fillMaxWidth(),
                label = "المبلغ المستهدف",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(theme.spacing.s),
            ) {
                SheetButton(
                    text = "إلغاء الهدف",
                    background = theme.colors.divider,
                    textColor = theme.colors.ink,
                    onClick = { onSave(null) },
                    modifier = Modifier.weight(1f),
                )
                SheetButton(
                    text = "حفظ",
                    background = theme.colors.ember,
                    textColor = theme.colors.parchment,
                    onClick = {
                        val parsed = runCatching { BigDecimal(amount) }.getOrNull()
                        val minor = parsed?.let { Money.of(it).amount.movePointRight(2).toLong() }
                        onSave(minor)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(background)
            .clickable(onClick = onClick)
            .padding(theme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        AtharText(text = text, style = theme.typography.headline, color = textColor)
    }
}
