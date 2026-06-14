package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.IngestSource
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

enum class HistoryStatusFilter { ALL, CONFIRMED, PENDING, DISMISSED }
enum class HistoryTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }
enum class HistorySourceFilter { ALL, SMS, NOTIFICATION, MANUAL, IMPORT, RECURRING, SHARE }
enum class HistoryCategoryFilter { ALL, UNCATEGORIZED, REPEATED_UNCATEGORIZED, CATEGORIZED }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val rules: CategoryRuleRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _status = MutableStateFlow(HistoryStatusFilter.ALL)
    val status: StateFlow<HistoryStatusFilter> = _status.asStateFlow()

    private val _type = MutableStateFlow(HistoryTypeFilter.ALL)
    val type: StateFlow<HistoryTypeFilter> = _type.asStateFlow()

    private val _source = MutableStateFlow(HistorySourceFilter.ALL)
    val source: StateFlow<HistorySourceFilter> = _source.asStateFlow()

    private val _category = MutableStateFlow(HistoryCategoryFilter.ALL)
    val category: StateFlow<HistoryCategoryFilter> = _category.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _lastBulkCategory = MutableStateFlow<BulkCategoryEvent?>(null)
    val lastBulkCategory: StateFlow<BulkCategoryEvent?> = _lastBulkCategory.asStateFlow()

    val categoryLabels: StateFlow<ImmutableMap<String, CategoryLabel>> =
        categories.observeAll(kind = null, includeArchived = true)
            .map { it.toCategoryLabels() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    private val activeCategories: StateFlow<List<Category>> =
        categories.observeAll(kind = null, includeArchived = false)
            .map { rows -> rows.sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<Transaction>> =
        combine(
            transactions.observeAll(),
            combine(_query, _status, _type, _source, _category) { q, s, t, source, category ->
                HistoryFilterState(q, s, t, source, category)
            },
        ) { all, filter ->
            filterHistoryTransactions(
                all = all,
                query = filter.query,
                status = filter.status,
                type = filter.type,
                source = filter.source,
                category = filter.category,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bulkCategoryState: StateFlow<HistoryBulkCategoryState> =
        combine(items, _selectedIds, _selectionMode, activeCategories) { visibleRows, selectedIds, selectionMode, categories ->
            buildHistoryBulkCategoryState(
                visibleRows = visibleRows,
                selectedIds = selectedIds,
                selectionMode = selectionMode,
                activeCategories = categories,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryBulkCategoryState.Empty)

    fun setQuery(q: String) {
        _query.value = q
        clearSelection()
    }

    fun setStatus(s: HistoryStatusFilter) {
        _status.value = s
        clearSelection()
    }

    fun setType(t: HistoryTypeFilter) {
        _type.value = t
        clearSelection()
    }

    fun setSource(s: HistorySourceFilter) {
        _source.value = s
        clearSelection()
    }

    fun setCategory(c: HistoryCategoryFilter) {
        _category.value = c
        clearSelection()
    }

    private val _lastBackfill = MutableStateFlow<BackfillEvent?>(null)
    /** Emits the count of dismissed/pending rows auto-recategorized by "Always categorize…". */
    val lastBackfill: StateFlow<BackfillEvent?> = _lastBackfill.asStateFlow()

    fun clearBackfill() { _lastBackfill.value = null }

    fun clearBulkCategory() { _lastBulkCategory.value = null }

    fun toggleSelectionMode() {
        val next = !_selectionMode.value
        _selectionMode.value = next
        if (!next) _selectedIds.value = emptySet()
    }

    fun clearSelection() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelected(id: String) {
        _selectionMode.value = true
        _selectedIds.value = _selectedIds.value.toggle(id)
    }

    fun selectVisibleRows() {
        val visibleIds = items.value.mapTo(mutableSetOf()) { it.id }
        if (visibleIds.isNotEmpty()) {
            _selectionMode.value = true
            _selectedIds.value = visibleIds
        }
    }

    fun selectMatchingSelectedMerchants() {
        val visibleRows = items.value
        val selectedMerchantKeys = visibleRows
            .filter { it.id in _selectedIds.value }
            .mapNotNull { it.merchantSelectionKey() }
            .toSet()
        if (selectedMerchantKeys.isNotEmpty()) {
            _selectionMode.value = true
            _selectedIds.value = visibleRows
                .filter { it.merchantSelectionKey() in selectedMerchantKeys }
                .mapTo(mutableSetOf()) { it.id }
        }
    }

    fun updateTransaction(tx: Transaction, learnRule: Boolean) {
        viewModelScope.launch {
            val now = clock.now()
            val confirmed = tx.copy(status = TxStatus.CONFIRMED, updatedAt = now)
            transactions.upsert(confirmed)
            val categoryId = confirmed.categoryId
            if (learnRule && categoryId != null && confirmed.merchantNormalized.isNotBlank()) {
                rules.learnFromCorrection(
                    merchantNormalized = confirmed.merchantNormalized,
                    categoryId = categoryId,
                    patternType = PatternType.SUBSTRING,
                )
                val backfilled = transactions.applyCategoryToMatching(
                    pattern = confirmed.merchantNormalized,
                    categoryId = categoryId,
                )
                _lastBackfill.value = BackfillEvent(
                    pattern = confirmed.merchant.ifBlank { confirmed.merchantNormalized },
                    count = backfilled,
                )
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { transactions.delete(id) }
    }

    fun applyBulkCategory(categoryId: String) {
        viewModelScope.launch {
            val category = categories.get(categoryId)?.takeUnless { it.archived } ?: return@launch
            val now = clock.now()
            var applied = 0
            var skipped = 0
            val appliedRows = mutableListOf<Transaction>()
            _selectedIds.value.forEach { id ->
                val tx = transactions.get(id)
                if (tx != null && tx.categoryKind() == category.kind) {
                    val updated = tx.copy(
                        categoryId = category.id,
                        status = TxStatus.CONFIRMED,
                        updatedAt = now,
                    )
                    transactions.upsert(updated)
                    appliedRows += updated
                    applied += 1
                } else {
                    skipped += 1
                }
            }
            val exactRuleLearned = learnExactRuleForRepeatedMerchant(appliedRows, category.id)
            _lastBulkCategory.value = BulkCategoryEvent(
                applied = applied,
                skipped = skipped,
                exactRuleLearned = exactRuleLearned,
            )
            clearSelection()
        }
    }

    private suspend fun learnExactRuleForRepeatedMerchant(
        appliedRows: List<Transaction>,
        categoryId: String,
    ): Boolean {
        if (appliedRows.size < 2) return false
        val merchantKey = appliedRows
            .mapNotNull { it.merchantSelectionKey() }
            .distinct()
            .singleOrNull()
            ?.takeIf { it.isSpecificMerchantKey() }
            ?: return false

        val matchingRules = rules.findMatching(merchantKey)
        val exactMatches = matchingRules.filter {
            it.patternType == PatternType.EXACT &&
                it.pattern.trim().equals(merchantKey, ignoreCase = true)
        }
        if (exactMatches.any { it.categoryId == categoryId }) return false
        if (matchingRules.any { it.learnedFromUser && it.patternType != PatternType.EXACT }) return false

        exactMatches
            .filter { it.learnedFromUser }
            .forEach { rules.delete(it.id) }
        rules.learnFromCorrection(
            merchantNormalized = merchantKey,
            categoryId = categoryId,
            patternType = PatternType.EXACT,
        )
        return true
    }

    data class BackfillEvent(val pattern: String, val count: Int)
    data class BulkCategoryEvent(
        val applied: Int,
        val skipped: Int,
        val exactRuleLearned: Boolean = false,
    )
}

private data class HistoryFilterState(
    val query: String,
    val status: HistoryStatusFilter,
    val type: HistoryTypeFilter,
    val source: HistorySourceFilter,
    val category: HistoryCategoryFilter,
)

internal fun filterHistoryTransactions(
    all: List<Transaction>,
    query: String,
    status: HistoryStatusFilter,
    type: HistoryTypeFilter,
    source: HistorySourceFilter,
    category: HistoryCategoryFilter,
): List<Transaction> {
    val baseRows = all.asSequence()
        .filter { tx ->
            when (status) {
                HistoryStatusFilter.ALL -> true
                HistoryStatusFilter.CONFIRMED -> tx.status == TxStatus.CONFIRMED
                HistoryStatusFilter.PENDING -> tx.status == TxStatus.PENDING
                HistoryStatusFilter.DISMISSED -> tx.status == TxStatus.DISMISSED
            }
        }
        .filter { tx ->
            when (type) {
                HistoryTypeFilter.ALL -> true
                HistoryTypeFilter.INCOME -> tx.type == TxType.INCOME
                HistoryTypeFilter.EXPENSE -> tx.type == TxType.EXPENSE
                HistoryTypeFilter.TRANSFER -> tx.type == TxType.TRANSFER
            }
        }
        .filter { tx ->
            when (source) {
                HistorySourceFilter.ALL -> true
                HistorySourceFilter.SMS -> tx.source == IngestSource.SMS
                HistorySourceFilter.NOTIFICATION -> tx.source == IngestSource.NOTIFICATION
                HistorySourceFilter.MANUAL -> tx.source == IngestSource.MANUAL
                HistorySourceFilter.IMPORT -> tx.source == IngestSource.IMPORT
                HistorySourceFilter.RECURRING -> tx.source == IngestSource.RECURRING
                HistorySourceFilter.SHARE -> tx.source == IngestSource.SHARE
            }
        }
        .filter { tx ->
            if (query.isBlank()) {
                true
            } else {
                tx.merchant.contains(query, ignoreCase = true) ||
                    tx.merchantNormalized.contains(query, ignoreCase = true) ||
                    (tx.notes?.contains(query, ignoreCase = true) == true) ||
                    (tx.sourceRefId?.contains(query, ignoreCase = true) == true)
            }
        }
        .toList()

    if (category != HistoryCategoryFilter.REPEATED_UNCATEGORIZED) {
        return baseRows.filter { tx ->
            when (category) {
                HistoryCategoryFilter.ALL -> true
                HistoryCategoryFilter.UNCATEGORIZED -> tx.categoryId.isNullOrBlank()
                HistoryCategoryFilter.CATEGORIZED -> !tx.categoryId.isNullOrBlank()
                HistoryCategoryFilter.REPEATED_UNCATEGORIZED -> false
            }
        }
    }

    val repeatedKeys = baseRows
        .mapNotNull { it.uncategorizedMerchantGroupKey() }
        .groupingBy { it }
        .eachCount()
        .filterValues { it > 1 }
        .keys

    return baseRows.filter { it.uncategorizedMerchantGroupKey() in repeatedKeys }
}

data class HistoryBulkCategoryState(
    val selectionMode: Boolean,
    val selectedIds: Set<String>,
    val selectedCount: Int,
    val visibleCount: Int,
    val matchingMerchantCount: Int,
    val eligibleCount: Int,
    val skippedCount: Int,
    val hasMixedCategoryKinds: Boolean,
    val categories: List<Category>,
) {
    val canApply: Boolean = selectionMode && selectedCount > 0 && eligibleCount > 0 && !hasMixedCategoryKinds

    companion object {
        val Empty = HistoryBulkCategoryState(
            selectionMode = false,
            selectedIds = emptySet(),
            selectedCount = 0,
            visibleCount = 0,
            matchingMerchantCount = 0,
            eligibleCount = 0,
            skippedCount = 0,
            hasMixedCategoryKinds = false,
            categories = emptyList(),
        )
    }
}

internal fun buildHistoryBulkCategoryState(
    visibleRows: List<Transaction>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    activeCategories: List<Category>,
): HistoryBulkCategoryState {
    val selectedRows = visibleRows.filter { it.id in selectedIds }
    val selectedKinds = selectedRows.mapNotNull { it.categoryKind() }.distinct()
    val categoryKind = selectedKinds.singleOrNull()
    val selectedMerchantKeys = selectedRows.mapNotNull { it.merchantSelectionKey() }.toSet()
    return HistoryBulkCategoryState(
        selectionMode = selectionMode,
        selectedIds = selectedRows.mapTo(mutableSetOf()) { it.id },
        selectedCount = selectedRows.size,
        visibleCount = visibleRows.size,
        matchingMerchantCount = if (selectedMerchantKeys.isEmpty()) {
            0
        } else {
            visibleRows.count { it.merchantSelectionKey() in selectedMerchantKeys }
        },
        eligibleCount = selectedRows.count { it.categoryKind() != null },
        skippedCount = selectedRows.count { it.categoryKind() == null },
        hasMixedCategoryKinds = selectedKinds.size > 1,
        categories = categoryKind
            ?.let { kind ->
                activeCategories
                    .filter { it.kind == kind && !it.archived }
                    .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.name })
            }
            .orEmpty(),
    )
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private fun Transaction.categoryKind(): CategoryKind? = when (type) {
    TxType.EXPENSE -> CategoryKind.EXPENSE
    TxType.INCOME -> CategoryKind.INCOME
    TxType.TRANSFER -> null
}

private data class UncategorizedMerchantGroupKey(
    val merchantKey: String,
    val categoryKind: CategoryKind,
)

private fun Transaction.uncategorizedMerchantGroupKey(): UncategorizedMerchantGroupKey? {
    if (!categoryId.isNullOrBlank()) return null
    val kind = categoryKind() ?: return null
    val merchantKey = merchantSelectionKey()?.takeIf { it.isSpecificMerchantKey() } ?: return null
    return UncategorizedMerchantGroupKey(merchantKey = merchantKey, categoryKind = kind)
}

private fun Transaction.merchantSelectionKey(): String? =
    merchantNormalized
        .ifBlank { merchant }
        .trim()
        .lowercase()
        .takeIf { it.isNotBlank() }

private fun String.isSpecificMerchantKey(): Boolean {
    if (length < 3) return false
    if (all { it.isDigit() || it.isWhitespace() || it == '-' || it == '+' }) return false
    if (this in genericMerchantKeys) return false
    if (genericMerchantKeys.any { this == it || startsWith("$it ") }) return false
    return true
}

private val genericMerchantKeys = setOf(
    "unknown",
    "merchant",
    "bank",
    "cash",
    "purchase",
    "online purchase",
    "transfer",
    "payment",
    "manual adjustment",
    "غير معروف",
    "تاجر",
    "بنك",
    "شراء",
    "تحويل",
    "دفع",
    "تسوية",
    "تسوية يدوية",
)
