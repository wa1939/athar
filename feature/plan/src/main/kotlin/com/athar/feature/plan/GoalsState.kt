package com.athar.feature.plan

import com.athar.core.common.money.Money
import com.athar.core.domain.model.GoalSettings
import java.math.BigDecimal

data class GoalsState(
    val monthlyIncome: Money,
    val monthlyExpense: Money,
    val monthlySavings: Money,
    val savingsRatePercent: BigDecimal?,
    val savingsRateTargetPercent: Int,
    val savingsRateProgress: Float,
    val liquidBalance: Money,
    val emergencyTargetAmount: Money,
    val emergencyMonthsCovered: BigDecimal?,
    val emergencyFundTargetMonths: Int,
    val emergencyFundProgress: Float,
    val isLoading: Boolean,
) {
    companion object {
        fun initial(): GoalsState = GoalsState(
            monthlyIncome = Money.zero(),
            monthlyExpense = Money.zero(),
            monthlySavings = Money.zero(),
            savingsRatePercent = null,
            savingsRateTargetPercent = GoalSettings.DEFAULT_SAVINGS_RATE_TARGET_PERCENT,
            savingsRateProgress = 0f,
            liquidBalance = Money.zero(),
            emergencyTargetAmount = Money.zero(),
            emergencyMonthsCovered = null,
            emergencyFundTargetMonths = GoalSettings.DEFAULT_EMERGENCY_FUND_TARGET_MONTHS,
            emergencyFundProgress = 0f,
            isLoading = true,
        )
    }
}

sealed interface GoalsEvent {
    data class SetSavingsRateTarget(val percent: Int) : GoalsEvent
    data class SetEmergencyFundTarget(val months: Int) : GoalsEvent
}
