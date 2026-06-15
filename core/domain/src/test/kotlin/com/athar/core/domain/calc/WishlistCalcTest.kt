package com.athar.core.domain.calc

import com.athar.core.common.money.Money
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RECONCILE_REF_PREFIX
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.WishlistItem
import com.athar.core.domain.model.WishlistStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.YearMonth

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
    fun `capacity from transactions excludes reconciliation adjustments`() {
        val capacity = WishlistCalc.monthlyCapacityFromTransactions(
            transactions = listOf(
                tx(id = "salary", type = TxType.INCOME, amount = Money.of("9000")),
                tx(id = "spend", type = TxType.EXPENSE, amount = Money.of("6000")),
                tx(
                    id = "reconcile-expense",
                    type = TxType.EXPENSE,
                    amount = Money.of("50000"),
                    source = IngestSource.MANUAL,
                    sourceRefId = "${RECONCILE_REF_PREFIX}gap",
                ),
                tx(
                    id = "reconcile-income",
                    type = TxType.INCOME,
                    amount = Money.of("20000"),
                    source = IngestSource.MANUAL,
                    sourceRefId = "${RECONCILE_REF_PREFIX}raise",
                ),
                tx(id = "transfer", type = TxType.TRANSFER, amount = Money.of("7000")),
            ),
            currency = "SAR",
        )

        assertThat(capacity.amount).isEqualTo(BigDecimal("1000.00"))
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

    @Test
    fun `projection respects future start month`() {
        val projection = WishlistCalc.project(
            item = wish(
                cost = Money.of("500"),
                saved = Money.zero(),
                startMonth = YearMonth.of(2026, 4),
            ),
            capacity = Money.of("100"),
            currentMonth = YearMonth.of(2026, 1),
        )

        assertThat(projection.status).isEqualTo(WishlistStatus.WaitUntil(YearMonth.of(2026, 9)))
        assertThat(projection.monthsNeeded).isEqualTo(8)
        assertThat(projection.remaining.amount).isEqualTo(BigDecimal("500"))
    }

    @Test
    fun `projection marks desired horizon infeasible when monthly requirement exceeds capacity`() {
        val projection = WishlistCalc.project(
            item = wish(
                cost = Money.of("1200"),
                saved = Money.zero(),
                desiredMonths = 6,
                startMonth = YearMonth.of(2026, 1),
            ),
            capacity = Money.of("100"),
            currentMonth = YearMonth.of(2026, 1),
        )

        assertThat(projection.status).isEqualTo(WishlistStatus.Infeasible)
        assertThat(projection.targetMonth).isEqualTo(YearMonth.of(2026, 7))
        assertThat(projection.monthlyRequired!!.amount).isEqualTo(BigDecimal("200.00"))
        assertThat(projection.targetFeasible).isFalse()
    }

    @Test
    fun `projection accepts desired horizon when capacity reaches target month`() {
        val projection = WishlistCalc.project(
            item = wish(
                cost = Money.of("600"),
                saved = Money.of("100"),
                desiredMonths = 5,
                startMonth = YearMonth.of(2026, 1),
            ),
            capacity = Money.of("100"),
            currentMonth = YearMonth.of(2026, 1),
        )

        assertThat(projection.status).isEqualTo(WishlistStatus.WaitUntil(YearMonth.of(2026, 6)))
        assertThat(projection.monthsNeeded).isEqualTo(5)
        assertThat(projection.monthlyRequired!!.amount).isEqualTo(BigDecimal("100.00"))
        assertThat(projection.targetFeasible).isTrue()
    }

    @Test
    fun `summary counts wishlist states and totals remaining amounts`() {
        val currentMonth = YearMonth.of(2026, 1)
        val projections = listOf(
            WishlistCalc.project(
                item = wish(
                    cost = Money.of("1000"),
                    saved = Money.of("1000"),
                    desiredMonths = 3,
                ),
                capacity = Money.of("500"),
                currentMonth = currentMonth,
            ),
            WishlistCalc.project(
                item = wish(
                    cost = Money.of("600"),
                    saved = Money.of("100"),
                    desiredMonths = 5,
                ),
                capacity = Money.of("100"),
                currentMonth = currentMonth,
            ),
            WishlistCalc.project(
                item = wish(
                    cost = Money.of("1200"),
                    saved = Money.zero(),
                    desiredMonths = 6,
                ),
                capacity = Money.of("100"),
                currentMonth = currentMonth,
            ),
        )

        val summary = WishlistCalc.summarize(projections)

        assertThat(summary.totalRemaining.amount).isEqualTo(BigDecimal("1700"))
        assertThat(summary.targetMonthlyRequired.amount).isEqualTo(BigDecimal("300.00"))
        assertThat(summary.readyNowCount).isEqualTo(1)
        assertThat(summary.waitingCount).isEqualTo(1)
        assertThat(summary.infeasibleCount).isEqualTo(1)
        assertThat(summary.nextReachableMonth).isEqualTo(YearMonth.of(2026, 6))
    }

    @Test
    fun `summary is empty when there are no wishes`() {
        val summary = WishlistCalc.summarize(emptyList(), currency = "USD")

        assertThat(summary.totalRemaining).isEqualTo(Money.zero("USD"))
        assertThat(summary.targetMonthlyRequired).isEqualTo(Money.zero("USD"))
        assertThat(summary.readyNowCount).isEqualTo(0)
        assertThat(summary.waitingCount).isEqualTo(0)
        assertThat(summary.infeasibleCount).isEqualTo(0)
        assertThat(summary.nextReachableMonth).isNull()
    }

    private fun wish(
        cost: Money,
        saved: Money,
        desiredMonths: Int? = null,
        startMonth: YearMonth = YearMonth.of(2026, 1),
    ): WishlistItem = WishlistItem(
        id = "wish",
        name = "Wish",
        cost = cost,
        currentSaved = saved,
        desiredMonths = desiredMonths,
        startMonth = startMonth,
        notes = null,
    )

    private fun tx(
        id: String,
        type: TxType,
        amount: Money,
        source: IngestSource = IngestSource.SMS,
        sourceRefId: String? = "sms-$id",
    ): Transaction = Transaction(
        id = id,
        accountId = "account",
        type = type,
        amount = amount,
        date = LocalDate(2026, 1, 1),
        occurredAt = null,
        merchant = id,
        merchantNormalized = id,
        categoryId = null,
        notes = null,
        source = source,
        sourceRefId = sourceRefId,
        status = TxStatus.CONFIRMED,
        confidence = 1f,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
