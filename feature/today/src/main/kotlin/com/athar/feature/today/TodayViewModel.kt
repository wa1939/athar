package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.repo.AccountRepository
import com.athar.core.domain.repo.CategoryRuleRepository
import com.athar.core.domain.repo.TransactionRepository
import com.athar.core.domain.repo.UserPreferencesRepository
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
class TodayViewModel @Inject constructor(
    private val transactions: TransactionRepository,
    private val rules: CategoryRuleRepository,
    private val prefs: UserPreferencesRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) : ViewModel() {

    private val month = MutableStateFlow(currentMonth())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState> =
        month
            .flatMapLatest { m ->
                val period = Period.Month(m)
                prefs.displayCurrency().flatMapLatest { currency ->
                    combine(
                        transactions.observeByPeriod(period, status = TxStatus.CONFIRMED),
                        transactions.observePending(),
                        accounts.observeNetWorth(currency),
                    ) { confirmed, pending, netWorth ->
                        deriveState(m, confirmed, pending, currency, netWorth)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState.empty(currentMonth()))

    fun onEvent(event: TodayEvent) {
        when (event) {
            is TodayEvent.ConfirmPending -> viewModelScope.launch {
                transactions.setStatus(event.id, TxStatus.CONFIRMED)
            }
            is TodayEvent.DismissPending -> viewModelScope.launch {
                transactions.setStatus(event.id, TxStatus.DISMISSED)
            }
            is TodayEvent.OpenTransaction, TodayEvent.AddManual, TodayEvent.OpenHistory -> Unit // UI-owned
            TodayEvent.BulkConfirmConfident -> viewModelScope.launch {
                transactions.confirmAllConfident(minConfidence = 0.85f)
            }
            TodayEvent.BulkDismissLowConfidence -> viewModelScope.launch {
                transactions.dismissAllLowConfidence(maxConfidence = 0.70f)
            }
            TodayEvent.BulkDismissAll -> viewModelScope.launch {
                transactions.dismissAllPending()
            }
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
            }
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch { transactions.delete(id) }
    }

    private fun deriveState(
        month: YearMonth,
        confirmed: List<Transaction>,
        pending: List<Transaction>,
        currency: String,
        netWorth: com.athar.core.domain.model.NetWorth,
    ): TodayState {
        val (income, expense) = confirmed.partition { it.type == TxType.INCOME }
        val incomeSum = Money.sumAmounts(income.map { it.amount }, currency)
        val expenseSum = Money.sumAmounts(expense.map { it.amount }, currency)
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val (todayTxns, monthTxns) = confirmed.partition { it.date == today }
        val (todayIncome, todayExpense) = todayTxns.partition { it.type == TxType.INCOME }
        val todayNet = Money.sumAmounts(todayIncome.map { it.amount }, currency) -
            Money.sumAmounts(todayExpense.map { it.amount }, currency)
        return TodayState(
            month = month,
            netFlow = incomeSum - expenseSum,
            totalExpense = expenseSum,
            totalIncome = incomeSum,
            todayNet = todayNet,
            netWorth = netWorth.total,
            netWorthIsMixed = netWorth.isMixedCurrency,
            today = todayTxns.toImmutableList(),
            recent = monthTxns.take(10).toImmutableList(),
            pending = pending.toImmutableList(),
            isLoading = false,
        )
    }

    private fun currentMonth(): YearMonth {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
    }
}
