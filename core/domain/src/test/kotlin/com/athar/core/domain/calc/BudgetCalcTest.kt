package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BudgetCalcTest {

    @Test
    fun `variance is null when no target`() {
        assertThat(BudgetCalc.variance(actual = Money.of("100"), target = null)).isNull()
    }

    @Test
    fun `variance is actual minus target`() {
        // Spent 1,200 against a 1,000 target → +200 over
        val v = BudgetCalc.variance(actual = Money.of("1200"), target = Money.of("1000"))
        assertThat(v?.amount?.toPlainString()).isEqualTo("200")
    }

    @Test
    fun `over budget when actual exceeds target`() {
        assertThat(BudgetCalc.isOver(actual = Money.of("1200"), target = Money.of("1000"))).isTrue()
        assertThat(BudgetCalc.isUnder(actual = Money.of("1200"), target = Money.of("1000"))).isFalse()
    }

    @Test
    fun `under budget when actual below target`() {
        assertThat(BudgetCalc.isOver(actual = Money.of("800"), target = Money.of("1000"))).isFalse()
        assertThat(BudgetCalc.isUnder(actual = Money.of("800"), target = Money.of("1000"))).isTrue()
    }

    @Test
    fun `no target means neither over nor under`() {
        assertThat(BudgetCalc.isOver(actual = Money.of("800"), target = null)).isFalse()
        assertThat(BudgetCalc.isUnder(actual = Money.of("800"), target = null)).isFalse()
    }

    @Test
    fun `exactly on target is neither over nor under`() {
        assertThat(BudgetCalc.isOver(actual = Money.of("1000"), target = Money.of("1000"))).isFalse()
        assertThat(BudgetCalc.isUnder(actual = Money.of("1000"), target = Money.of("1000"))).isFalse()
    }
}
