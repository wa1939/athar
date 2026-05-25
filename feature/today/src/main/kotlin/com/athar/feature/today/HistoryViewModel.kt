package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

enum class HistoryStatusFilter { ALL, CONFIRMED, PENDING, DISMISSED }
enum class HistoryTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val rules: CategoryRuleRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _status = MutableStateFlow(HistoryStatusFilter.ALL)
    val status: StateFlow<HistoryStatusFilter> = _status.asStateFlow()

    private val _type = MutableStateFlow(HistoryTypeFilter.ALL)
    val type: StateFlow<HistoryTypeFilter> = _type.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<Transaction>> =
        combine(transactions.observeAll(), _query, _status, _type) { all, q, s, t ->
            all.asSequence()
                .filter { tx ->
                    when (s) {
                        HistoryStatusFilter.ALL -> true
                        HistoryStatusFilter.CONFIRMED -> tx.status == TxStatus.CONFIRMED
                        HistoryStatusFilter.PENDING -> tx.status == TxStatus.PENDING
                        HistoryStatusFilter.DISMISSED -> tx.status == TxStatus.DISMISSED
                    }
                }
                .filter { tx ->
                    when (t) {
                        HistoryTypeFilter.ALL -> true
                        HistoryTypeFilter.INCOME -> tx.type == TxType.INCOME
                        HistoryTypeFilter.EXPENSE -> tx.type == TxType.EXPENSE
                        HistoryTypeFilter.TRANSFER -> tx.type == TxType.TRANSFER
                    }
                }
                .filter { tx ->
                    if (q.isBlank()) true
                    else tx.merchant.contains(q, ignoreCase = true) ||
                        (tx.notes?.contains(q, ignoreCase = true) == true)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setStatus(s: HistoryStatusFilter) { _status.value = s }
    fun setType(t: HistoryTypeFilter) { _type.value = t }

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
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { transactions.delete(id) }
    }
}
