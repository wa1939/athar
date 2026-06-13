package com.athar.feature.today

import com.athar.core.common.money.Money
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.RECONCILE_REF_PREFIX
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class ManualEntrySuggestionBuilderTest {

    @Test
    fun `ranks merchants by frequency and uses latest amount and category`() {
        val suggestions = ManualEntrySuggestionBuilder.build(
            transactions = listOf(
                tx("coffee-old", merchant = "Brew Lab", amount = "12", categoryId = "cat-coffee", day = 1),
                tx("grocery", merchant = "Tamimi", amount = "80", categoryId = "cat-grocery", day = 2),
                tx("coffee-new", merchant = "Brew Lab", amount = "15", categoryId = "cat-cafe", day = 3),
            ),
            displayCurrency = "SAR",
        )

        assertThat(suggestions.map { it.merchant }).containsExactly("Brew Lab", "Tamimi").inOrder()
        assertThat(suggestions.first().uses).isEqualTo(2)
        assertThat(suggestions.first().amountInput).isEqualTo("15")
        assertThat(suggestions.first().categoryId).isEqualTo("cat-cafe")
    }

    @Test
    fun `excludes unsafe rows and hides amount for a different display currency`() {
        val suggestions = ManualEntrySuggestionBuilder.build(
            transactions = listOf(
                tx("pending", merchant = "Pending Shop", status = TxStatus.PENDING),
                tx("transfer", merchant = "Savings", type = TxType.TRANSFER),
                tx(
                    "reconcile",
                    merchant = "Manual adjustment",
                    sourceRefId = "$RECONCILE_REF_PREFIX-1",
                ),
                tx("usd", merchant = "US Store", amount = "10", currency = "USD"),
            ),
            displayCurrency = "SAR",
        )

        assertThat(suggestions).hasSize(1)
        assertThat(suggestions.single().merchant).isEqualTo("US Store")
        assertThat(suggestions.single().amountInput).isNull()
    }

    private fun tx(
        id: String,
        merchant: String,
        amount: String = "10",
        currency: String = "SAR",
        categoryId: String? = "cat-food",
        type: TxType = TxType.EXPENSE,
        status: TxStatus = TxStatus.CONFIRMED,
        sourceRefId: String? = null,
        day: Int = 1,
    ): Transaction {
        val instant = Instant.parse("2026-05-${day.toString().padStart(2, '0')}T12:00:00Z")
        return Transaction(
            id = id,
            accountId = "manual",
            type = type,
            amount = Money.of(amount, currency),
            date = LocalDate(2026, 5, day),
            occurredAt = instant,
            merchant = merchant,
            merchantNormalized = merchant.lowercase().trim(),
            categoryId = categoryId,
            notes = null,
            source = IngestSource.MANUAL,
            sourceRefId = sourceRefId,
            status = status,
            confidence = 1f,
            createdAt = instant,
            updatedAt = instant,
        )
    }
}
