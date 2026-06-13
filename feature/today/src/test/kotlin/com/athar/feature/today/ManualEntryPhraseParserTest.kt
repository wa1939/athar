package com.athar.feature.today

import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class ManualEntryPhraseParserTest {

    @Test
    fun `parses English expense phrase`() {
        val parsed = ManualEntryPhraseParser.parse("spent 50 at Starbucks")

        assertThat(parsed).isEqualTo(
            ManualEntryPhrase(
                amountInput = "50",
                merchant = "Starbucks",
                merchantNormalized = "starbucks",
                type = TxType.EXPENSE,
            ),
        )
    }

    @Test
    fun `parses English income phrase`() {
        val parsed = ManualEntryPhraseParser.parse("income 2500 from Acme Payroll")

        assertThat(parsed).isEqualTo(
            ManualEntryPhrase(
                amountInput = "2500",
                merchant = "Acme Payroll",
                merchantNormalized = "acme payroll",
                type = TxType.INCOME,
            ),
        )
    }

    @Test
    fun `parses Arabic expense and income phrases`() {
        val expense = ManualEntryPhraseParser.parse("دفعت ٣٥ في كارفور")
        val income = ManualEntryPhraseParser.parse("استلمت ١٠٠٠ من الراتب")

        assertThat(expense?.amountInput).isEqualTo("35")
        assertThat(expense?.merchant).isEqualTo("كارفور")
        assertThat(expense?.type).isEqualTo(TxType.EXPENSE)
        assertThat(income?.amountInput).isEqualTo("1000")
        assertThat(income?.merchant).isEqualTo("الراتب")
        assertThat(income?.type).isEqualTo(TxType.INCOME)
    }

    @Test
    fun `from source does not make expense phrase income`() {
        val english = ManualEntryPhraseParser.parse("paid 50 from wallet at Starbucks")
        val arabic = ManualEntryPhraseParser.parse("دفعت ٣٥ من المحفظة في كارفور")

        assertThat(english?.merchant).isEqualTo("Starbucks")
        assertThat(english?.type).isEqualTo(TxType.EXPENSE)
        assertThat(arabic?.merchant).isEqualTo("كارفور")
        assertThat(arabic?.type).isEqualTo(TxType.EXPENSE)
    }

    @Test
    fun `applier reuses matching recent suggestion category`() {
        val state = AddTransactionState.initial(LocalDate(2026, 6, 13)).copy(
            merchantSuggestions = persistentListOf(
                ManualEntrySuggestion(
                    merchant = "Starbucks",
                    merchantNormalized = "starbucks",
                    amountInput = "22",
                    type = TxType.EXPENSE,
                    categoryId = "cat-coffee",
                    uses = 4,
                ),
            ),
        )

        val applied = ManualEntryPhraseApplier.apply(state, "spent 50 at Starbucks")

        assertThat(applied.amount).isEqualTo("50")
        assertThat(applied.merchant).isEqualTo("Starbucks")
        assertThat(applied.type).isEqualTo(TxType.EXPENSE)
        assertThat(applied.selectedCategoryId).isEqualTo("cat-coffee")
        assertThat(applied.quickEntryError).isNull()
    }

    @Test
    fun `applier reports failed parse without changing existing fields`() {
        val state = AddTransactionState.initial(LocalDate(2026, 6, 13)).copy(
            amount = "12",
            merchant = "Existing",
            selectedCategoryId = "cat-existing",
        )

        val applied = ManualEntryPhraseApplier.apply(state, "coffee later")

        assertThat(applied.amount).isEqualTo("12")
        assertThat(applied.merchant).isEqualTo("Existing")
        assertThat(applied.selectedCategoryId).isEqualTo("cat-existing")
        assertThat(applied.quickEntryError).isEqualTo(QuickEntryError.PARSE_FAILED)
    }
}
