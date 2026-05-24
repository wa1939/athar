package com.athar.core.domain.calc

import com.athar.core.common.money.Money

/**
 * Budget variance helpers. Extracted from `PlanViewModel`/`BudgetRow`. Sign convention:
 *
 *  - positive variance → actual exceeds target (over budget, "ember" pill)
 *  - negative variance → actual under target (under budget, "olive" pill)
 *  - null → no target set; nothing to compare
 */
object BudgetCalc {

    fun variance(actual: Money, target: Money?): Money? = target?.let { actual - it }

    fun isOver(actual: Money, target: Money?): Boolean = variance(actual, target)?.isPositive() == true
    fun isUnder(actual: Money, target: Money?): Boolean = variance(actual, target)?.isNegative() == true
}
