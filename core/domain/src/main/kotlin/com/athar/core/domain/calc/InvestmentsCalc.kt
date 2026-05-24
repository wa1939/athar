package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Family-Investments share-and-return math. Extracted from `InvestmentsViewModel` so it
 * can be tested without Android dependencies. Master Brief §4.5 InvestmentPool spec.
 */
object InvestmentsCalc {

    /** Returns the contributor's share as a fraction in `[0.0, 1.0]`. */
    fun sharePercent(amount: Money, totalCorpus: Money): Double {
        if (totalCorpus.amount.signum() <= 0) return 0.0
        return amount.amount.toDouble() / totalCorpus.amount.toDouble()
    }

    /** Returns the contributor's share of [totalReturn], rounded to 2 dp banker's. */
    fun shareReturn(totalReturn: Money, sharePct: Double): Money {
        val scaled = totalReturn.amount.multiply(BigDecimal(sharePct))
            .setScale(2, RoundingMode.HALF_EVEN)
        return Money.of(scaled, totalReturn.currency)
    }
}
