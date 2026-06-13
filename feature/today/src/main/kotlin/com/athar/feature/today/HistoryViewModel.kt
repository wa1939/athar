package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
enum class HistoryCategoryFilter { ALL, UNCATEGORIZED, CATEGORIZED }

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

    val categoryLabels: StateFlow<ImmutableMap<String, CategoryLabel>> =
        categories.observeAll(kind = null, includeArchived = true)
            .map { it.toCategoryLabels() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

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

    fun setQuery(q: String) { _query.value = q }
    fun setStatus(s: HistoryStatusFilter) { _status.value = s }
    fun setType(t: HistoryTypeFilter) { _type.value = t }
    fun setSource(s: HistorySourceFilter) { _source.value = s }
    fun setCategory(c: HistoryCategoryFilter) { _category.value = c }

    private val _lastBackfill = MutableStateFlow<BackfillEvent?>(null)
    /** Emits the count of dismissed/pending rows auto-recategorized by "Always categorize…". */
    val lastBackfill: StateFlow<BackfillEvent?> = _lastBackfill.asStateFlow()

    fun clearBackfill() { _lastBackfill.value = null }

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

    data class BackfillEvent(val pattern: String, val count: Int)
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
): List<Transaction> =
    all.asSequence()
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
            when (category) {
                HistoryCategoryFilter.ALL -> true
                HistoryCategoryFilter.UNCATEGORIZED -> tx.categoryId.isNullOrBlank()
                HistoryCategoryFilter.CATEGORIZED -> !tx.categoryId.isNullOrBlank()
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
