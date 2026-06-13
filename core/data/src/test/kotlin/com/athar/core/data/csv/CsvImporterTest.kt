package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CsvImportColumnMapping
import com.athar.core.domain.repo.CsvImportPreviewResult
import com.athar.core.domain.repo.CsvImportResult
import com.athar.core.domain.repo.CsvImportRowDecision
import com.athar.core.domain.repo.TransactionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal

class CsvImporterTest {

    @Test
    fun `preview reports rows and detected columns without committing`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementCsv.toByteArray()))

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(preview.columns.date).isEqualTo("Date")
        assertThat(preview.columns.merchant).isEqualTo("Description")
        assertThat(preview.columns.debit).isEqualTo("Debit")
        assertThat(preview.columns.credit).isEqualTo("Credit")
        assertThat(preview.sampleRows.map { it.rowNumber }).containsExactly(2, 4).inOrder()
        assertThat(preview.sampleRows.first().merchant).isEqualTo("Starbucks")
        assertThat(preview.sampleRows.first().type).isEqualTo(TxType.EXPENSE)
        assertThat(preview.sampleRows.first().category).isEqualTo("Coffee")
        assertThat(preview.skippedRows.single().rowNumber).isEqualTo(3)
        assertThat(transactions.upserts).isEmpty()
    }

    @Test
    fun `import commits the same importable and skipped counts as preview`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val result = importer.import(ByteArrayInputStream(statementCsv.toByteArray()))

        assertThat(result).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 1))
        assertThat(transactions.upserts).hasSize(2)
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Starbucks", "Salary").inOrder()
        assertThat(transactions.upserts.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
    }

    @Test
    fun `row decisions exclude selected rows from preview and import`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )
        val decisions = listOf(CsvImportRowDecision(rowNumber = 2, shouldImport = false))

        val result = importer.preview(
            input = ByteArrayInputStream(statementCsv.toByteArray()),
            rowDecisions = decisions,
        )
        val imported = importer.import(
            input = ByteArrayInputStream(statementCsv.toByteArray()),
            rowDecisions = decisions,
        )

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(1)
        assertThat(preview.skipped).isEqualTo(2)
        assertThat(preview.sampleRows.map { it.rowNumber }).containsExactly(2, 4).inOrder()
        assertThat(preview.sampleRows.first().included).isFalse()
        assertThat(preview.skippedRows.map { it.reason }).contains("Excluded from import")
        assertThat(imported).isEqualTo(CsvImportResult.Done(imported = 1, skipped = 2))
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Salary")
    }

    @Test
    fun `import assigns statement rows to selected account`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        importer.import(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")

        assertThat(transactions.upserts).hasSize(2)
        assertThat(transactions.upserts.map { it.accountId }).containsExactly("acc-checking", "acc-checking")
    }

    @Test
    fun `preview supports semicolon delimited statement csv`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementSemicolonCsv.toByteArray()))

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(preview.columns.date).isEqualTo("Date")
        assertThat(preview.columns.merchant).isEqualTo("Description")
        assertThat(preview.columns.debit).isEqualTo("Debit")
        assertThat(preview.sampleRows.first().amount).isEqualTo("18.50")
        assertThat(preview.sampleRows.first().currency).isEqualTo("EUR")
        assertThat(preview.sampleRows.first().category).isEqualTo("Coffee")
        assertThat(preview.skippedRows.single().rowNumber).isEqualTo(3)
        assertThat(transactions.upserts).isEmpty()
    }

    @Test
    fun `import supports tab delimited statement csv`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.import(ByteArrayInputStream(statementTabCsv.toByteArray()))

        assertThat(result).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 0))
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Coffee Shop", "Salary").inOrder()
        assertThat(transactions.upserts.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
        assertThat(transactions.upserts.map { it.amount.currency }).containsExactly("GBP", "GBP").inOrder()
    }

    @Test
    fun `preview supports positive amount with debit credit indicator column`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementIndicatorCsv.toByteArray()))

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(0)
        assertThat(preview.columns.amount).isEqualTo("Amount")
        assertThat(preview.columns.type).isEqualTo("D/C")
        assertThat(preview.sampleRows.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
        assertThat(preview.sampleRows.map { it.amount }).containsExactly("42.00", "15.25").inOrder()
    }

    @Test
    fun `preview requests mapping for unknown csv headers and imports with manual mapping`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val first = importer.preview(ByteArrayInputStream(statementUnknownHeadersCsv.toByteArray()))

        val required = first as CsvImportPreviewResult.MappingRequired
        assertThat(required.columns).containsExactly("Booked", "Counterparty text", "Out", "In", "ISO", "Bucket")
        assertThat(required.reason).contains("Map date")

        val mapping = CsvImportColumnMapping(
            date = "Booked",
            merchant = "Counterparty text",
            debit = "Out",
            credit = "In",
            currency = "ISO",
            category = "Bucket",
        )
        val preview = importer.preview(
            input = ByteArrayInputStream(statementUnknownHeadersCsv.toByteArray()),
            mapping = mapping,
        ) as CsvImportPreviewResult.Done
        val imported = importer.import(
            input = ByteArrayInputStream(statementUnknownHeadersCsv.toByteArray()),
            mapping = mapping,
        )

        assertThat(preview.preview.importable).isEqualTo(2)
        assertThat(preview.preview.availableColumns).containsExactly(
            "Booked",
            "Counterparty text",
            "Out",
            "In",
            "ISO",
            "Bucket",
        ).inOrder()
        assertThat(preview.preview.columns.date).isEqualTo("Booked")
        assertThat(preview.preview.columns.merchant).isEqualTo("Counterparty text")
        assertThat(preview.preview.columns.debit).isEqualTo("Out")
        assertThat(preview.preview.columns.credit).isEqualTo("In")
        assertThat(preview.preview.sampleRows.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
        assertThat(imported).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 0))
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Unknown Coffee", "Payroll").inOrder()
        assertThat(transactions.upserts.map { it.amount.currency }).containsExactly("USD", "USD").inOrder()
    }

    @Test
    fun `preview reports ofx transactions without committing`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementOfx.toByteArray()))

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(preview.columns.date).isEqualTo("OFX DTPOSTED")
        assertThat(preview.columns.amount).isEqualTo("OFX TRNAMT")
        assertThat(preview.sampleRows.map { it.rowNumber }).containsExactly(1, 2).inOrder()
        assertThat(preview.sampleRows.first().date).isEqualTo("2026-06-01")
        assertThat(preview.sampleRows.first().merchant).isEqualTo("Starbucks")
        assertThat(preview.sampleRows.first().amount).isEqualTo("18.50")
        assertThat(preview.sampleRows.first().currency).isEqualTo("USD")
        assertThat(preview.sampleRows.first().type).isEqualTo(TxType.EXPENSE)
        assertThat(preview.sampleRows[1].type).isEqualTo(TxType.INCOME)
        assertThat(preview.skippedRows.single().rowNumber).isEqualTo(3)
        assertThat(transactions.upserts).isEmpty()
    }

    @Test
    fun `import commits ofx transactions through same preview path`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.import(ByteArrayInputStream(statementOfx.toByteArray()))

        assertThat(result).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 1))
        assertThat(transactions.upserts).hasSize(2)
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Starbucks", "Acme Payroll").inOrder()
        assertThat(transactions.upserts.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
        assertThat(transactions.upserts.map { it.amount.amount })
            .containsExactly(BigDecimal("18.50"), BigDecimal("1000.00"))
            .inOrder()
        assertThat(transactions.upserts.map { it.amount.currency }).containsExactly("USD", "USD").inOrder()
        val sourceRefs = transactions.upserts.mapNotNull { it.sourceRefId }
        assertThat(sourceRefs).hasSize(2)
        assertThat(sourceRefs).containsNoDuplicates()
        assertThat(sourceRefs.all { it.startsWith("import:ofx:") }).isTrue()
    }

    @Test
    fun `preview reports mt940 transactions without committing`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementMt940.toByteArray()))

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(2)
        assertThat(preview.skipped).isEqualTo(1)
        assertThat(preview.columns.date).isEqualTo("MT940 :61: date")
        assertThat(preview.columns.merchant).isEqualTo("MT940 :86:")
        assertThat(preview.columns.amount).isEqualTo("MT940 :61: amount")
        assertThat(preview.sampleRows.map { it.rowNumber }).containsExactly(1, 2).inOrder()
        assertThat(preview.sampleRows.first().date).isEqualTo("2026-06-01")
        assertThat(preview.sampleRows.first().merchant).isEqualTo("Starbucks")
        assertThat(preview.sampleRows.first().amount).isEqualTo("18.50")
        assertThat(preview.sampleRows.first().currency).isEqualTo("USD")
        assertThat(preview.sampleRows.first().type).isEqualTo(TxType.EXPENSE)
        assertThat(preview.sampleRows[1].type).isEqualTo(TxType.INCOME)
        assertThat(preview.skippedRows.single().rowNumber).isEqualTo(3)
        assertThat(transactions.upserts).isEmpty()
    }

    @Test
    fun `import commits mt940 transactions through same preview path`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(emptyList()),
            clock = FixedClock,
        )

        val result = importer.import(ByteArrayInputStream(statementMt940.toByteArray()))

        assertThat(result).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 1))
        assertThat(transactions.upserts).hasSize(2)
        assertThat(transactions.upserts.map { it.merchant }).containsExactly("Starbucks", "Acme Payroll").inOrder()
        assertThat(transactions.upserts.map { it.type }).containsExactly(TxType.EXPENSE, TxType.INCOME).inOrder()
        assertThat(transactions.upserts.map { it.amount.amount })
            .containsExactly(BigDecimal("18.50"), BigDecimal("1000.00"))
            .inOrder()
        assertThat(transactions.upserts.map { it.amount.currency }).containsExactly("USD", "USD").inOrder()
        val sourceRefs = transactions.upserts.mapNotNull { it.sourceRefId }
        assertThat(sourceRefs).hasSize(2)
        assertThat(sourceRefs).containsNoDuplicates()
        assertThat(sourceRefs.all { it.startsWith("import:mt940:") }).isTrue()
    }

    @Test
    fun `statement import skips rows already imported for same account`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val first = importer.import(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")
        val secondPreview = importer.preview(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")
        val secondImport = importer.import(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")

        assertThat(first).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 1))
        val preview = (secondPreview as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(0)
        assertThat(preview.skipped).isEqualTo(3)
        assertThat(preview.skippedRows.map { it.rowNumber }).containsExactly(2, 3, 4).inOrder()
        assertThat(preview.skippedRows.map { it.reason })
            .containsExactly("Already imported", "Unparseable row", "Already imported")
            .inOrder()
        assertThat(secondImport).isEqualTo(CsvImportResult.Done(imported = 0, skipped = 3))
        assertThat(transactions.upserts).hasSize(2)
    }

    @Test
    fun `statement import skips legacy imported csv rows by content`() = runTest {
        val transactions = FakeTransactionRepository(
            initialRows = listOf(
                importedTx(
                    accountId = "acc-checking",
                    merchant = "Starbucks",
                    amount = "18.50",
                    type = TxType.EXPENSE,
                    date = LocalDate(2026, 6, 1),
                    sourceRefId = "csv-row-2",
                ),
                importedTx(
                    accountId = "acc-checking",
                    merchant = "Salary",
                    amount = "1000.00",
                    type = TxType.INCOME,
                    date = LocalDate(2026, 6, 2),
                    sourceRefId = "csv-row-4",
                ),
            ),
        )
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val result = importer.preview(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")

        val preview = (result as CsvImportPreviewResult.Done).preview
        assertThat(preview.importable).isEqualTo(0)
        assertThat(preview.skippedRows.map { it.reason })
            .containsExactly("Already imported", "Unparseable row", "Already imported")
            .inOrder()
    }

    @Test
    fun `statement import source refs are account scoped`() = runTest {
        val transactions = FakeTransactionRepository()
        val importer = CsvImporter(
            transactions = transactions,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        importer.import(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-checking")
        val checkingRefs = transactions.upserts.map { it.sourceRefId }

        val second = importer.import(ByteArrayInputStream(statementCsv.toByteArray()), accountId = "acc-savings")
        val savingsRefs = transactions.upserts.drop(2).map { it.sourceRefId }

        assertThat(second).isEqualTo(CsvImportResult.Done(imported = 2, skipped = 1))
        assertThat(transactions.upserts).hasSize(4)
        assertThat(checkingRefs.intersect(savingsRefs.toSet())).isEmpty()
        assertThat((checkingRefs + savingsRefs).filterNotNull().all { it.startsWith("import:csv:") }).isTrue()
    }

    private class FakeTransactionRepository(
        private val initialRows: List<Transaction> = emptyList(),
    ) : TransactionRepository {
        val upserts = mutableListOf<Transaction>()

        override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observePending(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeAll(): Flow<List<Transaction>> = flowOf(initialRows + upserts)
        override suspend fun get(id: String): Transaction? = null
        override suspend fun upsert(transaction: Transaction) {
            upserts += transaction
        }
        override suspend fun delete(id: String) = unsupported()
        override suspend fun setStatus(id: String, status: TxStatus) = unsupported()
        override suspend fun clearPending(): Int = unsupported()
        override suspend fun confirmAllConfident(minConfidence: Float): Int = unsupported()
        override suspend fun dismissAllLowConfidence(maxConfidence: Float): Int = unsupported()
        override suspend fun dismissAllPending(): Int = unsupported()
        override suspend fun recoverDismissedToPending(): Int = unsupported()
        override suspend fun applyCategoryToMatching(pattern: String, categoryId: String): Int = unsupported()
    }

    private class FakeCategoryRepository(private val rows: List<Category>) : CategoryRepository {
        override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> = flowOf(rows)
        override suspend fun get(id: String): Category? = rows.firstOrNull { it.id == id }
        override suspend fun upsert(category: Category) = unsupported()
        override suspend fun archive(id: String) = unsupported()
        override suspend fun reorder(ids: List<String>) = unsupported()
    }

    private companion object {
        val FixedInstant: Instant = Instant.parse("2026-06-13T12:00:00Z")
        val FixedClock = object : Clock {
            override fun now(): Instant = FixedInstant
        }

        val statementCsv = """
            Date,Description,Debit,Credit,Currency,Category
            2026-06-01,Starbucks,18.50,,SAR,Coffee
            not-a-date,Broken row,9.00,,SAR,Coffee
            2026-06-02,Salary,,1000.00,SAR,
        """.trimIndent()

        val statementSemicolonCsv = """
            Date;Description;Debit;Credit;Currency;Category
            2026-06-01;Starbucks;18,50;;EUR;Coffee
            not-a-date;Broken row;9,00;;EUR;Coffee
            2026-06-02;Salary;;1000,00;EUR;
        """.trimIndent()

        val statementTabCsv = listOf(
            "Date\tNarrative\tAmount\tCurrency",
            "2026-06-01\tCoffee Shop\t-12.25\tGBP",
            "2026-06-02\tSalary\t1000.00\tGBP",
        ).joinToString("\n")

        val statementIndicatorCsv = """
            Booking Date,Narrative,Amount,D/C,Currency
            2026-06-01,Train ticket,42.00,D,GBP
            2026-06-02,Refund,15.25,C,GBP
        """.trimIndent()

        val statementUnknownHeadersCsv = """
            Booked,Counterparty text,Out,In,ISO,Bucket
            2026-06-01,Unknown Coffee,18.50,,USD,Coffee
            2026-06-02,Payroll,,1000.00,USD,
        """.trimIndent()

        val statementOfx = """
            OFXHEADER:100
            DATA:OFXSGML
            <OFX>
            <BANKMSGSRSV1>
            <STMTTRNRS>
            <STMTRS>
            <CURDEF>USD
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <DTPOSTED>20260601120000[-5:EST]
            <TRNAMT>-18.50
            <FITID>fit-1
            <NAME>Starbucks
            <MEMO>Card purchase
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>CREDIT
            <DTPOSTED>20260602120000
            <TRNAMT>1000.00
            <FITID>fit-2
            <NAME>Acme Payroll
            </STMTTRN>
            <STMTTRN>
            <TRNTYPE>DEBIT
            <TRNAMT>-9.00
            <NAME>Broken Row
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </STMTTRNRS>
            </BANKMSGSRSV1>
            </OFX>
        """.trimIndent()

        val statementMt940 = """
            :20:STARTUMSE
            :25:123456789
            :28C:00001/001
            :60F:C260531USD1000,00
            :61:2606010601D18,50NMSCNONREF//mt1
            :86:Starbucks
            :61:2606020602C1000,00NTRFNONREF//mt2
            :86:Acme Payroll
            :61:2606030603D9,00NMSCNONREF//mt3
            :62F:C260602USD1981,50
            -}
        """.trimIndent()

        fun coffeeCategory(): Category = Category(
            id = "cat-coffee",
            name = "Coffee",
            nameAr = "قهوة",
            kind = CategoryKind.EXPENSE,
            icon = null,
            monthlyTarget = null,
            archived = false,
            sortOrder = 0,
        )

        fun importedTx(
            accountId: String,
            merchant: String,
            amount: String,
            type: TxType,
            date: LocalDate,
            sourceRefId: String,
        ): Transaction = Transaction(
            id = "legacy-$sourceRefId",
            accountId = accountId,
            type = type,
            amount = Money.of(BigDecimal(amount), "SAR"),
            date = date,
            occurredAt = null,
            merchant = merchant,
            merchantNormalized = merchant.lowercase().trim(),
            categoryId = null,
            notes = null,
            source = IngestSource.IMPORT,
            sourceRefId = sourceRefId,
            status = TxStatus.CONFIRMED,
            confidence = 1.0f,
            createdAt = FixedInstant,
            updatedAt = FixedInstant,
        )

        fun unsupported(): Nothing = throw UnsupportedOperationException("not used in this test")
    }
}
