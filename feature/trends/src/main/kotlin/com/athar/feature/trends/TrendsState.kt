package com.athar.feature.trends

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.designsystem.component.AtharBarItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class PeriodKey { MONTH, MONTHS_3, YEAR, MONTH_VS_PREVIOUS }

/** Single row in the period-vs-period category-delta table (Excel "Historical Comparison" parity). */
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
    val isLoading: Boolean,
) {
    /** Signed delta: positive = spent more this period than last. */
    val expenseDelta: Money? = if (previousExpense.isZero()) null else (totalExpense - previousExpense)

    /** Percent change vs previous period (null if previous = 0). */
    val expenseDeltaPercent: Double? = if (previousExpense.isZero()) null else
        ((totalExpense.amount.toDouble() - previousExpense.amount.toDouble()) / previousExpense.amount.toDouble()) * 100.0

    val savingsRate: Double? =
        if (totalIncome.isZero()) null
        else savings.amount.toDouble() / totalIncome.amount.toDouble() * 100.0

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
            isLoading = true,
        )
    }
}

sealed interface TrendsEvent {
    data class SelectPeriod(val key: PeriodKey) : TrendsEvent
}
