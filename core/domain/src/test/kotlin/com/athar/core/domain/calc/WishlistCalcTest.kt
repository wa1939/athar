package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class WishlistCalcTest {

    @Test
    fun `capacity is zero when expenses exceed income`() {
        val capacity = WishlistCalc.monthlyCapacity(
            income = Money.of("3000"),
            expense = Money.of("4500"),
        )
        assertThat(capacity.amount).isEqualTo(BigDecimal.ZERO)
    }

    @Test
    fun `capacity divides 3-month net by 3`() {
        // 3-month net = 3000; per month = 1000
        val capacity = WishlistCalc.monthlyCapacity(
            income = Money.of("9000"),
            expense = Money.of("6000"),
        )
        assertThat(capacity.amount).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `capacity rounds banker's HALF_EVEN`() {
        // (income - expense) = 1.25, /3 = 0.41666... rounds to 0.42 (HALF_EVEN at 2dp)
        val capacity = WishlistCalc.monthlyCapacity(
            income = Money.of("1.25"),
            expense = Money.zero(),
        )
        assertThat(capacity.amount).isEqualTo(BigDecimal("0.42"))
    }

    @Test
    fun `months needed is 0 when already saved`() {
        val months = WishlistCalc.monthsNeeded(
            remaining = Money.zero(),
            capacity = Money.of("100"),
        )
        assertThat(months).isEqualTo(0)
    }

    @Test
    fun `months needed is null when capacity is zero`() {
        val months = WishlistCalc.monthsNeeded(
            remaining = Money.of("100"),
            capacity = Money.zero(),
        )
        assertThat(months).isNull()
    }

    @Test
    fun `months needed rounds up via ceil`() {
        // 250 / 100 = 2.5 → 3 months
        val months = WishlistCalc.monthsNeeded(
            remaining = Money.of("250"),
            capacity = Money.of("100"),
        )
        assertThat(months).isEqualTo(3)
    }

    @Test
    fun `months needed is 1 even for 0_01 of remainder`() {
        // 100.01 / 100 = 1.0001 → 2 months (ceil)
        val months = WishlistCalc.monthsNeeded(
            remaining = Money.of("100.01"),
            capacity = Money.of("100"),
        )
        assertThat(months).isEqualTo(2)
    }
}
