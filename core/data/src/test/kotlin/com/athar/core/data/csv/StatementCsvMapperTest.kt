package com.athar.core.data.csv

import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CsvImportColumnMapping
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StatementCsvMapperTest {

    @Test
    fun `detects existing Athar export layout`() {
        val columns = StatementCsvMapper.detect(
            listOf("date", "vendor", "amount", "currency", "category", "type", "notes"),
        )

        val row = StatementCsvMapper.map(
            row = listOf("2026-05-01", "Jarir", "149.75", "SAR", "Shopping", "EXPENSE", "printer ink"),
            columns = columns!!,
        )

        assertThat(row!!.date).isEqualTo(LocalDate(2026, 5, 1))
        assertThat(row.merchant).isEqualTo("Jarir")
        assertThat(row.amount).isEqualTo(BigDecimal("149.75"))
        assertThat(row.currency).isEqualTo("SAR")
        assertThat(row.type).isEqualTo(TxType.EXPENSE)
        assertThat(row.category).isEqualTo("Shopping")
        assertThat(row.notes).isEqualTo("printer ink")
    }

    @Test
    fun `maps debit and credit split statement columns`() {
        val columns = StatementCsvMapper.detect(
            listOf("Transaction Date", "Description", "Debit", "Credit", "Currency", "Running Balance"),
        )!!

        val expense = StatementCsvMapper.map(
            row = listOf("01/05/2026", "Coffee shop", "18.50", "", "SAR", "981.50"),
            columns = columns,
        )
        val income = StatementCsvMapper.map(
            row = listOf("02/05/2026", "Salary", "", "10,000.00", "SAR", "10,981.50"),
            columns = columns,
        )

        assertThat(expense!!.date).isEqualTo(LocalDate(2026, 5, 1))
        assertThat(expense.type).isEqualTo(TxType.EXPENSE)
        assertThat(expense.amount).isEqualTo(BigDecimal("18.50"))
        assertThat(income!!.type).isEqualTo(TxType.INCOME)
        assertThat(income.amount).isEqualTo(BigDecimal("10000.00"))
    }

    @Test
    fun `infers signed bank amount direction from statement-like headers`() {
        val columns = StatementCsvMapper.detect(listOf("Posted Date", "Narrative", "Amount", "Currency"))!!

        val cardPurchase = StatementCsvMapper.map(
            row = listOf("2026-05-03", "Train ticket", "-42.00", "GBP"),
            columns = columns,
        )
        val refund = StatementCsvMapper.map(
            row = listOf("2026-05-04", "Refund", "15.25", "GBP"),
            columns = columns,
        )

        assertThat(cardPurchase!!.type).isEqualTo(TxType.EXPENSE)
        assertThat(cardPurchase.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(cardPurchase.currency).isEqualTo("GBP")
        assertThat(refund!!.type).isEqualTo(TxType.INCOME)
        assertThat(refund.amount).isEqualTo(BigDecimal("15.25"))
    }

    @Test
    fun `maps positive amount with debit credit indicator column`() {
        val columns = StatementCsvMapper.detect(
            listOf("Booking Date", "Narrative", "Amount", "D/C", "Currency"),
        )!!

        val expense = StatementCsvMapper.map(
            row = listOf("2026-05-03", "Train ticket", "42.00", "D", "GBP"),
            columns = columns,
        )
        val income = StatementCsvMapper.map(
            row = listOf("2026-05-04", "Refund", "15.25", "C", "GBP"),
            columns = columns,
        )

        assertThat(expense!!.type).isEqualTo(TxType.EXPENSE)
        assertThat(expense.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(income!!.type).isEqualTo(TxType.INCOME)
        assertThat(income.amount).isEqualTo(BigDecimal("15.25"))
    }

    @Test
    fun `maps Arabic debit credit indicator column`() {
        val columns = StatementCsvMapper.detect(
            listOf("تاريخ القيد", "البيان", "المبلغ", "مدين/دائن", "العملة"),
        )!!

        val expense = StatementCsvMapper.map(
            row = listOf("2026-05-03", "مطعم", "42.00", "مدين", "SAR"),
            columns = columns,
        )
        val income = StatementCsvMapper.map(
            row = listOf("2026-05-04", "راتب", "1000.00", "دائن", "SAR"),
            columns = columns,
        )

        assertThat(expense!!.type).isEqualTo(TxType.EXPENSE)
        assertThat(expense.amount).isEqualTo(BigDecimal("42.00"))
        assertThat(income!!.type).isEqualTo(TxType.INCOME)
        assertThat(income.amount).isEqualTo(BigDecimal("1000.00"))
    }

    @Test
    fun `keeps positive Athar amount as expense when no type is present`() {
        val columns = StatementCsvMapper.detect(listOf("date", "vendor", "amount"))!!

        val row = StatementCsvMapper.map(
            row = listOf("2026-05-05", "Grocery", "200.00"),
            columns = columns,
        )

        assertThat(row!!.type).isEqualTo(TxType.EXPENSE)
        assertThat(row.amount).isEqualTo(BigDecimal("200.00"))
    }

    @Test
    fun `supports Arabic headers and Arabic Indic digits`() {
        val columns = StatementCsvMapper.detect(listOf("تاريخ الحركة", "البيان", "مدين", "دائن", "العملة"))!!

        val row = StatementCsvMapper.map(
            row = listOf("٠٥/٠٥/٢٠٢٦", "مطعم", "٤٥٫٥٠", "", "SAR"),
            columns = columns,
        )

        assertThat(row!!.date).isEqualTo(LocalDate(2026, 5, 5))
        assertThat(row.type).isEqualTo(TxType.EXPENSE)
        assertThat(row.amount).isEqualTo(BigDecimal("45.50"))
    }

    @Test
    fun `rejects rows that have both debit and credit amounts`() {
        val columns = StatementCsvMapper.detect(listOf("Date", "Details", "Debit", "Credit"))!!

        val row = StatementCsvMapper.map(
            row = listOf("2026-05-06", "Ambiguous row", "10.00", "5.00"),
            columns = columns,
        )

        assertThat(row).isNull()
    }

    @Test
    fun `manual mapping supports unknown bank headers`() {
        val columns = StatementCsvMapper.detect(
            header = listOf("Booked", "Counterparty text", "Out", "In", "ISO"),
            mapping = CsvImportColumnMapping(
                date = "Booked",
                merchant = "Counterparty text",
                debit = "Out",
                credit = "In",
                currency = "ISO",
            ),
        )!!

        val row = StatementCsvMapper.map(
            row = listOf("2026-05-07", "Unknown Coffee", "12.75", "", "USD"),
            columns = columns,
        )

        assertThat(row!!.date).isEqualTo(LocalDate(2026, 5, 7))
        assertThat(row.merchant).isEqualTo("Unknown Coffee")
        assertThat(row.type).isEqualTo(TxType.EXPENSE)
        assertThat(row.amount).isEqualTo(BigDecimal("12.75"))
        assertThat(row.currency).isEqualTo("USD")
    }
}
