package com.athar.core.data.report

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RECONCILE_REF_PREFIX
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class TaxReportBuilderTest {

    @Test
    fun `builds annual totals and excludes reconciliation adjustments`() {
        val food = category("cat-food", "Food", "المطاعم")
        val salary = category("cat-salary", "Salary", "الراتب", CategoryKind.INCOME)
        val report = TaxReportBuilder.build(
            year = 2026,
            transactions = listOf(
                tx("1", "2026-01-02", TxType.EXPENSE, "Coffee", "12.50", "SAR", food.id),
                tx("2", "2026-02-03", TxType.EXPENSE, "Lunch", "20.00", "SAR", food.id),
                tx("3", "2026-02-25", TxType.INCOME, "Employer", "10000.00", "SAR", salary.id),
                tx("4", "2026-03-01", TxType.EXPENSE, "Manual adjustment", "500.00", "SAR", null, reconcile = true),
                tx("5", "2025-12-31", TxType.EXPENSE, "Old year", "99.00", "SAR", food.id),
            ),
            categories = listOf(food, salary),
            localeTag = "en",
        )

        assertThat(report.transactions.map { it.merchant }).containsExactly("Coffee", "Lunch", "Employer").inOrder()
        assertThat(report.excludedReconciliationCount).isEqualTo(1)
        assertThat(report.categoryTotals).hasSize(2)
        assertThat(report.categoryTotals.first { it.categoryName == "Food" }.amount).isEqualTo(Money.of("32.50").amount)
        assertThat(report.categoryTotals.first { it.categoryName == "Salary" }.amount).isEqualTo(Money.of("10000.00").amount)
    }

    @Test
    fun `uses Arabic category names and uncategorized label for Arabic exports`() {
        val food = category("cat-food", "Food", "المطاعم")

        val report = TaxReportBuilder.build(
            year = 2026,
            transactions = listOf(
                tx("1", "2026-01-02", TxType.EXPENSE, "Coffee", "12.50", "SAR", food.id),
                tx("2", "2026-01-03", TxType.EXPENSE, "Unknown", "5.00", "SAR", null),
            ),
            categories = listOf(food),
            localeTag = "ar",
        )

        assertThat(report.locale).isEqualTo(TaxReportLocale.AR)
        assertThat(report.transactions.map { it.categoryName }).containsExactly("المطاعم", "غير مصنف").inOrder()
    }

    private fun category(
        id: String,
        name: String,
        nameAr: String,
        kind: CategoryKind = CategoryKind.EXPENSE,
    ): Category = Category(
        id = id,
        name = name,
        nameAr = nameAr,
        kind = kind,
        icon = null,
        monthlyTarget = null,
        archived = false,
        sortOrder = 0,
    )

    private fun tx(
        id: String,
        date: String,
        type: TxType,
        merchant: String,
        amount: String,
        currency: String,
        categoryId: String?,
        reconcile: Boolean = false,
    ): Transaction = Transaction(
        id = id,
        accountId = "acc",
        type = type,
        amount = Money.of(amount, currency),
        date = LocalDate.parse(date),
        occurredAt = null,
        merchant = merchant,
        merchantNormalized = merchant.lowercase(),
        categoryId = categoryId,
        notes = null,
        source = IngestSource.MANUAL,
        sourceRefId = if (reconcile) "$RECONCILE_REF_PREFIX$id" else null,
        status = TxStatus.CONFIRMED,
        confidence = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
