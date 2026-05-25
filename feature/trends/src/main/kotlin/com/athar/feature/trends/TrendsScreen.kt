package com.athar.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athar.core.designsystem.component.AtharBarChart
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharMonthlyChart
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.theme.AtharTheme

@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drilldown by viewModel.drilldown.collectAsStateWithLifecycle()
    TrendsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBarTapped = viewModel::openDrilldown,
        modifier = modifier,
    )
    drilldown?.let { d ->
        DrilldownSheet(state = d, onDismiss = viewModel::closeDrilldown)
    }
}

@Composable
private fun TrendsContent(
    state: TrendsState,
    onEvent: (TrendsEvent) -> Unit,
    onBarTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = AtharTheme
    Box(modifier = modifier
        .fillMaxSize()
        .background(theme.colors.parchment)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.l),
        ) {
            AtharText(text = "النمط", style = theme.typography.overline, color = theme.colors.muted)

            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(PeriodKey.MONTH, "شهر"),
                    AtharSegment(PeriodKey.MONTHS_3, "٣ أشهر"),
                    AtharSegment(PeriodKey.YEAR, "سنة"),
                    AtharSegment(PeriodKey.MONTH_VS_PREVIOUS, "مقارنة"),
                ),
                selected = state.periodKey,
                onSelect = { onEvent(TrendsEvent.SelectPeriod(it)) },
            )

            SummaryCard(state = state)
            IncomeExpenseSavingsCard(state = state)
            if (state.periodKey == PeriodKey.MONTH_VS_PREVIOUS && state.categoryDeltas.isNotEmpty()) {
                CategoryComparisonTable(state = state)
            }

            if (state.categories.isEmpty() && !state.isLoading) {
                AtharEmptyState(
                    text = "أضف بعض الحركات لرؤية النمط.",
                    subtle = "كل حركة مؤكدة تُضاف إلى الرسم البياني.",
                )
            } else {
                AtharCard {
                    AtharText(text = "أعلى التصنيفات", style = theme.typography.headline)
                    AtharText(
                        text = "اضغط على بطاقة لرؤية تطور التصنيف خلال آخر ١٢ شهرًا.",
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                    AtharBarChart(
                        items = state.categories,
                        modifier = Modifier.padding(top = theme.spacing.s),
                        onItemClick = onBarTapped,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "المصاريف", style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.totalExpense, landmark = true)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                val delta = state.expenseDelta
                val pct = state.expenseDeltaPercent
                if (delta == null || pct == null) {
                    AtharText(
                        text = "لا مقارنة (لا توجد بيانات سابقة)",
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                } else {
                    val isOver = delta.isPositive()
                    val color = if (isOver) theme.colors.ember else theme.colors.olive
                    val arrow = if (isOver) "↑" else "↓"
                    val absPct = String.format("%.0f%%", kotlin.math.abs(pct))
                    AtharText(text = "$arrow $absPct", style = theme.typography.headline, color = color)
                    AtharText(text = "مقارنة بالفترة السابقة", style = theme.typography.caption, color = theme.colors.muted)
                }
            }
        }
    }
}

@Composable
private fun IncomeExpenseSavingsCard(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "الدخل · المصاريف · الادخار", style = theme.typography.headline)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = "الدخل", style = theme.typography.caption, color = theme.colors.muted)
                    AtharNumber(money = state.totalIncome, color = theme.colors.olive)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = "المصاريف", style = theme.typography.caption, color = theme.colors.muted)
                    AtharNumber(money = state.totalExpense, color = theme.colors.ember)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = "الادخار", style = theme.typography.caption, color = theme.colors.muted)
                    val savingsColor = if (state.savings.isNegative()) theme.colors.ember else theme.colors.olive
                    AtharNumber(money = state.savings, color = savingsColor)
                }
            }
            state.savingsRate?.let { rate ->
                AtharText(
                    text = "نسبة الادخار: ${"%.1f".format(rate)}٪",
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun CategoryComparisonTable(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = "مقارنة شهرية · بالتصنيف", style = theme.typography.headline)
            AtharText(
                text = "هذا الشهر مقابل الشهر السابق. الزيادة بالأحمر، الانخفاض بالزيتي.",
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            state.categoryDeltas.take(15).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AtharText(text = row.labelAr, style = theme.typography.body)
                        AtharText(text = row.labelEn, style = theme.typography.caption, color = theme.colors.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AtharNumber(money = row.currentTotal)
                        val delta = row.deltaAmount
                        val deltaColor = when {
                            delta.isPositive() -> theme.colors.ember
                            delta.isNegative() -> theme.colors.olive
                            else -> theme.colors.muted
                        }
                        val sign = if (delta.isPositive()) "+" else if (delta.isNegative()) "−" else "·"
                        val absDelta = if (delta.isNegative()) -delta else delta
                        val pctText = row.deltaPercent?.let { String.format(" (%+.0f%%)", it) }.orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AtharText(text = sign, style = theme.typography.caption, color = deltaColor)
                            AtharNumber(money = absDelta, color = deltaColor)
                            if (pctText.isNotEmpty()) {
                                AtharText(text = pctText, style = theme.typography.caption, color = deltaColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrilldownSheet(
    state: DrilldownState,
    onDismiss: () -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            AtharText(text = state.categoryLabelAr, style = theme.typography.title)
            AtharText(text = "خلال آخر ١٢ شهرًا", style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.total, landmark = true)
            AtharMonthlyChart(bars = state.bars)
        }
    }
}
