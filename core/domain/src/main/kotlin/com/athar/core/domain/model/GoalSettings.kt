package com.athar.core.domain.model

data class GoalSettings(
    val savingsRateTargetPercent: Int = DEFAULT_SAVINGS_RATE_TARGET_PERCENT,
    val emergencyFundTargetMonths: Int = DEFAULT_EMERGENCY_FUND_TARGET_MONTHS,
) {
    companion object {
        const val DEFAULT_SAVINGS_RATE_TARGET_PERCENT = 20
        const val DEFAULT_EMERGENCY_FUND_TARGET_MONTHS = 6
    }
}
