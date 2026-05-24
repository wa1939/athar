package com.athar.core.common.money

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `adds two amounts of same currency`() {
        val a = Money.of("100.50")
        val b = Money.of("200.25")
        assertThat((a + b).amount).isEqualTo(BigDecimal("300.75"))
    }

    @Test
    fun `subtracts and supports negative result`() {
        val a = Money.of("50")
        val b = Money.of("100")
        assertThat((a - b).amount).isEqualTo(BigDecimal("-50"))
    }

    @Test
    fun `rejects mixed currencies on addition`() {
        val sar = Money.of("100", "SAR")
        val usd = Money.of("100", "USD")
        assertThrows<IllegalArgumentException> { sar + usd }
    }

    @Test
    fun `minor units roundtrip`() {
        val m = Money.ofMinor(20050)
        assertThat(m.amount).isEqualTo(BigDecimal("200.50"))
        assertThat(m.currency).isEqualTo("SAR")
    }

    @Test
    fun `rounds banker's rounding by default`() {
        // 0.125 rounded to 2dp with HALF_EVEN goes to 0.12 (rounds to even).
        val m = Money.of("0.125").rounded()
        assertThat(m.amount).isEqualTo(BigDecimal("0.12"))
    }

    @Test
    fun `compareTo orders amounts`() {
        val a = Money.of("100")
        val b = Money.of("200")
        assertThat(a < b).isTrue()
        assertThat(b > a).isTrue()
        assertThat(a.compareTo(Money.of("100"))).isEqualTo(0)
    }
}
