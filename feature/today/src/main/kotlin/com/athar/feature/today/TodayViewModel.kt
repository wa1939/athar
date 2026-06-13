package com.athar.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athar.core.common.money.Money
import com.athar.core.common.time.Period
import com.athar.core.domain.calc.GoalCalc
import com.athar.core.domain.model.AccountBalance
import com.athar.core.domain.model.AccountType
import com.athar.core.domain.model.NetWorth
import com.athar.core.domain.model.PatternType
import com.athar.core.domain.model.Transaction
import com.athar.core.domain.model.TxStatus
import com.athar.core.domain.model.TxType
import com.athar.core.domain.model.isReconciliation
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
import java.math.BigDecimal
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
                val goalsPeriod = last3MonthsPeriod()
                prefs.displayCurrency().flatMapLatest { currency ->
                    combine(
                        combine(
                            transactions.observeByPeriod(period, status = TxStatus.CONFIRMED),
                            transactions.observePending(),
                            transactions.observeByPeriod(period, status = TxStatus.DISMISSED),
                            accounts.observeNetWorth(currency),
                        ) { confirmed, pending, dismissed, netWorth ->
                            TodayInputs(confirmed, pending, dismissed, netWorth)
                        },
                        transactions.observeByPeriod(goalsPeriod, status = TxStatus.CONFIRMED),
                        prefs.savingsRateTargetPercent(),
                        prefs.emergencyFundTargetMonths(),
                    ) { inputs, goalTransactions, savingsTarget, emergencyMonths ->
                        deriveState(
                            month = m,
                            confirmed = inputs.confirmed,
                            pending = inputs.pending,
                            dismissed = inputs.dismissed,
                            currency = currency,
                            netWorth = inputs.netWorth,
                            goalNudge = deriveGoalNudge(
                                transactions = goalTransactions,
                                netWorth = inputs.netWorth,
                                currency = currency,
                                savingsTarget = savingsTarget,
                                emergencyMonths = emergencyMonths,
                            ),
                        )
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
                // Backfill: apply the same category to every other PENDING/DISMISSED
                // row whose merchant matches. Otherwise picking "Always categorize Hemmah
                // as Home maintenance" would only fix the one row the user just edited,
                // leaving every other Hemmah charge stranded in the dismissed tray.
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

    private val _lastBackfill = MutableStateFlow<BackfillEvent?>(null)
    /**
     * Emits the last "Always categorize X as Y" backfill result so the UI can show
     * a transient toast ("Applied to N other Hemmah charges"). Cleared by [clearBackfill].
     */
    val lastBackfill: StateFlow<BackfillEvent?> = _lastBackfill

    fun clearBackfill() { _lastBackfill.value = null }

    data class BackfillEvent(val pattern: String, val count: Int)

    fun deleteTransaction(id: String) {
        viewModelScope.launch { transactions.delete(id) }
    }

    private fun deriveState(
        month: YearMonth,
        confirmed: List<Transaction>,
        pending: List<Transaction>,
        dismissed: List<Transaction>,
        currency: String,
        netWorth: NetWorth,
        goalNudge: TodayGoalNudge?,
    ): TodayState {
        // Reconciliation adjustments only affect net worth; never count them as income/expense.
        val operating = confirmed.filterNot { it.isReconciliation() }
        val (income, expense) = operating.partition { it.type == TxType.INCOME }
        val incomeSum = Money.sumAmounts(income.map { it.amount }, currency)
        val expenseSum = Money.sumAmounts(expense.map { it.amount }, currency)
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val (todayTxns, monthTxns) = confirmed.partition { it.date == today }
        val todayOperating = todayTxns.filterNot { it.isReconciliation() }
        val (todayIncome, todayExpense) = todayOperating.partition { it.type == TxType.INCOME }
        val todayNet = Money.sumAmounts(todayIncome.map { it.amount }, currency) -
            Money.sumAmounts(todayExpense.map { it.amount }, currency)
        val dismissedToday = dismissed.filter { it.date == today }
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
            dismissedToday = dismissedToday.toImmutableList(),
            goalNudge = goalNudge,
            isLoading = false,
        )
    }

    private fun deriveGoalNudge(
        transactions: List<Transaction>,
        netWorth: NetWorth,
        currency: String,
        savingsTarget: Int,
        emergencyMonths: Int,
    ): TodayGoalNudge {
        val operating = transactions.filterNot { it.isReconciliation() }
        val totalIncome = Money.sumAmounts(
            operating.filter { it.type == TxType.INCOME }.map { it.amount },
            currency,
        )
        val totalExpense = Money.sumAmounts(
            operating.filter { it.type == TxType.EXPENSE }.map { it.amount },
            currency,
        )
        val monthlyIncome = GoalCalc.averageMonthly(totalIncome, LOOKBACK_MONTHS)
        val monthlyExpense = GoalCalc.averageMonthly(totalExpense, LOOKBACK_MONTHS)
        val savingsRate = GoalCalc.savingsRatePercent(monthlyIncome, monthlyExpense)
        val emergencyCovered = GoalCalc.emergencyMonthsCovered(
            liquidBalance(netWorth.accounts, currency),
            monthlyExpense,
        )

        return TodayGoalNudge(
            savingsRatePercent = savingsRate,
            savingsRateTargetPercent = savingsTarget,
            savingsRateProgress = GoalCalc.savingsRateProgress(savingsRate, savingsTarget).toProgressFloat(),
            emergencyMonthsCovered = emergencyCovered,
            emergencyFundTargetMonths = emergencyMonths,
            emergencyFundProgress = GoalCalc.emergencyProgress(emergencyCovered, emergencyMonths).toProgressFloat(),
        )
    }

    private fun liquidBalance(balances: List<AccountBalance>, currency: String): Money =
        Money.sumAmounts(
            balances
                .filter { it.account.type in LIQUID_ACCOUNT_TYPES }
                .map { it.current },
            currency,
        ).let { if (it.amount.signum() < 0) Money.zero(currency) else it }

    private fun last3MonthsPeriod(): Period {
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return Period.Last(months = LOOKBACK_MONTHS, endingAt = today)
    }

    private fun currentMonth(): YearMonth {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return YearMonth.of(now.year, now.monthNumber)
    }

    private fun BigDecimal.toProgressFloat(): Float =
        toFloat().coerceIn(0f, 1f)

    private data class TodayInputs(
        val confirmed: List<Transaction>,
        val pending: List<Transaction>,
        val dismissed: List<Transaction>,
        val netWorth: NetWorth,
    )

    companion object {
        private const val LOOKBACK_MONTHS = 3
        private val LIQUID_ACCOUNT_TYPES = setOf(
            AccountType.CHECKING,
            AccountType.SAVINGS,
            AccountType.CASH,
        )
    }
}
