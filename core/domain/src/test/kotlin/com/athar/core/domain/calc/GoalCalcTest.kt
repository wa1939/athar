package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class GoalCalcTest {

    @Test
    fun `average monthly divides total by lookback months`() {
        val average = GoalCalc.averageMonthly(Money.of("9000"), months = 3)

        assertThat(average.amount).isEqualTo(BigDecimal("3000.00"))
    }

    @Test
    fun `savings rate is net savings divided by income`() {
        val rate = GoalCalc.savingsRatePercent(
            monthlyIncome = Money.of("10000"),
            monthlyExpense = Money.of("7500"),
        )

        assertThat(rate).isEqualTo(BigDecimal("25.0"))
    }

    @Test
    fun `savings rate is null when income is zero`() {
        val rate = GoalCalc.savingsRatePercent(
            monthlyIncome = Money.zero(),
            monthlyExpense = Money.of("500"),
        )

        assertThat(rate).isNull()
    }

    @Test
    fun `savings rate progress floors negative savings at zero`() {
        val progress = GoalCalc.savingsRateProgress(
            actualPercent = BigDecimal("-10.0"),
            targetPercent = 20,
        )

        assertThat(progress).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `savings rate progress caps at one`() {
        val progress = GoalCalc.savingsRateProgress(
            actualPercent = BigDecimal("40.0"),
            targetPercent = 20,
        )

        assertThat(progress).isEqualTo(BigDecimal.ONE)
    }

    @Test
    fun `emergency target multiplies average expense by target months`() {
        val target = GoalCalc.emergencyTargetAmount(
            monthlyExpense = Money.of("2500"),
            targetMonths = 6,
        )

        assertThat(target.amount).isEqualTo(BigDecimal("15000"))
    }

    @Test
    fun `emergency months covered divides liquid balance by monthly expense`() {
        val covered = GoalCalc.emergencyMonthsCovered(
            liquidBalance = Money.of("7500"),
            monthlyExpense = Money.of("2500"),
        )

        assertThat(covered).isEqualTo(BigDecimal("3.0"))
    }

    @Test
    fun `emergency coverage is unbounded when there is no monthly burn`() {
        val covered = GoalCalc.emergencyMonthsCovered(
            liquidBalance = Money.of("7500"),
            monthlyExpense = Money.zero(),
        )
        val progress = GoalCalc.emergencyProgress(covered, targetMonths = 6)

        assertThat(covered).isNull()
        assertThat(progress).isEqualTo(BigDecimal.ONE)
    }

    @Test
    fun `negative liquid balance counts as zero emergency coverage`() {
        val covered = GoalCalc.emergencyMonthsCovered(
            liquidBalance = Money.of("-100"),
            monthlyExpense = Money.of("2500"),
        )

        assertThat(covered).isEqualTo(BigDecimal("0.0"))
    }
}
