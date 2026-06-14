package com.athar.core.data.csv

import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.CategoryRule
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.MerchantBulkImportResult
import com.athar.core.domain.repo.TransactionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MerchantBulkCsvTest {

    @Test
    fun `export emits stable key and importer matches changed transaction id`() = runTest {
        val oldTx = tx(
            id = "old-id",
            sourceRefId = "inbox-42",
            notes = "شراء\nمبلغ:SAR 19\nمن:BARNS",
        )
        val exportRepo = FakeTransactionRepository(listOf(oldTx))
        val out = ByteArrayOutputStream()

        val exported = MerchantBulkExporter(
            transactions = exportRepo,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
        ).exportUncategorized(out)

        assertThat(exported).isEqualTo(com.athar.core.domain.repo.MerchantBulkExportResult.Done(rows = 1))
        val csv = out.toString(Charsets.UTF_8)
        assertThat(csv).contains("id,stable_key,source_ref_id,merchant,merchant_normalized,merchant_group_count,category_options")
        assertThat(csv).contains("cat-coffee=Coffee / قهوة")
        assertThat(csv).contains("\"شراء\nمبلغ:SAR 19\nمن:BARNS\"")

        val filledCsv = csv.trimEnd().removeSuffix(",") + ",cat-coffee\n"
        val currentTx = oldTx.copy(id = "new-id", categoryId = null, status = TxStatus.PENDING)
        val importRepo = FakeTransactionRepository(listOf(currentTx))
        val rules = FakeCategoryRuleRepository()
        val importer = MerchantBulkImporter(
            transactions = importRepo,
            rules = rules,
            categories = FakeCategoryRepository(listOf(coffeeCategory())),
            clock = FixedClock,
        )

        val result = importer.importCategorizations(ByteArrayInputStream(filledCsv.toByteArray()))

        assertThat(result).isEqualTo(MerchantBulkImportResult.Done(updated = 1, rulesAdded = 1, skipped = 0))
        assertThat(importRepo.get("new-id")?.categoryId).isEqualTo("cat-coffee")
        assertThat(importRepo.get("new-id")?.status).isEqualTo(TxStatus.CONFIRMED)
        assertThat(rules.learnedPatterns).containsExactly("barns", "cat-coffee")
    }

    @Test
    fun `importer can match old csv without stable key by content fingerprint`() = runTest {
        val currentTx = tx(id = "new-id", sourceRefId = null, merchant = "Hemmah", amount = "99.00")
        val repo = FakeTransactionRepository(listOf(currentTx))
        val importer = MerchantBulkImporter(
            transactions = repo,
            rules = FakeCategoryRuleRepository(),
            categories = FakeCategoryRepository(listOf(homeCategory())),
            clock = FixedClock,
        )
        val oldCsv = """
            id,merchant,merchant_normalized,amount,currency,type,status,date,raw_body,category_id
            old-id,Hemmah,hemmah,99.0,SAR,EXPENSE,PENDING,2026-02-14,"line one
            line two",cat-home-maintenance
        """.trimIndent()

        val result = importer.importCategorizations(ByteArrayInputStream(oldCsv.toByteArray()))

        assertThat(result).isEqualTo(MerchantBulkImportResult.Done(updated = 1, rulesAdded = 1, skipped = 0))
        assertThat(repo.get("new-id")?.categoryId).isEqualTo("cat-home-maintenance")
        assertThat(repo.get("new-id")?.status).isEqualTo(TxStatus.CONFIRMED)
    }

    @Test
    fun `export groups repeated merchants first and skips transfers`() = runTest {
        val rows = listOf(
            tx(id = "singleton", sourceRefId = "inbox-1", merchant = "Zed Market"),
            tx(id = "transfer", sourceRefId = "inbox-2", merchant = "Internal Transfer", type = TxType.TRANSFER),
            tx(id = "repeated-new", sourceRefId = "inbox-3", merchant = "Hemmah", date = LocalDate(2026, 3, 2)),
            tx(
                id = "categorized",
                sourceRefId = "inbox-4",
                merchant = "Starbucks",
                status = TxStatus.CONFIRMED,
                categoryId = "cat-coffee",
            ),
            tx(
                id = "repeated-old",
                sourceRefId = "inbox-5",
                merchant = "Hemmah",
                status = TxStatus.DISMISSED,
                date = LocalDate(2026, 3, 1),
            ),
            tx(
                id = "confirmed-empty",
                sourceRefId = "inbox-6",
                merchant = "BARNS",
                status = TxStatus.CONFIRMED,
            ),
        )
        val out = ByteArrayOutputStream()

        val exported = MerchantBulkExporter(
            transactions = FakeTransactionRepository(rows),
            categories = FakeCategoryRepository(
                listOf(
                    homeCategory(),
                    coffeeCategory(),
                    salaryCategory(),
                    coffeeCategory().copy(id = "cat-archived", archived = true),
                ),
            ),
        ).exportUncategorized(out)

        assertThat(exported).isEqualTo(com.athar.core.domain.repo.MerchantBulkExportResult.Done(rows = 4))
        val lines = out.toString(Charsets.UTF_8).trim().lines()
        val header = lines.first().split(",")
        val merchantIdx = header.indexOf("merchant")
        val groupCountIdx = header.indexOf("merchant_group_count")
        val optionsIdx = header.indexOf("category_options")
        val dataRows = lines.drop(1).map { it.split(",") }

        assertThat(dataRows.map { it[merchantIdx] })
            .containsExactly("Hemmah", "Hemmah", "BARNS", "Zed Market")
            .inOrder()
        assertThat(dataRows.map { it[groupCountIdx] })
            .containsExactly("2", "2", "1", "1")
            .inOrder()
        assertThat(dataRows.first()[optionsIdx]).contains("cat-home-maintenance=Home maintenance")
        assertThat(dataRows.first()[optionsIdx]).contains("cat-coffee=Coffee")
        assertThat(dataRows.first()[optionsIdx]).doesNotContain("cat-salary")
        assertThat(dataRows.first()[optionsIdx]).doesNotContain("cat-archived")
        assertThat(out.toString(Charsets.UTF_8)).doesNotContain("Internal Transfer")
        assertThat(out.toString(Charsets.UTF_8)).doesNotContain("Starbucks")
    }

    @Test
    fun `export uses income category options for income rows`() = runTest {
        val out = ByteArrayOutputStream()

        val exported = MerchantBulkExporter(
            transactions = FakeTransactionRepository(
                listOf(
                    tx(
                        id = "income",
                        sourceRefId = "inbox-income",
                        merchant = "Monthly Profit",
                        type = TxType.INCOME,
                    ),
                ),
            ),
            categories = FakeCategoryRepository(listOf(coffeeCategory(), salaryCategory())),
        ).exportUncategorized(out)

        assertThat(exported).isEqualTo(com.athar.core.domain.repo.MerchantBulkExportResult.Done(rows = 1))
        val lines = out.toString(Charsets.UTF_8).trim().lines()
        val header = lines.first().split(",")
        val options = lines.drop(1).single().split(",")[header.indexOf("category_options")]

        assertThat(options).contains("cat-salary=Salary / راتب")
        assertThat(options).doesNotContain("cat-coffee")
    }

    private fun tx(
        id: String,
        sourceRefId: String?,
        merchant: String = "BARNS",
        amount: String = "19.00",
        notes: String? = null,
        type: TxType = TxType.EXPENSE,
        status: TxStatus = TxStatus.PENDING,
        categoryId: String? = null,
        date: LocalDate = LocalDate(2026, 2, 14),
    ): Transaction = Transaction(
        id = id,
        accountId = "acc-1",
        type = type,
        amount = Money.of(amount),
        date = date,
        occurredAt = FixedInstant,
        merchant = merchant,
        merchantNormalized = merchant.lowercase().trim(),
        categoryId = categoryId,
        notes = notes,
        source = IngestSource.SMS,
        sourceRefId = sourceRefId,
        status = status,
        confidence = 0.8f,
        createdAt = FixedInstant,
        updatedAt = FixedInstant,
    )

    private fun coffeeCategory(): Category = Category(
        id = "cat-coffee",
        name = "Coffee",
        nameAr = "قهوة",
        kind = CategoryKind.EXPENSE,
        icon = null,
        monthlyTarget = null,
        archived = false,
        sortOrder = 0,
    )

    private fun homeCategory(): Category = Category(
        id = "cat-home-maintenance",
        name = "Home maintenance",
        nameAr = "صيانة منزل",
        kind = CategoryKind.EXPENSE,
        icon = null,
        monthlyTarget = null,
        archived = false,
        sortOrder = 1,
    )

    private fun salaryCategory(): Category = Category(
        id = "cat-salary",
        name = "Salary",
        nameAr = "راتب",
        kind = CategoryKind.INCOME,
        icon = null,
        monthlyTarget = null,
        archived = false,
        sortOrder = 100,
    )

    private class FakeTransactionRepository(initial: List<Transaction>) : TransactionRepository {
        private val rows = MutableStateFlow(initial.associateBy { it.id })

        override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> = observeAll()
        override fun observePending(): Flow<List<Transaction>> =
            flowOf(rows.value.values.filter { it.status == TxStatus.PENDING })

        override fun observeAll(): Flow<List<Transaction>> = flowOf(rows.value.values.toList())
        override suspend fun get(id: String): Transaction? = rows.value[id]
        override suspend fun upsert(transaction: Transaction) {
            rows.value = rows.value + (transaction.id to transaction)
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
        override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> =
            flowOf(
                rows.filter { category ->
                    (kind == null || category.kind == kind) &&
                        (includeArchived || !category.archived)
                },
            )

        override suspend fun get(id: String): Category? = rows.firstOrNull { it.id == id }
        override suspend fun upsert(category: Category) = unsupported()
        override suspend fun archive(id: String) = unsupported()
        override suspend fun reorder(ids: List<String>) = unsupported()
    }

    private class FakeCategoryRuleRepository : CategoryRuleRepository {
        val learnedPatterns = mutableMapOf<String, String>()

        override fun observeAll(): Flow<List<CategoryRule>> = flowOf(emptyList())
        override suspend fun findMatching(merchantNormalized: String): List<CategoryRule> = emptyList()
        override suspend fun upsert(rule: CategoryRule) = unsupported()
        override suspend fun delete(id: String) = unsupported()
        override suspend fun learnFromCorrection(
            merchantNormalized: String,
            categoryId: String,
            patternType: PatternType,
        ): CategoryRule {
            learnedPatterns[merchantNormalized] = categoryId
            return CategoryRule(
                id = merchantNormalized,
                pattern = merchantNormalized,
                patternType = patternType,
                categoryId = categoryId,
                priority = 200,
                learnedFromUser = true,
                createdAt = FixedInstant,
            )
        }
    }

    private companion object {
        val FixedInstant: Instant = Instant.parse("2026-02-14T12:00:00Z")
        val FixedClock = object : Clock {
            override fun now(): Instant = FixedInstant
        }

        fun unsupported(): Nothing = throw UnsupportedOperationException("not used in this test")
    }
}
