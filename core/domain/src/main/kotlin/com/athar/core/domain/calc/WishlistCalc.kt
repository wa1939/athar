package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.model.WishlistStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import kotlin.math.ceil

/**
 * Wishlist capacity + projection math. Extracted from `WishlistViewModel` so it can be
 * exercised by pure JVM tests (no Android, no Hilt, no Compose). Master Brief §10.4 spec.
 */
object WishlistCalc {

    private const val LOOKBACK_MONTHS = 3
    private val LOOKBACK = BigDecimal(LOOKBACK_MONTHS)
    private const val MAX_REASONABLE_MONTHS = 36

    data class Projection(
        val remaining: Money,
        val status: WishlistStatus,
        val monthsNeeded: Int?,
        val projectedMonth: YearMonth?,
        val targetMonth: YearMonth?,
        val monthlyRequired: Money?,
        val targetFeasible: Boolean?,
    )

    /**
     * Monthly capacity = `(income - expense) / 3`, floored at zero. The denominator is
     * the lookback window in months that the VM passes (3-month rolling).
     */
    fun monthlyCapacity(income: Money, expense: Money): Money {
        require(income.currency == expense.currency) {
            "income/expense currency mismatch: ${income.currency} vs ${expense.currency}"
        }
        val net = income - expense
        if (net.amount.signum() <= 0) return Money.zero(net.currency)
        val per = net.amount.divide(LOOKBACK, 2, RoundingMode.HALF_EVEN)
        return Money.of(per, net.currency)
    }

    /**
     * Months needed to save [remaining] at [capacity] per month.
     * Returns 0 if already saved, null if capacity is zero or negative.
     */
    fun monthsNeeded(remaining: Money, capacity: Money): Int? = when {
        remaining.amount.signum() <= 0 -> 0
        capacity.amount.signum() <= 0 -> null
        else -> ceil(remaining.amount.toDouble() / capacity.amount.toDouble()).toInt()
    }

    /**
     * Projects a single wish from the user's current monthly capacity. If a desired
     * horizon is set, the wish is only "feasible" when capacity can reach it by the
     * target month. Without a desired horizon, anything beyond [MAX_REASONABLE_MONTHS]
     * remains infeasible to avoid presenting unrealistic long-tail promises.
     */
    fun project(item: WishlistItem, capacity: Money, currentMonth: YearMonth): Projection {
        val remaining = item.cost - item.currentSaved
        if (remaining.amount.signum() <= 0) {
            return Projection(
                remaining = Money.zero(item.cost.currency),
                status = WishlistStatus.Now,
                monthsNeeded = 0,
                projectedMonth = currentMonth,
                targetMonth = targetMonth(item),
                monthlyRequired = monthlyRequired(remaining, item.desiredMonths),
                targetFeasible = true,
            )
        }

        val targetMonth = targetMonth(item)
        val monthlyRequired = monthlyRequired(remaining, item.desiredMonths)
        if (capacity.amount.signum() <= 0) {
            return Projection(
                remaining = remaining,
                status = WishlistStatus.Infeasible,
                monthsNeeded = null,
                projectedMonth = null,
                targetMonth = targetMonth,
                monthlyRequired = monthlyRequired,
                targetFeasible = false,
            )
        }

        val savingStart = maxOf(item.startMonth, currentMonth)
        val savingMonths = monthsNeeded(remaining, capacity) ?: return Projection(
            remaining = remaining,
            status = WishlistStatus.Infeasible,
            monthsNeeded = null,
            projectedMonth = null,
            targetMonth = targetMonth,
            monthlyRequired = monthlyRequired,
            targetFeasible = false,
        )
        val projectedMonth = savingStart.plusMonths(savingMonths.toLong())
        val totalMonths = monthsBetween(currentMonth, projectedMonth)
        val targetFeasible = targetMonth?.let { !projectedMonth.isAfter(it) }
        val status = when {
            targetFeasible == false -> WishlistStatus.Infeasible
            targetMonth == null && totalMonths > MAX_REASONABLE_MONTHS -> WishlistStatus.Infeasible
            else -> WishlistStatus.WaitUntil(projectedMonth)
        }

        return Projection(
            remaining = remaining,
            status = status,
            monthsNeeded = if (status is WishlistStatus.Infeasible) null else totalMonths,
            projectedMonth = projectedMonth,
            targetMonth = targetMonth,
            monthlyRequired = monthlyRequired,
            targetFeasible = targetFeasible,
        )
    }

    private fun targetMonth(item: WishlistItem): YearMonth? =
        item.desiredMonths?.takeIf { it > 0 }?.let { item.startMonth.plusMonths(it.toLong()) }

    private fun monthlyRequired(remaining: Money, desiredMonths: Int?): Money? {
        val months = desiredMonths?.takeIf { it > 0 } ?: return null
        val amount = if (remaining.amount.signum() <= 0) {
            BigDecimal.ZERO
        } else {
            remaining.amount.divide(BigDecimal(months), 2, RoundingMode.HALF_EVEN)
        }
        return Money.of(amount, remaining.currency)
    }

    private fun monthsBetween(start: YearMonth, end: YearMonth): Int =
        ((end.year - start.year) * 12 + (end.monthValue - start.monthValue)).coerceAtLeast(0)
}
