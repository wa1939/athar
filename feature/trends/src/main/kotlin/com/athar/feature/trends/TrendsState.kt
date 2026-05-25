package com.athar.feature.trends

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.designsystem.component.AtharBarItem
import com.athar.core.designsystem.component.AtharMonthlyBar
import com.athar.core.designsystem.component.AtharSplitSegment
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class PeriodKey { WEEK, MONTH, MONTHS_3, YEAR, MONTH_VS_PREVIOUS, CUSTOM }

/** Per-category row in the period-vs-period comparison table (Excel "Historical Comparison" parity). */
@Immutable
data class CategoryDeltaRow(
    val categoryId: String,
    val labelAr: String,
    val labelEn: String,
    val currentTotal: Money,
    val previousTotal: Money,
) {
    val deltaAmount: Money = currentTotal - previousTotal
    val deltaPercent: Double? = if (previousTotal.isZero()) null else
        ((currentTotal.amount.toDouble() - previousTotal.amount.toDouble()) / previousTotal.amount.toDouble()) * 100.0
}

/** 12-month series for the Dashboard charts (Income / Expenses / Savings by Month). */
@Immutable
data class MonthlySeries(
    val income: ImmutableList<AtharMonthlyBar>,
    val expense: ImmutableList<AtharMonthlyBar>,
    val savings: ImmutableList<AtharMonthlyBar>,
    val averageIncome: Money,
    val averageExpense: Money,
    val averageSavings: Money,
    val targetIncome: Money?,
    val targetExpense: Money?,
    val targetSavings: Money?,
) {
    companion object {
        fun empty(): MonthlySeries = MonthlySeries(
            income = persistentListOf(),
            expense = persistentListOf(),
            savings = persistentListOf(),
            averageIncome = Money.zero(),
            averageExpense = Money.zero(),
            averageSavings = Money.zero(),
            targetIncome = null,
            targetExpense = null,
            targetSavings = null,
        )
    }
}

@Immutable
data class TrendsState(
    val periodKey: PeriodKey,
    val period: Period?,
    val totalExpense: Money,
    val totalIncome: Money,
    val previousExpense: Money,
    val previousIncome: Money,
    val savings: Money,
    val previousSavings: Money,
    val categories: ImmutableList<AtharBarItem>,
    val categoryDeltas: ImmutableList<CategoryDeltaRow>,
    val categorySplit: ImmutableList<AtharSplitSegment>,
    val monthly: MonthlySeries,
    val customStart: kotlinx.datetime.LocalDate?,
    val customEnd: kotlinx.datetime.LocalDate?,
    val isLoading: Boolean,
) {
    val expenseDelta: Money? = if (previousExpense.isZero()) null else (totalExpense - previousExpense)

    val expenseDeltaPercent: Double? = if (previousExpense.isZero()) null else
        ((totalExpense.amount.toDouble() - previousExpense.amount.toDouble()) / previousExpense.amount.toDouble()) * 100.0

    val incomeDelta: Money? = if (previousIncome.isZero()) null else (totalIncome - previousIncome)
    val incomeDeltaPercent: Double? = if (previousIncome.isZero()) null else
        ((totalIncome.amount.toDouble() - previousIncome.amount.toDouble()) / previousIncome.amount.toDouble()) * 100.0

    val savingsDelta: Money? = if (previousSavings.isZero()) null else (savings - previousSavings)

    val savingsRate: Double? =
        if (totalIncome.isZero()) null
        else savings.amount.toDouble() / totalIncome.amount.toDouble() * 100.0

    val previousSavingsRate: Double? =
        if (previousIncome.isZero()) null
        else previousSavings.amount.toDouble() / previousIncome.amount.toDouble() * 100.0

    val expensePercentOfIncome: Double? =
        if (totalIncome.isZero()) null
        else totalExpense.amount.toDouble() / totalIncome.amount.toDouble() * 100.0

    companion object {
        fun initial(): TrendsState = TrendsState(
            periodKey = PeriodKey.MONTH,
            period = null,
            totalExpense = Money.zero(),
            totalIncome = Money.zero(),
            previousExpense = Money.zero(),
            previousIncome = Money.zero(),
            savings = Money.zero(),
            previousSavings = Money.zero(),
            categories = persistentListOf(),
            categoryDeltas = persistentListOf(),
            categorySplit = persistentListOf(),
            monthly = MonthlySeries.empty(),
            customStart = null,
            customEnd = null,
            isLoading = true,
        )
    }
}

sealed interface TrendsEvent {
    data class SelectPeriod(val key: PeriodKey) : TrendsEvent
    data class SelectCustomRange(val start: kotlinx.datetime.LocalDate, val end: kotlinx.datetime.LocalDate) : TrendsEvent
}
