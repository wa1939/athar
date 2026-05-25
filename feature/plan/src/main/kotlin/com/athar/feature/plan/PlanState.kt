package com.athar.feature.plan

import androidx.compose.runtime.Immutable
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Category
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import java.time.YearMonth

/**
 * Period selector for the Plan screen. Mirrors the Trends one but drops the
 * "vs previous" key (irrelevant for budget vs actual) and keeps the month / 3-month /
 * year / custom buckets.
 */
enum class PlanPeriodKey { MONTH, MONTHS_3, YEAR, CUSTOM }

@Immutable
data class PlanState(
    val month: YearMonth,
    val periodKey: PlanPeriodKey,
    val period: Period?,
    val customStart: LocalDate?,
    val customEnd: LocalDate?,
    /** Target multiplier — when period spans N months the per-category monthly target is scaled to N×. */
    val targetMultiplier: Double,
    val rows: ImmutableList<BudgetRow>,
    val totalTarget: Money,
    val totalActual: Money,
    val isLoading: Boolean,
) {
    companion object {
        fun empty(month: YearMonth): PlanState = PlanState(
            month = month,
            periodKey = PlanPeriodKey.MONTH,
            period = null,
            customStart = null,
            customEnd = null,
            targetMultiplier = 1.0,
            rows = persistentListOf(),
            totalTarget = Money.zero(),
            totalActual = Money.zero(),
            isLoading = true,
        )
    }
}

data class BudgetRow(
    val category: Category,
    val actual: Money,
    val target: Money?,
) {
    val variance: Money? = target?.let { actual - it }
    val isOver: Boolean = variance?.isPositive() == true
    val isUnder: Boolean = variance?.isNegative() == true

    val limit: LimitState = when {
        target == null || target.isZero() -> LimitState.None
        actual.amount > target.amount -> LimitState.Over
        else -> {
            val pct = actual.amount.toDouble() / target.amount.toDouble()
            when {
                pct >= 0.90 -> LimitState.Tight
                pct >= 0.70 -> LimitState.Watch
                else -> LimitState.Healthy
            }
        }
    }
}

enum class LimitState { None, Healthy, Watch, Tight, Over }

sealed interface PlanEvent {
    data class OpenTargetEditor(val categoryId: String) : PlanEvent
    data class SaveTarget(val categoryId: String, val targetMinor: Long?) : PlanEvent
    data object DismissTargetEditor : PlanEvent
    data class SelectPeriod(val key: PlanPeriodKey) : PlanEvent
    data class SelectCustomRange(val start: LocalDate, val end: LocalDate) : PlanEvent
}
