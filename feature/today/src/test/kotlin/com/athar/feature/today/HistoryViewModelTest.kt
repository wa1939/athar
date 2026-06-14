package com.athar.feature.today

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
import com.athar.core.domain.repo.TransactionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var mainDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bulk category assignment confirms compatible selected rows and skips transfers`() = runTest(mainDispatcher) {
        val txRepo = HistoryFakeTransactionRepository(
            listOf(
                tx(id = "expense", type = TxType.EXPENSE, status = TxStatus.PENDING),
                tx(id = "transfer", type = TxType.TRANSFER, status = TxStatus.PENDING),
            ),
        )
        val viewModel = HistoryViewModel(
            transactions = txRepo,
            rules = HistoryFakeRuleRepository,
            categories = HistoryFakeCategoryRepository(
                listOf(
                    category(id = "cat-food", kind = CategoryKind.EXPENSE),
                    category(id = "cat-salary", kind = CategoryKind.INCOME),
                ),
            ),
            clock = FixedHistoryClock,
        )

        viewModel.toggleSelected("expense")
        viewModel.toggleSelected("transfer")
        viewModel.applyBulkCategory("cat-food")
        advanceUntilIdle()

        assertThat(txRepo.upserts.map { it.id }).containsExactly("expense")
        val updated = txRepo.upserts.single()
        assertThat(updated.categoryId).isEqualTo("cat-food")
        assertThat(updated.status).isEqualTo(TxStatus.CONFIRMED)
        assertThat(updated.updatedAt).isEqualTo(FixedHistoryClock.now())
        assertThat(viewModel.lastBulkCategory.value).isEqualTo(
            HistoryViewModel.BulkCategoryEvent(applied = 1, skipped = 1),
        )
        assertThat(viewModel.selectionMode.value).isFalse()
        assertThat(viewModel.selectedIds.value).isEmpty()
    }
}

private class HistoryFakeTransactionRepository(rows: List<Transaction>) : TransactionRepository {
    private val rowsById = rows.associateBy { it.id }.toMutableMap()
    val upserts = mutableListOf<Transaction>()

    override fun observeByPeriod(period: Period, status: TxStatus?): Flow<List<Transaction>> = flowOf(
        rowsById.values.filter { status == null || it.status == status },
    )

    override fun observePending(): Flow<List<Transaction>> = flowOf(rowsById.values.filter { it.status == TxStatus.PENDING })
    override fun observeAll(): Flow<List<Transaction>> = flowOf(rowsById.values.toList())
    override suspend fun get(id: String): Transaction? = rowsById[id]
    override suspend fun upsert(transaction: Transaction) {
        rowsById[transaction.id] = transaction
        upserts += transaction
    }

    override suspend fun delete(id: String) = Unit
    override suspend fun setStatus(id: String, status: TxStatus) = Unit
    override suspend fun clearPending(): Int = 0
    override suspend fun confirmAllConfident(minConfidence: Float): Int = 0
    override suspend fun dismissAllLowConfidence(maxConfidence: Float): Int = 0
    override suspend fun dismissAllPending(): Int = 0
    override suspend fun recoverDismissedToPending(): Int = 0
    override suspend fun applyCategoryToMatching(pattern: String, categoryId: String): Int = 0
}

private object HistoryFakeRuleRepository : CategoryRuleRepository {
    override fun observeAll(): Flow<List<CategoryRule>> = flowOf(emptyList())
    override suspend fun findMatching(merchantNormalized: String): List<CategoryRule> = emptyList()
    override suspend fun upsert(rule: CategoryRule) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun learnFromCorrection(
        merchantNormalized: String,
        categoryId: String,
        patternType: PatternType,
    ): CategoryRule = CategoryRule(
        id = "unused",
        pattern = merchantNormalized,
        patternType = patternType,
        categoryId = categoryId,
        priority = 0,
        learnedFromUser = true,
        createdAt = FixedHistoryClock.now(),
    )
}

private class HistoryFakeCategoryRepository(
    private val rows: List<Category>,
) : CategoryRepository {
    override fun observeAll(kind: CategoryKind?, includeArchived: Boolean): Flow<List<Category>> = flowOf(
        rows.filter { (kind == null || it.kind == kind) && (includeArchived || !it.archived) },
    )

    override suspend fun get(id: String): Category? = rows.firstOrNull { it.id == id }
    override suspend fun upsert(category: Category) = Unit
    override suspend fun archive(id: String) = Unit
    override suspend fun reorder(ids: List<String>) = Unit
}

private object FixedHistoryClock : Clock {
    override fun now(): Instant = Instant.parse("2026-06-14T12:00:00Z")
}

private fun tx(id: String, type: TxType, status: TxStatus): Transaction = Transaction(
    id = id,
    accountId = "account",
    type = type,
    amount = Money.of("10"),
    date = LocalDate(2026, 6, 14),
    occurredAt = Instant.parse("2026-06-14T12:00:00Z"),
    merchant = id,
    merchantNormalized = id,
    categoryId = null,
    notes = null,
    source = IngestSource.SMS,
    sourceRefId = "sms-$id",
    status = status,
    confidence = null,
    createdAt = Instant.parse("2026-06-14T12:00:00Z"),
    updatedAt = Instant.parse("2026-06-14T12:00:00Z"),
)

private fun category(id: String, kind: CategoryKind): Category = Category(
    id = id,
    name = id,
    nameAr = id,
    kind = kind,
    icon = null,
    monthlyTarget = null,
    archived = false,
    sortOrder = 0,
)
