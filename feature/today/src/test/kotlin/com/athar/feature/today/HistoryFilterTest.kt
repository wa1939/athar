package com.athar.feature.today

import com.athar.core.common.money.Money
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class HistoryFilterTest {

    @Test
    fun `filters by source independently from status and type`() {
        val rows = listOf(
            tx(id = "sms-expense", source = IngestSource.SMS, status = TxStatus.PENDING),
            tx(id = "manual-expense", source = IngestSource.MANUAL, status = TxStatus.PENDING),
            tx(id = "notification-income", source = IngestSource.NOTIFICATION, type = TxType.INCOME),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.PENDING,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.SMS,
            category = HistoryCategoryFilter.ALL,
        )

        assertThat(filtered.map { it.id }).containsExactly("sms-expense")
    }

    @Test
    fun `searches source reference ids for imported and SMS rows`() {
        val rows = listOf(
            tx(id = "import", source = IngestSource.IMPORT, sourceRefId = "csv-row-42"),
            tx(id = "sms", source = IngestSource.SMS, sourceRefId = "content://sms/900"),
            tx(id = "manual", source = IngestSource.MANUAL, sourceRefId = null),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "row-42",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.ALL,
            category = HistoryCategoryFilter.ALL,
        )

        assertThat(filtered.map { it.id }).containsExactly("import")
    }

    @Test
    fun `keeps all source filter inclusive`() {
        val rows = listOf(
            tx(id = "sms", source = IngestSource.SMS),
            tx(id = "manual", source = IngestSource.MANUAL),
            tx(id = "recurring", source = IngestSource.RECURRING),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.ALL,
            category = HistoryCategoryFilter.ALL,
        )

        assertThat(filtered.map { it.id }).containsExactly("sms", "manual", "recurring").inOrder()
    }

    @Test
    fun `filters uncategorized rows across status type and source`() {
        val rows = listOf(
            tx(id = "sms-missing", source = IngestSource.SMS, status = TxStatus.PENDING, categoryId = null),
            tx(id = "sms-blank", source = IngestSource.SMS, status = TxStatus.PENDING, categoryId = " "),
            tx(id = "sms-categorized", source = IngestSource.SMS, status = TxStatus.PENDING, categoryId = "cat-food"),
            tx(id = "manual-missing", source = IngestSource.MANUAL, status = TxStatus.PENDING, categoryId = null),
            tx(id = "sms-income", source = IngestSource.SMS, status = TxStatus.PENDING, type = TxType.INCOME, categoryId = null),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.PENDING,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.SMS,
            category = HistoryCategoryFilter.UNCATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly("sms-missing", "sms-blank").inOrder()
    }

    @Test
    fun `filters repeated uncategorized actionable merchant groups within current filters`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = null,
                merchant = "Coffee A",
                merchantNormalized = "Coffee Shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = " ",
                merchant = "Coffee B",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "coffee-income",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                type = TxType.INCOME,
                categoryId = null,
                merchant = "Coffee Rebate",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "coffee-categorized",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = "cat-coffee",
                merchant = "Coffee Done",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "grocery-single",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = null,
                merchant = "Grocery",
                merchantNormalized = "grocery",
            ),
            tx(
                id = "transfer-a",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                type = TxType.TRANSFER,
                categoryId = null,
                merchant = "Wallet",
                merchantNormalized = "wallet",
            ),
            tx(
                id = "transfer-b",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                type = TxType.TRANSFER,
                categoryId = null,
                merchant = "Wallet",
                merchantNormalized = "wallet",
            ),
            tx(
                id = "manual-coffee",
                source = IngestSource.MANUAL,
                status = TxStatus.PENDING,
                categoryId = null,
                merchant = "Coffee Manual",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "unknown-a",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = null,
                merchant = "Unknown",
                merchantNormalized = "unknown",
            ),
            tx(
                id = "unknown-b",
                source = IngestSource.SMS,
                status = TxStatus.PENDING,
                categoryId = null,
                merchant = "Unknown",
                merchantNormalized = "unknown",
            ),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.PENDING,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.SMS,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly("coffee-a", "coffee-b").inOrder()
    }

    @Test
    fun `repeated uncategorized filter respects merchant search before grouping`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Coffee first",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Coffee second",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-a",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Tea first",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "tea-b",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Tea second",
                merchantNormalized = "tea shop",
            ),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "coffee",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.IMPORT,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly("coffee-a", "coffee-b").inOrder()
    }

    @Test
    fun `repeated uncategorized filter prioritizes largest visible merchant groups`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee first",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea first",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee second",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea second",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "tea-c",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea third",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "grocery-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Grocery first",
                merchantNormalized = "grocery",
            ),
            tx(
                id = "grocery-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Grocery second",
                merchantNormalized = "grocery",
            ),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.SMS,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly(
            "tea-a",
            "tea-b",
            "tea-c",
            "coffee-a",
            "coffee-b",
            "grocery-a",
            "grocery-b",
        ).inOrder()
    }

    @Test
    fun `uncategorized filter keeps original row order instead of repeated priority order`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee first",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea first",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee second",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea second",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "tea-c",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea third",
                merchantNormalized = "tea shop",
            ),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.EXPENSE,
            source = HistorySourceFilter.SMS,
            category = HistoryCategoryFilter.UNCATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly(
            "coffee-a",
            "tea-a",
            "coffee-b",
            "tea-b",
            "tea-c",
        ).inOrder()
    }

    @Test
    fun `top repeated backlog group ids choose largest visible group`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee first",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea first",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee second",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea second",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "tea-c",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea third",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "grocery-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Grocery first",
                merchantNormalized = "grocery",
            ),
            tx(
                id = "grocery-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Grocery second",
                merchantNormalized = "grocery",
            ),
        )

        val topGroup = topRepeatedBacklogGroupIds(
            visibleRows = rows,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(topGroup).containsExactly("tea-a", "tea-b", "tea-c")
        assertThat(
            topRepeatedBacklogGroupIds(
                visibleRows = rows,
                category = HistoryCategoryFilter.UNCATEGORIZED,
            ),
        ).isEmpty()
    }

    @Test
    fun `top repeated backlog group ids break equal group ties by first visible row`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee first",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "tea-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea first",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "tea-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Tea second",
                merchantNormalized = "tea shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee second",
                merchantNormalized = "coffee shop",
            ),
        )

        val topGroup = topRepeatedBacklogGroupIds(
            visibleRows = rows,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(topGroup).containsExactly("coffee-a", "coffee-b")
    }

    @Test
    fun `repeated backlog count map annotates repeated visible rows only`() {
        val rows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Coffee A",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.SMS,
                categoryId = " ",
                merchant = "Coffee B",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "grocery",
                source = IngestSource.SMS,
                categoryId = null,
                merchant = "Grocery",
                merchantNormalized = "grocery",
            ),
            tx(
                id = "coffee-income",
                source = IngestSource.SMS,
                type = TxType.INCOME,
                categoryId = null,
                merchant = "Coffee Income",
                merchantNormalized = "coffee shop",
            ),
        )

        val counts = buildRepeatedBacklogCountById(
            visibleRows = rows,
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(counts).containsExactly(
            "coffee-a", 2,
            "coffee-b", 2,
        )
        assertThat(
            buildRepeatedBacklogCountById(
                visibleRows = rows,
                category = HistoryCategoryFilter.UNCATEGORIZED,
            ),
        ).isEmpty()
    }

    @Test
    fun `filters categorized rows without hiding searched merchants`() {
        val rows = listOf(
            tx(id = "coffee-categorized", source = IngestSource.IMPORT, categoryId = "cat-coffee"),
            tx(id = "coffee-missing", source = IngestSource.IMPORT, categoryId = null),
            tx(id = "grocery-categorized", source = IngestSource.IMPORT, categoryId = "cat-groceries"),
        )

        val filtered = filterHistoryTransactions(
            all = rows,
            query = "coffee",
            status = HistoryStatusFilter.ALL,
            type = HistoryTypeFilter.ALL,
            source = HistorySourceFilter.IMPORT,
            category = HistoryCategoryFilter.CATEGORIZED,
        )

        assertThat(filtered.map { it.id }).containsExactly("coffee-categorized")
    }

    @Test
    fun `bulk category state offers expense categories and skips selected transfers`() {
        val state = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(id = "expense", source = IngestSource.SMS, type = TxType.EXPENSE),
                tx(id = "transfer", source = IngestSource.SMS, type = TxType.TRANSFER),
            ),
            selectedIds = setOf("expense", "transfer"),
            selectionMode = true,
            activeCategories = listOf(
                category(id = "cat-food", kind = CategoryKind.EXPENSE),
                category(id = "cat-salary", kind = CategoryKind.INCOME),
            ),
        )

        assertThat(state.canApply).isTrue()
        assertThat(state.selectedIds).containsExactly("expense", "transfer")
        assertThat(state.selectedCount).isEqualTo(2)
        assertThat(state.visibleCount).isEqualTo(2)
        assertThat(state.eligibleCount).isEqualTo(1)
        assertThat(state.skippedCount).isEqualTo(1)
        assertThat(state.categories.map { it.id }).containsExactly("cat-food")
    }

    @Test
    fun `bulk category state blocks mixed expense and income selections`() {
        val state = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(id = "expense", source = IngestSource.SMS, type = TxType.EXPENSE),
                tx(id = "income", source = IngestSource.SMS, type = TxType.INCOME),
            ),
            selectedIds = setOf("expense", "income"),
            selectionMode = true,
            activeCategories = listOf(
                category(id = "cat-food", kind = CategoryKind.EXPENSE),
                category(id = "cat-salary", kind = CategoryKind.INCOME),
            ),
        )

        assertThat(state.canApply).isFalse()
        assertThat(state.hasMixedCategoryKinds).isTrue()
        assertThat(state.categories).isEmpty()
    }

    @Test
    fun `bulk category state counts visible rows matching selected merchants`() {
        val state = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(
                    id = "coffee-a",
                    source = IngestSource.IMPORT,
                    merchant = "Coffee First",
                    merchantNormalized = "Coffee Shop",
                ),
                tx(
                    id = "coffee-b",
                    source = IngestSource.IMPORT,
                    merchant = "Coffee Second",
                    merchantNormalized = "coffee shop",
                ),
                tx(id = "grocery", source = IngestSource.IMPORT, merchantNormalized = "grocery"),
            ),
            selectedIds = setOf("coffee-a", "coffee-b"),
            selectionMode = true,
            activeCategories = listOf(category(id = "cat-food", kind = CategoryKind.EXPENSE)),
        )

        assertThat(state.selectedIds).containsExactly("coffee-a", "coffee-b")
        assertThat(state.selectedMerchantName).isEqualTo("Coffee First")
        assertThat(state.matchingMerchantCount).isEqualTo(2)
    }

    @Test
    fun `bulk category state hides selected merchant context for mixed or generic merchants`() {
        val activeCategories = listOf(category(id = "cat-food", kind = CategoryKind.EXPENSE))
        val mixedState = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(id = "coffee", source = IngestSource.IMPORT, merchantNormalized = "coffee shop"),
                tx(id = "grocery", source = IngestSource.IMPORT, merchantNormalized = "grocery"),
            ),
            selectedIds = setOf("coffee", "grocery"),
            selectionMode = true,
            activeCategories = activeCategories,
        )
        val genericState = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(
                    id = "unknown-a",
                    source = IngestSource.IMPORT,
                    merchant = "Unknown",
                    merchantNormalized = "unknown",
                ),
                tx(
                    id = "unknown-b",
                    source = IngestSource.IMPORT,
                    merchant = "Unknown",
                    merchantNormalized = "unknown",
                ),
            ),
            selectedIds = setOf("unknown-a", "unknown-b"),
            selectionMode = true,
            activeCategories = activeCategories,
        )

        assertThat(mixedState.selectedMerchantName).isNull()
        assertThat(genericState.selectedMerchantName).isNull()
    }

    @Test
    fun `bulk category state suggests unambiguous same merchant category from history`() {
        val visibleRows = listOf(
            tx(
                id = "coffee-a",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Coffee First",
                merchantNormalized = "coffee shop",
            ),
            tx(
                id = "coffee-b",
                source = IngestSource.IMPORT,
                categoryId = null,
                merchant = "Coffee Second",
                merchantNormalized = "coffee shop",
            ),
        )
        val state = buildHistoryBulkCategoryState(
            visibleRows = visibleRows,
            selectedIds = setOf("coffee-a", "coffee-b"),
            selectionMode = true,
            activeCategories = listOf(
                category(id = "cat-coffee", kind = CategoryKind.EXPENSE),
                category(id = "cat-groceries", kind = CategoryKind.EXPENSE),
                category(id = "cat-salary", kind = CategoryKind.INCOME),
            ),
            allRows = visibleRows + listOf(
                tx(
                    id = "coffee-old-a",
                    source = IngestSource.SMS,
                    categoryId = "cat-coffee",
                    merchant = "Coffee Old",
                    merchantNormalized = "Coffee Shop",
                ),
                tx(
                    id = "coffee-old-b",
                    source = IngestSource.SMS,
                    categoryId = "cat-coffee",
                    merchant = "Coffee Old 2",
                    merchantNormalized = "coffee shop",
                ),
                tx(
                    id = "coffee-income",
                    source = IngestSource.SMS,
                    type = TxType.INCOME,
                    categoryId = "cat-salary",
                    merchant = "Coffee Refund",
                    merchantNormalized = "coffee shop",
                ),
            ),
        )

        assertThat(state.suggestedCategoryId).isEqualTo("cat-coffee")
        assertThat(state.suggestedCategoryUseCount).isEqualTo(2)
        assertThat(state.canApplySuggestedCategory).isTrue()
    }

    @Test
    fun `bulk category state hides category suggestion for conflicting inactive or generic history`() {
        val activeCategories = listOf(
            category(id = "cat-coffee", kind = CategoryKind.EXPENSE),
            category(id = "cat-groceries", kind = CategoryKind.EXPENSE),
        )
        val visibleRows = listOf(
            tx(id = "coffee-a", source = IngestSource.IMPORT, merchantNormalized = "coffee shop"),
            tx(id = "coffee-b", source = IngestSource.IMPORT, merchantNormalized = "coffee shop"),
        )
        val conflictingState = buildHistoryBulkCategoryState(
            visibleRows = visibleRows,
            selectedIds = setOf("coffee-a", "coffee-b"),
            selectionMode = true,
            activeCategories = activeCategories,
            allRows = visibleRows + listOf(
                tx(
                    id = "coffee-old-a",
                    source = IngestSource.SMS,
                    categoryId = "cat-coffee",
                    merchantNormalized = "coffee shop",
                ),
                tx(
                    id = "coffee-old-b",
                    source = IngestSource.SMS,
                    categoryId = "cat-groceries",
                    merchantNormalized = "coffee shop",
                ),
            ),
        )
        val inactiveState = buildHistoryBulkCategoryState(
            visibleRows = visibleRows,
            selectedIds = setOf("coffee-a", "coffee-b"),
            selectionMode = true,
            activeCategories = activeCategories,
            allRows = visibleRows + listOf(
                tx(
                    id = "coffee-old",
                    source = IngestSource.SMS,
                    categoryId = "cat-archived",
                    merchantNormalized = "coffee shop",
                ),
            ),
        )
        val genericState = buildHistoryBulkCategoryState(
            visibleRows = listOf(
                tx(id = "unknown-a", source = IngestSource.IMPORT, merchantNormalized = "unknown"),
                tx(id = "unknown-b", source = IngestSource.IMPORT, merchantNormalized = "unknown"),
            ),
            selectedIds = setOf("unknown-a", "unknown-b"),
            selectionMode = true,
            activeCategories = activeCategories,
            allRows = listOf(
                tx(
                    id = "unknown-old",
                    source = IngestSource.SMS,
                    categoryId = "cat-coffee",
                    merchantNormalized = "unknown",
                ),
            ),
        )

        assertThat(conflictingState.suggestedCategoryId).isNull()
        assertThat(conflictingState.canApplySuggestedCategory).isFalse()
        assertThat(inactiveState.suggestedCategoryId).isNull()
        assertThat(inactiveState.canApplySuggestedCategory).isFalse()
        assertThat(genericState.suggestedCategoryId).isNull()
        assertThat(genericState.canApplySuggestedCategory).isFalse()
    }

    @Test
    fun `bulk category state exposes top repeated group selection state`() {
        val rows = listOf(
            tx(id = "tea-a", source = IngestSource.SMS, merchantNormalized = "tea shop"),
            tx(id = "tea-b", source = IngestSource.SMS, merchantNormalized = "tea shop"),
            tx(id = "tea-c", source = IngestSource.SMS, merchantNormalized = "tea shop"),
            tx(id = "coffee-a", source = IngestSource.SMS, merchantNormalized = "coffee shop"),
            tx(id = "coffee-b", source = IngestSource.SMS, merchantNormalized = "coffee shop"),
        )

        val emptySelection = buildHistoryBulkCategoryState(
            visibleRows = rows,
            selectedIds = emptySet(),
            selectionMode = true,
            activeCategories = listOf(category(id = "cat-food", kind = CategoryKind.EXPENSE)),
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )
        val topSelection = buildHistoryBulkCategoryState(
            visibleRows = rows,
            selectedIds = setOf("tea-a", "tea-b", "tea-c"),
            selectionMode = true,
            activeCategories = listOf(category(id = "cat-food", kind = CategoryKind.EXPENSE)),
            category = HistoryCategoryFilter.REPEATED_UNCATEGORIZED,
        )

        assertThat(emptySelection.topRepeatedGroupCount).isEqualTo(3)
        assertThat(emptySelection.selectedTopRepeatedGroupCount).isEqualTo(0)
        assertThat(emptySelection.canSelectTopRepeatedGroup).isTrue()
        assertThat(topSelection.topRepeatedGroupCount).isEqualTo(3)
        assertThat(topSelection.selectedTopRepeatedGroupCount).isEqualTo(3)
        assertThat(topSelection.canSelectTopRepeatedGroup).isFalse()
    }

    private fun tx(
        id: String,
        source: IngestSource,
        status: TxStatus = TxStatus.CONFIRMED,
        type: TxType = TxType.EXPENSE,
        sourceRefId: String? = "$source-$id",
        categoryId: String? = null,
        merchant: String = "Merchant $id",
        merchantNormalized: String = "merchant $id",
    ) = Transaction(
        id = id,
        accountId = "account",
        type = type,
        amount = Money.of(BigDecimal("10")),
        date = LocalDate(2026, 6, 13),
        occurredAt = Instant.parse("2026-06-13T00:00:00Z"),
        merchant = merchant,
        merchantNormalized = merchantNormalized,
        categoryId = categoryId,
        notes = null,
        source = source,
        sourceRefId = sourceRefId,
        status = status,
        confidence = null,
        createdAt = Instant.parse("2026-06-13T00:00:00Z"),
        updatedAt = Instant.parse("2026-06-13T00:00:00Z"),
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
}
