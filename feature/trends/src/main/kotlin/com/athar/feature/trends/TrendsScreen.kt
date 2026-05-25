package com.athar.feature.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.athar.core.designsystem.component.AtharBarChart
import com.athar.core.designsystem.component.AtharCard
import com.athar.core.designsystem.component.AtharComparisonBars
import com.athar.core.designsystem.component.AtharEmptyState
import com.athar.core.designsystem.component.AtharMonthlyChart
import com.athar.core.designsystem.component.AtharMonthlyChartWithLines
import com.athar.core.designsystem.component.AtharNumber
import com.athar.core.designsystem.component.AtharProportionBar
import com.athar.core.designsystem.component.AtharSegment
import com.athar.core.designsystem.component.AtharSegmentedControl
import com.athar.core.designsystem.component.AtharText
import com.athar.core.designsystem.component.AtharTextField
import com.athar.core.designsystem.theme.AtharTheme
import kotlinx.datetime.LocalDate

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
    var showCustomRange by remember { mutableStateOf(false) }
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
            AtharText(text = stringResource(R.string.trends_overline), style = theme.typography.overline, color = theme.colors.muted)

            AtharSegmentedControl(
                segments = listOf(
                    AtharSegment(PeriodKey.WEEK, stringResource(R.string.trends_period_week)),
                    AtharSegment(PeriodKey.MONTH, stringResource(R.string.trends_period_month)),
                    AtharSegment(PeriodKey.MONTHS_3, stringResource(R.string.trends_period_months_3)),
                    AtharSegment(PeriodKey.YEAR, stringResource(R.string.trends_period_year)),
                    AtharSegment(PeriodKey.MONTH_VS_PREVIOUS, stringResource(R.string.trends_period_vs_previous)),
                    AtharSegment(PeriodKey.CUSTOM, stringResource(R.string.trends_period_custom)),
                ),
                selected = state.periodKey,
                onSelect = {
                    if (it == PeriodKey.CUSTOM) showCustomRange = true
                    else onEvent(TrendsEvent.SelectPeriod(it))
                },
            )

            if (state.periodKey == PeriodKey.CUSTOM && state.customStart != null && state.customEnd != null) {
                CustomRangeBanner(
                    start = state.customStart,
                    end = state.customEnd,
                    onEdit = { showCustomRange = true },
                )
            }

            HeadlineNumbersCard(state = state)
            IncomeExpenseSavingsCard(state = state)

            // Period-vs-prior visual: three paired bars + delta table.
            if (state.periodKey == PeriodKey.MONTH_VS_PREVIOUS) {
                PeriodComparisonCard(state = state)
                if (state.categoryDeltas.isNotEmpty()) {
                    CategoryComparisonTable(state = state)
                }
            }

            // TMOAP Dashboard parity — three monthly bar panels with target + average overlays.
            if (state.monthly.expense.any { it.amount.amount.signum() > 0 } ||
                state.monthly.income.any { it.amount.amount.signum() > 0 }
            ) {
                MonthlyDashboardCard(state = state)
            }

            // Category split (horizontal proportional bar — pie replacement).
            if (state.categorySplit.isNotEmpty()) {
                AtharCard {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                        AtharText(text = stringResource(R.string.trends_section_category_split), style = theme.typography.headline)
                        AtharProportionBar(segments = state.categorySplit)
                    }
                }
            }

            if (state.categories.isEmpty() && !state.isLoading) {
                AtharEmptyState(
                    text = stringResource(R.string.trends_empty_text),
                    subtle = stringResource(R.string.trends_empty_subtle),
                )
            } else {
                AtharCard {
                    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                        AtharText(text = stringResource(R.string.trends_section_top_categories), style = theme.typography.headline)
                        AtharText(
                            text = stringResource(R.string.trends_top_categories_hint),
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

    if (showCustomRange) {
        CustomRangePickerSheet(
            initialStart = state.customStart,
            initialEnd = state.customEnd,
            onDismiss = { showCustomRange = false },
            onConfirm = { s, e ->
                onEvent(TrendsEvent.SelectCustomRange(s, e))
                showCustomRange = false
            },
        )
    }
}

@Composable
private fun HeadlineNumbersCard(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = stringResource(R.string.trends_label_expense), style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.totalExpense, landmark = true)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
                val delta = state.expenseDelta
                val pct = state.expenseDeltaPercent
                if (delta == null || pct == null) {
                    AtharText(
                        text = stringResource(R.string.trends_no_comparison),
                        style = theme.typography.caption,
                        color = theme.colors.muted,
                    )
                } else {
                    val isOver = delta.isPositive()
                    val color = if (isOver) theme.colors.ember else theme.colors.olive
                    val arrow = if (isOver) "↑" else "↓"
                    val absPct = String.format("%.0f%%", kotlin.math.abs(pct))
                    AtharText(text = "$arrow $absPct", style = theme.typography.headline, color = color)
                    AtharText(text = stringResource(R.string.trends_vs_previous_period), style = theme.typography.caption, color = theme.colors.muted)
                }
            }
            state.expensePercentOfIncome?.let { pct ->
                AtharText(
                    text = stringResource(R.string.trends_expense_percent_of_income, "%.1f".format(pct)),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun IncomeExpenseSavingsCard(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = stringResource(R.string.trends_income_expense_savings), style = theme.typography.headline)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = stringResource(R.string.trends_label_income), style = theme.typography.caption, color = theme.colors.muted)
                    AtharNumber(money = state.totalIncome, color = theme.colors.olive)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = stringResource(R.string.trends_label_expense), style = theme.typography.caption, color = theme.colors.muted)
                    AtharNumber(money = state.totalExpense, color = theme.colors.ember)
                }
                Column(horizontalAlignment = Alignment.Start) {
                    AtharText(text = stringResource(R.string.trends_label_savings), style = theme.typography.caption, color = theme.colors.muted)
                    val savingsColor = if (state.savings.isNegative()) theme.colors.ember else theme.colors.olive
                    AtharNumber(money = state.savings, color = savingsColor)
                }
            }
            state.savingsRate?.let { rate ->
                AtharText(
                    text = stringResource(R.string.trends_savings_rate, "%.1f".format(rate)),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun PeriodComparisonCard(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = stringResource(R.string.trends_period_comparison), style = theme.typography.headline)
            AtharText(
                text = stringResource(R.string.trends_period_comparison_legend),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharComparisonBars(
                currentIncome = state.totalIncome,
                previousIncome = state.previousIncome,
                currentExpense = state.totalExpense,
                previousExpense = state.previousExpense,
                currentSavings = state.savings,
                previousSavings = state.previousSavings,
            )
            DeltaRow(label = stringResource(R.string.trends_delta_income), value = state.incomeDelta, theme = theme, positiveIsGood = true)
            DeltaRow(label = stringResource(R.string.trends_delta_expense), value = state.expenseDelta, theme = theme, positiveIsGood = false)
            DeltaRow(label = stringResource(R.string.trends_delta_savings), value = state.savingsDelta, theme = theme, positiveIsGood = true)
            val curRate = state.savingsRate
            val prevRate = state.previousSavingsRate
            if (curRate != null && prevRate != null) {
                AtharText(
                    text = stringResource(
                        R.string.trends_savings_rate_comparison,
                        "%.1f".format(curRate),
                        "%.1f".format(prevRate),
                    ),
                    style = theme.typography.caption,
                    color = theme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun DeltaRow(
    label: String,
    value: com.athar.core.common.money.Money?,
    theme: Any, // unused — kept for signature compatibility, recomputed inline below
    positiveIsGood: Boolean,
) {
    val t = AtharTheme
    if (value == null) return
    val isPositive = value.isPositive()
    val isGood = (isPositive && positiveIsGood) || (!isPositive && !positiveIsGood)
    val color = when {
        value.isZero() -> t.colors.muted
        isGood -> t.colors.olive
        else -> t.colors.ember
    }
    val sign = when {
        value.isZero() -> "·"
        value.isPositive() -> "+"
        else -> "−"
    }
    val absValue = if (value.isNegative()) -value else value
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(t.spacing.xs)) {
        AtharText(text = label, style = t.typography.caption, color = t.colors.muted, modifier = Modifier.weight(1f))
        AtharText(text = sign, style = t.typography.caption, color = color)
        AtharNumber(money = absValue, color = color)
    }
}

@Composable
private fun MonthlyDashboardCard(state: TrendsState) {
    val theme = AtharTheme
    val m = state.monthly
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.l)) {
            AtharText(text = stringResource(R.string.trends_last_12_months), style = theme.typography.headline)
            // Income panel
            MonthlyPanel(
                title = stringResource(R.string.trends_monthly_income),
                bars = m.income,
                color = theme.colors.olive,
                target = m.targetIncome,
                average = m.averageIncome,
                isExpense = false,
            )
            // Expense panel
            MonthlyPanel(
                title = stringResource(R.string.trends_monthly_expense),
                bars = m.expense,
                color = theme.colors.ember,
                target = m.targetExpense,
                average = m.averageExpense,
                isExpense = true,
            )
            // Savings panel
            MonthlyPanel(
                title = stringResource(R.string.trends_monthly_savings),
                bars = m.savings,
                color = theme.colors.ink,
                target = m.targetSavings,
                average = m.averageSavings,
                isExpense = false,
            )
        }
    }
}

@Composable
private fun MonthlyPanel(
    title: String,
    bars: List<com.athar.core.designsystem.component.AtharMonthlyBar>,
    color: androidx.compose.ui.graphics.Color,
    target: com.athar.core.common.money.Money?,
    average: com.athar.core.common.money.Money,
    isExpense: Boolean,
) {
    val theme = AtharTheme
    Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
        AtharText(text = title, style = theme.typography.body)
        AtharMonthlyChartWithLines(
            bars = bars,
            barColor = color,
            targetLine = target,
            averageLine = average,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                AtharText(text = stringResource(R.string.trends_monthly_average), style = theme.typography.caption, color = theme.colors.muted)
                AtharNumber(money = average)
            }
            target?.let {
                Column(horizontalAlignment = Alignment.End) {
                    AtharText(text = stringResource(R.string.trends_target), style = theme.typography.caption, color = theme.colors.muted)
                    AtharNumber(money = it, color = theme.colors.muted)
                    val variance = average - it
                    val sign = if (variance.isPositive()) "+" else if (variance.isNegative()) "−" else "·"
                    val abs = if (variance.isNegative()) -variance else variance
                    val varColor = when {
                        variance.isZero() -> theme.colors.muted
                        // For expenses: spending more than target = ember; for income/savings: less than target = ember
                        isExpense -> if (variance.isPositive()) theme.colors.ember else theme.colors.olive
                        else -> if (variance.isPositive()) theme.colors.olive else theme.colors.ember
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AtharText(text = stringResource(R.string.trends_variance_prefix, sign), style = theme.typography.caption, color = varColor)
                        AtharNumber(money = abs, color = varColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryComparisonTable(state: TrendsState) {
    val theme = AtharTheme
    AtharCard {
        Column(verticalArrangement = Arrangement.spacedBy(theme.spacing.s)) {
            AtharText(text = stringResource(R.string.trends_category_comparison), style = theme.typography.headline)
            AtharText(
                text = stringResource(R.string.trends_category_comparison_legend),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            state.categoryDeltas.take(20).forEach { row ->
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

@Composable
private fun CustomRangeBanner(start: LocalDate, end: LocalDate, onEdit: () -> Unit) {
    val theme = AtharTheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.spacing.s))
            .background(theme.colors.surface)
            .clickable(onClick = onEdit)
            .padding(theme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AtharText(
            text = stringResource(R.string.trends_custom_range_label, start.toString(), end.toString()),
            style = theme.typography.caption,
            color = theme.colors.muted,
            modifier = Modifier.weight(1f),
        )
        AtharText(text = stringResource(R.string.trends_custom_range_edit), style = theme.typography.caption, color = theme.colors.ember)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePickerSheet(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val theme = AtharTheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var startText by remember { mutableStateOf(initialStart?.toString().orEmpty()) }
    var endText by remember { mutableStateOf(initialEnd?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidDate = stringResource(R.string.trends_error_invalid_date)
    val startBeforeEnd = stringResource(R.string.trends_error_start_before_end)

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
            AtharText(text = stringResource(R.string.trends_custom_range_title), style = theme.typography.headline)
            AtharText(
                text = stringResource(R.string.trends_custom_range_hint),
                style = theme.typography.caption,
                color = theme.colors.muted,
            )
            AtharTextField(value = startText, onValueChange = { startText = it; error = null },
                label = stringResource(R.string.trends_custom_range_from), modifier = Modifier.fillMaxWidth())
            AtharTextField(value = endText, onValueChange = { endText = it; error = null },
                label = stringResource(R.string.trends_custom_range_to), modifier = Modifier.fillMaxWidth())
            error?.let {
                AtharText(text = it, style = theme.typography.caption, color = theme.colors.ember)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(theme.spacing.s))
                    .background(theme.colors.ember)
                    .clickable {
                        val s = runCatching { LocalDate.parse(startText.trim()) }.getOrNull()
                        val e = runCatching { LocalDate.parse(endText.trim()) }.getOrNull()
                        when {
                            s == null || e == null -> error = invalidDate
                            !(s < e) -> error = startBeforeEnd
                            else -> onConfirm(s, e)
                        }
                    }
                    .padding(theme.spacing.m),
                contentAlignment = Alignment.Center,
            ) {
                AtharText(text = stringResource(R.string.trends_action_apply), style = theme.typography.headline, color = theme.colors.parchment)
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
            modifier = Modifier.fillMaxWidth().padding(theme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(theme.spacing.m),
        ) {
            AtharText(text = state.categoryLabelAr, style = theme.typography.title)
            AtharText(text = stringResource(R.string.trends_over_last_12_months), style = theme.typography.caption, color = theme.colors.muted)
            AtharNumber(money = state.total, landmark = true)
            // Show the chart with the monthly target overlay (TMOAP shows the budget line on drilldowns too)
            AtharMonthlyChartWithLines(
                bars = state.bars,
                barColor = theme.colors.ember,
                targetLine = state.monthlyTarget,
                averageLine = null,
            )
            state.monthlyTarget?.let { target ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        AtharText(text = stringResource(R.string.trends_monthly_target), style = theme.typography.caption, color = theme.colors.muted)
                        AtharNumber(money = target)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        AtharText(text = stringResource(R.string.trends_average_12_months), style = theme.typography.caption, color = theme.colors.muted)
                        val barCurrency = state.bars.firstOrNull()?.amount?.currency ?: target.currency
                        val avg = com.athar.core.common.money.Money.sumAmounts(state.bars.map { it.amount }, barCurrency)
                        val n = state.bars.size.coerceAtLeast(1)
                        val avgMoney = com.athar.core.common.money.Money.of(
                            avg.amount.divide(java.math.BigDecimal(n), 2, java.math.RoundingMode.HALF_EVEN),
                            barCurrency,
                        )
                        AtharNumber(money = avgMoney)
                    }
                }
            }
        }
    }
}
