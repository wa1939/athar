package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InvestmentsCalcTest {

    @Test
    fun `share percent is 0 when total corpus is zero`() {
        val pct = InvestmentsCalc.sharePercent(
            amount = Money.of("1000"),
            totalCorpus = Money.zero(),
        )
        assertThat(pct).isEqualTo(0.0)
    }

    @Test
    fun `share percent is amount over total`() {
        // Walid contributes 200k of a 1M corpus → 20%
        val pct = InvestmentsCalc.sharePercent(
            amount = Money.of("200000"),
            totalCorpus = Money.of("1000000"),
        )
        assertThat(pct).isWithin(1e-9).of(0.2)
    }

    @Test
    fun `share return is proportional to share percent`() {
        // 17,500 total return * 20% share = 3,500
        val shareReturn = InvestmentsCalc.shareReturn(
            totalReturn = Money.of("17500"),
            sharePct = 0.2,
        )
        assertThat(shareReturn.amount).isEqualTo(BigDecimal("3500.00"))
    }

    @Test
    fun `share return rounds to 2dp banker's`() {
        // 100 * 1/3 = 33.333... rounds HALF_EVEN to 33.33
        val shareReturn = InvestmentsCalc.shareReturn(
            totalReturn = Money.of("100"),
            sharePct = 1.0 / 3.0,
        )
        assertThat(shareReturn.amount).isEqualTo(BigDecimal("33.33"))
    }

    @Test
    fun `share return is zero when share pct is zero`() {
        val shareReturn = InvestmentsCalc.shareReturn(
            totalReturn = Money.of("17500"),
            sharePct = 0.0,
        )
        assertThat(shareReturn.amount).isEqualTo(BigDecimal("0.00"))
    }
}
