package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

/**
 * Wishlist capacity + projection math. Extracted from `WishlistViewModel` so it can be
 * exercised by pure JVM tests (no Android, no Hilt, no Compose). Master Brief §10.4 spec.
 */
object WishlistCalc {

    private const val LOOKBACK_MONTHS = 3
    private val LOOKBACK = BigDecimal(LOOKBACK_MONTHS)

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
}
