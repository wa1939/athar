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
    fun `parses pasted English receipt text using total instead of tax`() {
        val parsed = ManualEntryPhraseParser.parse(
            """
            STARBUCKS COFFEE
            Riyadh Park
            Receipt 883322
            VAT 15% 3.91
            Total SAR 29.50
            Date 2026-06-12
            """.trimIndent(),
        )

        assertThat(parsed).isEqualTo(
            ManualEntryPhrase(
                amountInput = "29.5",
                merchant = "STARBUCKS COFFEE",
                merchantNormalized = "starbucks coffee",
                type = TxType.EXPENSE,
                date = LocalDate(2026, 6, 12),
            ),
        )
    }

    @Test
    fun `parses pasted Arabic receipt text with Arabic digits`() {
        val parsed = ManualEntryPhraseParser.parse(
            """
            فاتورة ضريبية مبسطة
            الدانوب
            ضريبة القيمة المضافة ٤٫٥٠
            الإجمالي ٣٤٫٥٠ ر.س
            تاريخ ١٥/٠٦/٢٠٢٦
            """.trimIndent(),
        )

        assertThat(parsed?.amountInput).isEqualTo("34.5")
        assertThat(parsed?.merchant).isEqualTo("الدانوب")
        assertThat(parsed?.merchantNormalized).isEqualTo("الدانوب")
        assertThat(parsed?.type).isEqualTo(TxType.EXPENSE)
        assertThat(parsed?.date).isEqualTo(LocalDate(2026, 6, 15))
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
    fun `applier fills receipt total merchant date and matching category`() {
        val state = AddTransactionState.initial(LocalDate(2026, 6, 13)).copy(
            merchantSuggestions = persistentListOf(
                ManualEntrySuggestion(
                    merchant = "Starbucks Coffee",
                    merchantNormalized = "starbucks coffee",
                    amountInput = "22",
                    type = TxType.EXPENSE,
                    categoryId = "cat-coffee",
                    uses = 4,
                ),
            ),
        )

        val applied = ManualEntryPhraseApplier.apply(
            state,
            """
            Starbucks Coffee
            VAT 1.50
            Grand total SAR 31.00
            12/06/2026
            """.trimIndent(),
        )

        assertThat(applied.amount).isEqualTo("31")
        assertThat(applied.merchant).isEqualTo("Starbucks Coffee")
        assertThat(applied.date).isEqualTo(LocalDate(2026, 6, 12))
        assertThat(applied.selectedCategoryId).isEqualTo("cat-coffee")
        assertThat(applied.quickEntryError).isNull()
    }

    @Test
    fun `ocr prefill fills receipt fields without replacing quick entry text`() {
        val state = AddTransactionState.initial(LocalDate(2026, 6, 13)).copy(
            quickEntry = "spent 15 at old place",
            selectedCategoryId = "cat-existing",
        )

        val result = ReceiptOcrPrefillApplier.apply(
            state,
            """
            JARIR BOOKSTORE
            VAT SAR 7.50
            Total SAR 57.50
            Date 2026-06-12
            """.trimIndent(),
        )

        assertThat(result.status).isEqualTo(ReceiptOcrStatus.FILLED)
        assertThat(result.state.quickEntry).isEqualTo("spent 15 at old place")
        assertThat(result.state.amount).isEqualTo("57.5")
        assertThat(result.state.merchant).isEqualTo("JARIR BOOKSTORE")
        assertThat(result.state.date).isEqualTo(LocalDate(2026, 6, 12))
        assertThat(result.state.selectedCategoryId).isEqualTo("cat-existing")
        assertThat(result.state.quickEntryError).isNull()
    }

    @Test
    fun `ocr prefill reports no text without changing existing fields`() {
        val state = AddTransactionState.initial(LocalDate(2026, 6, 13)).copy(
            amount = "12",
            merchant = "Existing",
            quickEntry = "spent 12 at Existing",
            selectedCategoryId = "cat-existing",
        )

        val result = ReceiptOcrPrefillApplier.apply(state, "loyalty points only")

        assertThat(result.status).isEqualTo(ReceiptOcrStatus.NO_TEXT)
        assertThat(result.state).isEqualTo(state)
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
