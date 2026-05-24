package com.athar.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.Category
import com.athar.core.domain.model.CategoryKind
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.CategoryRepository
import com.athar.core.domain.repo.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<PlanState> =
        month
            .flatMapLatest { m ->
                val period = Period.Month(m)
                combine(
                    categories.observeAll(kind = CategoryKind.EXPENSE),
                    transactions.observeByPeriod(period, status = TxStatus.CONFIRMED),
                ) { cats, txs ->
                    derive(m, cats, txs)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState.empty(currentMonth()))

    fun onEvent(event: PlanEvent) {
        when (event) {
            is PlanEvent.SaveTarget -> saveTarget(event.categoryId, event.targetMinor)
            is PlanEvent.OpenTargetEditor, PlanEvent.DismissTargetEditor -> Unit // UI-owned
        }
    }

    private fun saveTarget(categoryId: String, targetMinor: Long?) {
        viewModelScope.launch {
            val current = categories.get(categoryId) ?: return@launch
            val newTarget = targetMinor?.let { Money.ofMinor(it) }
            categories.upsert(current.copy(monthlyTarget = newTarget))
        }
    }

    private fun derive(
        month: YearMonth,
        cats: List<Category>,
        txs: List<Transaction>,
    ): PlanState {
        val byCategory = txs
            .filter { it.type == TxType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.fold(Money.zero()) { acc, tx -> acc + tx.amount } }

        val rows = cats.map { cat ->
            BudgetRow(
                category = cat,
                actual = byCategory[cat.id] ?: Money.zero(),
                target = cat.monthlyTarget,
            )
        }.toImmutableList()

        val totalTarget = cats.mapNotNull { it.monthlyTarget }.fold(Money.zero()) { acc, m -> acc + m }
        val totalActual = byCategory.values.fold(Money.zero()) { acc, m -> acc + m }

        return PlanState(
            month = month,
            rows = rows,
            totalTarget = totalTarget,
            totalActual = totalActual,
            isLoading = false,
        )
    }

    private fun currentMonth(): YearMonth {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
    }
}
