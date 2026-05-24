package com.athar.feature.trends

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.designsystem.component.AtharBarItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class PeriodKey { MONTH, MONTHS_3, YEAR }

@Immutable
data class TrendsState(
    val periodKey: PeriodKey,
    val period: Period?,
    val totalExpense: Money,
    val totalIncome: Money,
    val previousExpense: Money,
    val categories: ImmutableList<AtharBarItem>,
    val isLoading: Boolean,
) {
    /** Signed delta: positive = spent more this period than last. */
    val expenseDelta: Money? = if (previousExpense.isZero()) null else (totalExpense - previousExpense)

    /** Percent change vs previous period (null if previous = 0). */
    val expenseDeltaPercent: Double? = if (previousExpense.isZero()) null else
        ((totalExpense.amount.toDouble() - previousExpense.amount.toDouble()) / previousExpense.amount.toDouble()) * 100.0

    companion object {
        fun initial(): TrendsState = TrendsState(
            periodKey = PeriodKey.MONTH,
            period = null,
            totalExpense = Money.zero(),
            totalIncome = Money.zero(),
            previousExpense = Money.zero(),
            categories = persistentListOf(),
            isLoading = true,
        )
    }
}

sealed interface TrendsEvent {
    data class SelectPeriod(val key: PeriodKey) : TrendsEvent
}
